package com.example.core.data.importer

import android.content.Context
import android.net.Uri
import com.example.core.model.PackageUpdateState
import com.example.core.playback.AuraDownloadService
import com.example.debug.AuraDebugManager
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Descarga con progreso en vivo y verificación criptográfica SHA-256.
 * Rol arquitectónico: blindaje OTA (host válido + hash oficial + staged).
 */
    internal fun fetchYtDlpExpectedSha256(checksumsUrl: String, assetName: String): String? {
        return try {
            if (!isTrustedYtDlpHost(checksumsUrl)) return null
            val req = Request.Builder().url(checksumsUrl).header("User-Agent", "AuraMusic-Android-Client").build()
            YtDlpUpdaterState.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val content = resp.body?.string() ?: return null

                content.lineSequence().firstOrNull { line ->
                    line.trim().endsWith(" $assetName") || line.trim().endsWith("*$assetName")
                }?.trim()?.split("\\s+".toRegex())?.firstOrNull()?.lowercase()
            }
        } catch (_: Throwable) {
            null
        }
    }

    internal fun isTrustedYtDlpHost(url: String): Boolean {
        val host = Uri.parse(url).host?.lowercase() ?: return false
        return host == "github.com" || host.endsWith(".github.com") ||
                host == "objects.githubusercontent.com" || host.endsWith(".githubusercontent.com")
    }

    internal fun computeYtDlpSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buf = ByteArray(8192)
            var n: Int
            while (stream.read(buf).also { n = it } != -1) digest.update(buf, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    internal fun downloadYtDlpWithVerification(
        url: String,
        expectedSha256: String?,
        versionTag: String,
        context: Context,
        showNotifications: Boolean
    ): Boolean {
        if (!isTrustedYtDlpHost(url)) {
            AuraDebugManager.logWarning(YTDLP_UPDATER_TAG, "Rechazada descarga desde dominio no confiable: $url")
            return false
        }

        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()

        val tempFile = File(binDir, "yt-dlp.tmp")
        val stagedFile = ytdlpStagedFile(context)
        val stagedVersionFile = ytdlpStagedVersionFile(context)

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AuraMusic-Android-Client")
                .get()
                .build()

            YtDlpUpdaterState.downloadHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false

                val body = response.body ?: return false
                val totalBytes = body.contentLength().takeIf { it > 0L } ?: 3_800_000L
                var downloadedBytes = 0L
                var lastReportedPct = -1

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            val pct = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 95)
                            if (pct >= lastReportedPct + 2 || lastReportedPct == -1) {
                                lastReportedPct = pct
                                YtDlpUpdaterState.packageState.value = PackageUpdateState.Downloading(
                                    progressPercent = pct,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    version = versionTag,
                                    message = "Descargando actualización de paquetes ($pct%)..."
                                )
                                if (showNotifications) {
                                    AuraDownloadService.notifyPackageDownloadProgress(context, pct, versionTag)
                                }
                            }
                        }
                    }
                }

                if (!tempFile.exists() || tempFile.length() < 100_000L) {
                    tempFile.delete()
                    if (showNotifications) AuraDownloadService.dismissPackageNotification(context)
                    return false
                }

                // Reportar progreso de verificación de hash SHA-256
                YtDlpUpdaterState.packageState.value = PackageUpdateState.Downloading(
                    progressPercent = 98,
                    downloadedBytes = totalBytes,
                    totalBytes = totalBytes,
                    version = versionTag,
                    message = "Verificando integridad SHA-256 (98%)..."
                )
                if (showNotifications) {
                    AuraDownloadService.notifyPackageDownloadProgress(context, 98, versionTag)
                }

                if (expectedSha256.isNullOrBlank()) {
                    AuraDebugManager.logError(
                        YTDLP_UPDATER_TAG,
                        "Rechazando actualización OTA: No se pudo obtener la suma criptográfica SHA-256 oficial para yt-dlp ($versionTag). Abortando por seguridad."
                    )
                    tempFile.delete()
                    if (showNotifications) AuraDownloadService.dismissPackageNotification(context)
                    return false
                }

                val computedHash = computeYtDlpSha256(tempFile)
                if (!computedHash.equals(expectedSha256, ignoreCase = true)) {
                    AuraDebugManager.logError(
                        YTDLP_UPDATER_TAG,
                        "Fallo de integridad SHA-256 en yt-dlp. Esperado: $expectedSha256, Calculado: $computedHash"
                    )
                    tempFile.delete()
                    if (showNotifications) AuraDownloadService.dismissPackageNotification(context)
                    return false
                }

                if (stagedFile.exists()) stagedFile.delete()
                val renamed = tempFile.renameTo(stagedFile)
                if (!renamed) {
                    tempFile.copyTo(stagedFile, overwrite = true)
                    tempFile.delete()
                }
                stagedFile.setExecutable(true, false)
                stagedVersionFile.writeText(versionTag)

                // Emitir 100% completado con éxito
                YtDlpUpdaterState.packageState.value = PackageUpdateState.Downloading(
                    progressPercent = 100,
                    downloadedBytes = totalBytes,
                    totalBytes = totalBytes,
                    version = versionTag,
                    message = "¡Paquete verificado exitosamente! (100%)"
                )
                if (showNotifications) {
                    AuraDownloadService.notifyPackageDownloadProgress(context, 100, versionTag)
                }
                return true
            }
        } catch (t: Throwable) {
            if (tempFile.exists()) tempFile.delete()
            AuraDebugManager.logWarning(YTDLP_UPDATER_TAG, "Error en downloadFileWithProgressAndVerification: ${t.message}")
            if (showNotifications) {
                AuraDownloadService.dismissPackageNotification(context)
            }
            return false
        }
    }

    internal fun fetchYtDlpLatestTagFallback(): String? {
        return try {
            val req = Request.Builder()
                .url("https://raw.githubusercontent.com/yt-dlp/yt-dlp/master/yt_dlp/version.py")
                .header("User-Agent", "AuraMusic-Android-Client")
                .build()
            YtDlpUpdaterState.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val match = """__version__\s*=\s*'([^']+)'""".toRegex().find(body)
                match?.groupValues?.get(1)?.trim()
            }
        } catch (_: Exception) {
            null
        }
    }
