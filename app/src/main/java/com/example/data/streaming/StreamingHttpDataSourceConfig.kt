package com.example.data.streaming

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import java.util.concurrent.ConcurrentHashMap

/**
 * Aura Music - Registro Dinámico de Cabeceras HTTP y Fábrica de DataSource para Streaming
 *
 * Rol Arquitectónico:
 * - Resuelve de raíz el error `ERROR_CODE_IO_BAD_HTTP_STATUS (Response code: 403)` en ExoPlayer
 *   cuando reproduce flujos firmados de `googlevideo.com` extraídos por `yt-dlp` o `InnerTube`.
 * - Asocia cada `videoId` o URL firmada con el `User-Agent`, `Origin`, `Referer` y cabeceras exactas
 *   con las que se generó el token (`c=WEB`, `c=ANDROID_VR`, `c=IOS`, `c=ANDROID_MUSIC`, etc.).
 * - Limpia parámetros de restricción de rango (`&range=...`, `&rn=...`, `&rbuf=...`) que provocan
 *   rechazo HTTP 403 cuando ExoPlayer envía su propia cabecera `Range: bytes=0-`.
 */
object StreamingHttpDataSourceConfig {

    private const val DEFAULT_CHROME_MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    private const val VR_CLIENT_UA =
        "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

    private const val VISION_CLIENT_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"

    private const val ANDROID_MUSIC_UA =
        "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 14; es_US) gzip"

    private const val IOS_CLIENT_UA =
        "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"

    private const val TV_CLIENT_UA =
        "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"

    private val headersByVideoId = ConcurrentHashMap<String, Map<String, String>>()
    private val headersByUrlKey = ConcurrentHashMap<String, Map<String, String>>()

    /**
     * Registra las cabeceras HTTP exactas devueltas por `yt-dlp` o `InnerTube` para un `videoId`
     * y sus URLs de audio y video asociadas.
     */
    fun registerStreamHeaders(
        videoId: String?,
        audioUrl: String?,
        videoUrl: String?,
        headers: Map<String, String>,
        videoHeaders: Map<String, String>? = null
    ) {
        fun cleanMap(raw: Map<String, String>): Map<String, String> {
            return raw.filter { (k, v) ->
                k.isNotBlank() && v.isNotBlank() &&
                    !k.equals("Range", ignoreCase = true) &&
                    !k.equals("Accept-Encoding", ignoreCase = true)
            }
        }

        val cleanAudioHeaders = cleanMap(headers)
        val cleanVideoHeaders = cleanMap(videoHeaders ?: headers)

        if (!videoId.isNullOrBlank() && cleanAudioHeaders.isNotEmpty()) {
            headersByVideoId[videoId.trim()] = cleanAudioHeaders
        }
        if (!audioUrl.isNullOrBlank() && cleanAudioHeaders.isNotEmpty()) {
            headersByUrlKey[extractUrlSignatureKey(audioUrl)] = cleanAudioHeaders
        }
        if (!videoUrl.isNullOrBlank() && cleanVideoHeaders.isNotEmpty()) {
            headersByUrlKey[extractUrlSignatureKey(videoUrl)] = cleanVideoHeaders
        }
    }

    /**
     * Obtiene las cabeceras óptimas para una URL o `videoId`, alineando estrictamente el `User-Agent`,
     * `Origin` y `Referer` requeridos por el parámetro `c=` de `googlevideo.com` para prevenir HTTP 403.
     */
    fun resolveHeadersForStream(videoId: String?, rawUrl: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()

        // 1. Cabeceras registradas explícitamente por yt-dlp o InnerTube para esta URL específica (prioridad por clave de URL + itag)
        val byUrl = headersByUrlKey[extractUrlSignatureKey(rawUrl)]
        val byId = if (!videoId.isNullOrBlank()) headersByVideoId[videoId.trim()] else null
        val baseHeaders = byUrl ?: byId ?: emptyMap()
        result.putAll(baseHeaders)

        // 2. Verificar si es un host de YouTube / GoogleVideo para alinear el User-Agent con el cliente (`c=`)
        val isGoogleVideo = isGoogleVideoOrYoutubeUrl(rawUrl)
        if (isGoogleVideo) {
            val clientParam = extractQueryParam(rawUrl, "c")?.uppercase() ?: ""
            val inferredUa = when {
                clientParam.contains("ANDROID_VR") -> VR_CLIENT_UA
                clientParam.contains("VISIONOS") -> VISION_CLIENT_UA
                clientParam.contains("ANDROID_MUSIC") -> ANDROID_MUSIC_UA
                clientParam.contains("IOS") -> IOS_CLIENT_UA
                clientParam.contains("TVHTML5") -> TV_CLIENT_UA
                else -> null
            }

            val currentUa = result["User-Agent"] ?: result["user-agent"]
            // Si la URL pertenece a un cliente nativo (ANDROID_VR, IOS, ANDROID_MUSIC) pero las cabeceras tenían un User-Agent de navegador Web (o viceversa), corregir para evitar HTTP 403
            val hasMismatchedWebUaOnNativeClient = inferredUa != null &&
                !clientParam.startsWith("WEB") &&
                !clientParam.contains("VISIONOS") &&
                (currentUa == null || currentUa.contains("Mozilla/5.0 (Windows", ignoreCase = true) || currentUa.contains("Chrome/", ignoreCase = true))

            if (currentUa.isNullOrBlank() || hasMismatchedWebUaOnNativeClient) {
                result.remove("user-agent")
                result["User-Agent"] = inferredUa ?: DEFAULT_CHROME_MOBILE_UA
            }

            if (clientParam.startsWith("WEB") || clientParam.isEmpty()) {
                if (!result.keys.any { it.equals("Origin", ignoreCase = true) }) {
                    result["Origin"] = "https://www.youtube.com"
                }
                if (!result.keys.any { it.equals("Referer", ignoreCase = true) }) {
                    result["Referer"] = "https://www.youtube.com/"
                }
            } else if (clientParam.contains("IOS") || clientParam.contains("ANDROID")) {
                // Los clientes nativos (IOS / ANDROID_VR / ANDROID_MUSIC) no deben enviar Origin/Referer de navegador web
                result.keys.removeAll {
                    it.equals("Origin", ignoreCase = true) || it.equals("Referer", ignoreCase = true)
                }
            }

            // Adjuntar cookies de WebView solo si fue extraído por cliente web y no tiene Cookie propia
            if (clientParam.startsWith("WEB") && !result.keys.any { it.equals("Cookie", ignoreCase = true) }) {
                try {
                    val cookies = CookieManager.getInstance().getCookie("https://m.youtube.com")
                    if (!cookies.isNullOrBlank()) {
                        result["Cookie"] = cookies
                    }
                } catch (_: Throwable) {}
            }
        } else if (!result.keys.any { it.equals("User-Agent", ignoreCase = true) }) {
            result["User-Agent"] = DEFAULT_CHROME_MOBILE_UA
        }

        result["Accept"] = "*/*"
        return result
    }

    /**
     * Limpia parámetros conflictivos en URLs de `googlevideo.com` (`&range=...`, `&rn=...`, `&rbuf=...`)
     * para que `ExoPlayer` pueda abrir sus propias peticiones `Range: bytes=0-` sin recibir `403 Forbidden`.
     */
    fun sanitizeStreamUri(uri: Uri): Uri {
        val raw = uri.toString()
        if (!isGoogleVideoOrYoutubeUrl(raw)) return uri
        val cleaned = raw
            .replace(Regex("""&range=[^&]+"""), "")
            .replace(Regex("""\?range=[^&]+&"""), "?")
            .replace(Regex("""&rn=[^&]+"""), "")
            .replace(Regex("""&rbuf=[^&]+"""), "")
        return if (cleaned != raw) Uri.parse(cleaned) else uri
    }

    /**
     * Construye un [DataSource.Factory] para ExoPlayer (Audio y Video Canvas) que soporta tanto archivos locales
     * (`file://`, `content://`) como flujos HTTP/HTTPS con inyección dinámica de cabeceras y saneamiento de URL.
     */
    @OptIn(UnstableApi::class)
    fun buildExoPlayerDataSourceFactory(context: Context): DataSource.Factory {
        val appContext = context.applicationContext
        val baseHttpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(DEFAULT_CHROME_MOBILE_UA)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)

        val dynamicHttpFactory = DataSource.Factory {
            val delegate = baseHttpFactory.createDataSource()
            ResolvingDataSource(delegate) { dataSpec ->
                val originalUri = dataSpec.uri
                val sanitizedUri = sanitizeStreamUri(originalUri)
                val dynamicHeaders = resolveHeadersForStream(videoId = null, rawUrl = originalUri.toString())

                val mergedHeaders = LinkedHashMap<String, String>()
                mergedHeaders.putAll(dataSpec.httpRequestHeaders)
                for ((k, v) in dynamicHeaders) {
                    if (!k.equals("Range", ignoreCase = true)) {
                        mergedHeaders[k] = v
                    }
                }

                dataSpec.buildUpon()
                    .setUri(sanitizedUri)
                    .setHttpRequestHeaders(mergedHeaders)
                    .build()
            }
        }

        return DefaultDataSource.Factory(appContext, dynamicHttpFactory)
    }

    private fun isGoogleVideoOrYoutubeUrl(rawUrl: String): Boolean {
        return try {
            val host = Uri.parse(rawUrl.trim()).host?.lowercase() ?: return false
            host == "googlevideo.com" ||
                host.endsWith(".googlevideo.com") ||
                host == "youtube.com" ||
                host.endsWith(".youtube.com")
        } catch (_: Exception) {
            false
        }
    }

    private fun extractQueryParam(rawUrl: String, param: String): String? {
        return try {
            Uri.parse(rawUrl).getQueryParameter(param)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractUrlSignatureKey(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val idParam = extractQueryParam(trimmed, "id")
        val itagParam = extractQueryParam(trimmed, "itag")
        if (!idParam.isNullOrBlank()) {
            return "gv_${idParam}_${itagParam.orEmpty()}"
        }
        return trimmed.substringBefore("&range=").take(220)
    }
}
