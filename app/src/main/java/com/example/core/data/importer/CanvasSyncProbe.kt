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
 * GOP corto para sync instantáneo y sondeo de dimensiones.
 * Rol arquitectónico: pieza de FFmpegCanvasOperations (fachada pública intacta).
 */
internal object CanvasSyncProbe {
    private const val TAG = "FFmpegNativeEngine"

suspend fun optimize(
    context: Context,
    inputFile: File,
    outputFile: File,
    gopSize: Int = 30,
    targetFps: Int = 30
): ExecutionResult = withContext(Dispatchers.IO) {
    FFmpegNativeEngine.init(context)
    val ffmpegBin = FFmpegNativeEngine.getBinaryFile(context)
    if (ffmpegBin == null) {
        return@withContext ExecutionResult(false, -1, "FFmpeg no disponible", null)
    }

    if (outputFile.exists()) outputFile.delete()

    val safeInput = FFmpegNativeEngine.sanitizeFilePath(inputFile)
    val safeOutput = FFmpegNativeEngine.sanitizeFilePath(outputFile)
    val scale480Filter = "scale='if(gt(iw,ih),-2,min(480,iw))':'if(gt(iw,ih),min(480,ih),-2)'"

    val args = arrayOf(
        ffmpegBin.absolutePath,
        "-y",
        "-i", safeInput,
        "-vf", scale480Filter,
        "-c:v", "mpeg4",
        "-q:v", "4",
        "-r", targetFps.toString(),
        "-g", gopSize.toString(),
        "-an",
        "-map_metadata", "-1",
        "-movflags", "+faststart",
        safeOutput
    )

    val result = FFmpegCommandExecutor.executeCommand(context, args, 0L, null)
    if (result.success) {
        AuraDebugManager.logInfo(TAG, "Video sincronizado optimizado a 480p con GOP corto ($gopSize) y faststart.")
        result
    } else {
        AuraDebugManager.logWarning(TAG, "Recodificación de video a 480p falló, aplicando faststart sin recodificar...")
        val safeFallbackInput = FFmpegNativeEngine.sanitizeFilePath(inputFile)
        val safeFallbackOutput = FFmpegNativeEngine.sanitizeFilePath(outputFile)
        val fallbackArgs = arrayOf(
            ffmpegBin.absolutePath,
            "-y",
            "-i", safeFallbackInput,
            "-c:v", "copy",
            "-an",
            "-map_metadata", "-1",
            "-movflags", "+faststart",
            safeFallbackOutput
        )
        FFmpegCommandExecutor.executeCommand(context, fallbackArgs, 0L, null)
    }
}

fun probe(videoFile: File): Pair<Int, Int>? {
    if (!videoFile.exists() || videoFile.length() < 1024L) return null
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(videoFile.absolutePath)
        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (w <= 0 || h <= 0) return null
        if (rot == 90 || rot == 270) {
            Pair(h, w)
        } else {
            Pair(w, h)
        }
    } catch (_: Throwable) {
        null
    } finally {
        try { retriever.release() } catch (_: Throwable) {}
    }
}
}
