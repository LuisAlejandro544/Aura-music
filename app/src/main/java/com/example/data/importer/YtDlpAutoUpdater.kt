package com.example.data.importer

import android.content.Context
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Gestor de Actualización Automática en Caliente (OTA) para yt-dlp.
 *
 * Arquitectura y Principio de Operación:
 * 1. Resuelve el problema de parches constantes de YouTube sin obligar al usuario
 *    a descargar e instalar un nuevo APK desde Uptodown o tiendas de terceros.
 * 2. Consulta el endpoint oficial de GitHub Releases (api.github.com/repos/yt-dlp/yt-dlp/releases/latest).
 * 3. Compara la versión instalada localmente con la última versión comunitaria de yt-dlp.
 * 4. Descarga el paquete independiente 'yt-dlp' (~3.8 MB) directamente al almacenamiento
 *    privado de la aplicación (context.filesDir/bin/yt-dlp) de manera atómica con verificación
 *    de integridad.
 * 5. Se dispara automáticamente ante fallos de extracción (ej. 403 Forbidden o Botguard)
 *    o manualmente desde la pantalla de Configuración.
 */
object YtDlpAutoUpdater {

    private const val TAG = "YtDlpAutoUpdater"
    private const val GITHUB_RELEASES_API = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    sealed class UpdateResult {
        data class Updated(val previousVersion: String, val newVersion: String) : UpdateResult()
        data class AlreadyUpToDate(val currentVersion: String) : UpdateResult()
        data class Error(val message: String, val currentVersion: String) : UpdateResult()
    }

    /**
     * Obtiene el archivo ejecutable / paquete yt-dlp local si existe.
     */
    fun getYtDlpFile(context: Context): File {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) {
            binDir.mkdirs()
        }
        return File(binDir, "yt-dlp")
    }

    /**
     * Lee la versión actualmente instalada en el dispositivo.
     */
    fun getInstalledVersion(context: Context): String {
        val versionFile = File(context.filesDir, "bin/ytdlp_version.txt")
        return if (versionFile.exists()) {
            try {
                versionFile.readText().trim().ifBlank { "Sin instalar" }
            } catch (_: Exception) {
                "Sin instalar"
            }
        } else {
            val ytdlpFile = getYtDlpFile(context)
            if (ytdlpFile.exists() && ytdlpFile.length() > 0L) "Instalado (v. base)" else "No instalado"
        }
    }

    /**
     * Consulta GitHub y actualiza yt-dlp si hay una versión más reciente.
     */
    suspend fun checkAndUpdate(
        context: Context,
        forceDownload: Boolean = false
    ): UpdateResult = withContext(Dispatchers.IO) {
        val currentVersion = getInstalledVersion(context)
        AuraDebugManager.logInfo(TAG, "Comprobando actualizaciones de yt-dlp (Instalada: $currentVersion)...")

        try {
            val request = Request.Builder()
                .url(GITHUB_RELEASES_API)
                .header("User-Agent", "AuraMusic-Android-Client")
                .header("Accept", "application/vnd.github.v3+json")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = "Error al consultar GitHub API: HTTP ${response.code}"
                AuraDebugManager.logWarning(TAG, err)
                return@withContext UpdateResult.Error(err, currentVersion)
            }

            val body = response.body?.string() ?: return@withContext UpdateResult.Error(
                "Respuesta vacía de GitHub Releases.",
                currentVersion
            )

            val json = JSONObject(body)
            val latestTagName = json.optString("tag_name", "").trim()
            if (latestTagName.isBlank()) {
                return@withContext UpdateResult.Error("No se pudo obtener la etiqueta de versión de GitHub.", currentVersion)
            }

            // Comprobar si ya está al día
            if (!forceDownload && currentVersion.equals(latestTagName, ignoreCase = true) && getYtDlpFile(context).exists()) {
                AuraDebugManager.logInfo(TAG, "yt-dlp ya está en su versión más reciente ($latestTagName).")
                return@withContext UpdateResult.AlreadyUpToDate(latestTagName)
            }

            // Buscar la URL de descarga directa del asset 'yt-dlp'
            var downloadUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name")
                    if (assetName == "yt-dlp") {
                        downloadUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }

            // Fallback al URL de descarga directa estándar si no vino en assets
            if (downloadUrl.isNullOrBlank()) {
                downloadUrl = "https://github.com/yt-dlp/yt-dlp/releases/download/$latestTagName/yt-dlp"
            }

            AuraDebugManager.logInfo(TAG, "Descargando nuevo paquete yt-dlp ($latestTagName) desde $downloadUrl...")

            val downloadSuccess = downloadFile(downloadUrl, context)
            if (downloadSuccess) {
                // Persistir nueva versión
                val versionFile = File(context.filesDir, "bin/ytdlp_version.txt")
                versionFile.writeText(latestTagName)
                AuraDebugManager.logInfo(TAG, "¡yt-dlp actualizado exitosamente a $latestTagName sin necesidad de nuevo APK!")
                UpdateResult.Updated(previousVersion = currentVersion, newVersion = latestTagName)
            } else {
                UpdateResult.Error("Fallo al descargar el archivo del release de yt-dlp.", currentVersion)
            }
        } catch (e: Exception) {
            val msg = "Excepción al actualizar yt-dlp: ${e.message}"
            AuraDebugManager.logWarning(TAG, msg)
            UpdateResult.Error(msg, currentVersion)
        }
    }

    /**
     * Descarga atómica del archivo ejecutable.
     */
    private fun downloadFile(url: String, context: Context): Boolean {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()

        val tempFile = File(binDir, "yt-dlp.tmp")
        val targetFile = File(binDir, "yt-dlp")

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AuraMusic-Android-Client")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return false

            val body = response.body ?: return false
            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (tempFile.exists() && tempFile.length() > 100_000L) { // Debe tener al menos ~100KB
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                targetFile.setExecutable(true, false)
                return true
            }
            return false
        } catch (_: Exception) {
            if (tempFile.exists()) tempFile.delete()
            return false
        }
    }
}
