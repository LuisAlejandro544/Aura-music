package com.example.core.data.importer

import android.content.Context
import com.example.debug.AuraDebugManager
import com.example.core.data.importer.FFmpegNativeEngine.ExecutionResult
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.regex.Pattern
import java.util.zip.ZipFile

/**
 * Ejecutor interno de comandos FFmpeg y extracción ZIP.
 * Rol arquitectónico: capa de E/S nativa usada por FFmpegAudioOperations y FFmpegCanvasOperations.
 */
internal object FFmpegCommandExecutor {

    private const val TAG = "FFmpegNativeEngine"

    private val TIME_PATTERN = Pattern.compile("time=(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})")

    internal fun executeCommand(
        context: Context,
        args: Array<String>,
        totalDurationMs: Long,
        onProgress: ((Float) -> Unit)?
    ): ExecutionResult {
        val logBuilder = StringBuilder()
        val outputFile = args.lastOrNull()?.let { File(it) }

        // Defensa contra Flag/Option Injection: El último argumento posicional (archivo de salida) no debe comenzar con '-'
        val lastArg = args.lastOrNull()
        if (lastArg != null && lastArg.startsWith("-")) {
            throw SecurityException("Inyección de banderas rechazada: El argumento de salida '$lastArg' no puede comenzar con '-'")
        }

        return try {
            AuraDebugManager.logInfo(TAG, "Iniciando proceso FFmpeg puro: ${args.joinToString(" ")}")
            val nativeDir = context.applicationInfo.nativeLibraryDir
            val envLibDir = File(context.filesDir, "env/ffmpeg/usr/lib")
            val processBuilder = ProcessBuilder(*args)
                .directory(File(nativeDir))
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
                        val matcher = TIME_PATTERN.matcher(currentLine)
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
            val success = exitCode == 0 && outputFile?.exists() == true && (outputFile.length() > 0L)

            if (success) {
                AuraDebugManager.logInfo(TAG, "Proceso FFmpeg finalizado exitosamente (Exit code: 0).")
                onProgress?.invoke(1.0f)
            } else {
                AuraDebugManager.logWarning(TAG, "Proceso FFmpeg terminó con código $exitCode. Log: ${logBuilder.takeLast(300)}")
            }

            ExecutionResult(
                success = success,
                exitCode = exitCode,
                outputLog = logBuilder.toString(),
                outputFile = if (success) outputFile else null
            )
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Excepción durante la ejecución de FFmpeg: ${e.message}")
            ExecutionResult(
                success = false,
                exitCode = -1,
                outputLog = "Excepción: ${e.message}\n${logBuilder}",
                outputFile = null
            )
        }
    }
    internal fun extractZip(zipFile: File, destDir: File) {
        val canonicalDestDir = destDir.canonicalFile
        val zip = ZipFile(zipFile)
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val entryFile = File(destDir, entry.name)
            val canonicalEntryFile = entryFile.canonicalFile
            if (!canonicalEntryFile.toPath().startsWith(canonicalDestDir.toPath())) {
                throw SecurityException("Violación de seguridad Zip Slip: '${entry.name}' intenta escapar de '${destDir.path}'")
            }

            if (entry.isDirectory) {
                entryFile.mkdirs()
            } else {
                entryFile.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(entryFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (entryFile.name.endsWith(".so") || entryFile.name.contains("bin")) {
                    entryFile.setExecutable(true, false)
                }
            }
        }
        zip.close()
    }
}
