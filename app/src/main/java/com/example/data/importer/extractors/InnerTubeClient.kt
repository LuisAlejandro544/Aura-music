package com.example.data.importer

import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente nativo directo para el protocolo InnerTube de YouTube.
 *
 * Rol Arquitectónico:
 * Consulta directamente los endpoints de YouTubei (/youtubei/v1/player) simulando
 * clientes de baja fricción como ANDROID_VR y VISIONOS que no sufren bloqueos de firma ni login,
 * resolviendo flujos de audio y video en <300ms sin necesidad de navegador.
 */
object InnerTubeClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Resuelve los flujos multimedia probando clientes InnerTube en cascada.
     */
    suspend fun resolve(
        videoId: String,
        originalUrl: String
    ): OnlineVideoAudioImporter.ResolvedMediaInfo? = withContext(Dispatchers.IO) {
        // Estrategia 1: ANDROID_VR (Meta Quest / VR). Entrega URLs directas sin signatureCipher ni n-sig.
        val vrResult = queryInnerTube(
            videoId = videoId,
            originalUrl = originalUrl,
            endpointUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            clientName = "ANDROID_VR",
            clientVersion = "1.60.19",
            userAgent = "Mozilla/5.0 (Android; VR; Meta Quest 3) AppleWebKit/537.36",
            extraClientProps = mapOf("deviceModel" to "Quest 3")
        )
        if (vrResult != null) return@withContext vrResult

        // Estrategia 2: VISIONOS (Apple Vision Pro). También entrega flujos directos en alta calidad.
        val visionResult = queryInnerTube(
            videoId = videoId,
            originalUrl = originalUrl,
            endpointUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            clientName = "VISIONOS",
            clientVersion = "0.1.0",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15",
            extraClientProps = emptyMap()
        )
        if (visionResult != null) return@withContext visionResult

        // Estrategia 3: ANDROID_MUSIC (YouTube Music nativo)
        val musicResult = queryInnerTube(
            videoId = videoId,
            originalUrl = originalUrl,
            endpointUrl = "https://music.youtube.com/youtubei/v1/player",
            clientName = "ANDROID_MUSIC",
            clientVersion = "6.42.52",
            userAgent = "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14; es_ES) gzip",
            extraClientProps = mapOf("androidSdkVersion" to 34)
        )
        if (musicResult != null) return@withContext musicResult

        null
    }

    private fun queryInnerTube(
        videoId: String,
        originalUrl: String,
        endpointUrl: String,
        clientName: String,
        clientVersion: String,
        userAgent: String,
        extraClientProps: Map<String, Any>
    ): OnlineVideoAudioImporter.ResolvedMediaInfo? {
        return try {
            val clientObj = JSONObject().apply {
                put("clientName", clientName)
                put("clientVersion", clientVersion)
                put("hl", "es")
                put("gl", "ES")
                for ((k, v) in extraClientProps) {
                    put(k, v)
                }
            }

            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", clientObj)
                })
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(endpointUrl)
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val responseBody = response.body?.string() ?: return null
            val json = JSONObject(responseBody)

            val playabilityStatus = json.optJSONObject("playabilityStatus")
            val status = playabilityStatus?.optString("status", "") ?: ""
            if (status != "OK" && status.isNotBlank()) {
                AuraDebugManager.logWarning("InnerTubeClient", "Status no OK ($clientName): $status")
                return null
            }

            val videoDetails = json.optJSONObject("videoDetails")
            val title = videoDetails?.optString("title", "Audio de YouTube") ?: "Audio de YouTube"
            val author = videoDetails?.optString("author", "YouTube") ?: "YouTube"
            val durationSeconds = videoDetails?.optLong("lengthSeconds", 0L) ?: 0L

            var thumbnail = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
            val thumbnailsArray = videoDetails?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            if (thumbnailsArray != null && thumbnailsArray.length() > 0) {
                thumbnail = thumbnailsArray.getJSONObject(thumbnailsArray.length() - 1).optString("url", thumbnail)
            }

            val streamingData = json.optJSONObject("streamingData") ?: return null
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: JSONArray()
            val combinedFormats = streamingData.optJSONArray("formats") ?: JSONArray()

            var bestAudioUrl: String? = null
            var maxAudioBitrate = 0

            var bestVideoUrl: String? = null
            var bestVideoScore = -999999

            // 1. Revisar formatos adaptativos priorizando estrictamente 480p en MP4 (avc1 / H.264) libre de m3u8/AV1
            for (i in 0 until adaptiveFormats.length()) {
                val format = adaptiveFormats.getJSONObject(i)
                val mimeType = format.optString("mimeType", "").lowercase()
                val bitrate = format.optInt("bitrate", 0)
                val url = format.optString("url", "")

                if (url.isNotBlank() && !url.contains(".m3u8") && !url.contains(".mpd")) {
                    if (mimeType.contains("audio/")) {
                        val bonus = if (mimeType.contains("mp4")) 50000 else 0
                        if (bitrate + bonus >= maxAudioBitrate) {
                            maxAudioBitrate = bitrate + bonus
                            bestAudioUrl = url
                        }
                    } else if (mimeType.contains("video/")) {
                        val height = format.optInt("height", 0)
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

            // 2. Revisar formatos combinados (contienen video + audio, ej. itag 18 MP4 360p) si falta audio o falta video
            for (i in 0 until combinedFormats.length()) {
                val format = combinedFormats.getJSONObject(i)
                val url = format.optString("url", "")
                val mimeType = format.optString("mimeType", "").lowercase()
                if (url.isNotBlank() && !url.contains(".m3u8")) {
                    if (bestVideoUrl == null && mimeType.contains("video/")) {
                        bestVideoUrl = url
                    }
                    if (bestAudioUrl == null) {
                        bestAudioUrl = url
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
                    coverUrl = thumbnail,
                    durationSeconds = durationSeconds,
                    httpHeaders = mapOf("User-Agent" to userAgent)
                )
            } else {
                null
            }
        } catch (t: Throwable) {
            AuraDebugManager.logWarning("InnerTubeClient", "Excepción consultando ($clientName): ${t.message}")
            null
        }
    }
}
