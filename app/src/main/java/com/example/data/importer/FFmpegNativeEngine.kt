package com.example.data.importer

import android.content.Context
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.regex.Pattern

/**
 * Motor de procesamiento multimedia basado en FFmpeg puro sin wrappers de terceros.
 *
 * Arquitectura y Principio de Operación:
 * 1. Opera directamente con el binario nativo 'ffmpeg' compilado para la arquitectura
 *    específica del dispositivo (arm64-v8a, armeabi-v7a, x86_64, x86).
 * 2. Busca el ejecutable en el directorio de librerías nativas de la aplicación
 *    (context.applicationInfo.nativeLibraryDir / libffmpeg.so) o en el directorio binario interno
 *    (context.filesDir/bin/ffmpeg).
 * 3. Ejecuta tareas esenciales para Aura Music:
 *    - Extracción de audio con transcodificación precisa (Opus, Vorbis, FLAC, WebM -> M4A AAC / MP3).
 *    - Fusión directa sin pérdida (Muxing) de flujos de video y audio independientes (DASH).
 *    - Recorte físico milimétrico de audio para la eliminación de silencios.
 * 4. Captura la salida estándar de error de FFmpeg en segundo plano para reportar el progreso
 *    en tiempo real y registrar incidentes en Aura Monitor sin bloquear el hilo principal.
 */
object FFmpegNativeEngine {

    private const val TAG = "FFmpegNativeEngine"
    private val TIME_PATTERN = Pattern.compile("time=(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})")

    data class ExecutionResult(
        val success: Boolean,
        val exitCode: Int,
        val outputLog: String,
        val outputFile: File?
    )

    /**
     * Comprueba si el motor nativo FFmpeg está disponible y es ejecutable en el dispositivo.
     */
    fun isAvailable(context: Context): Boolean {
        return getBinaryFile(context)?.canExecute() == true
    }

    /**
     * Obtiene el archivo ejecutable de FFmpeg local, asegurando permisos de ejecución si es necesario.
     */
    fun getBinaryFile(context: Context): File? {
        // 1. Ubicación estándar en directorio de librerías nativas del sistema (libffmpeg.so marcado ejecutable)
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val candidateInLib = File(nativeLibDir, "libffmpeg.so")
        if (candidateInLib.exists() && candidateInLib.canExecute()) {
            return candidateInLib
        }

        // 2. Ubicación en almacenamiento privado interno (files/bin/ffmpeg)
        val binDir = File(context.filesDir, "bin")
        val candidateInBin = File(binDir, "ffmpeg")
        if (candidateInBin.exists()) {
            if (!candidateInBin.canExecute()) {
                candidateInBin.setExecutable(true, false)
            }
            if (candidateInBin.canExecute()) {
                return candidateInBin
            }
        }

        // 3. Ubicación alternativa libffmpeg.so dentro de filesDir/bin
        val candidateAlt = File(binDir, "libffmpeg.so")
        if (candidateAlt.exists()) {
            if (!candidateAlt.canExecute()) {
                candidateAlt.setExecutable(true, false)
            }
            if (candidateAlt.canExecute()) {
                return candidateAlt
            }
        }

        return null
    }

    /**
     * Extrae y transcodifica una pista de audio desde cualquier archivo de video o audio.
     * Soporta contenedores WebM, MKV, MP4, AVI y códecs Opus, Vorbis, FLAC, AAC.
     */
    suspend fun extractAudio(
        context: Context,
        inputFile: File,
        outputFile: File,
        audioBitrate: String = "256k",
        targetFormat: String = "m4a",
        totalDurationMs: Long = 0L,
        onProgress: ((Float) -> Unit)? = null
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val ffmpegBin = getBinaryFile(context)
        if (ffmpegBin == null) {
            AuraDebugManager.logWarning(TAG, "FFmpeg nativo no encontrado en el sistema. Se requerirá extractor de respaldo.")
            return@withContext ExecutionResult(
                success = false,
                exitCode = -1,
                outputLog = "Binario FFmpeg no disponible en el almacenamiento del dispositivo.",
                outputFile = null
            )
        }

        if (outputFile.exists()) {
            outputFile.delete()
        }

        // Argumentos quirúrgicos para extracción de audio en alta fidelidad:
        // -y: Sobrescribir
        // -i <input>: Archivo origen
        // -vn: Descartar flujo de video
        // -c:a aac: Códec de audio AAC nativo
        // -b:a <bitrate>: Tasa de bits de audio (ej. 256k)
        val args = if (targetFormat.equals("mp3", ignoreCase = true)) {
            arrayOf(
                ffmpegBin.absolutePath,
                "-y",
                "-i", inputFile.absolutePath,
                "-vn",
                "-c:a", "libmp3lame",
                "-b:a", audioBitrate,
                outputFile.absolutePath
            )
        } else {
            arrayOf(
                ffmpegBin.absolutePath,
                "-y",
                "-i", inputFile.absolutePath,
                "-vn",
                "-c:a", "aac",
                "-b:a", audioBitrate,
                "-movflags", "+faststart",
                outputFile.absolutePath
            )
        }

        executeCommand(args, totalDurationMs, onProgress)
    }

    /**
     * Fusiona sin recodificación (Direct Stream Copy) un flujo de video y un flujo de audio independientes.
     * Ideal para unir pistas de video y audio DASH descargadas por separado.
     */
    suspend fun mergeAudioAndVideo(
        context: Context,
        videoFile: File,
        audioFile: File,
        outputFile: File,
        totalDurationMs: Long = 0L,
        onProgress: ((Float) -> Unit)? = null
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val ffmpegBin = getBinaryFile(context)
        if (ffmpegBin == null) {
            return@withContext ExecutionResult(
                success = false,
                exitCode = -1,
                outputLog = "Binario FFmpeg no disponible para fusión de flujos.",
                outputFile = null
            )
        }

        if (outputFile.exists()) {
            outputFile.delete()
        }

        // Fusión rápida sin pérdida (-c copy)
        val args = arrayOf(
            ffmpegBin.absolutePath,
            "-y",
            "-i", videoFile.absolutePath,
            "-i", audioFile.absolutePath,
            "-c:v", "copy",
            "-c:a", "copy",
            "-map", "0:v:0",
            "-map", "1:a:0",
            "-movflags", "+faststart",
            outputFile.absolutePath
        )

        executeCommand(args, totalDurationMs, onProgress)
    }

    /**
     * Recorta físicamente un archivo de audio sin pérdida de calidad.
     */
    suspend fun trimAudioLossless(
        context: Context,
        inputFile: File,
        outputFile: File,
        startMs: Long,
        durationMs: Long
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val ffmpegBin = getBinaryFile(context)
        if (ffmpegBin == null) {
            return@withContext ExecutionResult(
                success = false,
                exitCode = -1,
                outputLog = "FFmpeg no disponible para recorte físico.",
                outputFile = null
            )
        }

        val startSec = String.format(java.util.Locale.US, "%.3f", startMs / 1000.0)
        val durationSec = String.format(java.util.Locale.US, "%.3f", durationMs / 1000.0)

        val args = arrayOf(
            ffmpegBin.absolutePath,
            "-y",
            "-ss", startSec,
            "-t", durationSec,
            "-i", inputFile.absolutePath,
            "-c", "copy",
            outputFile.absolutePath
        )

        executeCommand(args, durationMs, null)
    }

    /**
     * Ejecuta el comando en un proceso nativo del sistema operativo y monitorea el avance.
     */
    private fun executeCommand(
        args: Array<String>,
        totalDurationMs: Long,
        onProgress: ((Float) -> Unit)?
    ): ExecutionResult {
        val logBuilder = StringBuilder()
        val outputFile = args.lastOrNull()?.let { File(it) }

        return try {
            AuraDebugManager.logInfo(TAG, "Iniciando proceso FFmpeg puro: ${args.joinToString(" ")}")
            val process = ProcessBuilder(*args)
                .redirectErrorStream(true)
                .start()

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
}
