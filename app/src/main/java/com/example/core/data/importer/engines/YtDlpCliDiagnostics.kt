package com.example.core.data.importer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Diagnóstico de ejecución CLI (yt-dlp --version en Python 3.11 nativo).
 * Rol arquitectónico: confirma arquitectura, C++ y QuickJS sin bloqueos.
 */
    internal suspend fun runYtDlpCliDiagnostics(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val ytdlpFile = resolveYtDlpFile(context)
            val pythonBin = YtDlpNativeEngine.getPythonExecutable(context)
            if (!ytdlpFile.exists() || pythonBin == null) {
                return@withContext Pair(false, "Binario de yt-dlp o entorno Python nativo no encontrado.")
            }
            val nativeDir = context.applicationInfo.nativeLibraryDir
            val pythonEnvDir = File(context.filesDir, "env/python")

            val cmd = listOf(pythonBin.absolutePath, "-I", ytdlpFile.absolutePath, "--version")
            val procResult = runYtDlpProcess(
                cmd = cmd,
                workDir = File(nativeDir),
                env = ytdlpProcessEnv(context, pythonEnvDir, File(nativeDir)),
                timeoutSeconds = 6,
                tag = YTDLP_UPDATER_TAG
            )
            if (procResult.timedOut) {
                return@withContext Pair(false, "Tiempo de espera agotado (6s) ejecutando yt-dlp --version.")
            }
            val output = procResult.stdout.trim()
            val exitCode = procResult.exitCode
            if (exitCode == 0 && output.isNotBlank()) {
                Pair(true, "yt-dlp $output ejecutado exitosamente en Python 3.11 nativo.")
            } else {
                Pair(false, "Código de salida $exitCode: $output")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Pair(false, "Error ejecutando diagnóstico: ${e.message}")
        }
    }
