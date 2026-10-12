package com.example.core.data.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.debug.AuraDebugManager
import com.example.core.model.AppUpdateReleaseInfo
import com.example.core.model.AppUpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Descargador e instalador de APK con verificación SHA-256.
 * Rol arquitectónico: descarga de hosts permitidos, instala con FileProvider;
 * la fachada AppReleaseUpdater delega aquí pasando su setter de estado.
 */
internal object ApkDownloadManager {

    private val ALLOWED_DOWNLOAD_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com"
    )

    internal data class MatchedApkAsset(
        val assetName: String,
        val downloadUrl: String,
        val sizeBytes: Long,
        val matchedAbiLabel: String,
        val sha256SumsUrl: String?
    )

    /**
     * Selecciona entre los 3 APKs del Pre-Release (`arm64-v8a`, `armeabi-v7a`, `universal`)
     * el que corresponde exactamente al procesador del teléfono, usando `universal` como respaldo.
     */
    internal fun selectBestApkAsset(assets: JSONArray, deviceAbi: String): MatchedApkAsset? {
        var exactAbiObj: JSONObject? = null
        var universalObj: JSONObject? = null
        var fallbackAnyApkObj: JSONObject? = null
        var sha256Url: String? = null

        for (j in 0 until assets.length()) {
            val asset = assets.optJSONObject(j) ?: continue
            val name = asset.optString("name", "")
            val url = asset.optString("browser_download_url", "")
            if (name.equals("SHA256SUMS.txt", ignoreCase = true) || name.endsWith("SHA256SUMS", ignoreCase = true)) {
                sha256Url = url
                continue
            }
            if (!name.endsWith(".apk", ignoreCase = true) || url.isBlank()) continue

            when {
                name.contains(deviceAbi, ignoreCase = true) -> exactAbiObj = asset
                name.contains("universal", ignoreCase = true) -> universalObj = asset
                fallbackAnyApkObj == null -> fallbackAnyApkObj = asset
            }
        }

        val chosen = exactAbiObj ?: universalObj ?: fallbackAnyApkObj ?: return null
        val chosenName = chosen.optString("name", "AuraBeta-update.apk")
        val chosenUrl = chosen.optString("browser_download_url", "")
        val chosenSize = chosen.optLong("size", 0L)
        val matchedLabel = when {
            exactAbiObj != null -> deviceAbi
            universalObj != null -> "universal"
            else -> "estándar"
        }

        return MatchedApkAsset(
            assetName = chosenName,
            downloadUrl = chosenUrl,
            sizeBytes = chosenSize,
            matchedAbiLabel = matchedLabel,
            sha256SumsUrl = sha256Url
        )
    }

    suspend fun downloadAndInstallUpdate(
        context: Context,
        releaseInfo: AppUpdateReleaseInfo,
        setState: (AppUpdateState) -> Unit = {}
    ): AppUpdateState = withContext(Dispatchers.IO) {
        try {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val updatesDir = File(baseDir, "updates").apply { if (!exists()) mkdirs() }

            // Limpiar APKs antiguos en updates/ para no desperdiciar almacenamiento
            updatesDir.listFiles()?.forEach { old ->
                if (old.name.endsWith(".apk") || old.name.endsWith(".tmp")) {
                    old.delete()
                }
            }

            val safeFileName = releaseInfo.apkAssetName
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .ifBlank { "AuraBeta-${releaseInfo.tagName}.apk" }

            val tempApkFile = File(updatesDir, "$safeFileName.tmp")
            val finalApkFile = File(updatesDir, safeFileName)

            setState(AppUpdateState.Downloading(
                releaseInfo = releaseInfo,
                progressPercent = 0,
                downloadedBytes = 0L,
                totalBytes = releaseInfo.apkSizeBytes,
                statusMessage = "Conectando con GitHub Releases (${releaseInfo.matchedAbi})..."
            ))

            // 1. Si existe SHA256SUMS.txt, obtener el hash esperado para este archivo APK
            val expectedSha256 = fetchExpectedSha256(releaseInfo.sha256SumsUrl, releaseInfo.apkAssetName)

            // 2. Descargar el archivo APK siguiendo redirecciones seguras de GitHub
            val connection = openValidatedDownloadConnection(releaseInfo.apkDownloadUrl)
            val totalLength = connection.contentLengthLong.takeIf { it > 0L } ?: releaseInfo.apkSizeBytes
            var downloaded = 0L

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(tempApkFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    var lastReportedPercent = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        val percent = if (totalLength > 0L) {
                            ((downloaded * 100L) / totalLength).toInt().coerceIn(0, 99)
                        } else {
                            50
                        }
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            setState(AppUpdateState.Downloading(
                                releaseInfo = releaseInfo,
                                progressPercent = percent,
                                downloadedBytes = downloaded,
                                totalBytes = totalLength,
                                statusMessage = "Descargando ${releaseInfo.apkAssetName} ($percent%)..."
                            ))
                        }
                    }
                    output.flush()
                }
            }
            connection.disconnect()

            if (!tempApkFile.exists() || tempApkFile.length() < 100_000L) {
                tempApkFile.delete()
                val err = AppUpdateState.Error("El archivo APK descargado está incompleto o corrupto.")
                setState(err)
                return@withContext err
            }

            // 3. Verificación criptográfica SHA-256
            var shaVerified = false
            if (!expectedSha256.isNullOrBlank()) {
                setState(AppUpdateState.Downloading(
                    releaseInfo = releaseInfo,
                    progressPercent = 100,
                    downloadedBytes = downloaded,
                    totalBytes = totalLength,
                    statusMessage = "Verificando firma de integridad SHA-256..."
                ))
                val actualSha256 = computeFileSha256(tempApkFile)
                if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    tempApkFile.delete()
                    val err = AppUpdateState.Error("Alerta de seguridad: La suma SHA-256 del APK no coincide con SHA256SUMS.txt.")
                    setState(err)
                    return@withContext err
                }
                shaVerified = true
            }

            if (finalApkFile.exists()) finalApkFile.delete()
            if (!tempApkFile.renameTo(finalApkFile)) {
                tempApkFile.copyTo(finalApkFile, overwrite = true)
                tempApkFile.delete()
            }

            val readyState = AppUpdateState.ReadyToInstall(
                releaseInfo = releaseInfo,
                apkFilePath = finalApkFile.absolutePath,
                sha256Verified = shaVerified
            )
            setState(readyState)

            // 4. Disparar el instalador nativo del sistema Android en el hilo principal
            withContext(Dispatchers.Main) {
                promptAndroidPackageInstaller(context, finalApkFile)
            }

            return@withContext readyState
        } catch (e: Exception) {
            AuraDebugManager.logWarning("AppUpdater", "Error descargando actualización de APK: ${e.message}")
            val err = AppUpdateState.Error("Error al descargar el APK: ${e.localizedMessage ?: "Fallo de conexión"}")
            setState(err)
            return@withContext err
        }
    }

    fun promptAndroidPackageInstaller(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = context.packageManager.canRequestPackageInstalls()
            if (!canInstall) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        val authority = "${context.packageName}.fileprovider"
        val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    internal fun openValidatedDownloadConnection(urlStr: String): HttpURLConnection {
        var currentUrl = URL(urlStr)
        var redirects = 0
        while (redirects < 6) {
            val host = currentUrl.host.lowercase(Locale.US)
            if (currentUrl.protocol != "https" || ALLOWED_DOWNLOAD_HOSTS.none { host == it || host.endsWith(".$it") }) {
                throw SecurityException("Dominio de descarga no autorizado: $host")
            }
            val conn = (currentUrl.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "AuraBeta-Android-Updater/${BuildConfig.VERSION_NAME}")
            }
            val status = conn.responseCode
            if (status in 300..399) {
                val location = conn.getHeaderField("Location") ?: throw IllegalStateException("Redirección sin cabecera Location")
                conn.disconnect()
                currentUrl = URL(currentUrl, location)
                redirects++
            } else if (status in 200..299) {
                return conn
            } else {
                conn.disconnect()
                throw IllegalStateException("Servidor de descarga devolvió HTTP $status")
            }
        }
        throw IllegalStateException("Demasiadas redirecciones al descargar el APK")
    }

    internal fun fetchExpectedSha256(sha256SumsUrl: String?, targetApkName: String): String? {
        if (sha256SumsUrl.isNullOrBlank()) return null
        return try {
            val conn = openValidatedDownloadConnection(sha256SumsUrl)
            val content = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            content.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.contains(targetApkName, ignoreCase = true) }
                .map { it.split(Regex("\\s+")).firstOrNull().orEmpty() }
                .firstOrNull { it.length == 64 }
        } catch (_: Exception) {
            null
        }
    }

    internal fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(32 * 1024)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun getDevicePrimaryMobileAbi(): String {
        val supported = Build.SUPPORTED_ABIS?.toList().orEmpty()
        return when {
            supported.contains("arm64-v8a") -> "arm64-v8a"
            supported.contains("armeabi-v7a") -> "armeabi-v7a"
            else -> "universal"
        }
    }

}
