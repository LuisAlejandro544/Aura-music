package com.example.core.data.importer.playlist

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.core.data.importer.YtDlpNativeEngine
import com.example.core.data.streaming.StreamingApiService
import com.example.debug.AuraDebugManager
import com.example.core.model.ExternalPlaylistPreview
import com.example.core.model.ExternalPlaylistSource
import com.example.core.model.ExternalPlaylistTrackItem
import com.example.core.model.StreamingSearchFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import kotlin.coroutines.resume

/**
 * Importador Universal de Playlists Públicas desde Spotify y YouTube Music / YouTube.
 *
 * Rol Arquitectónico:
 * - Opera 100% sin claves de API externas, sin archivos `.env` y sin requerir inicio de sesión.
 * - Soporta enlaces públicos de Spotify (`open.spotify.com/playlist/...`, `spotify.link/...`)
 *   y de YouTube Music / YouTube (`music.youtube.com/playlist?list=...`, `youtube.com/playlist?list=...`).
 * - Para Spotify:
 *   1. Extrae metadatos y pistas mediante el endpoint público de Embed (`open.spotify.com/embed/playlist/{id}`)
 *      analizando el bloque JSON hidratado (`__NEXT_DATA__` / `initial-state`) y el endpoint `oembed`.
 *   2. Si Spotify sirve hidratación dinámica por cliente, emplea un WebView ligero fuera de pantalla
 *      para extraer la lista de pistas del DOM/JSON.
 * - Para YouTube Music / YouTube:
 *   1. Consulta directamente el endpoint `browse` de InnerTube (`VL` + `playlistId`) en milisegundos.
 *   2. Cuenta con respaldo directo en `YtDlpNativeEngine.resolveFlatPlaylist` (`--flat-playlist --dump-single-json`).
 */
object ExternalPlaylistImporter {

    private const val TAG = "ExternalPlaylistImporter"

    private const val USER_AGENT_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    /**
     * Determina si una URL corresponde a una Playlist de Spotify o YouTube Music / YouTube.
     */
    fun isPlaylistUrl(rawUrl: String): Boolean {
        val clean = rawUrl.trim()
        if (!clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
            return false
        }
        val lower = clean.lowercase()
        if (lower.contains("spotify.com/playlist/") || lower.contains("spotify.com/embed/playlist/")) {
            return true
        }
        if ((lower.contains("youtube.com") || lower.contains("youtu.be")) && lower.contains("list=")) {
            // Si es un enlace directo a /playlist?list= o tiene list=PL / list=OLAK / list=UU
            if (lower.contains("/playlist") ||
                lower.contains("list=pl") ||
                lower.contains("list=olak") ||
                lower.contains("list=fl") ||
                lower.contains("list=uu")
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Detecta la plataforma de origen a partir de la URL ingresada por el usuario.
     */
    fun detectSourcePlatform(rawUrl: String): ExternalPlaylistSource? {
        val lower = rawUrl.trim().lowercase()
        return when {
            lower.contains("spotify.com") || lower.contains("spotify.link") -> ExternalPlaylistSource.SPOTIFY
            lower.contains("youtube.com") || lower.contains("youtu.be") -> ExternalPlaylistSource.YOUTUBE_MUSIC
            else -> null
        }
    }

    /**
     * Analiza una URL de Playlist pública de Spotify o YouTube Music y devuelve su previsualización
     * completa (título, autor, portada y lista de canciones).
     */
    suspend fun fetchPlaylistPreview(
        context: Context,
        rawUrl: String
    ): Result<ExternalPlaylistPreview> = withContext(Dispatchers.IO) {
        val cleanUrl = rawUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Ingresa un enlace válido de Playlist de Spotify o YouTube Music."))
        }

        val resolvedUrl = if (cleanUrl.contains("spotify.link", ignoreCase = true)) {
            resolveRedirectUrl(cleanUrl) ?: cleanUrl
        } else {
            cleanUrl
        }

        val platform = detectSourcePlatform(resolvedUrl)
            ?: return@withContext Result.failure(
                IllegalArgumentException("El enlace no pertenece a Spotify ni a YouTube Music / YouTube.")
            )

        return@withContext when (platform) {
            ExternalPlaylistSource.SPOTIFY -> fetchSpotifyPlaylist(context, resolvedUrl)
            ExternalPlaylistSource.YOUTUBE_MUSIC -> fetchYouTubePlaylist(context, resolvedUrl)
        }
    }

    /**
     * Empareja una pista de Spotify (`Título + Artista`) con el catálogo de YouTube Music
     * para obtener su `videoId`, miniatura HD y flujo de audio/video reproducible.
     */
    suspend fun matchSpotifyTrackToStreaming(
        item: ExternalPlaylistTrackItem
    ): ExternalPlaylistTrackItem = withContext(Dispatchers.IO) {
        if (item.videoIdOrUrl.length == 11 && !item.videoIdOrUrl.contains("/")) {
            return@withContext item
        }
        val query = "${item.title} ${item.artist}".trim()
        val results = StreamingApiService.searchStreamingCatalog(
            query = query,
            filter = StreamingSearchFilter.YT_MUSIC
        )
        val bestMatch = results.firstOrNull() ?: StreamingApiService.searchStreamingCatalog(
            query = query,
            filter = StreamingSearchFilter.YOUTUBE
        ).firstOrNull()

        if (bestMatch != null) {
            item.copy(
                videoIdOrUrl = bestMatch.videoId,
                thumbnailUrl = item.thumbnailUrl.ifBlank { bestMatch.thumbnailUrl },
                durationSeconds = if (item.durationSeconds > 0L) item.durationSeconds else bestMatch.durationSeconds
            )
        } else {
            item
        }
    }

    /**
     * Empareja un lote de pistas de Spotify en paralelo controlado (bloques de 5)
     * reportando el progreso al coordinador.
     */
    suspend fun matchSpotifyTracksBatch(
        tracks: List<ExternalPlaylistTrackItem>,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): List<ExternalPlaylistTrackItem> = withContext(Dispatchers.IO) {
        val total = tracks.size
        if (total == 0) return@withContext emptyList()
        val matched = MutableList(total) { tracks[it] }
        var completedCount = 0

        tracks.chunked(5).forEachIndexed { chunkIdx, chunk ->
            val chunkResults = coroutineScope {
                chunk.map { trackItem ->
                    async {
                        matchSpotifyTrackToStreaming(trackItem)
                    }
                }.awaitAll()
            }
            chunkResults.forEachIndexed { innerIdx, resolvedItem ->
                val globalIdx = chunkIdx * 5 + innerIdx
                if (globalIdx < matched.size) {
                    matched[globalIdx] = resolvedItem
                }
            }
            completedCount = (completedCount + chunk.size).coerceAtMost(total)
            onProgress(completedCount, total)
        }
        matched
    }

    // =========================================================================
    // 1. EXTRACTOR DE PLAYLISTS DE SPOTIFY (SIN API KEY NI LOGIN)
    // =========================================================================

    private suspend fun fetchSpotifyPlaylist(
        context: Context,
        url: String
    ): Result<ExternalPlaylistPreview> {
        val playlistId = extractSpotifyPlaylistId(url)
            ?: return Result.failure(
                IllegalArgumentException("No se encontró el ID de la Playlist de Spotify en el enlace proporcionado.")
            )

        AuraDebugManager.logInfo(TAG, "Analizando Playlist de Spotify ID=$playlistId")

        // Paso 1: Obtener título y portada desde oEmbed público de Spotify (instantáneo)
        var fallbackTitle = "Playlist de Spotify"
        var fallbackCover = ""
        try {
            val oembedUrl = "https://open.spotify.com/oembed?url=https://open.spotify.com/playlist/$playlistId"
            val oembedJson = httpGetText(oembedUrl)
            if (!oembedJson.isNullOrBlank()) {
                val obj = JSONObject(oembedJson)
                fallbackTitle = obj.optString("title", fallbackTitle).ifBlank { fallbackTitle }
                fallbackCover = obj.optString("thumbnail_url", "")
            }
        } catch (_: Exception) {}

        // Paso 2: Consultar la página pública de Embed de Spotify que contiene el árbol JSON hidratado (__NEXT_DATA__)
        val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId?utm_source=generator"
        val embedHtml = httpGetText(embedUrl)
        if (!embedHtml.isNullOrBlank()) {
            val parsedFromEmbed = parseSpotifyEmbedHtml(
                html = embedHtml,
                sourceUrl = url,
                playlistId = playlistId,
                fallbackTitle = fallbackTitle,
                fallbackCover = fallbackCover
            )
            if (parsedFromEmbed != null && parsedFromEmbed.tracks.isNotEmpty()) {
                AuraDebugManager.logInfo(TAG, "Spotify Embed extrajo ${parsedFromEmbed.tracks.size} canciones para '${parsedFromEmbed.title}'")
                return Result.success(parsedFromEmbed)
            }
        }

        // Paso 3: Consultar la página pública principal de Spotify (meta tags / initial-state)
        val mainPageHtml = httpGetText("https://open.spotify.com/playlist/$playlistId")
        if (!mainPageHtml.isNullOrBlank()) {
            val parsedFromMain = parseSpotifyEmbedHtml(
                html = mainPageHtml,
                sourceUrl = url,
                playlistId = playlistId,
                fallbackTitle = fallbackTitle,
                fallbackCover = fallbackCover
            )
            if (parsedFromMain != null && parsedFromMain.tracks.isNotEmpty()) {
                return Result.success(parsedFromMain)
            }
        }

        // Paso 4: Respaldo con WebView invisible para hidratar __NEXT_DATA__ / DOM de Spotify Embed
        val webViewPreview = extractSpotifyPlaylistViaWebView(
            context = context,
            embedUrl = embedUrl,
            sourceUrl = url,
            playlistId = playlistId,
            fallbackTitle = fallbackTitle,
            fallbackCover = fallbackCover
        )
        if (webViewPreview != null && webViewPreview.tracks.isNotEmpty()) {
            return Result.success(webViewPreview)
        }

        return Result.failure(
            IllegalStateException("No se pudieron leer las pistas de la Playlist de Spotify. Verifica que la Playlist sea pública.")
        )
    }

    private fun extractSpotifyPlaylistId(url: String): String? {
        val regex = Regex("playlist/([a-zA-Z0-9]{15,30})")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }

    private fun parseSpotifyEmbedHtml(
        html: String,
        sourceUrl: String,
        playlistId: String,
        fallbackTitle: String,
        fallbackCover: String
    ): ExternalPlaylistPreview? {
        try {
            // Buscar script __NEXT_DATA__
            val nextDataRegex = Regex("<script[^>]*id=[\"']__NEXT_DATA__[\"'][^>]*>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE)
            val nextDataMatch = nextDataRegex.find(html)
            if (nextDataMatch != null) {
                val jsonText = nextDataMatch.groupValues[1].trim()
                val root = JSONObject(jsonText)
                val entity = root.optJSONObject("props")
                    ?.optJSONObject("pageProps")
                    ?.optJSONObject("state")
                    ?.optJSONObject("data")
                    ?.optJSONObject("entity")

                if (entity != null) {
                    val title = entity.optString("name", "").ifBlank { entity.optString("title", fallbackTitle) }
                    val owner = entity.optString("subtitle", "")
                        .ifBlank { entity.optJSONObject("ownerV2")?.optJSONObject("data")?.optString("name", "") ?: "" }
                        .ifBlank { "Spotify" }

                    val coverUrl = extractSpotifyCoverFromEntity(entity).ifBlank { fallbackCover }
                    val trackListArr = entity.optJSONArray("trackList")
                    val tracks = mutableListOf<ExternalPlaylistTrackItem>()

                    if (trackListArr != null) {
                        for (i in 0 until trackListArr.length()) {
                            val tObj = trackListArr.optJSONObject(i) ?: continue
                            val trackTitle = tObj.optString("title", "").trim()
                            if (trackTitle.isBlank()) continue
                            val trackArtist = tObj.optString("subtitle", "")
                                .replace("\u00A0", " ")
                                .trim()
                                .ifBlank { "Artista" }
                            val durationMs = tObj.optLong("duration", 0L)
                            val uri = tObj.optString("uri", "")
                            val trackThumb = tObj.optString("imageUrl", "").ifBlank { coverUrl }

                            tracks.add(
                                ExternalPlaylistTrackItem(
                                    index = tracks.size + 1,
                                    title = trackTitle,
                                    artist = trackArtist,
                                    album = title,
                                    durationSeconds = (durationMs / 1000L).coerceAtLeast(0L),
                                    thumbnailUrl = trackThumb,
                                    videoIdOrUrl = uri,
                                    sourcePlatform = ExternalPlaylistSource.SPOTIFY
                                )
                            )
                        }
                    }

                    if (tracks.isNotEmpty()) {
                        return ExternalPlaylistPreview(
                            sourceUrl = sourceUrl,
                            playlistId = playlistId,
                            title = title,
                            author = owner,
                            coverUrl = coverUrl,
                            source = ExternalPlaylistSource.SPOTIFY,
                            tracks = tracks
                        )
                    }
                }
            }

            // Buscar bloque resource/initial-state alternativo en páginas de Spotify
            val tracksFromRegex = parseSpotifyTracksFromRawHtml(html, fallbackTitle, fallbackCover)
            if (tracksFromRegex.isNotEmpty()) {
                return ExternalPlaylistPreview(
                    sourceUrl = sourceUrl,
                    playlistId = playlistId,
                    title = fallbackTitle,
                    author = "Spotify",
                    coverUrl = fallbackCover,
                    source = ExternalPlaylistSource.SPOTIFY,
                    tracks = tracksFromRegex
                )
            }
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Error parseando HTML de Spotify Embed: ${e.message}")
        }
        return null
    }

    private fun extractSpotifyCoverFromEntity(entity: JSONObject): String {
        val coverArt = entity.optJSONObject("coverArt")
        val sources = coverArt?.optJSONArray("sources")
            ?: entity.optJSONObject("visualIdentity")?.optJSONObject("image")?.optJSONArray("sources")
            ?: entity.optJSONArray("images")
        if (sources != null && sources.length() > 0) {
            var bestUrl = ""
            var bestWidth = -1
            for (i in 0 until sources.length()) {
                val src = sources.optJSONObject(i) ?: continue
                val url = src.optString("url", "")
                val width = src.optInt("width", 0)
                if (url.startsWith("http", ignoreCase = true) && width >= bestWidth) {
                    bestWidth = width
                    bestUrl = url
                }
            }
            if (bestUrl.isNotBlank()) return bestUrl
        }
        return ""
    }

    private fun parseSpotifyTracksFromRawHtml(
        html: String,
        playlistTitle: String,
        coverUrl: String
    ): List<ExternalPlaylistTrackItem> {
        val list = mutableListOf<ExternalPlaylistTrackItem>()
        // Buscar objetos JSON tipo {"uri":"spotify:track:...","title":"...","subtitle":"...","duration":...}
        val itemRegex = Regex(
            "\\{[^{}]*\"title\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)+)\"[^{}]*\"subtitle\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)+)\"[^{}]*\"duration\"\\s*:\\s*(\\d+)[^{}]*\\}"
        )
        val seen = mutableSetOf<String>()
        for (match in itemRegex.findAll(html)) {
            val rawTitle = unescapeJsonText(match.groupValues[1]).trim()
            val rawArtist = unescapeJsonText(match.groupValues[2]).replace("\u00A0", " ").trim()
            val durationMs = match.groupValues[3].toLongOrNull() ?: 0L
            if (rawTitle.isBlank() || !seen.add("$rawTitle|$rawArtist")) continue
            list.add(
                ExternalPlaylistTrackItem(
                    index = list.size + 1,
                    title = rawTitle,
                    artist = rawArtist.ifBlank { "Artista" },
                    album = playlistTitle,
                    durationSeconds = (durationMs / 1000L).coerceAtLeast(0L),
                    thumbnailUrl = coverUrl,
                    sourcePlatform = ExternalPlaylistSource.SPOTIFY
                )
            )
        }
        return list
    }

    private suspend fun extractSpotifyPlaylistViaWebView(
        context: Context,
        embedUrl: String,
        sourceUrl: String,
        playlistId: String,
        fallbackTitle: String,
        fallbackCover: String
    ): ExternalPlaylistPreview? = withTimeoutOrNull(14_000L) {
        suspendCancellableCoroutine { cont ->
            val mainHandler = Handler(Looper.getMainLooper())
            var finished = false
            var webViewRef: WebView? = null

            fun finishOnce(result: ExternalPlaylistPreview?) {
                if (finished) return
                finished = true
                mainHandler.post {
                    try {
                        webViewRef?.stopLoading()
                        webViewRef?.destroy()
                    } catch (_: Throwable) {}
                }
                if (cont.isActive) cont.resume(result)
            }

            mainHandler.post {
                try {
                    val webView = WebView(context.applicationContext)
                    webViewRef = webView
                    webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = true
                        loadsImagesAutomatically = false
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        userAgentString = USER_AGENT_DESKTOP
                    }
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            super.onPageFinished(view, loadedUrl)
                            mainHandler.postDelayed({
                                if (finished) return@postDelayed
                                val js = "(function() { " +
                                    "var el = document.getElementById('__NEXT_DATA__'); " +
                                    "if (el) return el.innerHTML; " +
                                    "return document.documentElement.outerHTML; " +
                                    "})();"
                                view?.evaluateJavascript(js) { rawEval ->
                                    if (finished) return@evaluateJavascript
                                    val unquoted = unescapeJsEvaluation(rawEval)
                                    val preview = if (unquoted.startsWith("{")) {
                                        parseSpotifyEmbedHtml(
                                            html = "<script id=\"__NEXT_DATA__\">$unquoted</script>",
                                            sourceUrl = sourceUrl,
                                            playlistId = playlistId,
                                            fallbackTitle = fallbackTitle,
                                            fallbackCover = fallbackCover
                                        )
                                    } else {
                                        parseSpotifyEmbedHtml(
                                            html = unquoted,
                                            sourceUrl = sourceUrl,
                                            playlistId = playlistId,
                                            fallbackTitle = fallbackTitle,
                                            fallbackCover = fallbackCover
                                        )
                                    }
                                    finishOnce(preview)
                                }
                            }, 1800L)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = false
                    }
                    webView.loadUrl(embedUrl)
                } catch (_: Throwable) {
                    finishOnce(null)
                }
            }
        }
    }

    // =========================================================================
    // 2. EXTRACTOR DE PLAYLISTS DE YOUTUBE MUSIC / YOUTUBE
    // =========================================================================

    private suspend fun fetchYouTubePlaylist(
        context: Context,
        url: String
    ): Result<ExternalPlaylistPreview> {
        val playlistId = extractYouTubePlaylistId(url)
            ?: return Result.failure(
                IllegalArgumentException("No se encontró el parámetro 'list=' en el enlace de YouTube Music / YouTube.")
            )

        AuraDebugManager.logInfo(TAG, "Analizando Playlist de YouTube Music / YouTube ID=$playlistId")

        // Paso 1: Consultar endpoint 'browse' de InnerTube (WEB_REMIX y WEB)
        val innerTubePreview = fetchYouTubePlaylistViaInnerTube(url, playlistId)
        if (innerTubePreview != null && innerTubePreview.tracks.isNotEmpty()) {
            AuraDebugManager.logInfo(TAG, "InnerTube browse extrajo ${innerTubePreview.tracks.size} canciones para '${innerTubePreview.title}'")
            return Result.success(innerTubePreview)
        }

        // Paso 2: Respaldo con yt-dlp --flat-playlist
        val canonicalPlaylistUrl = "https://www.youtube.com/playlist?list=$playlistId"
        val flatDump = YtDlpNativeEngine.resolveFlatPlaylist(context, canonicalPlaylistUrl).getOrNull()
        if (flatDump != null && flatDump.entries.isNotEmpty()) {
            val tracks = flatDump.entries.mapIndexed { idx, entry ->
                ExternalPlaylistTrackItem(
                    index = idx + 1,
                    title = entry.title,
                    artist = entry.uploader,
                    album = flatDump.title,
                    durationSeconds = entry.durationSeconds,
                    thumbnailUrl = entry.thumbnailUrl,
                    videoIdOrUrl = entry.id,
                    sourcePlatform = ExternalPlaylistSource.YOUTUBE_MUSIC
                )
            }
            return Result.success(
                ExternalPlaylistPreview(
                    sourceUrl = url,
                    playlistId = playlistId,
                    title = flatDump.title.ifBlank { "Playlist de YouTube Music" },
                    author = flatDump.uploader.ifBlank { "YouTube Music" },
                    coverUrl = flatDump.thumbnailUrl.ifBlank { tracks.firstOrNull()?.thumbnailUrl.orEmpty() },
                    source = ExternalPlaylistSource.YOUTUBE_MUSIC,
                    tracks = tracks
                )
            )
        }

        return Result.failure(
            IllegalStateException("No se pudo cargar la Playlist de YouTube Music / YouTube. Verifica que sea pública o no listada.")
        )
    }

    private fun extractYouTubePlaylistId(url: String): String? {
        val regex = Regex("[?&]list=([a-zA-Z0-9_-]+)")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }

    private fun fetchYouTubePlaylistViaInnerTube(
        sourceUrl: String,
        playlistId: String
    ): ExternalPlaylistPreview? {
        val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"

        // Intentar primero con el cliente WEB de YouTube estándar (soporta tanto playlists de YT como de YT Music)
        val webBody = JSONObject().apply {
            put("browseId", browseId)
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20240724.00.00")
                    put("hl", "es")
                    put("gl", "US")
                })
            })
        }

        val responseStr = postJson(
            endpoint = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false",
            body = webBody.toString(),
            headers = mapOf(
                "User-Agent" to USER_AGENT_DESKTOP,
                "Origin" to "https://www.youtube.com"
            )
        )

        if (!responseStr.isNullOrBlank()) {
            val parsed = parseYouTubeBrowseResponse(JSONObject(responseStr), sourceUrl, playlistId)
            if (parsed != null && parsed.tracks.isNotEmpty()) {
                return parsed
            }
        }

        // Intentar también con WEB_REMIX (YouTube Music)
        val remixBody = JSONObject().apply {
            put("browseId", browseId)
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20240724.01.00")
                    put("hl", "es")
                    put("gl", "US")
                })
            })
        }

        val remixStr = postJson(
            endpoint = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false",
            body = remixBody.toString(),
            headers = mapOf(
                "User-Agent" to USER_AGENT_DESKTOP,
                "Origin" to "https://music.youtube.com"
            )
        )

        if (!remixStr.isNullOrBlank()) {
            val parsedRemix = parseYouTubeBrowseResponse(JSONObject(remixStr), sourceUrl, playlistId)
            if (parsedRemix != null && parsedRemix.tracks.isNotEmpty()) {
                return parsedRemix
            }
        }

        return null
    }

    private fun parseYouTubeBrowseResponse(
        root: JSONObject,
        sourceUrl: String,
        playlistId: String
    ): ExternalPlaylistPreview? {
        try {
            var playlistTitle = root.optJSONObject("metadata")
                ?.optJSONObject("playlistMetadataRenderer")
                ?.optString("title", "")
                .orEmpty()

            var author = ""
            var coverUrl = ""

            val headerRenderer = root.optJSONObject("header")?.optJSONObject("playlistHeaderRenderer")
                ?: root.optJSONObject("header")?.optJSONObject("musicDetailHeaderRenderer")
                ?: root.optJSONObject("header")?.optJSONObject("musicResponsiveHeaderRenderer")

            if (headerRenderer != null) {
                if (playlistTitle.isBlank()) {
                    playlistTitle = extractRunsText(headerRenderer.optJSONObject("title"))
                }
                author = extractRunsText(headerRenderer.optJSONObject("ownerText"))
                    .ifBlank { extractRunsText(headerRenderer.optJSONObject("straplineTextOne")) }
                coverUrl = extractBestThumbnail(
                    headerRenderer.optJSONObject("playlistHeaderBanner")
                        ?.optJSONObject("heroPlaylistThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?: headerRenderer.optJSONObject("thumbnail")
                            ?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")
                )
            }

            val tracks = mutableListOf<ExternalPlaylistTrackItem>()
            val seenIds = mutableSetOf<String>()

            // 1. Recolectar playlistVideoRenderer (YouTube Web)
            val videoRenderers = mutableListOf<JSONObject>()
            collectObjectsByKey(root, "playlistVideoRenderer", videoRenderers)
            for (vr in videoRenderers) {
                val videoId = vr.optString("videoId", "")
                if (videoId.length != 11 || !seenIds.add(videoId)) continue
                val title = extractRunsText(vr.optJSONObject("title"))
                if (title.isBlank() || title.equals("[Deleted video]", ignoreCase = true) || title.equals("[Private video]", ignoreCase = true)) {
                    continue
                }
                val artist = extractRunsText(vr.optJSONObject("shortBylineText"))
                    .replace(" - Topic", "")
                    .trim()
                    .ifBlank { "YouTube Music" }
                val durationSecs = vr.optString("lengthSeconds", "0").toLongOrNull()
                    ?: parseColonDuration(extractRunsText(vr.optJSONObject("lengthText")))
                val thumb = extractBestThumbnail(vr.optJSONObject("thumbnail"))
                    .ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }

                tracks.add(
                    ExternalPlaylistTrackItem(
                        index = tracks.size + 1,
                        title = title,
                        artist = artist,
                        album = playlistTitle.ifBlank { "YouTube Music" },
                        durationSeconds = durationSecs,
                        thumbnailUrl = thumb,
                        videoIdOrUrl = videoId,
                        sourcePlatform = ExternalPlaylistSource.YOUTUBE_MUSIC
                    )
                )
            }

            // 2. Recolectar musicResponsiveListItemRenderer (YouTube Music WEB_REMIX)
            if (tracks.isEmpty()) {
                val musicRenderers = mutableListOf<JSONObject>()
                collectObjectsByKey(root, "musicResponsiveListItemRenderer", musicRenderers)
                for (mr in musicRenderers) {
                    val videoId = mr.optJSONObject("playlistItemData")?.optString("videoId", "")
                        ?: mr.optJSONObject("overlay")?.optJSONObject("musicItemThumbnailOverlayRenderer")
                            ?.optJSONObject("content")?.optJSONObject("musicPlayButtonRenderer")
                            ?.optJSONObject("playNavigationEndpoint")?.optJSONObject("watchEndpoint")
                            ?.optString("videoId", "")
                        ?: ""
                    if (videoId.length != 11 || !seenIds.add(videoId)) continue

                    val flexCols = mr.optJSONArray("flexColumns") ?: continue
                    val title = if (flexCols.length() > 0) {
                        extractRunsText(
                            flexCols.optJSONObject(0)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                        )
                    } else ""
                    if (title.isBlank()) continue

                    val artist = if (flexCols.length() > 1) {
                        extractRunsText(
                            flexCols.optJSONObject(1)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                        ).split("•").firstOrNull()?.trim().orEmpty()
                    } else "YouTube Music"

                    val fixedCols = mr.optJSONArray("fixedColumns")
                    val durationText = if (fixedCols != null && fixedCols.length() > 0) {
                        extractRunsText(
                            fixedCols.optJSONObject(0)
                                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                                ?.optJSONObject("text")
                        )
                    } else ""
                    val durationSecs = parseColonDuration(durationText)
                    val thumb = extractBestThumbnail(
                        mr.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")
                    ).ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }

                    tracks.add(
                        ExternalPlaylistTrackItem(
                            index = tracks.size + 1,
                            title = title,
                            artist = artist.replace(" - Topic", "").ifBlank { "YouTube Music" },
                            album = playlistTitle.ifBlank { "YouTube Music" },
                            durationSeconds = durationSecs,
                            thumbnailUrl = thumb,
                            videoIdOrUrl = videoId,
                            sourcePlatform = ExternalPlaylistSource.YOUTUBE_MUSIC
                        )
                    )
                }
            }

            if (tracks.isEmpty()) return null

            val finalCover = coverUrl.ifBlank { tracks.firstOrNull()?.thumbnailUrl.orEmpty() }
            return ExternalPlaylistPreview(
                sourceUrl = sourceUrl,
                playlistId = playlistId,
                title = playlistTitle.ifBlank { "Playlist de YouTube Music" },
                author = author.ifBlank { "YouTube Music" },
                coverUrl = finalCover,
                source = ExternalPlaylistSource.YOUTUBE_MUSIC,
                tracks = tracks
            )
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Error parseando browse de YouTube Playlist: ${e.message}")
            return null
        }
    }

    // =========================================================================
    // UTILIDADES HTTP Y JSON
    // =========================================================================

    private fun collectObjectsByKey(node: Any?, targetKey: String, out: MutableList<JSONObject>) {
        if (out.size >= 250) return
        when (node) {
            is JSONObject -> {
                if (node.has(targetKey)) {
                    val child = node.optJSONObject(targetKey)
                    if (child != null) out.add(child)
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    collectObjectsByKey(node.opt(k), targetKey, out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectObjectsByKey(node.opt(i), targetKey, out)
                }
            }
        }
    }

    private fun extractRunsText(textObj: JSONObject?): String {
        if (textObj == null) return ""
        val simple = textObj.optString("simpleText", "")
        if (simple.isNotBlank()) return simple
        val runs = textObj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text", "") ?: "")
        }
        return sb.toString().trim()
    }

    private fun extractBestThumbnail(thumbnailObj: JSONObject?): String {
        val arr = thumbnailObj?.optJSONArray("thumbnails") ?: return ""
        if (arr.length() == 0) return ""
        return arr.optJSONObject(arr.length() - 1)?.optString("url", "") ?: ""
    }

    private fun parseColonDuration(text: String): Long {
        val clean = text.trim()
        if (!clean.contains(":")) return 0L
        val parts = clean.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60L + parts[1]
            3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
            else -> 0L
        }
    }

    private fun unescapeJsonText(raw: String): String {
        return try {
            JSONObject("{\"v\":\"$raw\"}").optString("v", raw)
        } catch (_: Exception) {
            raw.replace("\\\"", "\"").replace("\\\\", "\\")
        }
    }

    private fun unescapeJsEvaluation(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        return try {
            JSONArray("[$raw]").optString(0, "")
        } catch (_: Exception) {
            raw.trim('"')
        }
    }

    private fun resolveRedirectUrl(shortUrl: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(shortUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", USER_AGENT_DESKTOP)
            }
            conn.connect()
            conn.url?.toString()
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun httpGetText(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 9000
                readTimeout = 10000
                setRequestProperty("User-Agent", USER_AGENT_DESKTOP)
                setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            }
            if (conn.responseCode !in 200..299) return null
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun postJson(
        endpoint: String,
        body: String,
        headers: Map<String, String>
    ): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 9000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
                writer.flush()
            }
            if (conn.responseCode !in 200..299) return null
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
