package com.example.core.data.importer

import android.content.Context
import android.content.Intent
import com.example.debug.AuraDebugManager
import com.example.core.model.PackageUpdateState
import com.example.core.playback.AuraDownloadService
import java.io.File
import kotlin.system.exitProcess

/**
 * Rutas del paquete yt-dlp y aplicación de staged (.staged -> activo + reinicio).
 * Rol arquitectónico: almacenamiento privado files/bin sin duplicar el .so base.
 */
    fun resolveYtDlpFile(context: Context): File {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) {
            binDir.mkdirs()
        }
        val otaFile = File(binDir, "yt-dlp")
        if (otaFile.exists() && otaFile.length() > YTDLP_MIN_PACKAGE_BYTES) {
            return otaFile
        }
        val nativeYtdlpZipSo = File(context.applicationInfo.nativeLibraryDir, "libytdlp.zip.so")
        if (nativeYtdlpZipSo.exists() && nativeYtdlpZipSo.length() > YTDLP_MIN_PACKAGE_BYTES) {
            return nativeYtdlpZipSo
        }
        val nativeYtdlp = File(context.applicationInfo.nativeLibraryDir, "libytdlp.so")
        if (nativeYtdlp.exists() && nativeYtdlp.length() > YTDLP_MIN_PACKAGE_BYTES) {
            return nativeYtdlp
        }
        return otaFile
    }

    internal fun ytdlpOtaTargetFile(context: Context): File {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()
        return File(binDir, "yt-dlp")
    }

    internal fun ytdlpStagedFile(context: Context): File {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()
        return File(binDir, "yt-dlp.staged")
    }

    internal fun ytdlpStagedVersionFile(context: Context): File {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()
        return File(binDir, "ytdlp_version.staged")
    }

    fun applyStagedYtDlpUpdate(context: Context): Boolean {
        return try {
            val stagedFile = ytdlpStagedFile(context)
            val stagedVersionFile = ytdlpStagedVersionFile(context)
            if (stagedFile.exists() && stagedFile.length() > 100_000L) {
                val targetFile = ytdlpOtaTargetFile(context)
                val versionFile = File(context.filesDir, "bin/ytdlp_version.txt")
                val newVer = if (stagedVersionFile.exists()) {
                    stagedVersionFile.readText().trim().ifBlank { "Actualizado" }
                } else {
                    "Actualizado"
                }

                if (targetFile.exists()) targetFile.delete()
                val moved = stagedFile.renameTo(targetFile)
                if (!moved) {
                    stagedFile.copyTo(targetFile, overwrite = true)
                    stagedFile.delete()
                }
                targetFile.setExecutable(true, false)
                versionFile.writeText(newVer)
                if (stagedVersionFile.exists()) stagedVersionFile.delete()
                AuraDebugManager.logInfo(YTDLP_UPDATER_TAG, "Paquete yt-dlp ($newVer) aplicado exitosamente al iniciar la aplicación.")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            AuraDebugManager.logWarning(YTDLP_UPDATER_TAG, "No se pudo aplicar paquete .staged al inicio: ${e.message}")
            false
        }
    }

    internal fun applyPendingYtDlpUpdateAndRestart(context: Context) {
        val appContext = context.applicationContext
        applyStagedYtDlpUpdate(appContext)
        AuraDownloadService.dismissPackageNotification(appContext)
        YtDlpUpdaterState.packageState.value = PackageUpdateState.Idle

        try {
            val packageManager = appContext.packageManager
            val launchIntent = packageManager.getLaunchIntentForPackage(appContext.packageName)
            val componentName = launchIntent?.component
            if (componentName != null) {
                val restartIntent = Intent.makeRestartActivityTask(componentName)
                appContext.startActivity(restartIntent)
            }
        } catch (e: Exception) {
            AuraDebugManager.logWarning(YTDLP_UPDATER_TAG, "Error preparando intent de reinicio: ${e.message}")
        } finally {
            exitProcess(0)
        }
    }

    fun readInstalledYtDlpVersion(context: Context): String {
        val versionFile = File(context.filesDir, "bin/ytdlp_version.txt")
        return if (versionFile.exists()) {
            try {
                versionFile.readText().trim().ifBlank { "Sin instalar" }
            } catch (_: Exception) {
                "Sin instalar"
            }
        } else {
            val ytdlpFile = resolveYtDlpFile(context)
            if (ytdlpFile.exists() && ytdlpFile.length() > 0L) "Instalado (v. base)" else "No instalado"
        }
    }
