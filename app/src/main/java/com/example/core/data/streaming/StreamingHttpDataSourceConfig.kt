package com.example.core.data.streaming

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
        "Mozilla/5.0 (Android; VR; Meta Quest 3) AppleWebKit/537.36"

    private const val VISION_CLIENT_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15"

    private const val ANDROID_MUSIC_UA =
        "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14; es_ES) gzip"

    private const val ANDROID_CLIENT_UA =
        "com.google.android.youtube/19.29.37 (Linux; U; Android 14; es_ES) gzip"

    private const val IOS_CLIENT_UA =
        "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)"

    private const val TVHTML5_CLIENT_UA =
        "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"

    private val headersByVideoId = ConcurrentHashMap<String, Map<String, String>>()
    private val headersByUrlKey = ConcurrentHashMap<String, Map<String, String>>()

    /**
     * Registra las cabeceras HTTP exactas devueltas por `yt-dlp` o `InnerTube` para un `videoId`
     * y sus URLs de audio y video asociadas (respetando cabeceras independientes para audio y video).
     */
    fun registerStreamHeaders(
        videoId: String?,
        audioUrl: String?,
        videoUrl: String?,
        headers: Map<String, String>,
        audioHeaders: Map<String, String> = emptyMap(),
        videoHeaders: Map<String, String> = emptyMap()
    ) {
        fun sanitizeMap(raw: Map<String, String>): Map<String, String> {
            return raw.filter { (k, v) ->
                k.isNotBlank() && v.isNotBlank() &&
                    !k.equals("Range", ignoreCase = true) &&
                    !k.equals("Accept-Encoding", ignoreCase = true)
            }
        }

        val cleanBase = sanitizeMap(headers)
        val cleanAudio = sanitizeMap(audioHeaders).ifEmpty { cleanBase }
        val cleanVideo = sanitizeMap(videoHeaders).ifEmpty { cleanBase }

        if (!videoId.isNullOrBlank() && cleanAudio.isNotEmpty()) {
            headersByVideoId[videoId.trim()] = cleanAudio
        }
        if (!audioUrl.isNullOrBlank() && cleanAudio.isNotEmpty()) {
            headersByUrlKey[extractUrlSignatureKey(audioUrl)] = cleanAudio
        }
        if (!videoUrl.isNullOrBlank() && cleanVideo.isNotEmpty()) {
            headersByUrlKey[extractUrlSignatureKey(videoUrl)] = cleanVideo
        }
    }

    /**
     * Infiere el `User-Agent` canónico exigido por el parámetro `c=` firmado dentro de una URL de `googlevideo.com`.
     */
    fun inferCanonicalUserAgentForClient(clientParamRaw: String?): String? {
        val c = clientParamRaw?.trim()?.uppercase() ?: return null
        return when {
            c.contains("ANDROID_VR") -> VR_CLIENT_UA
            c.contains("VISIONOS") -> VISION_CLIENT_UA
            c.contains("ANDROID_MUSIC") -> ANDROID_MUSIC_UA
            c == "ANDROID" || c.startsWith("ANDROID_") -> ANDROID_CLIENT_UA
            c.contains("IOS") -> IOS_CLIENT_UA
            c.contains("TVHTML5") -> TVHTML5_CLIENT_UA
            c.startsWith("WEB") || c.startsWith("MWEB") -> DEFAULT_CHROME_MOBILE_UA
            else -> null
        }
    }

    /**
     * Comprueba si un `User-Agent` existente es compatible con la familia del parámetro `c=` de `googlevideo.com`.
     * Si el formato de audio fue extraído con `c=IOS` pero el mapa tenía un User-Agent de Chrome/Android,
     * devuelve `false` para corregir la colisión y evitar el rechazo `HTTP 403 Forbidden`.
     */
    private fun isUserAgentCompatibleWithClient(userAgent: String, clientParamRaw: String?): Boolean {
        val c = clientParamRaw?.trim()?.uppercase() ?: return true
        if (c.isEmpty()) return true
        val uaLower = userAgent.lowercase()
        return when {
            c.contains("IOS") -> uaLower.contains("ios") || uaLower.contains("iphone") || uaLower.contains("cfnetwork")
            c.contains("ANDROID_VR") -> uaLower.contains("vr") || uaLower.contains("quest") || uaLower.contains("android")
            c.contains("VISIONOS") -> uaLower.contains("macintosh") || uaLower.contains("vision") || uaLower.contains("applewebkit")
            c.contains("ANDROID_MUSIC") -> uaLower.contains("youtube.music") || uaLower.contains("android")
            c == "ANDROID" -> uaLower.contains("com.google.android.youtube") || uaLower.contains("android")
            c.startsWith("WEB") || c.startsWith("MWEB") -> uaLower.contains("mozilla/5.0") && !uaLower.contains("com.google.ios.youtube")
            else -> true
        }
    }

    /**
     * Obtiene las cabeceras óptimas para una URL o `videoId`, alineando estrictamente el `User-Agent`,
     * `Origin`, `Referer` y `Cookie` con el parámetro `c=` firmado en la URL de `googlevideo.com`.
     */
    fun resolveHeadersForStream(
        videoId: String?,
        rawUrl: String,
        explicitHeaders: Map<String, String> = emptyMap()
    ): Map<String, String> {
        val result = LinkedHashMap<String, String>()

        // 1. Cabeceras registradas explícitamente por URL, explícitas pasadas por parámetro o por videoId
        val byUrl = headersByUrlKey[extractUrlSignatureKey(rawUrl)]
        val byId = if (!videoId.isNullOrBlank()) headersByVideoId[videoId.trim()] else null
        val baseHeaders = when {
            explicitHeaders.isNotEmpty() -> explicitHeaders
            byUrl != null -> byUrl
            byId != null -> byId
            else -> emptyMap()
        }
        for ((k, v) in baseHeaders) {
            if (!k.equals("Range", ignoreCase = true) && !k.equals("Accept-Encoding", ignoreCase = true)) {
                result[k] = v
            }
        }

        // 2. Verificar si es un host de YouTube / GoogleVideo para alinear el User-Agent con el cliente (`c=`)
        val isGoogleVideo = isGoogleVideoOrYoutubeUrl(rawUrl)
        if (isGoogleVideo) {
            val clientParam = extractQueryParam(rawUrl, "c")?.uppercase() ?: ""
            val inferredUa = inferCanonicalUserAgentForClient(clientParam)

            val existingUaKey = result.keys.firstOrNull { it.equals("User-Agent", ignoreCase = true) }
            val currentUa = if (existingUaKey != null) result[existingUaKey] else null

            // Si no hay User-Agent o el User-Agent actual colisiona con el cliente firmado `c=`, corregirlo
            if (currentUa.isNullOrBlank() || !isUserAgentCompatibleWithClient(currentUa, clientParam)) {
                if (existingUaKey != null) result.remove(existingUaKey)
                result["User-Agent"] = inferredUa ?: DEFAULT_CHROME_MOBILE_UA
            }

            val isWebFamilyClient = clientParam.startsWith("WEB") || clientParam.startsWith("MWEB") || clientParam.isEmpty()
            if (isWebFamilyClient) {
                if (!result.keys.any { it.equals("Origin", ignoreCase = true) }) {
                    result["Origin"] = "https://www.youtube.com"
                }
                if (!result.keys.any { it.equals("Referer", ignoreCase = true) }) {
                    result["Referer"] = "https://www.youtube.com/"
                }
                if (!result.keys.any { it.equals("Cookie", ignoreCase = true) }) {
                    try {
                        val cookies = CookieManager.getInstance().getCookie("https://m.youtube.com")
                        if (!cookies.isNullOrBlank()) {
                            result["Cookie"] = cookies
                        }
                    } catch (_: Throwable) {}
                }
            } else {
                // Para clientes nativos (IOS, ANDROID_VR, ANDROID_MUSIC, ANDROID, VISIONOS),
                // eliminar Origin/Referer/Cookie de navegador web que provocan rechazo 403 inmediato en googlevideo.com
                val keysToRemove = result.keys.filter {
                    it.equals("Origin", ignoreCase = true) ||
                        it.equals("Referer", ignoreCase = true) ||
                        it.equals("Cookie", ignoreCase = true)
                }
                keysToRemove.forEach { result.remove(it) }
            }
        } else if (!result.keys.any { it.equals("User-Agent", ignoreCase = true) }) {
            result["User-Agent"] = DEFAULT_CHROME_MOBILE_UA
        }

        result["Accept"] = "*/*"
        result["Accept-Encoding"] = "identity"
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
