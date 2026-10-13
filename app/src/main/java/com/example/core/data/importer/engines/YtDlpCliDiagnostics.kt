package com.example.core.data.importer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

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
            val stdlibZip = File(pythonEnvDir, "stdlib.zip")
            val stdlibDir = File(pythonEnvDir, "stdlib")
            val modulesDir = File(pythonEnvDir, "modules")
            val directPythonZipSo = File(nativeDir, "libpython.zip.so")

            val cmd = listOf(pythonBin.absolutePath, "-I", ytdlpFile.absolutePath, "--version")
            val pb = ProcessBuilder(cmd)
                .directory(File(nativeDir))
                .redirectErrorStream(true)

            pb.environment().apply {
                this["PYTHONHOME"] = pythonEnvDir.absolutePath
                this["PYTHONPATH"] = "${directPythonZipSo.absolutePath}:${stdlibZip.absolutePath}:${stdlibDir.absolutePath}:${modulesDir.absolutePath}:${pythonEnvDir.absolutePath}"
                this["HOME"] = pythonEnvDir.absolutePath
                this["LD_LIBRARY_PATH"] = nativeDir
                this["PATH"] = "$nativeDir:${context.filesDir.absolutePath}/bin"
            }

            val proc = pb.start()
            val completed = proc.waitFor(6, TimeUnit.SECONDS)
            if (!completed) {
                proc.destroyForcibly()
                return@withContext Pair(false, "Tiempo de espera agotado (6s) ejecutando yt-dlp --version.")
            }
            val output = proc.inputStream.bufferedReader().readText().trim()
            val exitCode = proc.exitValue()
            if (exitCode == 0 && output.isNotBlank()) {
                Pair(true, "yt-dlp $output ejecutado exitosamente en Python 3.11 nativo.")
            } else {
                Pair(false, "Código de salida $exitCode: $output")
            }
        } catch (e: Exception) {
            Pair(false, "Error ejecutando diagnóstico: ${e.message}")
        }
    }
