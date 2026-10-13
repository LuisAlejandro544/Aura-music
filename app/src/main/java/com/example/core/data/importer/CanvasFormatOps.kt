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
 * Lienzo vertical, poda de audio y pipeline principal del Canvas.
 * Rol arquitectónico: pieza de FFmpegCanvasOperations (fachada pública intacta).
 */
internal object CanvasFormatOps {
    private const val TAG = "FFmpegNativeEngine"

suspend fun verticalCanvas(
    context: Context,
    inputFile: File,
    outputFile: File,
    targetWidth: Int = 480,
    targetHeight: Int = 854,
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

    // Filtro complejo:
    // 1. Divide el video en [fg] (primer plano) y [bg] (fondo).
    // 2. [bg] se escala para rellenar 480x854 (9:16), se recorta exactamente a esas dimensiones, se difumina y oscurece.
    // 3. [fg] se escala al ancho exacto manteniendo su relación de aspecto (-2 para paridad de píxeles H.264).
    // 4. Se superpone [fg] en el centro vertical de [bg].
    val filterComplex = "[0:v]split=2[fg][bg];" +
            "[bg]scale=${targetWidth}:${targetHeight}:force_original_aspect_ratio=increase,crop=${targetWidth}:${targetHeight},boxblur=16:2,eq=brightness=-0.12[bg_blur];" +
            "[fg]scale=${targetWidth}:-2[fg_scaled];" +
            "[bg_blur][fg_scaled]overlay=(W-w)/2:(H-h)/2[v_out]"

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

    val result = FFmpegCommandExecutor.executeCommand(context, args, 0L, null)
    if (result.success) {
        AuraDebugManager.logInfo(TAG, "Lienzo vertical 9:16 generado exitosamente para video horizontal (${outputFile.name}).")
        result
    } else {
        AuraDebugManager.logWarning(TAG, "Filtro de lienzo 9:16 falló, aplicando fallback de sincronización GOP...")
        CanvasSyncProbe.optimize(context, inputFile, outputFile, gopSize = targetFps, targetFps = targetFps)
    }
}

suspend fun strip(
    context: Context,
    inputFile: File,
    outputFile: File
): ExecutionResult = withContext(Dispatchers.IO) {
    FFmpegNativeEngine.init(context)
    val ffmpegBin = FFmpegNativeEngine.getBinaryFile(context)
    if (ffmpegBin == null) {
        return@withContext ExecutionResult(false, -1, "FFmpeg no disponible", null)
    }
    if (outputFile.exists()) outputFile.delete()
    val safeInput = FFmpegNativeEngine.sanitizeFilePath(inputFile)
    val safeOutput = FFmpegNativeEngine.sanitizeFilePath(outputFile)
    val args = arrayOf(
        ffmpegBin.absolutePath,
        "-y",
        "-i", safeInput,
        "-c:v", "copy",
        "-an",
        "-map_metadata", "-1",
        "-movflags", "+faststart",
        safeOutput
    )
    FFmpegCommandExecutor.executeCommand(context, args, 0L, null)
}

/**
 * Optimización dedicada al fondo de pantalla de galería (video corto de fondo).
 * - Formato de salida comprimido (MP4 + mpeg4, sin audio ni metadatos, faststart).
 * - Baja la resolución solo si supera el mínimo aceptable (lado corto > 720px):
 *   escala el lado corto a 720px preservando aspecto y paridad H.264 (-2).
 * - Si el video ya está por debajo del mínimo (lado corto <= 720px), se deja
 *   como está en resolución y solo se re-contiene/re-codifica al formato
 *   comprimido para ahorrar almacenamiento sin perder nitidez.
 * - Calidad q:v 3 (alta pero comprimida) + 30fps + GOP corto para seek fluido.
 */
suspend fun optimizeWallpaper(
    context: Context,
    inputFile: File,
    outputFile: File,
    minShortSidePx: Int = 720,
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

    val dimensions = CanvasSyncProbe.probe(inputFile)
    val shortSide = if (dimensions != null && dimensions.first > 0 && dimensions.second > 0) {
        minOf(dimensions.first, dimensions.second)
    } else {
        // Sin dimensiones fiables: asumir que necesita escalado seguro a 720px.
        minShortSidePx + 1
    }
    // Si ya está por debajo del mínimo aceptado, no se toca la resolución:
    // solo se re-codifica al formato comprimido (-c:v mpeg4) sin filtro -vf.
    val videoArgs: Array<String> = if (shortSide <= minShortSidePx) {
        AuraDebugManager.logInfo(TAG, "Fondo video ${dimensions?.first}x${dimensions?.second}: bajo el mínimo (${minShortSidePx}px), solo se convierte al formato comprimido sin reescalar.")
        arrayOf(
            ffmpegBin.absolutePath,
            "-y",
            "-i", safeInput,
            "-c:v", "mpeg4",
            "-q:v", "3",
            "-r", targetFps.toString(),
            "-g", targetFps.toString(),
            "-an",
            "-map_metadata", "-1",
            "-movflags", "+faststart",
            safeOutput
        )
    } else {
        AuraDebugManager.logInfo(TAG, "Fondo video ${dimensions?.first}x${dimensions?.second}: supera el mínimo, se reduce el lado corto a ${minShortSidePx}px para ahorrar almacenamiento.")
        val scaleFilter = "scale='if(gt(iw,ih),-2,${minShortSidePx})':'if(gt(iw,ih),${minShortSidePx},-2)'"
        arrayOf(
            ffmpegBin.absolutePath,
            "-y",
            "-i", safeInput,
            "-vf", scaleFilter,
            "-c:v", "mpeg4",
            "-q:v", "3",
            "-r", targetFps.toString(),
            "-g", targetFps.toString(),
            "-an",
            "-map_metadata", "-1",
            "-movflags", "+faststart",
            safeOutput
        )
    }
    FFmpegCommandExecutor.executeCommand(context, videoArgs, 0L, null)
}

suspend fun process(
    context: Context,
    inputFile: File,
    outputFile: File,
    isLoop: Boolean,
    loopStyle: CanvasLoopStyle = CanvasLoopStyle.CROSSFADE,
    preferVerticalCanvasOnHorizontal: Boolean = false
): ExecutionResult = withContext(Dispatchers.IO) {
    if (!FFmpegNativeEngine.isAvailable(context)) {
        return@withContext try {
            inputFile.copyTo(outputFile, overwrite = true)
            ExecutionResult(true, 0, "Copia directa sin FFmpeg", outputFile)
        } catch (e: Exception) {
            ExecutionResult(false, -1, e.message ?: "Error al copiar", null)
        }
    }

    val dimensions = CanvasSyncProbe.probe(inputFile)
    val isHorizontal = dimensions != null && (dimensions.first > dimensions.second * 1.15f)

    val res = when {
        // Caso 1: Video horizontal con lienzo 9:16 pre-renderizado explícito
        isHorizontal && preferVerticalCanvasOnHorizontal -> {
            AuraDebugManager.logInfo(TAG, "Detectado video horizontal (${dimensions?.first}x${dimensions?.second}). Adaptando a lienzo 9:16...")
            verticalCanvas(context, inputFile, outputFile)
        }
        // Caso 2: Video con repetición en bucle (conserva proporción limpia para permitir Rellenar o Adaptado en vivo)
        isLoop -> {
            when (loopStyle) {
                CanvasLoopStyle.BOOMERANG -> CanvasLoopEffects.boomerang(context, inputFile, outputFile)
                CanvasLoopStyle.CROSSFADE -> CanvasLoopEffects.seamless(context, inputFile, outputFile)
            }
        }
        // Caso 3: Video largo sincronizado (conserva proporción limpia 480p con GOP corto para Rellenar o Adaptado en vivo)
        else -> {
            CanvasSyncProbe.optimize(context, inputFile, outputFile)
        }
    }

    if (res.success && outputFile.exists() && outputFile.length() > 4096L) {
        res
    } else {
        val fallbackStrip = strip(context, inputFile, outputFile)
        if (fallbackStrip.success && outputFile.exists() && outputFile.length() > 4096L) {
            fallbackStrip
        } else {
            try {
                inputFile.copyTo(outputFile, overwrite = true)
                ExecutionResult(true, 0, "Respaldo directo de flujo MP4", outputFile)
            } catch (e: Exception) {
                res
            }
        }
    }
}
}
