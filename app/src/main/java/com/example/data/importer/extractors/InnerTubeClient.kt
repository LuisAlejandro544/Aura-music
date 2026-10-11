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
 * Cliente nativo directo para el protocolo InnerTube de YouTube y YouTube Music.
 *
 * Rol Arquitectónico:
 * 1. Consulta directamente los endpoints de YouTubei (`/youtubei/v1/player`) utilizando clientes
 *    actualizados de baja fricción (`ANDROID_VR` con el User-Agent nativo de Oculus Quest,
 *    `IOS` moderno y `TVHTML5_SIMPLY_EMBEDDED_PLAYER` con `thirdParty.embedUrl`).
 * 2. Obtiene y reutiliza automáticamente un `visitorData` válido (`X-Goog-Visitor-Id`) y las cookies
 *    sincronizadas del `WebViewCookieBridge` para evitar el bloqueo "Sign in to confirm you're not a bot".
 */
object InnerTubeClient {

    private const val TAG = "InnerTubeClient"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var cachedVisitorData: String? = null
    @Volatile
    private var lastVisitorFetchMs: Long = 0L

    /**
     * Obtiene un `visitorData` real desde YouTube (o desde el puente WebView como respaldo) para autenticar sesiones
     * de invitado ante InnerTube y `yt-dlp` (`android_vr` + `player_skip=webpage,configs`) evitando bloqueos de bot.
     */
    fun getOrFetchVisitorData(context: android.content.Context? = null): String? {
        val now = System.currentTimeMillis()
        cachedVisitorData?.let { if (it.isNotBlank() && now - lastVisitorFetchMs < 30 * 60 * 1000L) return it }

        return try {
            val reqBuilder = Request.Builder()
                .url("https://www.youtube.com/sw.js_data")
                .header("User-Agent", WebViewCookieBridge.DEFAULT_MOBILE_USER_AGENT)
                .get()

            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val raw = resp.body?.string().orEmpty()
                    val regex = Regex("\"(Cgt[a-zA-Z0-9_\\-%=]+)\"")
                    val match = regex.find(raw)
                    if (match != null) {
                        val vd = match.groupValues[1]
                        cachedVisitorData = vd
                        lastVisitorFetchMs = now
                        return vd
                    }
                }
            }
            cachedVisitorData ?: context?.let { WebViewCookieBridge.getCapturedVisitorData(it) }
        } catch (_: Throwable) {
            cachedVisitorData ?: context?.let { WebViewCookieBridge.getCapturedVisitorData(it) }
        }
    }

    /**
     * Resuelve los flujos multimedia probando clientes InnerTube modernos en cascada.
     */
    suspend fun resolve(
        videoId: String,
        originalUrl: String
    ): OnlineVideoAudioImporter.ResolvedMediaInfo? = withContext(Dispatchers.IO) {
        val visitorData = getOrFetchVisitorData()
        val hasAuthSession = WebViewCookieBridge.hasAuthenticatedSessionCookies()
        val authCookieHeader = if (hasAuthSession) WebViewCookieBridge.getConsolidatedCookieHeader() else null

        // IMPORTANTE: Los clientes de aplicación nativa (ANDROID_VR, IOS, ANDROID_TESTSUITE)
        // NO deben enviar cookies de invitado de navegador móvil (m.youtube.com como VISITOR_INFO1_LIVE / YSC)
        // ni cabeceras Origin: https://www.youtube.com, porque GoogleVideo ata el firma/token al contexto
        // de sesión web y devuelve HTTP 403 Forbidden al descargar con el User-Agent nativo de Oculus/iOS.
        // Estrategia 1: ANDROID_VR (Oculus Quest 3 nativo). Entrega URLs directas sin signatureCipher ni PO Token.
        val vrResult = queryInnerTube(
            videoId = videoId,
            originalUrl = originalUrl,
            endpointUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            clientName = "ANDROID_VR",
            clientVersion = "1.62.27",
            clientIdHeader = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
            visitorData = visitorData,
            cookieHeader = null,
            sendWebOriginHeader = false,
            extraClientProps = mapOf(
                "deviceMake" to "Oculus",
                "deviceModel" to "Quest 3",
                "androidSdkVersion" to 32,
                "osName" to "Android",
                "osVersion" to "12"
            )
        )
        if (vrResult != null) return@withContext vrResult

        // Estrategia 2: IOS (YouTube iOS nativo). Excelente para música oficial y flujos MP4/M4A directos.
        val iosResult = queryInnerTube(
            videoId = videoId,
            originalUrl = originalUrl,
            endpointUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            clientName = "IOS",
            clientVersion = "20.10.4",
            clientIdHeader = "5",
            userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            visitorData = visitorData,
            cookieHeader = null,
            sendWebOriginHeader = false,
            extraClientProps = mapOf(
                "deviceMake" to "Apple",
                "deviceModel" to "iPhone16,2",
                "osName" to "iPhone",
                "osVersion" to "18.3.2.22D82"
            )
        )
        if (iosResult != null) return@withContext iosResult

        // Estrategia 3: TVHTML5_SIMPLY_EMBEDDED_PLAYER (Reproductor embebido TV). Salta bloqueos de sesión y edad.
        val tvEmbedResult = queryInnerTube(
            videoId = videoId,
            originalUrl = originalUrl,
            endpointUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            clientIdHeader = "85",
            userAgent = "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15",
            visitorData = visitorData,
            cookieHeader = authCookieHeader,
            sendWebOriginHeader = true,
            extraClientProps = emptyMap(),
            embedThirdParty = true
        )
        if (tvEmbedResult != null) return@withContext tvEmbedResult

        // Estrategia 4: ANDROID_TESTSUITE (Cliente de diagnóstico interno de Android)
        val testSuiteResult = queryInnerTube(
            videoId = videoId,
            originalUrl = originalUrl,
            endpointUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            clientName = "ANDROID_TESTSUITE",
            clientVersion = "1.9",
            clientIdHeader = "30",
            userAgent = "com.google.android.youtube/1.9 (Linux; U; Android 14; en_US) gzip",
            visitorData = visitorData,
            cookieHeader = null,
            sendWebOriginHeader = false,
            extraClientProps = mapOf("androidSdkVersion" to 34)
        )
        if (testSuiteResult != null) return@withContext testSuiteResult

        null
    }

    private fun queryInnerTube(
        videoId: String,
        originalUrl: String,
        endpointUrl: String,
        clientName: String,
        clientVersion: String,
        clientIdHeader: String,
        userAgent: String,
        visitorData: String?,
        cookieHeader: String?,
        sendWebOriginHeader: Boolean = false,
        extraClientProps: Map<String, Any>,
        embedThirdParty: Boolean = false
    ): OnlineVideoAudioImporter.ResolvedMediaInfo? {
        return try {
            val clientObj = JSONObject().apply {
                put("clientName", clientName)
                put("clientVersion", clientVersion)
                put("hl", "es")
                put("gl", "US")
                if (!visitorData.isNullOrBlank()) {
                    put("visitorData", visitorData)
                }
                for ((k, v) in extraClientProps) {
                    put(k, v)
                }
            }

            val contextObj = JSONObject().apply {
                put("client", clientObj)
                if (embedThirdParty) {
                    put("thirdParty", JSONObject().apply {
                        put("embedUrl", "https://www.youtube.com/")
                    })
                }
            }

            val payload = JSONObject().apply {
                put("context", contextObj)
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val requestBuilder = Request.Builder()
                .url(endpointUrl)
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .header("X-YouTube-Client-Name", clientIdHeader)
                .header("X-YouTube-Client-Version", clientVersion)
                .post(body)

            if (sendWebOriginHeader) {
                requestBuilder.header("Origin", "https://www.youtube.com")
                requestBuilder.header("Referer", "https://www.youtube.com/")
            }

            if (!visitorData.isNullOrBlank()) {
                requestBuilder.header("X-Goog-Visitor-Id", visitorData)
            }
            if (!cookieHeader.isNullOrBlank()) {
                requestBuilder.header("Cookie", cookieHeader)
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return null

                val responseBody = response.body?.string() ?: return null
                val json = JSONObject(responseBody)

                // Guardar visitorData si la respuesta incluye uno nuevo en responseContext
                val respVisitor = json.optJSONObject("responseContext")?.optString("visitorData", "")
                if (!respVisitor.isNullOrBlank()) {
                    cachedVisitorData = respVisitor
                    lastVisitorFetchMs = System.currentTimeMillis()
                }

                val playabilityStatus = json.optJSONObject("playabilityStatus")
                val status = playabilityStatus?.optString("status", "") ?: ""
                if (status != "OK" && status.isNotBlank()) {
                    val reason = playabilityStatus?.optString("reason", "") ?: ""
                    AuraDebugManager.logWarning(TAG, "Status no OK ($clientName): $status - $reason")
                    return null
                }

                val videoDetails = json.optJSONObject("videoDetails")
                val title = videoDetails?.optString("title", "Audio de YouTube") ?: "Audio de YouTube"
                val author = (videoDetails?.optString("author", "YouTube") ?: "YouTube").replace(" - Topic", "").trim()
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
            }
        } catch (t: Throwable) {
            AuraDebugManager.logWarning(TAG, "Excepción consultando ($clientName): ${t.message}")
            null
        }
    }
}
