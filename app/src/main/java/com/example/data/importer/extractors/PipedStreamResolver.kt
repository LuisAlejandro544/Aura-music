package com.example.data.importer

import android.net.Uri
import com.example.debug.AuraDebugManager
import com.example.model.StreamingSearchFilter
import com.example.model.StreamingSearchItem
import com.example.model.StreamingSourcePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Resolvedor y Buscador de Código Abierto basado en la red federada de Piped API.
 *
 * Rol Arquitectónico:
 * 1. Proporciona extracción de flujos de audio (`.m4a`/`.opus`) y video (`.mp4` 480p) sin anuncios
 *    y sin bloqueos de IP/403 mediante el endpoint `/streams/{videoId}` de instancias públicas de Piped
 *    (las cuales hacen proxy de los enlaces de `googlevideo.com` a través de `pipedproxy`).
 * 2. Soporta búsqueda nativa tanto de **YouTube Music (`filter=music_songs`)** como de
 *    **YouTube Estándar (`filter=videos`)**, además de recomendaciones similares (`relatedStreams`).
 * 3. Incluye auto-descubrimiento dinámico de instancias activas consultando el directorio oficial
 *    de Piped (`https://piped-instances.kavin.rocks/`) combinado con una lista semilla verificada.
 */
object PipedStreamResolver {

    private const val TAG = "PipedStreamResolver"
    private const val PIPED_INSTANCES_DIRECTORY_URL = "https://piped-instances.kavin.rocks/"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(9, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val SEED_INSTANCES = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.tokhmi.xyz",
        "https://pipedapi.moomoo.me",
        "https://pipedapi.syncpundit.io",
        "https://api-piped.mha.fi",
        "https://piped-api.garudalinux.org",
        "https://pipedapi.r4fo.com",
        "https://pipedapi.leptons.xyz",
        "https://pipedapi.nosebs.ru",
        "https://pipedapi.drgns.space",
        "https://pipedapi.ducks.party",
        "https://pipedapi.ngn.tf"
    )

    private val activeInstances = CopyOnWriteArrayList<String>(SEED_INSTANCES)
    @Volatile
    private var lastDirectoryFetchMs = 0L

    private val SAFE_VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")

    /**
     * Valida que una URL devuelta por un servidor externo sea estrictamente HTTPS
     * y no apunte a direcciones privadas o loopback (Prevención Anti-SSRF).
     */
    private fun isSafeExternalHttpsUrl(rawUrl: String?, baseInstance: String? = null): String? {
        if (rawUrl.isNullOrBlank()) return null
        val normalized = when {
            rawUrl.startsWith("//") -> "https:$rawUrl"
            rawUrl.startsWith("/") && !baseInstance.isNullOrBlank() -> "${baseInstance.trimEnd('/')}$rawUrl"
            else -> rawUrl.trim()
        }
        return try {
            val uri = Uri.parse(normalized)
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "https") return null

            val host = uri.host?.lowercase() ?: return null
            if (host == "localhost" ||
                host.endsWith(".local") ||
                host.endsWith(".internal") ||
                host.startsWith("127.") ||
                host.startsWith("10.") ||
                host.startsWith("192.168.") ||
                host.startsWith("169.254.") ||
                host == "0.0.0.0" ||
                host.startsWith("[::1]") ||
                Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*").matches(host)
            ) {
                return null
            }
            normalized
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Actualiza la lista de instancias activas de Piped cada 20 minutos desde el directorio oficial.
     */
    private fun refreshInstancesIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastDirectoryFetchMs < 20 * 60 * 1000L && activeInstances.isNotEmpty()) return
        lastDirectoryFetchMs = now

        try {
            val req = Request.Builder()
                .url(PIPED_INSTANCES_DIRECTORY_URL)
                .header("User-Agent", WebViewCookieBridge.DEFAULT_MOBILE_USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return
                val body = resp.body?.string() ?: return
                val arr = JSONArray(body)
                val discovered = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val apiUrl = isSafeExternalHttpsUrl(obj.optString("api_url", "")) ?: continue
                    discovered.add(apiUrl.trimEnd('/'))
                }
                if (discovered.isNotEmpty()) {
                    val combined = (discovered + SEED_INSTANCES).distinct()
                    activeInstances.clear()
                    activeInstances.addAll(combined)
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Resuelve los flujos de audio y video (480p) de cualquier video de YouTube o pista de YouTube Music
     * consultando `/streams/{videoId}` en la red Piped (funciona con o sin yt-dlp).
     */
    suspend fun resolve(
        videoId: String,
        originalUrl: String
    ): OnlineVideoAudioImporter.ResolvedMediaInfo? = withContext(Dispatchers.IO) {
        val cleanVideoId = videoId.trim()
        if (!SAFE_VIDEO_ID_REGEX.matches(cleanVideoId)) {
            AuraDebugManager.logWarning(TAG, "ID de video inválido rechazado en Piped: $videoId")
            return@withContext null
        }

        refreshInstancesIfNeeded()

        for (instance in activeInstances) {
            val info = queryStreamInstance(instance, cleanVideoId, originalUrl)
            if (info != null) {
                // Promover la instancia rápida al primer lugar para siguientes canciones
                if (activeInstances.firstOrNull() != instance) {
                    activeInstances.remove(instance)
                    activeInstances.add(0, instance)
                }
                return@withContext info
            }
        }
        null
    }

    private fun queryStreamInstance(
        baseInstance: String,
        videoId: String,
        originalUrl: String
    ): OnlineVideoAudioImporter.ResolvedMediaInfo? {
        return try {
            val requestUrl = "${baseInstance.trimEnd('/')}/streams/$videoId"
            val request = Request.Builder()
                .url(requestUrl)
                .header("User-Agent", WebViewCookieBridge.DEFAULT_MOBILE_USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                if (!body.trimStart().startsWith("{")) return null

                val json = JSONObject(body)
                if (json.has("error") && !json.has("audioStreams")) return null

                val title = json.optString("title", "Audio de YouTube").trim()
                val uploader = json.optString("uploader", "YouTube Music").replace(" - Topic", "").trim()
                val durationSeconds = json.optLong("duration", 0L)
                val coverUrl = isSafeExternalHttpsUrl(json.optString("thumbnailUrl", ""), baseInstance)
                    ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                val audioStreams = json.optJSONArray("audioStreams")
                val videoStreams = json.optJSONArray("videoStreams")

                var bestAudioUrl: String? = null
                var maxAudioScore = -1L

                if (audioStreams != null) {
                    for (i in 0 until audioStreams.length()) {
                        val stream = audioStreams.optJSONObject(i) ?: continue
                        val url = isSafeExternalHttpsUrl(stream.optString("url", ""), baseInstance) ?: continue
                        if (url.contains(".m3u8") || url.contains(".mpd")) continue

                        val mimeType = stream.optString("mimeType", "").lowercase()
                        val format = stream.optString("format", "").lowercase()
                        val bitrate = stream.optLong("bitrate", 0L)
                        // Priorizar M4A/MP4A para máxima compatibilidad con MediaExtractor y FFmpeg -c copy
                        val m4aBonus = if (mimeType.contains("mp4") || format.contains("m4a")) 60_000L else 0L
                        val score = bitrate + m4aBonus
                        if (score > maxAudioScore) {
                            maxAudioScore = score
                            bestAudioUrl = url
                        }
                    }
                }

                var bestVideoUrl: String? = null
                var bestVideoScore = -999_999

                if (videoStreams != null) {
                    for (i in 0 until videoStreams.length()) {
                        val stream = videoStreams.optJSONObject(i) ?: continue
                        val url = isSafeExternalHttpsUrl(stream.optString("url", ""), baseInstance) ?: continue
                        if (url.contains(".m3u8") || url.contains(".mpd")) continue

                        val mimeType = stream.optString("mimeType", "").lowercase()
                        val format = stream.optString("format", "").lowercase()
                        val codec = stream.optString("codec", "").lowercase()
                        val quality = stream.optString("quality", "")
                        val height = stream.optInt("height", 0).takeIf { it > 0 }
                            ?: quality.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0

                        val diff = kotlin.math.abs(height - 480)
                        var score = 10_000 - (diff * 15)
                        if (height == 480) score += 5_000
                        if (mimeType.contains("mp4") || format.contains("mpeg_4")) score += 3_500
                        if (codec.contains("avc1") || codec.contains("h264")) {
                            score += 4_000
                        } else if (codec.contains("av01") || codec.contains("av1")) {
                            score -= 6_000
                        }

                        if (score > bestVideoScore) {
                            bestVideoScore = score
                            bestVideoUrl = url
                        }
                    }
                }

                if (!bestAudioUrl.isNullOrBlank()) {
                    OnlineVideoAudioImporter.ResolvedMediaInfo(
                        originalUrl = originalUrl,
                        suggestedTitle = title,
                        suggestedArtist = uploader,
                        videoUrl = bestVideoUrl ?: bestAudioUrl,
                        audioUrl = bestAudioUrl,
                        coverUrl = coverUrl,
                        durationSeconds = durationSeconds,
                        httpHeaders = mapOf("User-Agent" to WebViewCookieBridge.DEFAULT_MOBILE_USER_AGENT)
                    )
                } else {
                    null
                }
            }
        } catch (t: Throwable) {
            AuraDebugManager.logWarning(TAG, "Aviso en instancia Piped $baseInstance: ${t.message}")
            null
        }
    }

    /**
     * Busca canciones de YouTube Music (`music_songs`) o videos de YouTube (`videos`) usando Piped API.
     */
    suspend fun search(
        query: String,
        filter: StreamingSearchFilter = StreamingSearchFilter.ALL
    ): List<StreamingSearchItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        refreshInstancesIfNeeded()

        val pipedFilter = when (filter) {
            StreamingSearchFilter.YT_MUSIC -> "music_songs"
            StreamingSearchFilter.YOUTUBE -> "videos"
            StreamingSearchFilter.ALL -> "music_songs"
        }

        for (instance in activeInstances.take(6)) {
            val results = querySearchInstance(instance, cleanQuery, pipedFilter, filter)
            if (results.isNotEmpty()) {
                return@withContext results
            }
        }
        emptyList()
    }

    private fun querySearchInstance(
        baseInstance: String,
        query: String,
        pipedFilter: String,
        requestedFilter: StreamingSearchFilter
    ): List<StreamingSearchItem> {
        return try {
            val encodedQ = URLEncoder.encode(query, "UTF-8")
            val url = "${baseInstance.trimEnd('/')}/search?q=$encodedQ&filter=$pipedFilter"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", WebViewCookieBridge.DEFAULT_MOBILE_USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                val json = JSONObject(body)
                val itemsArr = json.optJSONArray("items") ?: return emptyList()

                val results = mutableListOf<StreamingSearchItem>()
                val seenIds = mutableSetOf<String>()

                for (i in 0 until itemsArr.length()) {
                    val item = itemsArr.optJSONObject(i) ?: continue
                    val type = item.optString("type", "stream")
                    if (type != "stream") continue

                    val itemUrl = item.optString("url", "")
                    val videoId = WebStreamExtractor.extractVideoId("https://www.youtube.com$itemUrl")
                        ?: itemUrl.substringAfter("v=", "").take(11).takeIf { SAFE_VIDEO_ID_REGEX.matches(it) }
                        ?: continue

                    if (!seenIds.add(videoId)) continue

                    val title = item.optString("title", "").trim()
                    if (title.isBlank()) continue
                    val uploader = item.optString("uploaderName", "YouTube Music").replace(" - Topic", "").trim()
                    val duration = item.optLong("duration", 0L).coerceAtLeast(0L)
                    val thumb = isSafeExternalHttpsUrl(item.optString("thumbnail", ""), baseInstance)
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    val platform = when (requestedFilter) {
                        StreamingSearchFilter.YT_MUSIC -> StreamingSourcePlatform.YT_MUSIC
                        StreamingSearchFilter.YOUTUBE -> StreamingSourcePlatform.YOUTUBE
                        StreamingSearchFilter.ALL -> if (pipedFilter == "music_songs") {
                            StreamingSourcePlatform.YT_MUSIC
                        } else {
                            StreamingSourcePlatform.YOUTUBE
                        }
                    }

                    results.add(
                        StreamingSearchItem(
                            videoId = videoId,
                            title = title,
                            artist = uploader,
                            album = if (platform == StreamingSourcePlatform.YT_MUSIC) "YT Music" else "YouTube",
                            durationSeconds = duration,
                            thumbnailUrl = thumb,
                            platform = platform
                        )
                    )
                    if (results.size >= 24) break
                }
                results
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Obtiene canciones relacionadas/similares directamente desde `/streams/{videoId}` (`relatedStreams`) en Piped.
     */
    suspend fun fetchRelatedStreams(
        videoId: String,
        preferredPlatform: StreamingSourcePlatform,
        excludedVideoIds: Set<String>
    ): List<StreamingSearchItem> = withContext(Dispatchers.IO) {
        val cleanVideoId = videoId.trim()
        if (!SAFE_VIDEO_ID_REGEX.matches(cleanVideoId)) return@withContext emptyList()

        refreshInstancesIfNeeded()

        for (instance in activeInstances.take(5)) {
            try {
                val requestUrl = "${instance.trimEnd('/')}/streams/$cleanVideoId"
                val request = Request.Builder()
                    .url(requestUrl)
                    .header("User-Agent", WebViewCookieBridge.DEFAULT_MOBILE_USER_AGENT)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val relatedArr = json.optJSONArray("relatedStreams") ?: return@use

                    val results = mutableListOf<StreamingSearchItem>()
                    for (i in 0 until relatedArr.length()) {
                        val node = relatedArr.optJSONObject(i) ?: continue
                        val itemUrl = node.optString("url", "")
                        val id = WebStreamExtractor.extractVideoId("https://www.youtube.com$itemUrl")
                            ?: itemUrl.substringAfter("v=", "").take(11).takeIf { SAFE_VIDEO_ID_REGEX.matches(it) }
                            ?: continue

                        if (id == cleanVideoId || excludedVideoIds.contains(id)) continue

                        val title = node.optString("title", "").trim()
                        if (title.isBlank()) continue
                        val uploader = node.optString("uploaderName", "YouTube Music").replace(" - Topic", "").trim()
                        val duration = node.optLong("duration", 0L).coerceAtLeast(0L)
                        val thumb = isSafeExternalHttpsUrl(node.optString("thumbnail", ""), instance)
                            ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"

                        results.add(
                            StreamingSearchItem(
                                videoId = id,
                                title = title,
                                artist = uploader,
                                album = if (preferredPlatform == StreamingSourcePlatform.YT_MUSIC) "YT Music Radio" else "YouTube Radio",
                                durationSeconds = duration,
                                thumbnailUrl = thumb,
                                platform = preferredPlatform
                            )
                        )
                        if (results.size >= 12) break
                    }
                    if (results.isNotEmpty()) {
                        return@withContext results
                    }
                }
            } catch (_: Throwable) {}
        }
        emptyList()
    }
}
