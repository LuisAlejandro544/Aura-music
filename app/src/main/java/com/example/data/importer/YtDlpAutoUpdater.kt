package com.example.data.importer

import android.content.Context
import android.net.Uri
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
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
 *    criptográfica estricta de integridad SHA-256 mediante 'SHA2-256SUMS'.
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
     * Consulta GitHub y actualiza yt-dlp si hay una versión más reciente,
     * garantizando verificación de integridad SHA-256.
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

            // Buscar la URL de descarga directa del asset 'yt-dlp' y el archivo de checksums SHA2-256SUMS
            var downloadUrl: String? = null
            var checksumsUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name")
                    if (assetName == "yt-dlp") {
                        downloadUrl = asset.optString("browser_download_url")
                    } else if (assetName == "SHA2-256SUMS") {
                        checksumsUrl = asset.optString("browser_download_url")
                    }
                }
            }

            // Fallback al URL de descarga directa estándar si no vino en assets
            if (downloadUrl.isNullOrBlank()) {
                downloadUrl = "https://github.com/yt-dlp/yt-dlp/releases/download/$latestTagName/yt-dlp"
            }
            if (checksumsUrl.isNullOrBlank()) {
                checksumsUrl = "https://github.com/yt-dlp/yt-dlp/releases/download/$latestTagName/SHA2-256SUMS"
            }

            // Obtener el hash SHA-256 esperado desde SHA2-256SUMS
            val expectedSha256 = fetchExpectedSha256(checksumsUrl, "yt-dlp")
            AuraDebugManager.logInfo(TAG, "Descargando nuevo paquete yt-dlp ($latestTagName) desde $downloadUrl con hash esperado: ${expectedSha256 ?: "N/D"}...")

            val downloadSuccess = downloadFileWithVerification(downloadUrl, expectedSha256, context)
            if (downloadSuccess) {
                // Persistir nueva versión
                val versionFile = File(context.filesDir, "bin/ytdlp_version.txt")
                versionFile.writeText(latestTagName)
                AuraDebugManager.logInfo(TAG, "¡yt-dlp verificado criptográficamente y actualizado a $latestTagName sin necesidad de nuevo APK!")
                UpdateResult.Updated(previousVersion = currentVersion, newVersion = latestTagName)
            } else {
                UpdateResult.Error("Fallo de integridad o descarga del ejecutable yt-dlp.", currentVersion)
            }
        } catch (e: Exception) {
            val msg = "Excepción al actualizar yt-dlp: ${e.message}"
            AuraDebugManager.logWarning(TAG, msg)
            UpdateResult.Error(msg, currentVersion)
        }
    }

    /**
     * Consulta el archivo oficial SHA2-256SUMS de GitHub Releases y extrae el hash del asset.
     */
    private fun fetchExpectedSha256(checksumsUrl: String, assetName: String): String? {
        return try {
            if (!isValidDownloadHost(checksumsUrl)) return null
            val req = Request.Builder().url(checksumsUrl).header("User-Agent", "AuraMusic-Android-Client").build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return null
            val content = resp.body?.string() ?: return null

            // Líneas formato: "<hash>  yt-dlp" o "<hash> *yt-dlp"
            content.lineSequence().firstOrNull { line ->
                line.trim().endsWith(" $assetName") || line.trim().endsWith("*$assetName")
            }?.trim()?.split("\\s+".toRegex())?.firstOrNull()?.lowercase()
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Valida que el host de descarga pertenezca estrictamente a dominios oficiales de GitHub.
     */
    private fun isValidDownloadHost(url: String): Boolean {
        val host = Uri.parse(url).host?.lowercase() ?: return false
        return host == "github.com" ||
                host.endsWith(".github.com") ||
                host == "objects.githubusercontent.com" ||
                host.endsWith(".githubusercontent.com")
    }

    /**
     * Calcula el hash SHA-256 de un archivo en disco.
     */
    private fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Descarga atómica del archivo ejecutable con verificación de origen y hash SHA-256.
     */
    private fun downloadFileWithVerification(url: String, expectedSha256: String?, context: Context): Boolean {
        if (!isValidDownloadHost(url)) {
            AuraDebugManager.logWarning(TAG, "Rechazada descarga desde dominio no confiable: $url")
            return false
        }

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

            // Comprobación de tamaño mínimo válido
            if (!tempFile.exists() || tempFile.length() < 100_000L) {
                tempFile.delete()
                return false
            }

            // Comprobación criptográfica de integridad SHA-256 si está disponible el checksum oficial
            if (!expectedSha256.isNullOrBlank()) {
                val computedHash = computeFileSha256(tempFile)
                if (!computedHash.equals(expectedSha256, ignoreCase = true)) {
                    AuraDebugManager.logError(
                        TAG,
                        "Fallo de integridad SHA-256 en yt-dlp. Esperado: $expectedSha256, Calculado: $computedHash"
                    )
                    tempFile.delete()
                    return false
                }
            }

            if (targetFile.exists()) targetFile.delete()
            val renamed = tempFile.renameTo(targetFile)
            if (renamed) {
                targetFile.setExecutable(true, false)
                return true
            }
            return false
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            AuraDebugManager.logWarning(TAG, "Error en downloadFileWithVerification: ${e.message}")
            return false
        }
    }
}
