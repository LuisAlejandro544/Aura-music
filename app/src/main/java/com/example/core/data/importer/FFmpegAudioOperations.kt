package com.example.core.data.importer

import android.content.Context
import com.example.debug.AuraDebugManager
import com.example.core.data.importer.FFmpegNativeEngine.ExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Operaciones de audio FFmpeg (extracción, fusión y recorte).
 * Rol arquitectónico: motor de audio puro; la fachada FFmpegNativeEngine delega aquí.
 */
internal object FFmpegAudioOperations {

    private const val TAG = "FFmpegNativeEngine"

suspend fun extractAudio(
    context: Context,
    inputFile: File,
    outputFile: File,
    audioBitrate: String = "256k",
    targetFormat: String = "m4a",
    totalDurationMs: Long = 0L,
    onProgress: ((Float) -> Unit)? = null
): ExecutionResult = withContext(Dispatchers.IO) {
    FFmpegNativeEngine.init(context)
    val ffmpegBin = FFmpegNativeEngine.getBinaryFile(context)
    if (ffmpegBin == null) {
        AuraDebugManager.logWarning(TAG, "FFmpeg nativo no encontrado en el sistema.")
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

    val safeInput = FFmpegNativeEngine.sanitizeFilePath(inputFile)
    val safeOutput = FFmpegNativeEngine.sanitizeFilePath(outputFile)

    val args = if (targetFormat.equals("mp3", ignoreCase = true)) {
        arrayOf(
            ffmpegBin.absolutePath,
            "-y",
            "-i", safeInput,
            "-vn",
            "-c:a", "libmp3lame",
            "-b:a", audioBitrate,
            safeOutput
        )
    } else {
        arrayOf(
            ffmpegBin.absolutePath,
            "-y",
            "-i", safeInput,
            "-vn",
            "-c:a", "aac",
            "-b:a", audioBitrate,
            "-movflags", "+faststart",
            safeOutput
        )
    }

    FFmpegCommandExecutor.executeCommand(context, args, totalDurationMs, onProgress)
}

/**
 * Fusiona sin recodificación (Direct Stream Copy) un flujo de video y un flujo de audio independientes.
 */
suspend fun mergeAudioAndVideo(
    context: Context,
    videoFile: File,
    audioFile: File,
    outputFile: File,
    totalDurationMs: Long = 0L,
    onProgress: ((Float) -> Unit)? = null
): ExecutionResult = withContext(Dispatchers.IO) {
    FFmpegNativeEngine.init(context)
    val ffmpegBin = FFmpegNativeEngine.getBinaryFile(context)
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

    val safeVideo = FFmpegNativeEngine.sanitizeFilePath(videoFile)
    val safeAudio = FFmpegNativeEngine.sanitizeFilePath(audioFile)
    val safeOutput = FFmpegNativeEngine.sanitizeFilePath(outputFile)

    val args = arrayOf(
        ffmpegBin.absolutePath,
        "-y",
        "-i", safeVideo,
        "-i", safeAudio,
        "-c:v", "copy",
        "-c:a", "copy",
        "-map", "0:v:0",
        "-map", "1:a:0",
        "-movflags", "+faststart",
        safeOutput
    )

    FFmpegCommandExecutor.executeCommand(context, args, totalDurationMs, onProgress)
}

/**
 * Une el archivo de Video Canvas (.mp4 sin sonido) con el archivo de audio de la canción (.m4a/.mp3)
 * para entregar al usuario en su carpeta personalizada un vídeo MP4 completo con sonido.
 * - Si el vídeo es sincronizado de duración completa: hace muxing instantáneo (-c:v copy -c:a aac).
 * - Si el vídeo es un loop corto (isVideoLoop = true): repite el bucle de vídeo (-stream_loop -1)
 *   hasta cubrir la duración de la canción (-shortest) sin recodificar el vídeo (-c:v copy).
 */
suspend fun mergeVideoCanvasWithTrackAudio(
    context: Context,
    videoFile: File,
    audioFile: File,
    outputFile: File,
    isVideoLoop: Boolean = false,
    totalDurationMs: Long = 0L
): ExecutionResult = withContext(Dispatchers.IO) {
    FFmpegNativeEngine.init(context)
    val ffmpegBin = FFmpegNativeEngine.getBinaryFile(context)
    if (ffmpegBin == null) {
        return@withContext ExecutionResult(
            success = false,
            exitCode = -1,
            outputLog = "FFmpeg no disponible para exportar vídeo con audio.",
            outputFile = null
        )
    }

    if (outputFile.exists()) {
        outputFile.delete()
    }

    val safeVideo = FFmpegNativeEngine.sanitizeFilePath(videoFile)
    val safeAudio = FFmpegNativeEngine.sanitizeFilePath(audioFile)
    val safeOutput = FFmpegNativeEngine.sanitizeFilePath(outputFile)

    // Intentar primero copia directa de vídeo + audio (ultrarrápido, ~0.5s)
    val fastArgs = if (isVideoLoop) {
        arrayOf(
            ffmpegBin.absolutePath,
            "-y",
            "-stream_loop", "-1",
            "-i", safeVideo,
            "-i", safeAudio,
            "-map", "0:v:0",
            "-map", "1:a:0",
            "-c:v", "copy",
            "-c:a", "aac",
            "-b:a", "192k",
            "-shortest",
            "-movflags", "+faststart",
            safeOutput
        )
    } else {
        arrayOf(
            ffmpegBin.absolutePath,
            "-y",
            "-i", safeVideo,
            "-i", safeAudio,
            "-map", "0:v:0",
            "-map", "1:a:0",
            "-c:v", "copy",
            "-c:a", "copy",
            "-shortest",
            "-movflags", "+faststart",
            safeOutput
        )
    }

    val fastResult = FFmpegCommandExecutor.executeCommand(context, fastArgs, totalDurationMs, null)
    if (fastResult.success && outputFile.exists() && outputFile.length() > 4096L) {
        return@withContext fastResult
    }

    // Fallback compatible si el códec de audio original (ej. Opus/FLAC) requiere transcodificación AAC para contenedor MP4
    if (outputFile.exists()) outputFile.delete()
    val compatArgs = arrayOf(
        ffmpegBin.absolutePath,
        "-y",
        "-i", safeVideo,
        "-i", safeAudio,
        "-map", "0:v:0",
        "-map", "1:a:0",
        "-c:v", "copy",
        "-c:a", "aac",
        "-b:a", "192k",
        "-shortest",
        "-movflags", "+faststart",
        safeOutput
    )
    FFmpegCommandExecutor.executeCommand(context, compatArgs, totalDurationMs, null)
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
    FFmpegNativeEngine.init(context)
    val ffmpegBin = FFmpegNativeEngine.getBinaryFile(context)
    if (ffmpegBin == null) {
        return@withContext ExecutionResult(
            success = false,
            exitCode = -1,
            outputLog = "FFmpeg no disponible para recorte físico.",
            outputFile = null
        )
    }

    val safeInput = FFmpegNativeEngine.sanitizeFilePath(inputFile)
    val safeOutput = FFmpegNativeEngine.sanitizeFilePath(outputFile)

    val startSec = String.format(java.util.Locale.US, "%.3f", startMs / 1000.0)
    val durationSec = String.format(java.util.Locale.US, "%.3f", durationMs / 1000.0)

    val args = arrayOf(
        ffmpegBin.absolutePath,
        "-y",
        "-ss", startSec,
        "-t", durationSec,
        "-i", safeInput,
        "-c", "copy",
        safeOutput
    )

    FFmpegCommandExecutor.executeCommand(context, args, durationMs, null)
}

/**
 * Estilo de bucle continuo para Video Canvas cortos (<= 20s):
 * - CROSSFADE: Fundido suave entre el final y el inicio mediante 'xfade'.
 * - BOOMERANG: Efecto Ping-Pong (Ida y Vuelta) hipnótico mediante 'reverse' + 'concat=n=2:v=1:a=0'.
 */
}
