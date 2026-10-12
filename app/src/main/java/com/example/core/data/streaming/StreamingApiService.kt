package com.example.core.data.streaming

import android.content.Context
import com.example.core.data.importer.InnerTubeClient
import com.example.core.data.importer.OnlineVideoAudioImporter
import com.example.core.data.importer.WebStreamExtractor
import com.example.core.data.importer.YtDlpNativeEngine
import com.example.debug.AuraDebugManager
import com.example.core.model.StreamingSearchFilter
import com.example.core.model.StreamingSearchItem
import com.example.core.model.StreamingSourcePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente de API para el Modo Streaming Híbrido.
 *
 * Rol Arquitectónico:
 * 1. Búsqueda Unificada por API (sin usar yt-dlp para buscar):
 *    Consulta simultáneamente la API de YouTube Music (`WEB_REMIX` en `music.youtube.com`)
 *    y la API de YouTube (`WEB` / `ANDROID` en `youtube.com`), etiquetando cada resultado
 *    con su insignia correspondiente (`YT MUSIC` vs `YOUTUBE`).
 * 2. Recomendación de Canción Similar (Radio / Autoplay antes de terminar la canción):
 *    Consulta el endpoint `/youtubei/v1/next` (Automix / Watch Next) para obtener la siguiente
 *    pista similar recomendada antes de que finalice la pista actual.
 * 3. Resolución de Reproducción con `yt-dlp`:
 *    Una vez elegida la canción o la recomendación similar, normaliza enlaces de `music.youtube.com`
 *    y utiliza `YtDlpNativeEngine` (con respaldo `InnerTubeClient`) para obtener las URLs reales
 *    de audio y Video Canvas para reproducir y pre-cargar en caché.
 */
object StreamingApiService {

    private const val TAG = "StreamingApiService"

    private const val YT_MUSIC_SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val YT_STANDARD_SEARCH_URL = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val YT_MUSIC_NEXT_URL = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false"
    private const val YT_STANDARD_NEXT_URL = "https://www.youtube.com/youtubei/v1/next?prettyPrint=false"

    /**
     * Busca canciones y videos musicales en tiempo real según el filtro elegido por el usuario:
     * - `ALL`: Combina e intercala resultados de YouTube Music (`YT MUSIC`), YouTube (`YOUTUBE`) y SoundCloud (`SOUNDCLOUD`).
     * - `YT_MUSIC`: Exclusivamente canciones de YouTube Music con insignia `YT MUSIC`.
     * - `YOUTUBE`: Exclusivamente videos musicales de YouTube con insignia `YOUTUBE`.
     * - `SOUNDCLOUD`: Exclusivamente pistas, remixes y sets de SoundCloud con insignia `SOUNDCLOUD`.
     */
    suspend fun searchStreamingCatalog(
        query: String,
        filter: StreamingSearchFilter = StreamingSearchFilter.ALL,
        context: Context? = null
    ): List<StreamingSearchItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext getDefaultTrendingCatalog(filter, context)

        coroutineScope {
            val ytMusicDeferred = async {
                if (filter == StreamingSearchFilter.ALL || filter == StreamingSearchFilter.YT_MUSIC) {
                    searchYouTubeMusicApi(cleanQuery)
                } else {
                    emptyList()
                }
            }
            val ytStandardDeferred = async {
                if (filter == StreamingSearchFilter.ALL || filter == StreamingSearchFilter.YOUTUBE) {
                    searchYouTubeStandardApi(cleanQuery)
                } else {
                    emptyList()
                }
            }
            val soundCloudDeferred = async {
                if (filter == StreamingSearchFilter.ALL || filter == StreamingSearchFilter.SOUNDCLOUD) {
                    SoundCloudApiClient.searchTracks(
                        context = context,
                        query = cleanQuery,
                        limit = if (filter == StreamingSearchFilter.SOUNDCLOUD) 25 else 14
                    )
                } else {
                    emptyList()
                }
            }

            val musicResults = ytMusicDeferred.await()
            val videoResults = ytStandardDeferred.await()
            val soundCloudResults = soundCloudDeferred.await()

            when (filter) {
                StreamingSearchFilter.YT_MUSIC -> musicResults.ifEmpty {
                    videoResults.map { it.copy(platform = StreamingSourcePlatform.YT_MUSIC) }
                }
                StreamingSearchFilter.YOUTUBE -> videoResults
                StreamingSearchFilter.SOUNDCLOUD -> soundCloudResults
                StreamingSearchFilter.ALL -> interleaveResults(musicResults, videoResults, soundCloudResults)
            }
        }
    }

    /**
     * Obtiene una lista inicial de descubrimiento/tendencias cuando el buscador está vacío.
     */
    suspend fun getDefaultTrendingCatalog(
        filter: StreamingSearchFilter = StreamingSearchFilter.ALL,
        context: Context? = null
    ): List<StreamingSearchItem> = withContext(Dispatchers.IO) {
        val defaultQuery = if (filter == StreamingSearchFilter.SOUNDCLOUD) {
            "Electronic Phonk Remix Hits"
        } else {
            "Top Hits Music Global"
        }
        searchStreamingCatalog(defaultQuery, filter, context)
    }

    /**
     * Consulta a la API antes de terminar la canción actual para obtener una pista similar (Radio / Automix)
     * que aún no haya sido reproducida en la sesión reciente.
     */
    suspend fun fetchSimilarNextTrack(
        currentVideoId: String,
        currentTitle: String,
        currentArtist: String,
        preferredPlatform: StreamingSourcePlatform = StreamingSourcePlatform.YT_MUSIC,
        excludedVideoIds: Set<String> = emptySet()
    ): StreamingSearchItem? = withContext(Dispatchers.IO) {
        val cleanVideoId = currentVideoId.trim()
        if (cleanVideoId.isBlank()) return@withContext null

        if (preferredPlatform == StreamingSourcePlatform.SOUNDCLOUD) {
            val scSimilar = SoundCloudApiClient.fetchSimilarTracks(
                currentTitle = currentTitle,
                currentArtist = currentArtist,
                excludedIds = excludedVideoIds + cleanVideoId
            )
            if (scSimilar.isNotEmpty()) {
                return@withContext scSimilar.first()
            }
        }

        // 1. Consultar endpoint Watch Next / Automix de YouTube Music o YouTube
        if (cleanVideoId.length == 11) {
            val relatedFromNext = fetchRelatedFromNextEndpoint(cleanVideoId, preferredPlatform)
                .filter { it.videoId != cleanVideoId && !excludedVideoIds.contains(it.videoId) }

            if (relatedFromNext.isNotEmpty()) {
                return@withContext relatedFromNext.first()
            }
        }

        // 2. Respaldo por búsqueda semántica de artista/canción similar en la API
        val fallbackQuery = if (currentArtist.isNotBlank() && !currentArtist.equals("Artista desconocido", ignoreCase = true)) {
            "$currentArtist mix canciones similares"
        } else {
            "$currentTitle mix"
        }
        val searchCandidates = searchStreamingCatalog(
            query = fallbackQuery,
            filter = when (preferredPlatform) {
                StreamingSourcePlatform.YT_MUSIC -> StreamingSearchFilter.YT_MUSIC
                StreamingSourcePlatform.SOUNDCLOUD -> StreamingSearchFilter.SOUNDCLOUD
                StreamingSourcePlatform.YOUTUBE -> StreamingSearchFilter.ALL
            }
        ).filter { it.videoId != cleanVideoId && !excludedVideoIds.contains(it.videoId) }

        searchCandidates.firstOrNull()
    }

    /**
     * Toma un elemento de búsqueda o canción similar de la API (`YT Music`, `YouTube` o `SoundCloud`)
     * y resuelve los flujos listos para reproducir en ExoPlayer, pre-cargar en caché o descargar:
     * - Si la plataforma es `SOUNDCLOUD`:
     *   Resuelve mediante `SoundCloudApiClient.resolvePlayableStream` (API v2 progresiva + respaldo `yt-dlp`).
     * - Si la plataforma es `YOUTUBE` (`StreamingSourcePlatform.YOUTUBE`):
     *   1º Utiliza exclusivamente `yt-dlp` (`YtDlpNativeEngine`, CPython + QuickJS) en primer lugar.
     *   2º Si el video presenta bloqueo o restricción, aplica respaldo automático con `InnerTubeClient` y `WebStreamExtractor`.
     * - Si la plataforma es `YT_MUSIC` (`StreamingSourcePlatform.YT_MUSIC`):
     *   1º Resuelve directamente con `InnerTubeClient` (`<300ms`) y respaldo `InvidiousStreamResolver`,
     *      sin invocar `yt-dlp` (ya que `yt-dlp` se reserva para pistas de YouTube/SoundCloud).
     */
    suspend fun resolvePlayableStreamWithYtDlp(
        context: Context,
        item: StreamingSearchItem,
        forceBypassYtDlp: Boolean = false
    ): Result<OnlineVideoAudioImporter.ResolvedMediaInfo> = withContext(Dispatchers.IO) {
        val watchUrl = item.canonicalWatchUrl

        fun enrichAndRegister(info: OnlineVideoAudioImporter.ResolvedMediaInfo): Result<OnlineVideoAudioImporter.ResolvedMediaInfo> {
            val enriched = info.copy(
                suggestedTitle = item.title.ifBlank { info.suggestedTitle },
                suggestedArtist = item.artist.ifBlank { info.suggestedArtist },
                coverUrl = item.thumbnailUrl.ifBlank { info.coverUrl },
                durationSeconds = if (item.durationSeconds > 0) item.durationSeconds else info.durationSeconds
            )
            StreamingHttpDataSourceConfig.registerStreamHeaders(
                videoId = item.videoId,
                audioUrl = enriched.audioUrl,
                videoUrl = enriched.videoUrl,
                headers = enriched.httpHeaders,
                audioHeaders = enriched.audioHeaders,
                videoHeaders = enriched.videoHeaders
            )
            return Result.success(enriched)
        }

        if (item.platform == StreamingSourcePlatform.SOUNDCLOUD) {
            val scResult = SoundCloudApiClient.resolvePlayableStream(context, item)
            val scInfo = scResult.getOrNull()
            if (scInfo != null) {
                return@withContext enrichAndRegister(scInfo)
            }
            return@withContext Result.failure(
                scResult.exceptionOrNull() ?: IllegalStateException("No se pudo resolver la pista de SoundCloud \"${item.title}\".")
            )
        }

        if (item.platform == StreamingSourcePlatform.YOUTUBE) {
            // === PLATAFORMA YOUTUBE: Usar yt-dlp en primer lugar ===
            if (!forceBypassYtDlp && !YtDlpAutoUpdaterIsBlockedSafe()) {
                val ytdlpResult = YtDlpNativeEngine.resolveStream(context, watchUrl)
                val info = ytdlpResult.getOrNull()
                if (info != null && (!info.audioUrl.isNullOrBlank() || info.videoUrl.isNotBlank())) {
                    return@withContext enrichAndRegister(info)
                }
            }

            // Respaldo automático si el video de YouTube tiene bloqueo en yt-dlp o recibió HTTP 403
            val innerTubeFallback = InnerTubeClient.resolve(
                videoId = item.videoId,
                originalUrl = watchUrl
            )
            if (innerTubeFallback != null && (!innerTubeFallback.audioUrl.isNullOrBlank() || innerTubeFallback.videoUrl.isNotBlank())) {
                return@withContext enrichAndRegister(innerTubeFallback)
            }

            val invidiousFallback = com.example.core.data.importer.InvidiousStreamResolver.resolve(
                videoId = item.videoId,
                originalUrl = watchUrl
            )
            if (invidiousFallback != null && (!invidiousFallback.audioUrl.isNullOrBlank() || invidiousFallback.videoUrl.isNotBlank())) {
                return@withContext enrichAndRegister(invidiousFallback)
            }

            val webFallback = com.example.core.data.importer.HeadlessWebViewExtractor.resolve(
                context = context,
                videoId = item.videoId,
                originalUrl = watchUrl
            )
            if (webFallback != null) {
                return@withContext enrichAndRegister(webFallback)
            }

            return@withContext Result.failure(
                IllegalStateException("No se pudo resolver la pista de YouTube \"${item.title}\".")
            )
        } else {
            // === PLATAFORMA YT MUSIC: Resolución directa sin usar yt-dlp ===
            val innerTubeInfo = InnerTubeClient.resolve(
                videoId = item.videoId,
                originalUrl = watchUrl
            )
            if (innerTubeInfo != null && (!innerTubeInfo.audioUrl.isNullOrBlank() || innerTubeInfo.videoUrl.isNotBlank())) {
                return@withContext enrichAndRegister(innerTubeInfo)
            }

            val invidiousInfo = com.example.core.data.importer.InvidiousStreamResolver.resolve(
                videoId = item.videoId,
                originalUrl = watchUrl
            )
            if (invidiousInfo != null && (!invidiousInfo.audioUrl.isNullOrBlank() || invidiousInfo.videoUrl.isNotBlank())) {
                return@withContext enrichAndRegister(invidiousInfo)
            }

            val webFallback = com.example.core.data.importer.HeadlessWebViewExtractor.resolve(
                context = context,
                videoId = item.videoId,
                originalUrl = watchUrl
            )
            if (webFallback != null) {
                return@withContext enrichAndRegister(webFallback)
            }

            return@withContext Result.failure(
                IllegalStateException("No se pudo resolver la pista de YT Music \"${item.title}\".")
            )
        }
    }

    private fun YtDlpAutoUpdaterIsBlockedSafe(): Boolean {
        return try {
            com.example.core.data.importer.YtDlpAutoUpdater.isYtDlpTemporarilyBlocked()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Consulta la API de YouTube Music (`WEB_REMIX`) para obtener canciones oficiales con metadatos limpios.
     */
    private fun searchYouTubeMusicApi(query: String): List<StreamingSearchItem> {
        return try {
            val payload = JSONObject().apply {
                put("query", query)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240724.01.00")
                        put("hl", "es")
                        put("gl", "US")
                    })
                })
            }

            val responseStr = postJson(
                endpoint = YT_MUSIC_SEARCH_URL,
                body = payload.toString(),
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
                    "Origin" to "https://music.youtube.com",
                    "Referer" to "https://music.youtube.com/"
                )
            ) ?: return emptyList()

            parseYouTubeMusicSearchResults(JSONObject(responseStr))
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Aviso en búsqueda YT Music API: ${e.message}")
            emptyList()
        }
    }

    /**
     * Consulta la API de YouTube (`WEB`) para obtener videos musicales y presentaciones en vivo.
     */
    private fun searchYouTubeStandardApi(query: String): List<StreamingSearchItem> {
        return try {
            val payload = JSONObject().apply {
                put("query", query)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20240726.00.00")
                        put("hl", "es")
                        put("gl", "US")
                    })
                })
            }

            val responseStr = postJson(
                endpoint = YT_STANDARD_SEARCH_URL,
                body = payload.toString(),
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
                    "Origin" to "https://www.youtube.com",
                    "Referer" to "https://www.youtube.com/"
                )
            ) ?: return emptyList()

            parseYouTubeStandardSearchResults(JSONObject(responseStr))
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Aviso en búsqueda YouTube API: ${e.message}")
            emptyList()
        }
    }

    /**
     * Consulta el endpoint `/youtubei/v1/next` para extraer la cola de Automix / canciones relacionadas.
     */
    private fun fetchRelatedFromNextEndpoint(
        videoId: String,
        preferredPlatform: StreamingSourcePlatform
    ): List<StreamingSearchItem> {
        val results = mutableListOf<StreamingSearchItem>()
        try {
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("enablePersistentPlaylistPanel", true)
                put("isAudioOnly", true)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20240726.00.00")
                        put("hl", "es")
                        put("gl", "US")
                    })
                })
            }

            val responseStr = postJson(
                endpoint = YT_STANDARD_NEXT_URL,
                body = payload.toString(),
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                )
            ) ?: return emptyList()

            val root = JSONObject(responseStr)
            val collected = mutableListOf<JSONObject>()
            collectJsonObjectsByKey(root, "compactVideoRenderer", collected)
            collectJsonObjectsByKey(root, "playlistPanelVideoRenderer", collected)
            collectJsonObjectsByKey(root, "lockupViewModel", collected)

            val seenIds = mutableSetOf<String>()
            for (node in collected) {
                val id = node.optString("videoId", "").ifBlank {
                    node.optString("contentId", "")
                }
                if (id.length != 11 || !seenIds.add(id)) continue

                val title = extractTextFromRuns(node.optJSONObject("title"))
                    .ifBlank { "Recomendación Aura" }
                val artist = extractTextFromRuns(node.optJSONObject("longBylineText"))
                    .ifBlank { extractTextFromRuns(node.optJSONObject("shortBylineText")) }
                    .ifBlank { "YouTube Music" }
                val lengthText = extractTextFromRuns(node.optJSONObject("lengthText"))
                val durationSecs = parseDurationTextToSeconds(lengthText)

                val platform = if (artist.contains(" - Topic", ignoreCase = true) ||
                    preferredPlatform == StreamingSourcePlatform.YT_MUSIC
                ) {
                    StreamingSourcePlatform.YT_MUSIC
                } else {
                    StreamingSourcePlatform.YOUTUBE
                }

                results.add(
                    StreamingSearchItem(
                        videoId = id,
                        title = cleanTrackTitle(title),
                        artist = artist.replace(" - Topic", "").trim(),
                        album = if (platform == StreamingSourcePlatform.YT_MUSIC) "YT Music Radio" else "YouTube Radio",
                        durationSeconds = durationSecs,
                        thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                        platform = platform
                    )
                )
                if (results.size >= 15) break
            }
        } catch (_: Exception) {}
        return results
    }

    private fun parseYouTubeMusicSearchResults(root: JSONObject): List<StreamingSearchItem> {
        val items = mutableListOf<StreamingSearchItem>()
        val seenIds = mutableSetOf<String>()
        val renderers = mutableListOf<JSONObject>()
        collectJsonObjectsByKey(root, "musicResponsiveListItemRenderer", renderers)

        for (renderer in renderers) {
            val playlistItemData = renderer.optJSONObject("playlistItemData")
            val videoId = playlistItemData?.optString("videoId", "") ?: ""
            if (videoId.length != 11 || !seenIds.add(videoId)) continue

            val flexColumns = renderer.optJSONArray("flexColumns") ?: continue
            var title = ""
            var artist = ""
            var album = "YT Music"
            var durationSecs = 0L

            if (flexColumns.length() > 0) {
                val col0 = flexColumns.optJSONObject(0)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                title = extractTextFromRuns(col0)
            }
            if (flexColumns.length() > 1) {
                val col1Runs = flexColumns.optJSONObject(1)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs")
                if (col1Runs != null) {
                    val parts = mutableListOf<String>()
                    for (i in 0 until col1Runs.length()) {
                        val t = col1Runs.optJSONObject(i)?.optString("text", "")?.trim() ?: ""
                        if (t.isNotBlank() && t != "•" && t != "·" && t != "&") {
                            parts.add(t)
                        }
                    }
                    for (part in parts) {
                        val maybeDur = parseDurationTextToSeconds(part)
                        if (maybeDur > 0L) {
                            durationSecs = maybeDur
                        } else if (artist.isBlank() && !part.equals("Canción", ignoreCase = true) && !part.equals("Song", ignoreCase = true) && !part.equals("Video", ignoreCase = true)) {
                            artist = part
                        } else if (album == "YT Music" && part != artist && !part.equals("Canción", ignoreCase = true)) {
                            album = part
                        }
                    }
                }
            }

            if (title.isBlank()) continue
            val thumb = extractBestThumbnail(renderer.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail"))
                .ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }

            items.add(
                StreamingSearchItem(
                    videoId = videoId,
                    title = title,
                    artist = artist.replace(" - Topic", "").ifBlank { "YouTube Music" },
                    album = album,
                    durationSeconds = durationSecs,
                    thumbnailUrl = thumb,
                    platform = StreamingSourcePlatform.YT_MUSIC
                )
            )
            if (items.size >= 25) break
        }
        return items
    }

    private fun parseYouTubeStandardSearchResults(root: JSONObject): List<StreamingSearchItem> {
        val items = mutableListOf<StreamingSearchItem>()
        val seenIds = mutableSetOf<String>()
        val renderers = mutableListOf<JSONObject>()
        collectJsonObjectsByKey(root, "videoRenderer", renderers)

        for (renderer in renderers) {
            val videoId = renderer.optString("videoId", "")
            if (videoId.length != 11 || !seenIds.add(videoId)) continue

            val title = extractTextFromRuns(renderer.optJSONObject("title"))
            if (title.isBlank()) continue

            val rawArtist = extractTextFromRuns(renderer.optJSONObject("ownerText"))
                .ifBlank { extractTextFromRuns(renderer.optJSONObject("longBylineText")) }
                .ifBlank { "YouTube" }

            val lengthText = extractTextFromRuns(renderer.optJSONObject("lengthText"))
                .ifBlank { renderer.optJSONObject("lengthText")?.optString("simpleText", "") ?: "" }
            val durationSecs = parseDurationTextToSeconds(lengthText)

            val isTopicMusic = rawArtist.endsWith(" - Topic", ignoreCase = true)
            val platform = if (isTopicMusic) StreamingSourcePlatform.YT_MUSIC else StreamingSourcePlatform.YOUTUBE
            val cleanArtist = rawArtist.replace(" - Topic", "").trim()

            val thumb = extractBestThumbnail(renderer.optJSONObject("thumbnail"))
                .ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }

            items.add(
                StreamingSearchItem(
                    videoId = videoId,
                    title = title,
                    artist = cleanArtist,
                    album = if (platform == StreamingSourcePlatform.YT_MUSIC) "YT Music" else "YouTube Video",
                    durationSeconds = durationSecs,
                    thumbnailUrl = thumb,
                    platform = platform
                )
            )
            if (items.size >= 25) break
        }
        return items
    }

    /**
     * Intercala resultados de YouTube Music, YouTube y SoundCloud para mostrar las tres plataformas
     * claramente diferenciadas con sus insignias (`YT MUSIC`, `YOUTUBE` y `SOUNDCLOUD`) sin duplicar IDs.
     */
    private fun interleaveResults(
        musicList: List<StreamingSearchItem>,
        videoList: List<StreamingSearchItem>,
        soundCloudList: List<StreamingSearchItem> = emptyList()
    ): List<StreamingSearchItem> {
        val combined = mutableListOf<StreamingSearchItem>()
        val seenIds = mutableSetOf<String>()
        val maxLen = maxOf(musicList.size, videoList.size, soundCloudList.size)
        for (i in 0 until maxLen) {
            if (i < musicList.size) {
                val m = musicList[i]
                if (seenIds.add(m.videoId)) combined.add(m)
            }
            if (i < videoList.size) {
                val v = videoList[i]
                if (seenIds.add(v.videoId)) combined.add(v)
            }
            if (i < soundCloudList.size) {
                val sc = soundCloudList[i]
                if (seenIds.add(sc.videoId)) combined.add(sc)
            }
        }
        return combined
    }

    private fun collectJsonObjectsByKey(node: Any?, targetKey: String, out: MutableList<JSONObject>) {
        if (out.size >= 45) return
        when (node) {
            is JSONObject -> {
                if (node.has(targetKey)) {
                    val child = node.optJSONObject(targetKey)
                    if (child != null) out.add(child)
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    collectJsonObjectsByKey(node.opt(k), targetKey, out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectJsonObjectsByKey(node.opt(i), targetKey, out)
                }
            }
        }
    }

    private fun extractTextFromRuns(textObj: JSONObject?): String {
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

    private fun parseDurationTextToSeconds(text: String): Long {
        val clean = text.trim()
        if (!clean.contains(":")) return 0L
        val parts = clean.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60L + parts[1]
            3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
            else -> 0L
        }
    }

    private fun cleanTrackTitle(raw: String): String {
        return raw
            .replace(Regex("\\s*\\(Official\\s*(Music\\s*)?Video\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\[Official\\s*(Music\\s*)?Video\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Official\\s*Audio\\)", RegexOption.IGNORE_CASE), "")
            .trim()
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
