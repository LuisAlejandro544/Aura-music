package com.example.core.data.importer

import android.content.Context
import android.net.Uri
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gestor de Almacenamiento, Validación y Preferencias de Cookies (Estilo YTDLnis)
 * y Configuración de Extracción para yt-dlp.
 *
 * Arquitectura:
 * - Soporte Opcional para Cookies: Los usuarios pueden importar un archivo `cookies.txt` (formato Netscape)
 *   o pegar el texto directamente para sortear el bloqueo de "Inicia sesión para confirmar que no eres un bot".
 * - yt-dlp lee de manera nativa el archivo de cookies mediante el flag `--cookies <ruta>`.
 * - Si no está activado o no hay cookies, yt-dlp opera de forma anónima con su estrategia natural.
 */
object YtDlpCookieManager {

    private const val TAG = "YtDlpCookieManager"
    private const val PREFS_NAME = "aura_ytdlp_settings_prefs"

    private const val KEY_COOKIES_ENABLED = "ytdlp_cookies_enabled"
    private const val KEY_PLAYER_CLIENT_STRATEGY = "ytdlp_player_client_strategy"
    private const val KEY_PO_TOKEN_ENABLED = "ytdlp_po_token_enabled"
    private const val KEY_CUSTOM_PO_TOKEN = "ytdlp_custom_po_token"
    private const val KEY_VISITOR_DATA = "ytdlp_visitor_data"
    private const val KEY_PO_TOKEN_GENERATED_AT = "ytdlp_po_token_generated_at"
    private const val KEY_AUTO_CHECK_OTA_STARTUP = "ytdlp_auto_check_ota_startup"

    const val STRATEGY_DEFAULT = "default"
    const val STRATEGY_MWEB_AND_WEB = "mweb,web"

    data class CookieSummary(
        val isEnabled: Boolean,
        val fileExists: Boolean,
        val sizeBytes: Long,
        val lineCount: Int,
        val validCookieCount: Int,
        val lastModifiedFormatted: String
    )

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Carpeta privada donde se resguarda el archivo cookies.txt
     */
    fun getCookiesDirectory(context: Context): File {
        val dir = File(context.filesDir, "ytdlp_cookies")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Archivo físico de cookies (cookies.txt en formato Netscape)
     */
    fun getCookiesFile(context: Context): File {
        return File(getCookiesDirectory(context), "cookies.txt")
    }

    /**
     * Retorna el archivo de cookies si y solo si el usuario ha habilitado la opción
     * y el archivo existe y contiene datos válidos.
     */
    fun getActiveCookiesFileIfEnabled(context: Context): File? {
        if (!isCookiesEnabled(context)) return null
        val file = getCookiesFile(context)
        return if (file.exists() && file.length() > 10L) file else null
    }

    /**
     * Indica si el uso opcional de cookies está activado por el usuario.
     */
    fun isCookiesEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_COOKIES_ENABLED, false)
    }

    fun setCookiesEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_COOKIES_ENABLED, enabled).apply()
        AuraDebugManager.logInfo(TAG, "Soporte de cookies yt-dlp: ${if (enabled) "ACTIVADO" else "DESACTIVADO"}")
    }

    /**
     * Estrategia de cliente de YouTube en yt-dlp (`player_client`).
     * Por defecto: "default" (Estrategia natural de yt-dlp sin forzar clientes deprecados).
     */
    fun getPlayerClientStrategy(context: Context): String {
        return getPrefs(context).getString(KEY_PLAYER_CLIENT_STRATEGY, STRATEGY_DEFAULT) ?: STRATEGY_DEFAULT
    }

    fun setPlayerClientStrategy(context: Context, strategy: String) {
        getPrefs(context).edit().putString(KEY_PLAYER_CLIENT_STRATEGY, strategy).apply()
        AuraDebugManager.logInfo(TAG, "Estrategia player_client de yt-dlp actualizada: $strategy")
    }

    /**
     * Indica si la verificación OTA automática al iniciar está activa.
     */
    fun isAutoCheckOtaStartupEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_CHECK_OTA_STARTUP, true)
    }

    fun setAutoCheckOtaStartupEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_CHECK_OTA_STARTUP, enabled).apply()
    }

    /**
     * Obtiene resumen analítico del archivo de cookies actual (E/S en hilo IO).
     */
    suspend fun getCookieSummary(context: Context): CookieSummary = withContext(Dispatchers.IO) {
        val enabled = isCookiesEnabled(context)
        val file = getCookiesFile(context)
        if (!file.exists() || file.length() == 0L) {
            return@withContext CookieSummary(
                isEnabled = enabled,
                fileExists = false,
                sizeBytes = 0L,
                lineCount = 0,
                validCookieCount = 0,
                lastModifiedFormatted = "No configuradas"
            )
        }

        var lines = 0
        var validCount = 0
        try {
            file.forEachLine { rawLine ->
                val line = rawLine.trim()
                if (line.isNotEmpty()) {
                    lines++
                    if (!line.startsWith("#")) {
                        val parts = line.split("\t")
                        if (parts.size >= 7 || (parts.size >= 5 && line.contains("youtube"))) {
                            validCount++
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = df.format(Date(file.lastModified()))

        return@withContext CookieSummary(
            isEnabled = enabled,
            fileExists = true,
            sizeBytes = file.length(),
            lineCount = lines,
            validCookieCount = validCount,
            lastModifiedFormatted = dateStr
        )
    }

    /**
     * Guarda contenido de cookies importado desde texto directo (portapapeles).
     * Soporta formato Netscape estándar o conversión inteligente desde header HTTP `Cookie: foo=bar; ...`.
     */
    suspend fun saveCookiesFromText(context: Context, rawText: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val trimmed = rawText.trim()
            if (trimmed.length < 5) {
                return@withContext Result.failure(IllegalArgumentException("El texto de cookies está vacío o es muy corto."))
            }

            val targetFile = getCookiesFile(context)
            val netscapeLines = mutableListOf<String>()

            // Si el texto ya está en formato Netscape (cabecera oficial o líneas con tabs),
            // se guarda tal cual. La mención suelta a ".youtube.com" NO basta: podría ser prosa
            // pegada por error, y en ese caso se intenta la conversión desde header HTTP.
            if (trimmed.contains("\t") || trimmed.contains("# Netscape HTTP Cookie File")) {
                trimmed.lineSequence().forEach { line ->
                    val clean = line.replace("\r", "")
                    if (clean.isNotBlank()) netscapeLines.add(clean)
                }
            } else if (trimmed.contains("=") && (trimmed.contains(";") || trimmed.contains("SID=") || trimmed.contains("SAPISID=") || trimmed.contains(".youtube.com"))) {
                // Conversión inteligente de Header HTTP ("Cookie: SID=xxx; HSID=yyy") a formato Netscape
                netscapeLines.add("# Netscape HTTP Cookie File")
                netscapeLines.add("# Exported and formatted by Aura Music (YTDLnis Style)")
                val pairs = trimmed.removePrefix("Cookie:").split(";")
                val nowSecs = (System.currentTimeMillis() / 1000L) + (365L * 24L * 3600L) // 1 año
                pairs.forEach { pair ->
                    val eqIdx = pair.indexOf('=')
                    if (eqIdx > 0) {
                        val name = pair.substring(0, eqIdx).trim()
                        val value = pair.substring(eqIdx + 1).trim()
                        if (name.isNotEmpty() && value.isNotEmpty()) {
                            netscapeLines.add(".youtube.com\tTRUE\t/\tTRUE\t$nowSecs\t$name\t$value")
                        }
                    }
                }
            } else {
                // Guardar tal cual con cabecera Netscape si no la tiene
                netscapeLines.add("# Netscape HTTP Cookie File")
                trimmed.lineSequence().forEach { line ->
                    val clean = line.replace("\r", "")
                    if (clean.isNotBlank()) netscapeLines.add(clean)
                }
            }

            if (netscapeLines.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No se reconocieron cookies válidas en el texto."))
            }

            targetFile.parentFile?.mkdirs()
            targetFile.writeText(netscapeLines.joinToString("\n") + "\n", Charsets.UTF_8)
            setCookiesEnabled(context, true)

            val count = netscapeLines.count { !it.startsWith("#") && it.isNotBlank() }
            AuraDebugManager.logInfo(TAG, "Guardadas exitosamente $count cookies en ${targetFile.absolutePath}")
            Result.success(count)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AuraDebugManager.logError(TAG, "Error guardando cookies de texto: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Guarda cookies importadas desde un archivo del sistema de archivos mediante SAF (Storage Access Framework).
     */
    suspend fun saveCookiesFromUri(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("No se pudo abrir el archivo seleccionado."))

            val text = inputStream.use { it.bufferedReader(Charsets.UTF_8).readText() }
            saveCookiesFromText(context, text)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AuraDebugManager.logError(TAG, "Error leyendo archivo de cookies SAF: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Elimina el archivo de cookies guardado y desactiva la opción.
     */
    fun clearCookies(context: Context): Boolean {
        val file = getCookiesFile(context)
        val deleted = if (file.exists()) file.delete() else true
        setCookiesEnabled(context, false)
        AuraDebugManager.logInfo(TAG, "Cookies de yt-dlp eliminadas.")
        return deleted
    }

    // ==========================================
    // GESTIÓN DE PO TOKEN (PROOF OF ORIGIN TOKEN)
    // ==========================================

    fun isPoTokenEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PO_TOKEN_ENABLED, true)
    }

    fun setPoTokenEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PO_TOKEN_ENABLED, enabled).apply()
        AuraDebugManager.logInfo(TAG, "PO Token yt-dlp: ${if (enabled) "ACTIVADO" else "DESACTIVADO"}")
    }

    fun getSavedPoToken(context: Context): String? {
        return getPrefs(context).getString(KEY_CUSTOM_PO_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    fun getSavedVisitorData(context: Context): String? {
        return getPrefs(context).getString(KEY_VISITOR_DATA, null)?.takeIf { it.isNotBlank() }
    }

    fun savePoTokenData(context: Context, token: String, visitorData: String?) {
        getPrefs(context).edit()
            .putString(KEY_CUSTOM_PO_TOKEN, token.trim())
            .putString(KEY_VISITOR_DATA, visitorData?.trim() ?: "")
            .putLong(KEY_PO_TOKEN_GENERATED_AT, System.currentTimeMillis())
            .apply()
        AuraDebugManager.logInfo(TAG, "PO Token actualizado en preferencias (longitud: ${token.length})")
    }

    fun getPoTokenLastGeneratedFormatted(context: Context): String {
        val millis = getPoTokenGeneratedAt(context)
        if (millis <= 0L) return "Nunca generado"
        val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return df.format(Date(millis))
    }

    fun getPoTokenGeneratedAt(context: Context): Long {
        return getPrefs(context).getLong(KEY_PO_TOKEN_GENERATED_AT, 0L)
    }
}
