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
 */
internal object FFmpegCanvasOperations {

    private const val TAG = "FFmpegNativeEngine"

suspend fun createBoomerangLoopVideo(
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
        createSeamlessLoopVideo(context, inputFile, outputFile, targetFps = targetFps)
    }
}

/**
 * Crea un bucle infinito cinemático sin cortes (Seamless Loop con Crossfade) para Video Canvas cortos (<= 20s) a 480p.
 * Mezcla suavemente los últimos segundos con los primeros mediante el filtro 'xfade',
 * garantizando que al repetirse continuamente en ExoPlayer no exista ningún salto brusco ni interrupción visual.
 */
suspend fun createSeamlessLoopVideo(
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
        return@withContext optimizeVideoForInstantSync(context, inputFile, outputFile, gopSize = targetFps, targetFps = targetFps)
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
        optimizeVideoForInstantSync(context, inputFile, outputFile, gopSize = targetFps, targetFps = targetFps)
    }
}

/**
 * Optimiza videos largos sincronizados (>20s) escalándolos a 480p e insertando fotogramas clave (Keyframes / GOP) cada 1 segundo.
 * Permite que incluso los videos 1080p/4K importados desde la galería funcionen a 480p con cero lag al grabar pantalla
 * y saltos temporales (SeekTo) instantáneos (0ms de congelamiento).
 */
suspend fun optimizeVideoForInstantSync(
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

/**
 * Inspecciona las dimensiones de ancho y alto de un archivo de video local,
 * considerando la rotación (90/270 grados).
 */
fun probeVideoDimensions(videoFile: File): Pair<Int, Int>? {
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

/**
 * Convierte un video horizontal (16:9 / panorámico) en un lienzo vertical cinemático 9:16 para Video Canvas.
 *
 * Composición:
 * - Capa de fondo: El video escalado para cubrir verticalmente 9:16 (ej. 480x854), con desenfoque 'boxblur=16:2' y leve oscurecimiento para contraste.
 * - Capa frontal: El video horizontal 16:9 original centrado en el medio sin recortar los bordes, garantizando que el 100% de los rostros y la acción sean visibles.
 * - Salida optimizada con GOP corto (30) y faststart para seek instantáneo (0ms).
 */
suspend fun createVerticalCanvasFromHorizontalVideo(
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
        optimizeVideoForInstantSync(context, inputFile, outputFile, gopSize = targetFps, targetFps = targetFps)
    }
}

/**
 * Elimina el audio (-an) y purga metadatos innecesarios (-map_metadata -1) de un video
 * de forma ultra rápida sin recodificar el video (-c:v copy).
 */
suspend fun stripAudioAndMetadata(
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
 * Orquestador inteligente de procesamiento para Video Canvas:
 * 1. Detecta la relación de aspecto del video (horizontal 16:9 vs vertical 9:16).
 *    - Si es horizontal (ancho > alto * 1.15): genera automáticamente un lienzo vertical cinemático 9:16
 *      con video frontal nítido centrado y fondo difuminado, evitando recortes de rostros en pantallas de móvil.
 * 2. Si ya es vertical o cuadrado:
 *    - Si es bucle (isLoop = true, <= 20s):
 *        - Si loopStyle == BOOMERANG: aplica Efecto Boomerang / Ping-Pong (reverse + concat).
 *        - Si loopStyle == CROSSFADE: aplica Seamless Loop con Crossfade (xfade).
 *    - Si es video largo sincronizado (isLoop = false, > 20s), aplica optimización de Keyframes (GOP Corto).
 */
suspend fun processVideoForCanvas(
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

    val dimensions = probeVideoDimensions(inputFile)
    val isHorizontal = dimensions != null && (dimensions.first > dimensions.second * 1.15f)

    val res = when {
        // Caso 1: Video horizontal con lienzo 9:16 pre-renderizado explícito
        isHorizontal && preferVerticalCanvasOnHorizontal -> {
            AuraDebugManager.logInfo(TAG, "Detectado video horizontal (${dimensions?.first}x${dimensions?.second}). Adaptando a lienzo 9:16...")
            createVerticalCanvasFromHorizontalVideo(context, inputFile, outputFile)
        }
        // Caso 2: Video con repetición en bucle (conserva proporción limpia para permitir Rellenar o Adaptado en vivo)
        isLoop -> {
            when (loopStyle) {
                CanvasLoopStyle.BOOMERANG -> createBoomerangLoopVideo(context, inputFile, outputFile)
                CanvasLoopStyle.CROSSFADE -> createSeamlessLoopVideo(context, inputFile, outputFile)
            }
        }
        // Caso 3: Video largo sincronizado (conserva proporción limpia 480p con GOP corto para Rellenar o Adaptado en vivo)
        else -> {
            optimizeVideoForInstantSync(context, inputFile, outputFile)
        }
    }

    if (res.success && outputFile.exists() && outputFile.length() > 4096L) {
        res
    } else {
        val fallbackStrip = stripAudioAndMetadata(context, inputFile, outputFile)
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

/**
 * Ejecuta el comando en un proceso nativo del sistema operativo y monitorea el avance.
 */
}
