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
        "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)"

    private const val VISION_CLIENT_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15"

    private const val ANDROID_MUSIC_UA =
        "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14; es_ES) gzip"

    private const val IOS_CLIENT_UA =
        "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"

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
        headers: Map<String, String>
    ) {
        if (headers.isEmpty()) return
        val cleanHeaders = headers.filter { (k, v) ->
            k.isNotBlank() && v.isNotBlank() &&
                !k.equals("Range", ignoreCase = true) &&
                !k.equals("Accept-Encoding", ignoreCase = true)
        }
        if (cleanHeaders.isEmpty()) return

        if (!videoId.isNullOrBlank()) {
            headersByVideoId[videoId.trim()] = cleanHeaders
        }
        if (!audioUrl.isNullOrBlank()) {
            headersByUrlKey[extractUrlSignatureKey(audioUrl)] = cleanHeaders
        }
        if (!videoUrl.isNullOrBlank()) {
            headersByUrlKey[extractUrlSignatureKey(videoUrl)] = cleanHeaders
        }
    }

    /**
     * Obtiene las cabeceras óptimas para una URL o `videoId`, infiriendo automáticamente el `User-Agent`
     * requerido por el parámetro `c=` de `googlevideo.com` si no hubiera cabeceras explícitas registradas.
     */
    fun resolveHeadersForStream(videoId: String?, rawUrl: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()

        // 1. Cabeceras registradas explícitamente por yt-dlp o InnerTube para esta URL o videoId
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
                else -> null
            }

            val currentUa = result["User-Agent"] ?: result["user-agent"]
            if (currentUa.isNullOrBlank()) {
                result["User-Agent"] = inferredUa ?: DEFAULT_CHROME_MOBILE_UA
            }

            // Si el cliente es WEB o WEB_REMIX y no hay Origin/Referer, agregarlos evita 403 en nodos CDN estrictos
            if (clientParam.startsWith("WEB") || clientParam.isEmpty()) {
                if (!result.keys.any { it.equals("Origin", ignoreCase = true) }) {
                    result["Origin"] = "https://www.youtube.com"
                }
                if (!result.keys.any { it.equals("Referer", ignoreCase = true) }) {
                    result["Referer"] = "https://www.youtube.com/"
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
