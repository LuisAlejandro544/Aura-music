package com.example.core.data.importer.mixtape

import android.content.Context
import com.example.core.data.importer.FFmpegNativeEngine
import com.example.debug.AuraDebugManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.regex.Pattern

/**
 * Ejecución FFmpeg con monitoreo de avance por duración.
 * Rol arquitectónico: pieza de MixtapeEngine (fachada pública intacta).
 */
internal object MixtapeFfmpegRunner {
    private const val TAG = "MixtapeEngine"

    fun run(
        context: Context,
        args: Array<String>,
        totalDurationMs: Long,
        onProgress: ((Float) -> Unit)?
    ): Boolean {
        val timePattern = Pattern.compile("time=(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})")
        val logBuilder = StringBuilder()

        return try {
            val nativeDir = context.applicationInfo.nativeLibraryDir
            val envLibDir = File(context.filesDir, "env/ffmpeg/usr/lib")
            val processBuilder = ProcessBuilder(*args)
                .directory(context.cacheDir)
                .redirectErrorStream(true)

            processBuilder.environment().apply {
                this["LD_LIBRARY_PATH"] = FFmpegNativeEngine.getLibraryPath(context)
                this["FFMPEG_LIB_DIR"] = if (envLibDir.exists()) envLibDir.absolutePath else nativeDir
                this["PATH"] = "$nativeDir:${context.filesDir.absolutePath}/bin:${System.getenv("PATH") ?: ""}"
                this["TMPDIR"] = context.cacheDir.absolutePath
            }

            val process = processBuilder.start()

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    logBuilder.append(currentLine).append("\n")

                    if (onProgress != null && totalDurationMs > 0L) {
                        val matcher = timePattern.matcher(currentLine)
                        if (matcher.find()) {
                            val hours = matcher.group(1)?.toLongOrNull() ?: 0L
                            val minutes = matcher.group(2)?.toLongOrNull() ?: 0L
                            val seconds = matcher.group(3)?.toLongOrNull() ?: 0L
                            val centis = matcher.group(4)?.toLongOrNull() ?: 0L
                            val currentMs = (hours * 3600 + minutes * 60 + seconds) * 1000L + centis * 10L
                            val progress = (currentMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                            onProgress(progress)
                        }
                    }
                }
            }

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                onProgress?.invoke(1.0f)
                true
            } else {
                AuraDebugManager.logWarning(TAG, "FFmpeg falló al generar Mixtape (código $exitCode). Log: ${logBuilder.takeLast(300)}")
                false
            }
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Error ejecutando proceso FFmpeg para Mixtape: ${e.message}")
            false
        }
    }
}
