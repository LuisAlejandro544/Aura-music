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
            var maxVideoBitrate = 0L

            if (adaptiveFormats != null) {
                for (i in 0 until adaptiveFormats.length()) {
                    val format = adaptiveFormats.getJSONObject(i)
                    val mimeType = format.optString("type", "")
                    val bitrate = format.optLong("bitrate", 0L)
                    val url = format.optString("url", "")

                    if (url.isNotBlank()) {
                        if (mimeType.contains("audio/")) {
                            if (bitrate >= maxAudioBitrate) {
                                maxAudioBitrate = bitrate
                                bestAudioUrl = url
                            }
                        } else if (mimeType.contains("video/")) {
                            if (bitrate >= maxVideoBitrate) {
                                maxVideoBitrate = bitrate
                                bestVideoUrl = url
                            }
                        }
                    }
                }
            }

            // Fallback a formatos combinados si no hubo audio adaptativo
            if (bestAudioUrl == null && formatStreams != null) {
                for (i in 0 until formatStreams.length()) {
                    val format = formatStreams.getJSONObject(i)
                    val url = format.optString("url", "")
                    if (url.isNotBlank()) {
                        bestAudioUrl = url
                        bestVideoUrl = url
                        break
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
