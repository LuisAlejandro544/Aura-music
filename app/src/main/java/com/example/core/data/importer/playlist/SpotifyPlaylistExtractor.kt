package com.example.core.data.importer.playlist

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import com.example.debug.AuraDebugManager
import com.example.core.model.ExternalPlaylistPreview
import com.example.core.model.ExternalPlaylistSource
import com.example.core.model.ExternalPlaylistTrackItem
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Extractor de playlists públicas de Spotify sin API key ni login.
 * Rol arquitectónico: fuente Spotify; la fachada ExternalPlaylistImporter delega aquí.
 */
internal object SpotifyPlaylistExtractor {

    private const val TAG = "ExternalPlaylistImporter"

    private const val USER_AGENT_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    internal suspend fun fetchSpotifyPlaylist(
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
            val oembedJson = PlaylistHttpClient.httpGetText(oembedUrl)
            if (!oembedJson.isNullOrBlank()) {
                val obj = JSONObject(oembedJson)
                fallbackTitle = obj.optString("title", fallbackTitle).ifBlank { fallbackTitle }
                fallbackCover = obj.optString("thumbnail_url", "")
            }
        } catch (_: Exception) {}

        // Paso 2: Consultar la página pública de Embed de Spotify que contiene el árbol JSON hidratado (__NEXT_DATA__)
        val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId?utm_source=generator"
        val embedHtml = PlaylistHttpClient.httpGetText(embedUrl)
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
        val mainPageHtml = PlaylistHttpClient.httpGetText("https://open.spotify.com/playlist/$playlistId")
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
    internal fun extractSpotifyPlaylistId(url: String): String? {
        val regex = Regex("playlist/([a-zA-Z0-9]{15,30})")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }
    internal fun parseSpotifyEmbedHtml(
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
    internal fun extractSpotifyCoverFromEntity(entity: JSONObject): String {
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
    internal fun parseSpotifyTracksFromRawHtml(
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
            val rawTitle = PlaylistHttpClient.unescapeJsonText(match.groupValues[1]).trim()
            val rawArtist = PlaylistHttpClient.unescapeJsonText(match.groupValues[2]).replace("\u00A0", " ").trim()
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
    internal suspend fun extractSpotifyPlaylistViaWebView(
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
                                    val unquoted = PlaylistHttpClient.unescapeJsEvaluation(rawEval)
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
}
