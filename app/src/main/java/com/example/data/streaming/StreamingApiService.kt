package com.example.data.streaming

import android.content.Context
import com.example.data.importer.InnerTubeClient
import com.example.data.importer.OnlineVideoAudioImporter
import com.example.data.importer.WebStreamExtractor
import com.example.data.importer.YtDlpNativeEngine
import com.example.debug.AuraDebugManager
import com.example.model.StreamingSearchFilter
import com.example.model.StreamingSearchItem
import com.example.model.StreamingSourcePlatform
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
 * Cliente de API para el Modo Streaming Híbrido (Estilo Spotify).
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
     * - `ALL`: Combina e intercala resultados de YouTube Music (`YT MUSIC`) y YouTube (`YOUTUBE`).
     * - `YT_MUSIC`: Exclusivamente canciones de YouTube Music con insignia `YT MUSIC`.
     * - `YOUTUBE`: Exclusivamente videos musicales de YouTube con insignia `YOUTUBE`.
     */
    suspend fun searchStreamingCatalog(
        query: String,
        filter: StreamingSearchFilter = StreamingSearchFilter.ALL
    ): List<StreamingSearchItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext getDefaultTrendingCatalog(filter)

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

            val musicResults = ytMusicDeferred.await()
            val videoResults = ytStandardDeferred.await()

            when (filter) {
                StreamingSearchFilter.YT_MUSIC -> musicResults.ifEmpty {
                    videoResults.map { it.copy(platform = StreamingSourcePlatform.YT_MUSIC) }
                }
                StreamingSearchFilter.YOUTUBE -> videoResults
                StreamingSearchFilter.ALL -> interleaveResults(musicResults, videoResults)
            }
        }
    }

    /**
     * Obtiene una lista inicial de descubrimiento/tendencias cuando el buscador está vacío.
     */
    suspend fun getDefaultTrendingCatalog(
        filter: StreamingSearchFilter = StreamingSearchFilter.ALL
    ): List<StreamingSearchItem> = withContext(Dispatchers.IO) {
        searchStreamingCatalog("Top Hits Music Global", filter)
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

        // 1. Consultar endpoint Watch Next / Automix de YouTube Music o YouTube
        val relatedFromNext = fetchRelatedFromNextEndpoint(cleanVideoId, preferredPlatform)
            .filter { it.videoId != cleanVideoId && !excludedVideoIds.contains(it.videoId) }

        if (relatedFromNext.isNotEmpty()) {
            return@withContext relatedFromNext.first()
        }

        // 2. Respaldo por búsqueda semántica de artista/canción similar en la API
        val fallbackQuery = if (currentArtist.isNotBlank() && !currentArtist.equals("Artista desconocido", ignoreCase = true)) {
            "$currentArtist mix canciones similares"
        } else {
            "$currentTitle mix"
        }
        val searchCandidates = searchStreamingCatalog(
            query = fallbackQuery,
            filter = if (preferredPlatform == StreamingSourcePlatform.YT_MUSIC) {
                StreamingSearchFilter.YT_MUSIC
            } else {
                StreamingSearchFilter.ALL
            }
        ).filter { it.videoId != cleanVideoId && !excludedVideoIds.contains(it.videoId) }

        searchCandidates.firstOrNull()
    }

    /**
     * Toma un elemento de búsqueda o canción similar de la API (`YT Music` o `YouTube`)
     * y utiliza `yt-dlp` (con normalización `music.youtube.com` y respaldo `InnerTubeClient`)
     * para obtener los flujos listos para reproducir en ExoPlayer y pre-cargar en caché.
     */
    suspend fun resolvePlayableStreamWithYtDlp(
        context: Context,
        item: StreamingSearchItem
    ): Result<OnlineVideoAudioImporter.ResolvedMediaInfo> = withContext(Dispatchers.IO) {
        val normalizedUrl = YtDlpNativeEngine.normalizeYouTubeMusicUrlForYtDlp(
            if (item.platform == StreamingSourcePlatform.YT_MUSIC) item.musicWatchUrl else item.canonicalWatchUrl
        )

        // 1. Para arranque instantáneo en streaming, probamos resolución rápida InnerTube + yt-dlp en cascada
        if (!YtDlpAutoUpdaterIsBlockedSafe()) {
            val ytdlpResult = YtDlpNativeEngine.resolveStream(context, normalizedUrl)
            if (ytdlpResult.isSuccess) {
                val info = ytdlpResult.getOrNull()
                if (info != null) {
                    return@withContext Result.success(
                        info.copy(
                            suggestedTitle = item.title.ifBlank { info.suggestedTitle },
                            suggestedArtist = item.artist.ifBlank { info.suggestedArtist },
                            coverUrl = item.thumbnailUrl.ifBlank { info.coverUrl },
                            durationSeconds = if (item.durationSeconds > 0) item.durationSeconds else info.durationSeconds
                        )
                    )
                }
            }
        }

        // 2. Respaldo directo de baja latencia mediante InnerTubeClient multi-cliente
        val innerTubeInfo = InnerTubeClient.resolve(
            videoId = item.videoId,
            originalUrl = normalizedUrl
        )
        if (innerTubeInfo != null) {
            return@withContext Result.success(
                innerTubeInfo.copy(
                    suggestedTitle = item.title.ifBlank { innerTubeInfo.suggestedTitle },
                    suggestedArtist = item.artist.ifBlank { innerTubeInfo.suggestedArtist },
                    coverUrl = item.thumbnailUrl.ifBlank { innerTubeInfo.coverUrl },
                    durationSeconds = if (item.durationSeconds > 0) item.durationSeconds else innerTubeInfo.durationSeconds
                )
            )
        }

        // 3. Tercer nivel: WebStreamExtractor completo
        val webResult = WebStreamExtractor.resolveStream(context, normalizedUrl)
        webResult.map { info ->
            info.copy(
                suggestedTitle = item.title.ifBlank { info.suggestedTitle },
                suggestedArtist = item.artist.ifBlank { info.suggestedArtist },
                coverUrl = item.thumbnailUrl.ifBlank { info.coverUrl },
                durationSeconds = if (item.durationSeconds > 0) item.durationSeconds else info.durationSeconds
            )
        }
    }

    private fun YtDlpAutoUpdaterIsBlockedSafe(): Boolean {
        return try {
            com.example.data.importer.YtDlpAutoUpdater.isYtDlpTemporarilyBlocked()
        } catch (_: Exception) {
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
     * Intercala resultados de YouTube Music y YouTube para mostrar ambas plataformas claramente diferenciadas
     * con sus insignias (`YT MUSIC` y `YOUTUBE`) sin duplicar el mismo `videoId` del mismo origen.
     */
    private fun interleaveResults(
        musicList: List<StreamingSearchItem>,
        videoList: List<StreamingSearchItem>
    ): List<StreamingSearchItem> {
        val combined = mutableListOf<StreamingSearchItem>()
        val seenIds = mutableSetOf<String>()
        val maxLen = maxOf(musicList.size, videoList.size)
        for (i in 0 until maxLen) {
            if (i < musicList.size) {
                val m = musicList[i]
                if (seenIds.add(m.videoId)) combined.add(m)
            }
            if (i < videoList.size) {
                val v = videoList[i]
                if (seenIds.add(v.videoId)) combined.add(v)
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
