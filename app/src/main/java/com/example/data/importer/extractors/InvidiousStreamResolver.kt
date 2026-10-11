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
 * Resolvedor y Buscador de Código Abierto basado en instancias públicas de Invidious API v1.
 *
 * Rol Arquitectónico:
 * 1. Resuelve flujos de audio y video (`.m4a`/`.mp4` 480p) consultando `/api/v1/videos/{videoId}?local=true`,
 *    reescribiendo las URLs de `googlevideo.com` para pasar a través del proxy `/latest_version?id=...&itag=...&local=true`
 *    de la instancia Invidious activa. Esto evita por completo el error HTTP 403 Forbidden por bloqueo de IP.
 * 2. Incluye auto-descubrimiento dinámico de instancias activas con `api=true` desde el directorio oficial
 *    `https://api.invidious.io/instances.json`, combinado con una lista semilla de espejos de alta disponibilidad.
 * 3. Provee también búsqueda de respaldo (`/api/v1/search`) y recomendaciones relacionadas (`recommendedVideos`).
 */
object InvidiousStreamResolver {

    private const val TAG = "InvidiousResolver"
    private const val INVIDIOUS_DIRECTORY_URL = "https://api.invidious.io/instances.json?sort_by=type,users"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(9, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Servidores públicos con API abierta y soporte de proxy local=true
    private val SEED_INSTANCES = listOf(
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de",
        "https://invidious.f5.si",
        "https://iv.ggtyler.dev",
        "https://inv.tux.pizza",
        "https://invidious.jing.rocks",
        "https://invidious.privacyredirect.com",
        "https://invidious.perennialte.ch",
        "https://yt.drgnz.club",
        "https://invidious.incogniweb.net"
    )

    private val activeInstances = CopyOnWriteArrayList<String>(SEED_INSTANCES)
    @Volatile
    private var lastDirectoryFetchMs = 0L

    private val SAFE_VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")

    /**
     * Valida que una URL devuelta por un servidor externo sea estrictamente HTTPS
     * y no apunte a direcciones privadas, loopback o de red interna (Prevención de SSRF / Open Redirect).
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
                host.endsWith(".onion") ||
                host.endsWith(".i2p") ||
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
     * Actualiza dinámicamente las instancias públicas de Invidious con API habilitada cada 25 minutos.
     */
    private fun refreshInstancesIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastDirectoryFetchMs < 25 * 60 * 1000L && activeInstances.isNotEmpty()) return
        lastDirectoryFetchMs = now

        try {
            val req = Request.Builder()
                .url(INVIDIOUS_DIRECTORY_URL)
                .header("User-Agent", WebViewCookieBridge.DEFAULT_MOBILE_USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return
                val body = resp.body?.string() ?: return
                val rootArr = JSONArray(body)
                val discovered = mutableListOf<String>()

                for (i in 0 until rootArr.length()) {
                    val entry = rootArr.optJSONArray(i) ?: continue
                    if (entry.length() < 2) continue
                    val details = entry.optJSONObject(1) ?: continue
                    val type = details.optString("type", "")
                    val apiEnabled = details.optBoolean("api", false)
                    val uri = details.optString("uri", "").trimEnd('/')
                    if (type == "https" && apiEnabled) {
                        val safeUri = isSafeExternalHttpsUrl(uri)
                        if (safeUri != null) {
                            discovered.add(safeUri)
                        }
                    }
                }

                if (discovered.isNotEmpty()) {
                    val merged = (discovered.take(12) + SEED_INSTANCES).distinct()
                    activeInstances.clear()
                    activeInstances.addAll(merged)
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Convierte una URL directa de `googlevideo.com` (que suele estar atada a la IP del servidor de Invidious)
     * en una URL proxificada a través de la propia instancia Invidious (`?local=true`) o `/latest_version`
     * para evitar errores HTTP 403 en el teléfono del usuario.
     */
    private fun buildProxiedStreamUrl(
        rawUrl: String,
        baseInstance: String,
        videoId: String,
        itag: String
    ): String? {
        val cleanBase = baseInstance.trimEnd('/')
        val safeUrl = isSafeExternalHttpsUrl(rawUrl, cleanBase)
        if (safeUrl != null && !safeUrl.contains("googlevideo.com", ignoreCase = true)) {
            return if (safeUrl.startsWith(cleanBase) && !safeUrl.contains("local=true")) {
                if (safeUrl.contains("?")) "$safeUrl&local=true" else "$safeUrl?local=true"
            } else {
                safeUrl
            }
        }
        if (itag.isNotBlank() && itag.all { it.isDigit() }) {
            return "$cleanBase/latest_version?id=$videoId&itag=$itag&local=true"
        }
        return safeUrl
    }

    /**
     * Resuelve los flujos de audio y video consultando los espejos de Invidious en secuencia rápida.
     */
    suspend fun resolve(
        videoId: String,
        originalUrl: String
    ): OnlineVideoAudioImporter.ResolvedMediaInfo? = withContext(Dispatchers.IO) {
        val cleanVideoId = videoId.trim()
        if (!SAFE_VIDEO_ID_REGEX.matches(cleanVideoId)) {
            AuraDebugManager.logWarning(TAG, "ID de video inválido rechazado: $videoId")
            return@withContext null
        }

        refreshInstancesIfNeeded()

        for (baseInstance in activeInstances) {
            val resolved = queryInstance(baseInstance, cleanVideoId, originalUrl)
            if (resolved != null) {
                if (activeInstances.firstOrNull() != baseInstance) {
                    activeInstances.remove(baseInstance)
                    activeInstances.add(0, baseInstance)
                }
                return@withContext resolved
            }
        }
        null
    }

    private fun queryInstance(
        baseInstance: String,
        videoId: String,
        originalUrl: String
    ): OnlineVideoAudioImporter.ResolvedMediaInfo? {
        return try {
            val cleanBase = baseInstance.trimEnd('/')
            val requestUrl = "$cleanBase/api/v1/videos/$videoId?local=true"
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
                if (json.has("error")) return null

                val title = json.optString("title", "Audio de YouTube").trim()
                val author = json.optString("author", "Música Online").replace(" - Topic", "").trim()
                val durationSeconds = json.optLong("lengthSeconds", 0L)

                var coverUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                val thumbnails = json.optJSONArray("videoThumbnails")
                if (thumbnails != null && thumbnails.length() > 0) {
                    val candidateThumb = thumbnails.getJSONObject(0).optString("url", "")
                    val safeThumb = isSafeExternalHttpsUrl(candidateThumb, cleanBase)
                    if (safeThumb != null) {
                        coverUrl = safeThumb
                    }
                }

                val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                val formatStreams = json.optJSONArray("formatStreams")

                var bestAudioUrl: String? = null
                var maxAudioBitrate = 0L

                var bestVideoUrl: String? = null
                var bestVideoScore = -999999

                if (adaptiveFormats != null) {
                    for (i in 0 until adaptiveFormats.length()) {
                        val format = adaptiveFormats.getJSONObject(i)
                        val mimeType = format.optString("type", "").lowercase()
                        val bitrate = format.optLong("bitrate", 0L)
                        val itag = format.optString("itag", "")
                        val rawUrl = format.optString("url", "")
                        val url = buildProxiedStreamUrl(rawUrl, cleanBase, videoId, itag) ?: continue

                        if (!url.contains(".m3u8") && !url.contains(".mpd")) {
                            if (mimeType.contains("audio/")) {
                                val bonus = if (mimeType.contains("mp4") || mimeType.contains("m4a")) 50000L else 0L
                                if (bitrate + bonus >= maxAudioBitrate) {
                                    maxAudioBitrate = bitrate + bonus
                                    bestAudioUrl = url
                                }
                            } else if (mimeType.contains("video/")) {
                                val qualityLabel = format.optString("qualityLabel", "")
                                val height = format.optInt("height", 0).takeIf { it > 0 }
                                    ?: qualityLabel.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                                val targetHeight = 480
                                val diff = kotlin.math.abs(height - targetHeight)
                                var score = 10000 - (diff * 15)
                                if (height == 480) score += 5000
                                if (mimeType.contains("video/mp4")) score += 3000
                                if (mimeType.contains("avc1")) {
                                    score += 4000
                                } else if (mimeType.contains("av01") || mimeType.contains("av1")) {
                                    score -= 6000
                                } else if (mimeType.contains("vp9")) {
                                    score -= 2000
                                }

                                if (score > bestVideoScore) {
                                    bestVideoScore = score
                                    bestVideoUrl = url
                                }
                            }
                        }
                    }
                }

                // Fallback a formatos combinados (formatStreams MP4, ej. itag 18 360p) si falta audio o falta video
                if (formatStreams != null) {
                    for (i in 0 until formatStreams.length()) {
                        val format = formatStreams.getJSONObject(i)
                        val itag = format.optString("itag", "")
                        val rawUrl = format.optString("url", "")
                        val url = buildProxiedStreamUrl(rawUrl, cleanBase, videoId, itag) ?: continue
                        val mimeType = format.optString("type", "").lowercase()
                        if (!url.contains(".m3u8")) {
                            if (bestVideoUrl == null && (mimeType.contains("video/") || mimeType.isBlank())) {
                                bestVideoUrl = url
                            }
                            if (bestAudioUrl == null) {
                                bestAudioUrl = url
                            }
                        }
                    }
                }

                if (!bestAudioUrl.isNullOrBlank()) {
                    OnlineVideoAudioImporter.ResolvedMediaInfo(
                        originalUrl = originalUrl,
                        suggestedTitle = title,
                        suggestedArtist = author,
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
            AuraDebugManager.logWarning(TAG, "Fallo en instancia $baseInstance: ${t.message}")
            null
        }
    }

    /**
     * Búsqueda de respaldo en instancias públicas de Invidious (`/api/v1/search?q=...&type=video`).
     */
    suspend fun search(
        query: String,
        filter: StreamingSearchFilter = StreamingSearchFilter.ALL
    ): List<StreamingSearchItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        refreshInstancesIfNeeded()

        for (instance in activeInstances.take(5)) {
            try {
                val encodedQ = URLEncoder.encode(cleanQuery, "UTF-8")
                val url = "${instance.trimEnd('/')}/api/v1/search?q=$encodedQ&type=video"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", WebViewCookieBridge.DEFAULT_MOBILE_USER_AGENT)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    if (!body.trimStart().startsWith("[")) return@use

                    val arr = JSONArray(body)
                    val results = mutableListOf<StreamingSearchItem>()
                    val seenIds = mutableSetOf<String>()

                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val videoId = item.optString("videoId", "").trim()
                        if (!SAFE_VIDEO_ID_REGEX.matches(videoId) || !seenIds.add(videoId)) continue

                        val title = item.optString("title", "").trim()
                        if (title.isBlank()) continue
                        val author = item.optString("author", "YouTube").replace(" - Topic", "").trim()
                        val duration = item.optLong("lengthSeconds", 0L).coerceAtLeast(0L)

                        val platform = when (filter) {
                            StreamingSearchFilter.YT_MUSIC -> StreamingSourcePlatform.YT_MUSIC
                            StreamingSearchFilter.YOUTUBE -> StreamingSourcePlatform.YOUTUBE
                            StreamingSearchFilter.ALL -> if (item.optString("author", "").endsWith(" - Topic", ignoreCase = true)) {
                                StreamingSourcePlatform.YT_MUSIC
                            } else {
                                StreamingSourcePlatform.YOUTUBE
                            }
                        }

                        results.add(
                            StreamingSearchItem(
                                videoId = videoId,
                                title = title,
                                artist = author,
                                album = if (platform == StreamingSourcePlatform.YT_MUSIC) "YT Music" else "YouTube",
                                durationSeconds = duration,
                                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                platform = platform
                            )
                        )
                        if (results.size >= 20) break
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
