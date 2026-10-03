package com.example.data.importer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.resume

/**
 * Motores de extracción disponibles para YouTube y video web:
 * - INNERTUBE: API nativa directa de YouTube Music (sin navegador, ultrarrápida y sin consumo de batería).
 * - WEBVIEW: Navegador efímero que ejecuta scripts y el reproductor en memoria para capturar el stream.
 */
enum class YoutubeExtractionEngine(val label: String, val description: String) {
    INNERTUBE("InnerTube (Rápido)", "API nativa directa de alta velocidad sin navegador"),
    WEBVIEW("Motor WebView", "Navegador efímero con ejecución de scripts en segundo plano")
}

/**
 * Extractor optimizado de flujos multimedia de alta fidelidad para videos web y streaming.
 *
 * Arquitectura Híbrida:
 * 1. InnerTube Engine (Principal / Recomendado): Consulta directa al endpoint de YouTube Music
 *    con el cliente oficial ANDROID_MUSIC o WEB_REMIX. Es 100% gratuito, opera directamente
 *    desde la conexión del teléfono (evitando bloqueos por IP de datacenter) y resuelve en <400ms.
 * 2. Motor Headless WebView (Alternativo / Fallback): Abre un WebView efímero en segundo plano
 *    con mediaPlaybackRequiresUserGesture = false y bypass de muros de cookies mediante modo Embed,
 *    interceptando llamadas de red a googlevideo.com y evaluando ytInitialPlayerResponse.
 */
object WebStreamExtractor {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val YOUTUBE_ID_PATTERN = Pattern.compile(
        "(?:youtu\\.be/|youtube\\.com/(?:embed/|v/|shorts/|watch\\?v=|watch\\?.+&v=))([a-zA-Z0-9_-]{11})"
    )

    fun isWebVideoUrl(url: String): Boolean {
        val clean = url.trim()
        return clean.contains("youtube.com", ignoreCase = true) ||
                clean.contains("youtu.be", ignoreCase = true)
    }

    fun extractVideoId(url: String): String? {
        val matcher = YOUTUBE_ID_PATTERN.matcher(url.trim())
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * Resuelve el enlace web y retorna la información de audio, video y carátula.
     * Permite especificar el motor preferido (InnerTube o WebView).
     */
    suspend fun resolveStream(
        context: Context,
        url: String,
        preferredEngine: YoutubeExtractionEngine = YoutubeExtractionEngine.INNERTUBE
    ): Result<OnlineVideoAudioImporter.ResolvedMediaInfo> {
        val videoId = extractVideoId(url)
            ?: return Result.failure(IllegalArgumentException("No se pudo identificar el ID del video de YouTube"))

        return if (preferredEngine == YoutubeExtractionEngine.INNERTUBE) {
            // Intento 1: InnerTube (Alta velocidad sin navegador)
            val innerTubeResult = tryInnerTubeResolution(videoId, url)
            if (innerTubeResult != null) {
                Result.success(innerTubeResult)
            } else {
                AuraDebugManager.logWarning("WebStreamExtractor", "InnerTube no devolvió streams. Intentando con Motor WebView...")
                // Fallback automático al WebView
                val webViewResult = tryHeadlessExtraction(context, videoId, url)
                if (webViewResult != null) {
                    Result.success(webViewResult)
                } else {
                    Result.failure(Exception("No se pudo extraer el audio con InnerTube ni con WebView. Verifica el enlace."))
                }
            }
        } else {
            // Intento 1: WebView solicitado por el usuario
            val webViewResult = tryHeadlessExtraction(context, videoId, url)
            if (webViewResult != null) {
                Result.success(webViewResult)
            } else {
                AuraDebugManager.logWarning("WebStreamExtractor", "Motor WebView falló. Intentando con InnerTube...")
                // Fallback automático a InnerTube
                val innerTubeResult = tryInnerTubeResolution(videoId, url)
                if (innerTubeResult != null) {
                    Result.success(innerTubeResult)
                } else {
                    Result.failure(Exception("No se pudo extraer el audio con WebView ni con InnerTube."))
                }
            }
        }
    }

    /**
     * Extracción mediante la API nativa de YouTube Music (InnerTube).
     * 100% gratuita, directa desde la IP móvil/residencial del usuario, sin riesgo de bloqueo por datacenter.
     */
    private suspend fun tryInnerTubeResolution(
        videoId: String,
        originalUrl: String
    ): OnlineVideoAudioImporter.ResolvedMediaInfo? = withContext(Dispatchers.IO) {
        // Estrategia A: Cliente ANDROID_MUSIC (El más tolerante y sin cifrado web de n-sig)
        val androidMusicInfo = requestInnerTubePlayer(
            videoId = videoId,
            originalUrl = originalUrl,
            clientName = "ANDROID_MUSIC",
            clientVersion = "6.42.52",
            userAgent = "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14; es_ES) gzip",
            clientNumber = "21"
        )
        if (androidMusicInfo != null) {
            return@withContext androidMusicInfo
        }

        // Estrategia B: Cliente WEB_REMIX (YouTube Music Web)
        val webRemixInfo = requestInnerTubePlayer(
            videoId = videoId,
            originalUrl = originalUrl,
            clientName = "WEB_REMIX",
            clientVersion = "1.20240401.01.00",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            clientNumber = "67"
        )
        if (webRemixInfo != null) {
            return@withContext webRemixInfo
        }

        null
    }

    /**
     * Ejecuta una petición HTTP POST contra el endpoint /youtubei/v1/player de YouTube Music.
     */
    private fun requestInnerTubePlayer(
        videoId: String,
        originalUrl: String,
        clientName: String,
        clientVersion: String,
        userAgent: String,
        clientNumber: String
    ): OnlineVideoAudioImporter.ResolvedMediaInfo? {
        try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", clientName)
                        put("clientVersion", clientVersion)
                        if (clientName == "ANDROID_MUSIC") {
                            put("androidSdkVersion", 34)
                        }
                        put("hl", "es")
                        put("gl", "ES")
                    })
                })
                put("videoId", videoId)
            }

            val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/player")
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .header("X-YouTube-Client-Name", clientNumber)
                .header("X-YouTube-Client-Version", clientVersion)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val responseBody = response.body?.string() ?: return null
            val json = JSONObject(responseBody)

            val playabilityStatus = json.optJSONObject("playabilityStatus")
            val status = playabilityStatus?.optString("status", "") ?: ""
            if (status != "OK" && status != "") {
                AuraDebugManager.logWarning("WebStreamExtractor", "InnerTube status no OK ($clientName): $status")
            }

            val videoDetails = json.optJSONObject("videoDetails")
            val title = videoDetails?.optString("title", "Audio de YouTube") ?: "Audio de YouTube"
            val author = videoDetails?.optString("author", "Artista de YouTube") ?: "Artista de YouTube"
            val durationSeconds = videoDetails?.optLong("lengthSeconds", 0L) ?: 0L

            // Extraer mejor miniatura
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
            var maxVideoBitrate = 0

            // 1. Buscar en adaptiveFormats (audio puro de máxima calidad y video para Canvas)
            for (i in 0 until adaptiveFormats.length()) {
                val format = adaptiveFormats.getJSONObject(i)
                val mimeType = format.optString("mimeType", "")
                val bitrate = format.optInt("bitrate", 0)
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

            // 2. Si no hubo url directa en adaptive, revisar combinedFormats
            if (bestAudioUrl == null) {
                for (i in 0 until combinedFormats.length()) {
                    val format = combinedFormats.getJSONObject(i)
                    val url = format.optString("url", "")
                    val bitrate = format.optInt("bitrate", 0)
                    if (url.isNotBlank()) {
                        bestAudioUrl = url
                        bestVideoUrl = url
                        break
                    }
                }
            }

            if (!bestAudioUrl.isNullOrBlank()) {
                return OnlineVideoAudioImporter.ResolvedMediaInfo(
                    originalUrl = originalUrl,
                    suggestedTitle = title,
                    suggestedArtist = author,
                    videoUrl = bestVideoUrl ?: bestAudioUrl,
                    audioUrl = bestAudioUrl,
                    coverUrl = thumbnail,
                    durationSeconds = durationSeconds
                )
            }
        } catch (t: Throwable) {
            AuraDebugManager.logWarning("WebStreamExtractor", "Error en InnerTube ($clientName): ${t.message}")
        }
        return null
    }

    /**
     * Motor Headless con WebView efímero optimizado y reparado:
     * - mediaPlaybackRequiresUserGesture = false para permitir que el reproductor web inicie automáticamente
     * - Modo Embed Nocookie para evitar muros de cookies y diálogos de consentimiento
     * - Detección dual: Intercepción de paquetes googlevideo.com + Evaluación de ytInitialPlayerResponse
     * - Se destruye al instante tras obtener el enlace liberando el 100% de la memoria
     */
    private suspend fun tryHeadlessExtraction(
        context: Context,
        videoId: String,
        originalUrl: String
    ): OnlineVideoAudioImporter.ResolvedMediaInfo? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(10000L) {
            suspendCancellableCoroutine { continuation ->
                var webView: WebView? = null

                fun cleanup() {
                    try {
                        webView?.stopLoading()
                        webView?.pauseTimers()
                        webView?.destroy()
                        webView = null
                    } catch (ignored: Throwable) {}
                }

                continuation.invokeOnCancellation {
                    cleanup()
                }

                try {
                    val view = WebView(context.applicationContext)
                    webView = view

                    view.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        // CRÍTICO: Permitir reproducción automática sin toque físico del usuario
                        mediaPlaybackRequiresUserGesture = false
                        loadsImagesAutomatically = false
                        blockNetworkImage = true
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    }

                    var streamExtracted = false
                    var capturedAudioUrl: String? = null
                    var capturedVideoUrl: String? = null

                    view.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: ""

                            // 1. Interceptar URL directa de streaming descifrada por el reproductor en tiempo real
                            if (reqUrl.contains("googlevideo.com/videoplayback") && reqUrl.contains("itag=")) {
                                val isAudio = reqUrl.contains("mime=audio") ||
                                        reqUrl.contains("itag=140") ||
                                        reqUrl.contains("itag=251") ||
                                        reqUrl.contains("itag=171")

                                if (isAudio) {
                                    capturedAudioUrl = reqUrl
                                    if (capturedVideoUrl == null) capturedVideoUrl = reqUrl
                                } else if (reqUrl.contains("mime=video")) {
                                    capturedVideoUrl = reqUrl
                                }

                                if (!streamExtracted && capturedAudioUrl != null) {
                                    streamExtracted = true
                                    Handler(Looper.getMainLooper()).post {
                                        if (continuation.isActive) {
                                            val info = OnlineVideoAudioImporter.ResolvedMediaInfo(
                                                originalUrl = originalUrl,
                                                suggestedTitle = "Audio Extraído ($videoId)",
                                                suggestedArtist = "YouTube Music",
                                                videoUrl = capturedVideoUrl ?: capturedAudioUrl!!,
                                                audioUrl = capturedAudioUrl,
                                                coverUrl = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg",
                                                durationSeconds = 0L
                                            )
                                            cleanup()
                                            continuation.resume(info)
                                        }
                                    }
                                }
                            }

                            // 2. Bloqueo de publicidad e imágenes para acelerar al máximo la carga
                            if (reqUrl.contains("googleads") ||
                                reqUrl.contains("doubleclick") ||
                                reqUrl.contains("analytics") ||
                                reqUrl.endsWith(".png") ||
                                reqUrl.endsWith(".jpg")
                            ) {
                                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                            }

                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (streamExtracted) return

                            // Inyección de script para aceptar consentimientos y extraer ytInitialPlayerResponse en memoria
                            val jsExtract = """
                                (function() {
                                    try {
                                        // Auto-aceptar posibles modales de cookies
                                        var consentBtns = document.querySelectorAll('button[aria-label*="Aceptar"], button[aria-label*="Agree"], button[aria-label*="accept"], form[action*="consent"] button');
                                        consentBtns.forEach(function(b) { b.click(); });

                                        var pr = window.ytInitialPlayerResponse || 
                                                 (window.ytplayer && window.ytplayer.config && window.ytplayer.config.args && JSON.parse(window.ytplayer.config.args.raw_player_response));
                                        
                                        if (pr && pr.streamingData) {
                                            var title = (pr.videoDetails && pr.videoDetails.title) || "";
                                            var author = (pr.videoDetails && pr.videoDetails.author) || "";
                                            var duration = (pr.videoDetails && pr.videoDetails.lengthSeconds) || "0";
                                            var formats = (pr.streamingData.adaptiveFormats || []).concat(pr.streamingData.formats || []);
                                            var audioUrl = "";
                                            var videoUrl = "";
                                            var maxBr = 0;
                                            for (var i = 0; i < formats.length; i++) {
                                                var f = formats[i];
                                                if (f.url && f.mimeType && f.mimeType.indexOf("audio/") === 0) {
                                                    var br = f.bitrate || 0;
                                                    if (br >= maxBr) {
                                                        maxBr = br;
                                                        audioUrl = f.url;
                                                    }
                                                }
                                            }
                                            for (var j = 0; j < formats.length; j++) {
                                                var vf = formats[j];
                                                if (vf.url && vf.mimeType && vf.mimeType.indexOf("video/") === 0) {
                                                    videoUrl = vf.url;
                                                    break;
                                                }
                                            }
                                            if (audioUrl) {
                                                return JSON.stringify({
                                                    title: title,
                                                    author: author,
                                                    duration: duration,
                                                    audioUrl: audioUrl,
                                                    videoUrl: videoUrl || audioUrl
                                                });
                                            }
                                        }

                                        // Fallback a etiqueta <video> si ya comenzó
                                        var vTag = document.querySelector('video');
                                        if (vTag && vTag.src && vTag.src.indexOf('http') === 0) {
                                            return JSON.stringify({
                                                title: document.title || "Video Web",
                                                author: "YouTube Web",
                                                duration: "0",
                                                audioUrl: vTag.src,
                                                videoUrl: vTag.src
                                            });
                                        }
                                    } catch(e) {}
                                    return "";
                                })();
                            """.trimIndent()

                            view?.evaluateJavascript(jsExtract) { resultJson ->
                                if (!streamExtracted && !resultJson.isNullOrBlank() && resultJson != "\"\"" && resultJson != "null") {
                                    try {
                                        val cleanJson = if (resultJson.startsWith("\"") && resultJson.endsWith("\"")) {
                                            try {
                                                org.json.JSONTokener(resultJson).nextValue().toString()
                                            } catch (_: Throwable) {
                                                resultJson.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
                                            }
                                        } else resultJson

                                        val obj = JSONObject(cleanJson)
                                        val aUrl = obj.optString("audioUrl", "")
                                        if (aUrl.isNotBlank()) {
                                            streamExtracted = true
                                            val info = OnlineVideoAudioImporter.ResolvedMediaInfo(
                                                originalUrl = originalUrl,
                                                suggestedTitle = obj.optString("title", "Audio de YouTube"),
                                                suggestedArtist = obj.optString("author", "Música Web"),
                                                videoUrl = obj.optString("videoUrl", aUrl),
                                                audioUrl = aUrl,
                                                coverUrl = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg",
                                                durationSeconds = obj.optLong("duration", 0L)
                                            )
                                            cleanup()
                                            if (continuation.isActive) {
                                                continuation.resume(info)
                                            }
                                        }
                                    } catch (e: Throwable) {
                                        AuraDebugManager.logWarning("WebStreamExtractor", "Error parseando JSON de WebView: ${e.message}")
                                    }
                                }
                            }
                        }
                    }

                    // Carga la versión embebida limpia nocookie con reproducción automática silenciada
                    view.loadUrl("https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&mute=1")

                } catch (t: Throwable) {
                    cleanup()
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        }
    }
}
