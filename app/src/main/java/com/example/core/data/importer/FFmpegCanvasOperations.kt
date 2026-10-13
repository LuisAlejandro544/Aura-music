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
 * Operaciones de Video Canvas FFmpeg (loops, lienzo vertical y sincronización).
 * Rol arquitectónico: motor de video puro; la fachada FFmpegNativeEngine delega aquí.
 *
 * Rol arquitectónico: fachada pública intacta que delega en 3 piezas internas.
 */
internal object FFmpegCanvasOperations {

    suspend fun createBoomerangLoopVideo(
        context: android.content.Context,
        inputFile: java.io.File,
        outputFile: java.io.File,
        maxSourceDurationSec: Float = 10.0f,
        targetFps: Int = 30
    ) = CanvasLoopEffects.boomerang(context, inputFile, outputFile, maxSourceDurationSec, targetFps)

    suspend fun createSeamlessLoopVideo(
        context: android.content.Context,
        inputFile: java.io.File,
        outputFile: java.io.File,
        crossfadeDurationSec: Float = 0.8f,
        targetFps: Int = 30
    ) = CanvasLoopEffects.seamless(context, inputFile, outputFile, crossfadeDurationSec, targetFps)

    suspend fun optimizeVideoForInstantSync(
        context: android.content.Context,
        inputFile: java.io.File,
        outputFile: java.io.File,
        gopSize: Int = 30,
        targetFps: Int = 30
    ) = CanvasSyncProbe.optimize(context, inputFile, outputFile, gopSize, targetFps)

    fun probeVideoDimensions(videoFile: java.io.File): Pair<Int, Int>? =
        CanvasSyncProbe.probe(videoFile)

    suspend fun createVerticalCanvasFromHorizontalVideo(
        context: android.content.Context,
        inputFile: java.io.File,
        outputFile: java.io.File,
        targetWidth: Int = 480,
        targetHeight: Int = 854,
        targetFps: Int = 30
    ) = CanvasFormatOps.verticalCanvas(context, inputFile, outputFile, targetWidth, targetHeight, targetFps)

    suspend fun stripAudioAndMetadata(
        context: android.content.Context,
        inputFile: java.io.File,
        outputFile: java.io.File
    ) = CanvasFormatOps.strip(context, inputFile, outputFile)

    suspend fun optimizeWallpaperVideo(
        context: android.content.Context,
        inputFile: java.io.File,
        outputFile: java.io.File,
        minShortSidePx: Int = 720,
        targetFps: Int = 30
    ) = CanvasFormatOps.optimizeWallpaper(context, inputFile, outputFile, minShortSidePx, targetFps)

    suspend fun processVideoForCanvas(
        context: android.content.Context,
        inputFile: java.io.File,
        outputFile: java.io.File,
        isLoop: Boolean,
        loopStyle: com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle = com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE,
        preferVerticalCanvasOnHorizontal: Boolean = false
    ) = CanvasFormatOps.process(context, inputFile, outputFile, isLoop, loopStyle, preferVerticalCanvasOnHorizontal)
}
