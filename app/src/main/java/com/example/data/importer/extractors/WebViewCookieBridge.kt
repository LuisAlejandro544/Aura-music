package com.example.data.importer

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/**
 * Extractor y Puente Automático de Cookies del WebView hacia formato Netscape (`cookies.txt`).
 *
 * Rol Arquitectónico:
 * 1. En Android, `--cookies-from-browser` está restringido por el sandbox de Linux entre aplicaciones.
 *    Este componente extrae en tiempo real todas las cookies reales del navegador WebView interno
 *    de la app (`CookieManager.getInstance()`) para los dominios de YouTube, YouTube Music y Google,
 *    y las exporta automáticamente a un archivo estándar Netscape HTTP Cookie File (`cookies.txt`).
 * 2. Permite que `yt-dlp` (`--cookies /ruta/a/cookies.txt --user-agent "..."`) e `InnerTubeClient`
 *    actúen exactamente como un navegador móvil de un usuario real, superando bloqueos de bot
 *    (`Sign in to confirm you're not a bot`) y restricciones de sesión.
 * 3. Incluye un calentador automático silencioso (`warmUpAndExportCookies`) y soporte para importar
 *    o refrescar cookies desde una sesión interactiva de navegador WebView o archivo `.txt`.
 */
object WebViewCookieBridge {

    private const val TAG = "WebViewCookieBridge"
    private const val PREFS_NAME = "aura_webview_cookies_prefs"
    private const val KEY_USER_AGENT = "captured_webview_user_agent"
    private const val KEY_VISITOR_DATA = "captured_visitor_data"

    const val DEFAULT_MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    private val TARGET_DOMAINS = listOf(
        "https://www.youtube.com" to ".youtube.com",
        "https://m.youtube.com" to ".youtube.com",
        "https://music.youtube.com" to ".youtube.com",
        "https://youtube.com" to ".youtube.com",
        "https://www.google.com" to ".google.com",
        "https://accounts.google.com" to ".google.com"
    )

    /**
     * Devuelve la ruta canónica del archivo `cookies.txt` dentro del directorio privado de la app.
     */
    fun getCookiesFile(context: Context): File {
        val envDir = File(context.filesDir, "env")
        if (!envDir.exists()) envDir.mkdirs()
        return File(envDir, "cookies.txt")
    }

    /**
     * Obtiene el User-Agent sincronizado con las cookies del WebView para que coincida 1:1 en `yt-dlp`.
     */
    fun getCapturedUserAgent(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_AGENT, null)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_MOBILE_USER_AGENT
    }

    fun saveCapturedUserAgent(context: Context, userAgent: String?) {
        if (userAgent.isNullOrBlank()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_AGENT, userAgent.trim())
            .apply()
    }

    fun getCapturedVisitorData(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_VISITOR_DATA, null)?.takeIf { it.isNotBlank() }
    }

    fun saveCapturedVisitorData(context: Context, visitorData: String?) {
        if (visitorData.isNullOrBlank()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VISITOR_DATA, visitorData.trim())
            .apply()
    }

    /**
     * Comprueba si existe un archivo `cookies.txt` válido con cookies de YouTube listas para usar.
     */
    fun hasValidCookiesFile(context: Context): Boolean {
        val file = getCookiesFile(context)
        return file.exists() && file.length() > 80L
    }

    /**
     * Comprueba si existen cookies reales de sesión autenticada de Google/YouTube
     * (`SAPISID`, `__Secure-3PAPISID`, `SID`, `LOGIN_INFO`), evitando pasar cookies vacías de invitado (`SOCS`)
     * que en `yt-dlp` o `InnerTubeClient` desactivan clientes nativos (`android_vr` / `ios`).
     */
    fun hasAuthenticatedSessionCookies(context: Context? = null): Boolean {
        return try {
            val authCookieNames = setOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID", "SID", "HSID", "SSID", "LOGIN_INFO")
            if (context != null) {
                val file = getCookiesFile(context)
                if (file.exists() && file.length() >= 120L) {
                    val foundInFile = file.readLines().any { line ->
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@any false
                        val cols = trimmed.split("\t")
                        cols.size >= 7 && cols[5].trim() in authCookieNames && cols[6].trim().isNotEmpty()
                    }
                    if (foundInFile) return true
                }
            }
            val rawHeader = getConsolidatedCookieHeader() ?: return false
            rawHeader.split(";").any { pair ->
                val name = pair.substringBefore("=", "").trim()
                val value = pair.substringAfter("=", "").trim()
                name in authCookieNames && value.isNotEmpty()
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Cuenta cuántas entradas de cookies activas existen actualmente en `cookies.txt`.
     */
    fun getCookieEntriesCount(context: Context): Int {
        return try {
            val file = getCookiesFile(context)
            if (!file.exists()) return 0
            file.readLines().count { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("\t")
            }
        } catch (_: Throwable) {
            0
        }
    }

    /**
     * Devuelve una cabecera HTTP `Cookie: ...` consolidada directamente desde el `CookieManager`
     * para usar en peticiones directas de `InnerTubeClient` o `StreamingApiService`.
     */
    fun getConsolidatedCookieHeader(): String? {
        return try {
            val cookieManager = CookieManager.getInstance()
            val merged = linkedMapOf<String, String>()
            val urls = listOf(
                "https://www.youtube.com",
                "https://m.youtube.com",
                "https://music.youtube.com"
            )
            for (url in urls) {
                val raw = cookieManager.getCookie(url) ?: continue
                raw.split(";").forEach { pair ->
                    val trimmed = pair.trim()
                    val eqIdx = trimmed.indexOf('=')
                    if (eqIdx > 0) {
                        val name = trimmed.substring(0, eqIdx).trim()
                        val value = trimmed.substring(eqIdx + 1).trim()
                        if (name.isNotEmpty() && value.isNotEmpty()) {
                            merged[name] = value
                        }
                    }
                }
            }
            if (!merged.containsKey("CONSENT")) {
                merged["CONSENT"] = "YES+cb.20240720-07-p0.es+FX+999"
            }
            if (!merged.containsKey("SOCS")) {
                merged["SOCS"] = "CAI"
            }
            if (merged.isEmpty()) null
            else merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Extrae todas las cookies actuales del `CookieManager` de Android WebView y las guarda
     * en formato estándar Netscape HTTP Cookie File (`cookies.txt`) para `--cookies` en `yt-dlp`.
     */
    fun syncWebViewCookiesToNetscapeFile(context: Context): File? {
        return try {
            val cookieManager = CookieManager.getInstance()
            try {
                cookieManager.flush()
            } catch (_: Throwable) {}

            val cookiesMap = linkedMapOf<String, CookieRecord>()
            val expirationEpoch = (System.currentTimeMillis() / 1000L) + (86400L * 180L) // +180 días

            for ((url, domainTag) in TARGET_DOMAINS) {
                val cookieHeader = try {
                    cookieManager.getCookie(url)
                } catch (_: Throwable) {
                    null
                } ?: continue

                val parts = cookieHeader.split(";")
                for (part in parts) {
                    val trimmed = part.trim()
                    val eqIndex = trimmed.indexOf('=')
                    if (eqIndex <= 0) continue

                    val rawName = trimmed.substring(0, eqIndex).trim()
                    val rawValue = trimmed.substring(eqIndex + 1).trim()
                    if (rawName.isBlank() || rawName.contains("\t") || rawName.contains("\n")) continue
                    if (rawValue.contains("\t") || rawValue.contains("\n")) continue

                    val key = "$domainTag|$rawName"
                    cookiesMap[key] = CookieRecord(
                        domain = domainTag,
                        includeSubdomains = "TRUE",
                        path = "/",
                        secure = "TRUE",
                        expiry = expirationEpoch,
                        name = rawName,
                        value = rawValue
                    )
                }
            }

            // Asegurar siempre las cookies de consentimiento de YouTube para evitar pantallas de bloqueo GDPR/SOCS
            if (!cookiesMap.containsKey(".youtube.com|SOCS")) {
                cookiesMap[".youtube.com|SOCS"] = CookieRecord(
                    domain = ".youtube.com",
                    includeSubdomains = "TRUE",
                    path = "/",
                    secure = "TRUE",
                    expiry = expirationEpoch,
                    name = "SOCS",
                    value = "CAI"
                )
            }
            if (!cookiesMap.containsKey(".youtube.com|CONSENT")) {
                cookiesMap[".youtube.com|CONSENT"] = CookieRecord(
                    domain = ".youtube.com",
                    includeSubdomains = "TRUE",
                    path = "/",
                    secure = "TRUE",
                    expiry = expirationEpoch,
                    name = "CONSENT",
                    value = "YES+cb.20240720-07-p0.es+FX+999"
                )
            }
            if (!cookiesMap.containsKey(".youtube.com|PREF")) {
                cookiesMap[".youtube.com|PREF"] = CookieRecord(
                    domain = ".youtube.com",
                    includeSubdomains = "TRUE",
                    path = "/",
                    secure = "TRUE",
                    expiry = expirationEpoch,
                    name = "PREF",
                    value = "hl=es&gl=US&f6=40000000"
                )
            }

            val outFile = getCookiesFile(context)
            // Si el usuario importó un archivo cookies.txt rico y el WebView aún no tiene sesión nueva, preservar las existentes
            if (cookiesMap.size <= 3 && outFile.exists() && outFile.length() > 350L) {
                return outFile
            }

            val sb = StringBuilder()
            sb.append("# Netscape HTTP Cookie File\n")
            sb.append("# Generado automaticamente por Aura Music WebViewCookieBridge para yt-dlp\n")
            sb.append("# This file is generated by Aura Music. Do not edit.\n\n")

            for (record in cookiesMap.values) {
                sb.append(record.domain).append('\t')
                    .append(record.includeSubdomains).append('\t')
                    .append(record.path).append('\t')
                    .append(record.secure).append('\t')
                    .append(record.expiry).append('\t')
                    .append(record.name).append('\t')
                    .append(record.value).append('\n')
            }

            outFile.writeText(sb.toString(), Charsets.UTF_8)
            outFile
        } catch (t: Throwable) {
            AuraDebugManager.logWarning(TAG, "Aviso sincronizando cookies del WebView: ${t.message}")
            null
        }
    }

    /**
     * Permite importar un archivo `cookies.txt` externo (formato Netscape) elegido por el usuario.
     */
    suspend fun importExternalCookiesTxt(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return@withContext Result.failure(IllegalArgumentException("No se pudo leer el archivo seleccionado."))

            val validLines = content.lines().filter { line ->
                val t = line.trim()
                t.isNotEmpty() && !t.startsWith("#") && t.split("\t").size >= 7
            }

            if (validLines.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("El archivo no tiene el formato Netscape cookies.txt válido (7 columnas separadas por tabulador).")
                )
            }

            val sb = StringBuilder()
            sb.append("# Netscape HTTP Cookie File\n\n")
            validLines.forEach { sb.append(it.trim()).append("\n") }

            val outFile = getCookiesFile(context)
            outFile.writeText(sb.toString(), Charsets.UTF_8)

            // También inyectar las cookies importadas al CookieManager del WebView
            try {
                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                for (line in validLines) {
                    val cols = line.trim().split("\t")
                    if (cols.size >= 7) {
                        val domain = cols[0].removePrefix("#HttpOnly_")
                        val name = cols[5]
                        val value = cols[6]
                        if (domain.contains("youtube.com")) {
                            cm.setCookie("https://www.youtube.com", "$name=$value; Domain=.youtube.com; Path=/; Secure")
                            cm.setCookie("https://music.youtube.com", "$name=$value; Domain=.youtube.com; Path=/; Secure")
                        }
                    }
                }
                cm.flush()
            } catch (_: Throwable) {}

            Result.success(validLines.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Limpia las cookies almacenadas y regenera la base limpia de consentimiento.
     */
    fun clearCookies(context: Context) {
        try {
            val file = getCookiesFile(context)
            if (file.exists()) file.delete()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (_: Throwable) {}
    }

    /**
     * Abre silenciosamente un WebView efímero en segundo plano hacia `m.youtube.com` y `music.youtube.com`
     * para obtener cookies frescas de visitante real (`VISITOR_INFO1_LIVE`, `YSC`, `PREF`) y el `visitorData`
     * de YouTube, y las guarda en `cookies.txt` para que `yt-dlp` pueda usarlas con `--cookies`.
     */
    suspend fun warmUpAndExportCookies(
        context: Context,
        targetVideoId: String? = null
    ): File? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { continuation ->
                var webView: WebView? = null

                fun cleanupAndFinish() {
                    try {
                        webView?.stopLoading()
                        webView?.destroy()
                        webView = null
                    } catch (_: Throwable) {}
                    val exported = syncWebViewCookiesToNetscapeFile(context)
                    if (continuation.isActive) {
                        continuation.resume(exported)
                    }
                }

                continuation.invokeOnCancellation {
                    try {
                        webView?.stopLoading()
                        webView?.destroy()
                        webView = null
                    } catch (_: Throwable) {}
                }

                try {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setCookie("https://www.youtube.com", "CONSENT=YES+cb.20240720-07-p0.es+FX+999; Domain=.youtube.com; Path=/; Secure")
                    cookieManager.setCookie("https://www.youtube.com", "SOCS=CAI; Domain=.youtube.com; Path=/; Secure")

                    val view = WebView(context.applicationContext)
                    webView = view
                    cookieManager.setAcceptThirdPartyCookies(view, true)

                    view.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = false
                        allowFileAccess = false
                        allowContentAccess = false
                        loadsImagesAutomatically = false
                        blockNetworkImage = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = DEFAULT_MOBILE_USER_AGENT
                    }
                    saveCapturedUserAgent(context, view.settings.userAgentString)

                    var finished = false
                    view.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val host = request?.url?.host?.lowercase() ?: return true
                            return !(host.endsWith("youtube.com") || host.endsWith("google.com"))
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (finished) return
                            finished = true

                            // Extraer visitorData de ytcfg si está disponible en el DOM móvil
                            val jsVisitor = """
                                (function() {
                                    try {
                                        if (window.ytcfg && window.ytcfg.get) {
                                            return window.ytcfg.get('VISITOR_DATA') || '';
                                        }
                                    } catch(e) {}
                                    return '';
                                })();
                            """.trimIndent()

                            view?.evaluateJavascript(jsVisitor) { vd ->
                                val cleanVd = vd?.trim()?.removeSurrounding("\"") ?: ""
                                if (cleanVd.isNotBlank() && cleanVd != "null") {
                                    saveCapturedVisitorData(context, cleanVd)
                                }
                                Handler(Looper.getMainLooper()).postDelayed({
                                    cleanupAndFinish()
                                }, 350L)
                            }
                        }
                    }

                    val targetUrl = if (!targetVideoId.isNullOrBlank()) {
                        "https://m.youtube.com/watch?v=${targetVideoId.trim()}"
                    } else {
                        "https://music.youtube.com/"
                    }
                    view.loadUrl(targetUrl)
                } catch (_: Throwable) {
                    cleanupAndFinish()
                }
            }
        } ?: syncWebViewCookiesToNetscapeFile(context)
    }

    private data class CookieRecord(
        val domain: String,
        val includeSubdomains: String,
        val path: String,
        val secure: String,
        val expiry: Long,
        val name: String,
        val value: String
    )
}
