package com.example.core.data.importer


import android.content.Context
import android.media.MediaMetadataRetriever
import com.example.debug.AuraDebugManager
import com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle
import com.example.core.data.importer.FFmpegNativeEngine.ExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


/**
 * Loops Boomerang y Seamless con crossfade xfade.
 * Rol arquitectónico: pieza de FFmpegCanvasOperations (fachada pública intacta).
 */
internal object CanvasLoopEffects {
    private const val TAG = "FFmpegNativeEngine"

suspend fun boomerang(
    context: Context,
    inputFile: File,
    outputFile: File,
    maxSourceDurationSec: Float = 10.0f,
    targetFps: Int = 30
): ExecutionResult = withContext(Dispatchers.IO) {
    FFmpegNativeEngine.init(context)
    val ffmpegBin = FFmpegNativeEngine.getBinaryFile(context)
    if (ffmpegBin == null) {
        return@withContext ExecutionResult(false, -1, "FFmpeg no disponible", null)
    }

    val retriever = android.media.MediaMetadataRetriever()
    var durationMs = 0L
    try {
        retriever.setDataSource(inputFile.absolutePath)
        durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Throwable) {} finally {
        try { retriever.release() } catch (_: Throwable) {}
    }

    val durationSec = (durationMs / 1000.0f).coerceAtLeast(1.0f)
    val clipSec = durationSec.coerceAtMost(maxSourceDurationSec)
    val clipFormatted = String.format(java.util.Locale.US, "%.3f", clipSec)

    // Filtro reverse + concat con escalado automático a 480p (lado menor <= 480px, múltiplo de 2)
    // para garantizar fluidez total de GPU/MediaCodec incluso en videos de galería 1080p/4K y al grabar pantalla
    val scale480Filter = "scale='if(gt(iw,ih),-2,min(480,iw))':'if(gt(iw,ih),min(480,ih),-2)'"
    val filterComplex = "[0:v]trim=start=0:end=${clipFormatted},setpts=PTS-STARTPTS,${scale480Filter},split=2[v_fwd][v_rev_src];" +
            "[v_rev_src]reverse,setpts=PTS-STARTPTS[v_rev];" +
            "[v_fwd][v_rev]concat=n=2:v=1:a=0[v_out]"

    if (outputFile.exists()) outputFile.delete()

    val safeInput = FFmpegNativeEngine.sanitizeFilePath(inputFile)
    val safeOutput = FFmpegNativeEngine.sanitizeFilePath(outputFile)

    val args = arrayOf(
        ffmpegBin.absolutePath,
        "-y",
        "-i", safeInput,
        "-filter_complex", filterComplex,
        "-map", "[v_out]",
        "-c:v", "mpeg4",
        "-q:v", "4",
        "-r", targetFps.toString(),
        "-g", targetFps.toString(),
        "-an",
        "-map_metadata", "-1",
        "-movflags", "+faststart",
        safeOutput
    )

    val expectedTotalMs = (clipSec * 2000.0f).toLong()
    val result = FFmpegCommandExecutor.executeCommand(context, args, expectedTotalMs, null)
    if (result.success) {
        AuraDebugManager.logInfo(TAG, "Loop Boomerang / Ping-Pong 480p (reverse + concat) generado exitosamente (${outputFile.name}).")
        result
    } else {
        AuraDebugManager.logWarning(TAG, "Filtro Boomerang reverse+concat falló, aplicando fallback Seamless Loop xfade...")
        seamless(context, inputFile, outputFile, targetFps = targetFps)
    }
}

suspend fun seamless(
    context: Context,
    inputFile: File,
    outputFile: File,
    crossfadeDurationSec: Float = 0.8f,
    targetFps: Int = 30
): ExecutionResult = withContext(Dispatchers.IO) {
    FFmpegNativeEngine.init(context)
    val ffmpegBin = FFmpegNativeEngine.getBinaryFile(context)
    if (ffmpegBin == null) {
        return@withContext ExecutionResult(false, -1, "FFmpeg no disponible", null)
    }

    // Obtener la duración exacta del video
    val retriever = android.media.MediaMetadataRetriever()
    var durationMs = 0L
    try {
        retriever.setDataSource(inputFile.absolutePath)
        durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Throwable) {} finally {
        try { retriever.release() } catch (_: Throwable) {}
    }

    val durationSec = durationMs / 1000.0f
    if (durationSec < 2.0f) {
        return@withContext CanvasSyncProbe.optimize(context, inputFile, outputFile, gopSize = targetFps, targetFps = targetFps)
    }

    val crossfadeSec = crossfadeDurationSec.coerceIn(0.4f, (durationSec * 0.25f).coerceAtLeast(0.4f))
    val splitPoint = String.format(java.util.Locale.US, "%.3f", durationSec - crossfadeSec)
    val durFormatted = String.format(java.util.Locale.US, "%.3f", crossfadeSec)
    val scale480Filter = "scale='if(gt(iw,ih),-2,min(480,iw))':'if(gt(iw,ih),min(480,ih),-2)'"

    val filterComplex = "[0:v]${scale480Filter},split=2[v_src1][v_src2];" +
            "[v_src1]trim=start=${splitPoint}:end=${String.format(java.util.Locale.US, "%.3f", durationSec)},setpts=PTS-STARTPTS[v_tail];" +
            "[v_src2]trim=start=0:end=${splitPoint},setpts=PTS-STARTPTS[v_main];" +
            "[v_tail][v_main]xfade=transition=fade:duration=${durFormatted}:offset=0[v_out]"

    if (outputFile.exists()) outputFile.delete()

    val safeInput = FFmpegNativeEngine.sanitizeFilePath(inputFile)
    val safeOutput = FFmpegNativeEngine.sanitizeFilePath(outputFile)

    val args = arrayOf(
        ffmpegBin.absolutePath,
        "-y",
        "-i", safeInput,
        "-filter_complex", filterComplex,
        "-map", "[v_out]",
        "-c:v", "mpeg4",
        "-q:v", "4",
        "-r", targetFps.toString(),
        "-g", targetFps.toString(),
        "-an",
        "-map_metadata", "-1",
        "-movflags", "+faststart",
        safeOutput
    )

    val result = FFmpegCommandExecutor.executeCommand(context, args, durationMs, null)
    if (result.success) {
        AuraDebugManager.logInfo(TAG, "Seamless Loop 480p con crossfade generado exitosamente (${outputFile.name}).")
        result
    } else {
        AuraDebugManager.logWarning(TAG, "Filtro xfade falló, aplicando fallback de optimización GOP 480p directa...")
        CanvasSyncProbe.optimize(context, inputFile, outputFile, gopSize = targetFps, targetFps = targetFps)
    }
}
}
