package com.example.core.data.importer

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
        .callTimeout(25, TimeUnit.SECONDS)
        .connectionPool(com.example.core.data.importer.SharedHttpClients.pool)
        .dispatcher(com.example.core.data.importer.SharedHttpClients.dispatcher)
        .followRedirects(true)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Resuelve los flujos multimedia probando clientes InnerTube en cascada.
     * @param audioQuality calidad elegida por el usuario (Opus/M4A); por defecto AUTO_HIFI.
     * @param preferMusicFirst true para pistas YT Music: prueba ANDROID_MUSIC (music.youtube.com)
     * en primer lugar, ya que los temas oficiales/Topic suelen devolver LOGIN_REQUIRED o solo-HLS
     * en clientes VR/WEB. En Beta esto evita el fallo típico de YT Music en Explorar.
     */
    suspend fun resolve(
        videoId: String,
        originalUrl: String,
        audioQuality: com.example.core.model.StreamingAudioQuality =
            com.example.core.model.StreamingAudioQuality.AUTO_HIFI,
        opusSupported: Boolean = true,
        preferMusicFirst: Boolean = originalUrl.contains("music.youtube.com", ignoreCase = true)
    ): OnlineVideoAudioImporter.ResolvedMediaInfo? = withContext(Dispatchers.IO) {
        // Para YT Music: ANDROID_MUSIC primero (endpoint music.youtube.com nativo).
        if (preferMusicFirst) {
            val musicFirst = queryInnerTube(
                videoId = videoId,
                originalUrl = originalUrl,
                endpointUrl = "https://music.youtube.com/youtubei/v1/player",
                clientName = "ANDROID_MUSIC",
                clientVersion = "6.42.52",
                userAgent = "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14; es_ES) gzip",
                extraClientProps = mapOf("androidSdkVersion" to 34),
                audioQuality = audioQuality,
                opusSupported = opusSupported
            )
            if (musicFirst != null) return@withContext musicFirst
        }

        // Estrategia 1: ANDROID_VR (Meta Quest / VR). Entrega URLs directas sin signatureCipher ni n-sig.
        val vrResult = queryInnerTube(
            videoId = videoId,
            originalUrl = originalUrl,
            endpointUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            clientName = "ANDROID_VR",
            clientVersion = "1.60.19",
            userAgent = "Mozilla/5.0 (Android; VR; Meta Quest 3) AppleWebKit/537.36",
            extraClientProps = mapOf("deviceModel" to "Quest 3"),
            audioQuality = audioQuality,
            opusSupported = opusSupported
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
            extraClientProps = emptyMap(),
            audioQuality = audioQuality,
            opusSupported = opusSupported
        )
        if (visionResult != null) return@withContext visionResult

        // Estrategia 3: ANDROID_MUSIC (YouTube Music nativo, omitido si ya se probó primero)
        if (!preferMusicFirst) {
            val musicResult = queryInnerTube(
                videoId = videoId,
                originalUrl = originalUrl,
                endpointUrl = "https://music.youtube.com/youtubei/v1/player",
                clientName = "ANDROID_MUSIC",
                clientVersion = "6.42.52",
                userAgent = "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14; es_ES) gzip",
                extraClientProps = mapOf("androidSdkVersion" to 34),
                audioQuality = audioQuality,
                opusSupported = opusSupported
            )
            if (musicResult != null) return@withContext musicResult
        }

        // Estrategia 4: IOS (Cliente nativo iPhone para pistas con restricción móvil en Android)
        val iosResult = queryInnerTube(
            videoId = videoId,
            originalUrl = originalUrl,
            endpointUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            clientName = "IOS",
            clientVersion = "19.29.1",
            userAgent = "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)",
            extraClientProps = mapOf("deviceModel" to "iPhone16,2", "osVersion" to "17.5.1.21F90"),
            audioQuality = audioQuality,
            opusSupported = opusSupported
        )
        if (iosResult != null) return@withContext iosResult

        // Estrategia 5: MWEB (Cliente web móvil para pistas con restricción)
        val mwebResult = queryInnerTube(
            videoId = videoId,
            originalUrl = originalUrl,
            endpointUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            clientName = "MWEB",
            clientVersion = "2.20240715.01.00",
            userAgent = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
            extraClientProps = emptyMap(),
            audioQuality = audioQuality,
            opusSupported = opusSupported
        )
        if (mwebResult != null) return@withContext mwebResult

        // Estrategia 6: TVHTML5 (Cliente de televisión embebida)
        val tvEmbedResult = queryInnerTube(
            videoId = videoId,
            originalUrl = originalUrl,
            endpointUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            userAgent = "Mozilla/5.0 (SMART-TV; LINUX; Tizen 5.0) AppleWebKit/537.36",
            extraClientProps = emptyMap(),
            audioQuality = audioQuality,
            opusSupported = opusSupported
        )
        if (tvEmbedResult != null) return@withContext tvEmbedResult

        null
    }

    private fun queryInnerTube(
        videoId: String,
        originalUrl: String,
        endpointUrl: String,
        clientName: String,
        clientVersion: String,
        userAgent: String,
        extraClientProps: Map<String, Any>,
        audioQuality: com.example.core.model.StreamingAudioQuality =
            com.example.core.model.StreamingAudioQuality.AUTO_HIFI,
        opusSupported: Boolean = true
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
            var maxAudioScore: Long = Long.MIN_VALUE

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
                        val itag = format.optInt("itag", 0)
                        val score = rankAudioFormat(mimeType, bitrate.toLong(), itag, audioQuality, opusSupported)
                        if (score >= maxAudioScore) {
                            maxAudioScore = score
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
                val clientHeaders = mapOf("User-Agent" to userAgent)
                OnlineVideoAudioImporter.ResolvedMediaInfo(
                    originalUrl = originalUrl,
                    suggestedTitle = title,
                    suggestedArtist = author,
                    videoUrl = bestVideoUrl ?: bestAudioUrl,
                    audioUrl = bestAudioUrl,
                    coverUrl = thumbnail,
                    durationSeconds = durationSeconds,
                    httpHeaders = clientHeaders,
                    audioHeaders = clientHeaders,
                    videoHeaders = clientHeaders
                )
            } else {
                null
            }
        } catch (t: Throwable) {
            AuraDebugManager.logWarning("InnerTubeClient", "Excepción consultando ($clientName): ${t.message}")
            null
        }
    }

    /**
     * Puntaje de formato de audio según la calidad elegida (Opus itag 249/250/251 vs M4A itag 140).
     * Mantiene compatibilidad: AUTO_HIFI prefiere Opus 251 y cae a M4A 140.
     */
    internal fun rankAudioFormat(
        mimeType: String,
        bitrate: Long,
        itag: Int,
        quality: com.example.core.model.StreamingAudioQuality,
        opusSupported: Boolean = true
    ): Long {
        val mime = mimeType.lowercase()
        val isOpus = mime.contains("opus") || mime.contains("webm")
        val isMp4 = mime.contains("mp4")
        // v0.2.1-beta.1f: sin decodificador Opus, penalizar Opus para no elegir algo irreproducible.
        if (!opusSupported && isOpus) return -1_000_000L + bitrate
        val itagPriority: Long = when (quality) {
            com.example.core.model.StreamingAudioQuality.AHORRO_OPUS_48K -> when (itag) {
                249 -> 400000L; 250 -> 300000L; 251 -> 200000L; 140 -> 100000L; else -> 0L
            }
            com.example.core.model.StreamingAudioQuality.ALTA_OPUS_160K -> when (itag) {
                251 -> 400000L; 140 -> 200000L; 250 -> 150000L; 249 -> 100000L; else -> 0L
            }
            com.example.core.model.StreamingAudioQuality.ESTANDAR_M4A_128K -> when (itag) {
                140 -> 400000L; 251 -> 200000L; 250 -> 100000L; 249 -> 50000L; else -> 0L
            }
            com.example.core.model.StreamingAudioQuality.AUTO_HIFI -> when (itag) {
                251 -> 400000L; 140 -> 300000L; 250 -> 200000L; 249 -> 100000L; else -> 0L
            }
        }
        if (itag != 0) return itagPriority + bitrate
        // Sin itag: puntaje por mime + bitrate según preferencia.
        return when (quality) {
            com.example.core.model.StreamingAudioQuality.AHORRO_OPUS_48K ->
                (if (isOpus) 100000L else 0L) - bitrate
            com.example.core.model.StreamingAudioQuality.ALTA_OPUS_160K ->
                bitrate + (if (isOpus) 50000L else 0L)
            com.example.core.model.StreamingAudioQuality.ESTANDAR_M4A_128K ->
                bitrate + (if (isMp4) 50000L else 0L)
            com.example.core.model.StreamingAudioQuality.AUTO_HIFI ->
                bitrate + (if (isOpus) 20000L else 0L)
        }
    }
}
