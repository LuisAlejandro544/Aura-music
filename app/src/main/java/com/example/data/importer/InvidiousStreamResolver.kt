package com.example.data.importer

import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Resolvedor de respaldo basado en instancias públicas de Invidious.
 *
 * Rol Arquitectónico:
 * Cuando YouTube impone restricciones de derechos o 'LOGIN_REQUIRED' en canciones oficiales
 * para clientes anónimos de InnerTube, este módulo consulta instancias públicas que resuelven
 * y descifran los flujos directamente a URLs puras de 'googlevideo.com', garantizando que
 * ninguna canción quede inaccesible.
 */
object InvidiousStreamResolver {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Servidores públicos con API abierta y alta disponibilidad
    private val INSTANCES = listOf(
        "https://invidious.f5.si",
        "https://iv.ggtyler.dev",
        "https://inv.nadeko.net",
        "https://inv.tux.pizza"
    )

    /**
     * Resuelve los flujos de audio y video consultando los espejos en secuencia rápida.
     */
    suspend fun resolve(
        videoId: String,
        originalUrl: String
    ): OnlineVideoAudioImporter.ResolvedMediaInfo? = withContext(Dispatchers.IO) {
        for (baseInstance in INSTANCES) {
            val resolved = queryInstance(baseInstance, videoId, originalUrl)
            if (resolved != null) {
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
            val requestUrl = "$baseInstance/api/v1/videos/$videoId"
            val request = Request.Builder()
                .url(requestUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val title = json.optString("title", "Audio de YouTube")
            val author = json.optString("author", "Música Online")
            val durationSeconds = json.optLong("lengthSeconds", 0L)

            // Miniatura en alta calidad
            var coverUrl = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
            val thumbnails = json.optJSONArray("videoThumbnails")
            if (thumbnails != null && thumbnails.length() > 0) {
                coverUrl = thumbnails.getJSONObject(0).optString("url", coverUrl)
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
                    val url = format.optString("url", "")

                    if (url.isNotBlank() && !url.contains(".m3u8") && !url.contains(".mpd")) {
                        if (mimeType.contains("audio/")) {
                            val bonus = if (mimeType.contains("mp4")) 50000L else 0L
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

            // Fallback a formatos combinados (formatStreams MP4) si falta audio o falta video
            if (formatStreams != null) {
                for (i in 0 until formatStreams.length()) {
                    val format = formatStreams.getJSONObject(i)
                    val url = format.optString("url", "")
                    val mimeType = format.optString("type", "").lowercase()
                    if (url.isNotBlank() && !url.contains(".m3u8")) {
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
                    durationSeconds = durationSeconds
                )
            } else {
                null
            }
        } catch (t: Throwable) {
            AuraDebugManager.logWarning("InvidiousResolver", "Fallo en instancia $baseInstance: ${t.message}")
            null
        }
    }
}
