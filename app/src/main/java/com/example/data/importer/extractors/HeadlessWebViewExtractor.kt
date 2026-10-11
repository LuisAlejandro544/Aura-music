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
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

/**
 * Extractor Headless basado en WebView para YouTube y video web.
 *
 * Rol Arquitectónico:
 * Ejecuta un navegador efímero en segundo plano en la página móvil oficial (m.youtube.com),
 * evitando las restricciones de inserción (error 150 de reproductores embebidos) y
 * capturando el flujo directamente mediante la intercepción de peticiones de red
 * a googlevideo.com o la evaluación del objeto 'ytInitialPlayerResponse'.
 */
object HeadlessWebViewExtractor {

    private val SAFE_YOUTUBE_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")

    private fun isAllowedYoutubeNavigationHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return h == "youtube.com" ||
                h.endsWith(".youtube.com") ||
                h == "googlevideo.com" ||
                h.endsWith(".googlevideo.com") ||
                h == "google.com" ||
                h.endsWith(".google.com") ||
                h == "ytimg.com" ||
                h.endsWith(".ytimg.com") ||
                h == "ggpht.com" ||
                h.endsWith(".ggpht.com")
    }

    suspend fun resolve(
        context: Context,
        videoId: String,
        originalUrl: String
    ): OnlineVideoAudioImporter.ResolvedMediaInfo? = withContext(Dispatchers.Main) {
        val cleanVideoId = videoId.trim()
        if (!SAFE_YOUTUBE_ID_REGEX.matches(cleanVideoId)) {
            AuraDebugManager.logWarning("HeadlessWebView", "ID de video inválido o potencialmente malicioso rechazado: $videoId")
            return@withContext null
        }

        // Timeout de 22 segundos para conexiones móviles y carga de DOM
        withTimeoutOrNull(22000L) {
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
                        databaseEnabled = false
                        allowFileAccess = false
                        allowContentAccess = false
                        @Suppress("DEPRECATION")
                        allowFileAccessFromFileURLs = false
                        @Suppress("DEPRECATION")
                        allowUniversalAccessFromFileURLs = false
                        mediaPlaybackRequiresUserGesture = false
                        loadsImagesAutomatically = true
                        blockNetworkImage = false
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    }

                    var streamExtracted = false
                    var capturedAudioUrl: String? = null
                    var capturedVideoUrl: String? = null

                    view.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val navUri = request?.url ?: return true
                            val scheme = navUri.scheme?.lowercase() ?: return true
                            if (scheme != "https") {
                                return true
                            }
                            return !isAllowedYoutubeNavigationHost(navUri.host)
                        }
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: ""

                            // 1. Interceptar URL directa de streaming descifrada por el reproductor
                            if (reqUrl.contains("googlevideo.com/videoplayback") && reqUrl.contains("itag=")) {
                                val isAudio = reqUrl.contains("mime=audio") ||
                                        reqUrl.contains("itag=140") ||
                                        reqUrl.contains("itag=251") ||
                                        reqUrl.contains("itag=171") ||
                                        reqUrl.contains("itag=249") ||
                                        reqUrl.contains("itag=250")

                                // Limpiar el parámetro de rango (&range=0-...) para descargar el stream completo
                                val cleanStreamUrl = reqUrl
                                    .replace(Regex("""&range=\d+-\d+"""), "")
                                    .replace(Regex("""\?range=\d+-\d+&"""), "?")

                                if (isAudio) {
                                    capturedAudioUrl = cleanStreamUrl
                                    if (capturedVideoUrl == null) capturedVideoUrl = cleanStreamUrl
                                } else if (reqUrl.contains("mime=video")) {
                                    capturedVideoUrl = cleanStreamUrl
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
                                                coverUrl = "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
                                                durationSeconds = 0L
                                            )
                                            cleanup()
                                            continuation.resume(info)
                                        }
                                    }
                                }
                            }

                            // 2. Bloqueo exclusivo de redes publicitarias pesadas (permitiendo sprites y assets esenciales)
                            if (reqUrl.contains("googleads") ||
                                reqUrl.contains("doubleclick") ||
                                reqUrl.contains("adservice.google") ||
                                reqUrl.contains("analytics")
                            ) {
                                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                            }

                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (streamExtracted) return

                            // Inyección de script para pulsar play e inspeccionar ytInitialPlayerResponse
                            val jsExtract = """
                                (function() {
                                    try {
                                        // Auto-aceptar posibles modales de cookies
                                        var consentBtns = document.querySelectorAll('button[aria-label*="Aceptar"], button[aria-label*="Agree"], button[aria-label*="accept"], form[action*="consent"] button');
                                        consentBtns.forEach(function(b) { b.click(); });

                                        // Simular inicio del reproductor
                                        var playBtn = document.querySelector('.ytp-large-play-button, button.ytp-play-button, .player-control-play-pause-icon, video');
                                        if (playBtn) { playBtn.click(); }

                                        var pr = window.ytInitialPlayerResponse || 
                                                 (window.ytplayer && window.ytplayer.config && window.ytplayer.config.args && JSON.parse(window.ytplayer.config.args.raw_player_response));
                                        
                                        if (pr && pr.streamingData) {
                                            var title = (pr.videoDetails && pr.videoDetails.title) || "";
                                            var author = (pr.videoDetails && pr.videoDetails.author) || "";
                                            var duration = (pr.videoDetails && pr.videoDetails.lengthSeconds) || "0";
                                            var formats = (pr.streamingData.adaptiveFormats || []).concat(pr.streamingData.formats || []);
                                            var audioUrl = "";
                                            var videoUrl = "";
                                            var coverUrl = "";
                                            var maxBr = 0;
                                            
                                            // 1. Extraer la miniatura de mayor resolución disponible desde videoDetails
                                            var thumbs = (pr.videoDetails && pr.videoDetails.thumbnail && pr.videoDetails.thumbnail.thumbnails) || [];
                                            if (thumbs.length > 0) {
                                                coverUrl = thumbs[thumbs.length - 1].url || "";
                                            }
                                            if (!coverUrl) {
                                                var ogImg = document.querySelector('meta[property="og:image"]');
                                                if (ogImg && ogImg.content) coverUrl = ogImg.content;
                                            }
                                            if (coverUrl && coverUrl.indexOf('//') === 0) {
                                                coverUrl = 'https:' + coverUrl;
                                            }

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
                                                    videoUrl: videoUrl || audioUrl,
                                                    coverUrl: coverUrl
                                                });
                                            }
                                        }

                                        // Fallback a etiqueta <video> si ya comenzó a cargar
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
                                            val rawCover = obj.optString("coverUrl")
                                            val safeCover = when {
                                                rawCover.startsWith("//") -> "https:$rawCover"
                                                rawCover.startsWith("http://") -> "https://" + rawCover.removePrefix("http://")
                                                rawCover.startsWith("https://") -> rawCover
                                                else -> "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                                            }
                                            val info = OnlineVideoAudioImporter.ResolvedMediaInfo(
                                                originalUrl = originalUrl,
                                                suggestedTitle = obj.optString("title", "Audio de YouTube"),
                                                suggestedArtist = obj.optString("author", "Música Web"),
                                                videoUrl = obj.optString("videoUrl", aUrl),
                                                audioUrl = aUrl,
                                                coverUrl = safeCover,
                                                durationSeconds = obj.optLong("duration", 0L)
                                            )
                                            cleanup()
                                            if (continuation.isActive) {
                                                continuation.resume(info)
                                            }
                                        }
                                    } catch (e: Throwable) {
                                        AuraDebugManager.logWarning("HeadlessWebView", "Error parseando JSON de WebView: ${e.message}")
                                    }
                                }
                            }
                        }
                    }

                    // Carga la versión móvil oficial en lugar de la versión embed
                    view.loadUrl("https://m.youtube.com/watch?v=$cleanVideoId")

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
