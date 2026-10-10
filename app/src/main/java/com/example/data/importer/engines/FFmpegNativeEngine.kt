package com.example.data.importer

import android.content.Context
import android.media.MediaMetadataRetriever
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.regex.Pattern
import java.util.zip.ZipFile

/**
 * Motor nativo de procesamiento multimedia basado en FFmpeg puro sin wrappers de terceros.
 *
 * Arquitectura y Principio de Operación:
 * 1. Opera directamente con el binario nativo ejecutable 'libffmpeg.so' empaquetado en el APK
 *    para cada arquitectura (arm64-v8a, armeabi-v7a, x86_64, x86) e instalado por el sistema
 *    en [context.applicationInfo.nativeLibraryDir] con permisos de ejecución nativos.
 * 2. Desempaqueta atómicamente en segundo plano las bibliotecas dinámicas compartidas de audio y
 *    video ([libffmpeg.zip.so]) en [context.filesDir/env/ffmpeg] sin inflar el sistema con wrappers.
 * 3. Ejecuta tareas esenciales para Aura Music:
 *    - Extracción de audio con transcodificación precisa (Opus, Vorbis, FLAC, WebM -> M4A AAC / MP3).
 *    - Fusión directa sin pérdida (Muxing -c copy) de flujos de video y audio independientes (DASH).
 *    - Recorte físico milimétrico de audio para la eliminación de silencios.
 * 4. Captura la salida estándar de FFmpeg en segundo plano para reportar el progreso en tiempo real
 *    y registrar incidentes en Aura Monitor sin bloquear el hilo principal.
 */
object FFmpegNativeEngine {

    private const val TAG = "FFmpegNativeEngine"
    private val TIME_PATTERN = Pattern.compile("time=(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})")

    @Volatile
    private var isInitialized = false

    data class ExecutionResult(
        val success: Boolean,
        val exitCode: Int,
        val outputLog: String,
        val outputFile: File?
    )

    /**
     * Inicializa el entorno nativo de bibliotecas dinámicas de FFmpeg directamente desde [nativeLibraryDir],
     * purgando cualquier carpeta residual antigua (`env/ffmpeg`) para no duplicar binarios en el teléfono.
     */
    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return

        try {
            val legacyFfmpegEnvDir = File(context.filesDir, "env/ffmpeg")
            if (legacyFfmpegEnvDir.exists()) {
                legacyFfmpegEnvDir.deleteRecursively()
                AuraDebugManager.logInfo(TAG, "Carpeta residual antigua env/ffmpeg eliminada para ahorrar almacenamiento.")
            }

            val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
            val ffmpegZip = File(nativeLibDir, "libffmpeg.zip.so")

            // Soporte de respaldo solo si algún entorno legado provee exclusivamente libffmpeg.zip.so sin librerías directas
            val hasDirectLibs = File(nativeLibDir, "libavcodec.so").exists() || File(nativeLibDir, "libffmpegkit.so").exists()
            if (!hasDirectLibs && ffmpegZip.exists()) {
                val versionMarker = File(legacyFfmpegEnvDir, ".version")
                val currentSignature = "${ffmpegZip.length()}_${ffmpegZip.lastModified()}"
                val needExtraction = !legacyFfmpegEnvDir.exists() ||
                        !versionMarker.exists() ||
                        versionMarker.readText().trim() != currentSignature

                if (needExtraction) {
                    if (legacyFfmpegEnvDir.exists()) legacyFfmpegEnvDir.deleteRecursively()
                    legacyFfmpegEnvDir.mkdirs()
                    extractZip(ffmpegZip, legacyFfmpegEnvDir)
                    versionMarker.writeText(currentSignature)
                }
            }

            isInitialized = true
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Fallo al inicializar librerías de FFmpeg: ${e.message}")
        }
    }

    /**
     * Comprueba si el motor nativo FFmpeg está disponible y es ejecutable en el dispositivo.
     */
    fun isAvailable(context: Context): Boolean {
        return getBinaryFile(context)?.canExecute() == true
    }

    /**
     * Obtiene el archivo ejecutable de FFmpeg local en nativeLibraryDir o en filesDir/bin.
     */
    fun getBinaryFile(context: Context): File? {
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val candidateInLib = File(nativeLibDir, "libffmpeg.so")
        if (candidateInLib.exists() && candidateInLib.canExecute()) {
            return candidateInLib
        }

        val binDir = File(context.filesDir, "bin")
        val candidateInBin = File(binDir, "ffmpeg")
        if (candidateInBin.exists() && candidateInBin.canExecute()) {
            return candidateInBin
        }

        return null
    }

    /**
     * Retorna la ruta de bibliotecas dinámicas compartidas de FFmpeg para LD_LIBRARY_PATH.
     */
    fun getLibraryPath(context: Context): String {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val envLibDir = File(context.filesDir, "env/ffmpeg/usr/lib")
        return if (envLibDir.exists()) {
            "${envLibDir.absolutePath}:$nativeDir"
        } else {
            nativeDir
        }
    }

    /**
     * Sanitiza y valida una ruta de archivo para prevenir vulnerabilidades de Flag / Option Injection en FFmpeg.
     * Garantiza que la ruta sea absoluta canónica y nunca comience con guión '-'.
     */
    fun sanitizeFilePath(file: File): String {
        val canonical = file.canonicalPath
        if (canonical.startsWith("-")) {
            throw SecurityException("Ruta no permitida para FFmpeg: '$canonical' comienza con un guión ('-') lo que constituye un intento de flag injection.")
        }
        return canonical
    }

    /**
     * Extrae y transcodifica una pista de audio desde cualquier archivo de video o audio.
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
        init(context)
        val ffmpegBin = getBinaryFile(context)
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

        val safeInput = sanitizeFilePath(inputFile)
        val safeOutput = sanitizeFilePath(outputFile)

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

        executeCommand(context, args, totalDurationMs, onProgress)
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
        init(context)
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

        val safeVideo = sanitizeFilePath(videoFile)
        val safeAudio = sanitizeFilePath(audioFile)
        val safeOutput = sanitizeFilePath(outputFile)

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

        executeCommand(context, args, totalDurationMs, onProgress)
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
        init(context)
        val ffmpegBin = getBinaryFile(context)
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

        val safeVideo = sanitizeFilePath(videoFile)
        val safeAudio = sanitizeFilePath(audioFile)
        val safeOutput = sanitizeFilePath(outputFile)

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

        val fastResult = executeCommand(context, fastArgs, totalDurationMs, null)
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
        executeCommand(context, compatArgs, totalDurationMs, null)
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
        init(context)
        val ffmpegBin = getBinaryFile(context)
        if (ffmpegBin == null) {
            return@withContext ExecutionResult(
                success = false,
                exitCode = -1,
                outputLog = "FFmpeg no disponible para recorte físico.",
                outputFile = null
            )
        }

        val safeInput = sanitizeFilePath(inputFile)
        val safeOutput = sanitizeFilePath(outputFile)

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

        executeCommand(context, args, durationMs, null)
    }

    /**
     * Estilo de bucle continuo para Video Canvas cortos (<= 20s):
     * - CROSSFADE: Fundido suave entre el final y el inicio mediante 'xfade'.
     * - BOOMERANG: Efecto Ping-Pong (Ida y Vuelta) hipnótico mediante 'reverse' + 'concat=n=2:v=1:a=0'.
     */
    enum class CanvasLoopStyle(val label: String, val description: String) {
        CROSSFADE("Crossfade Suave", "Fundido cruzado continuo entre el final y el inicio (xfade)"),
        BOOMERANG("Boomerang (Ping-Pong)", "Reproducción fluida de ida y vuelta en reversa sin cortes (reverse + concat)")
    }

    /**
     * Crea un bucle infinito cinemático con Efecto Boomerang / Ping-Pong (reverse + concat)
     * para Video Canvas cortos (<= 20s).
     * Reproduce el segmento hacia adelante y regresa suavemente en reversa, eliminando
     * al 100% cualquier salto o fantasmagoría en clips con movimiento rápido.
     */
    suspend fun createBoomerangLoopVideo(
        context: Context,
        inputFile: File,
        outputFile: File,
        maxSourceDurationSec: Float = 10.0f,
        targetFps: Int = 30
    ): ExecutionResult = withContext(Dispatchers.IO) {
        init(context)
        val ffmpegBin = getBinaryFile(context)
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

        val safeInput = sanitizeFilePath(inputFile)
        val safeOutput = sanitizeFilePath(outputFile)

        val args = arrayOf(
            ffmpegBin.absolutePath,
            "-y",
            "-i", safeInput,
            "-filter_complex", filterComplex,
            "-map", "[v_out]",
            "-c:v", "libx264",
            "-preset", "veryfast",
            "-crf", "23",
            "-r", targetFps.toString(),
            "-g", targetFps.toString(),
            "-keyint_min", (targetFps / 2).toString(),
            "-sc_threshold", "0",
            "-an",
            "-map_metadata", "-1",
            "-movflags", "+faststart",
            safeOutput
        )

        val expectedTotalMs = (clipSec * 2000.0f).toLong()
        val result = executeCommand(context, args, expectedTotalMs, null)
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
        init(context)
        val ffmpegBin = getBinaryFile(context)
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

        val safeInput = sanitizeFilePath(inputFile)
        val safeOutput = sanitizeFilePath(outputFile)

        val args = arrayOf(
            ffmpegBin.absolutePath,
            "-y",
            "-i", safeInput,
            "-filter_complex", filterComplex,
            "-map", "[v_out]",
            "-c:v", "libx264",
            "-preset", "veryfast",
            "-crf", "23",
            "-r", targetFps.toString(),
            "-g", targetFps.toString(),
            "-keyint_min", (targetFps / 2).toString(),
            "-sc_threshold", "0",
            "-an",
            "-map_metadata", "-1",
            "-movflags", "+faststart",
            safeOutput
        )

        val result = executeCommand(context, args, durationMs, null)
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
        init(context)
        val ffmpegBin = getBinaryFile(context)
        if (ffmpegBin == null) {
            return@withContext ExecutionResult(false, -1, "FFmpeg no disponible", null)
        }

        if (outputFile.exists()) outputFile.delete()

        val safeInput = sanitizeFilePath(inputFile)
        val safeOutput = sanitizeFilePath(outputFile)
        val scale480Filter = "scale='if(gt(iw,ih),-2,min(480,iw))':'if(gt(iw,ih),min(480,ih),-2)'"

        val args = arrayOf(
            ffmpegBin.absolutePath,
            "-y",
            "-i", safeInput,
            "-vf", scale480Filter,
            "-c:v", "libx264",
            "-preset", "veryfast",
            "-crf", "23",
            "-r", targetFps.toString(),
            "-g", gopSize.toString(),
            "-keyint_min", (gopSize / 2).toString(),
            "-sc_threshold", "0",
            "-an",
            "-map_metadata", "-1",
            "-movflags", "+faststart",
            safeOutput
        )

        val result = executeCommand(context, args, 0L, null)
        if (result.success) {
            AuraDebugManager.logInfo(TAG, "Video sincronizado optimizado a 480p con GOP corto ($gopSize) y faststart.")
            result
        } else {
            AuraDebugManager.logWarning(TAG, "Recodificación de video a 480p falló, aplicando faststart sin recodificar...")
            val safeFallbackInput = sanitizeFilePath(inputFile)
            val safeFallbackOutput = sanitizeFilePath(outputFile)
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
            executeCommand(context, fallbackArgs, 0L, null)
        }
    }

    /**
     * Inspecciona las dimensiones de ancho y alto de un archivo de video local,
     * considerando la rotación (90/270 grados).
     */
    fun probeVideoDimensions(videoFile: File): Pair<Int, Int>? {
        if (!videoFile.exists() || videoFile.length() < 1024L) return null
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoFile.absolutePath)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            retriever.release()
            if (w <= 0 || h <= 0) return null
            if (rot == 90 || rot == 270) {
                Pair(h, w)
            } else {
                Pair(w, h)
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Convierte un video horizontal (16:9 / panorámico) en un lienzo vertical cinemático 9:16 estilo Spotify/TikTok Canvas.
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
        init(context)
        val ffmpegBin = getBinaryFile(context)
        if (ffmpegBin == null) {
            return@withContext ExecutionResult(false, -1, "FFmpeg no disponible", null)
        }

        if (outputFile.exists()) outputFile.delete()

        val safeInput = sanitizeFilePath(inputFile)
        val safeOutput = sanitizeFilePath(outputFile)

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
            "-c:v", "libx264",
            "-preset", "veryfast",
            "-crf", "24",
            "-r", targetFps.toString(),
            "-g", targetFps.toString(),
            "-keyint_min", (targetFps / 2).toString(),
            "-sc_threshold", "0",
            "-an",
            "-map_metadata", "-1",
            "-movflags", "+faststart",
            safeOutput
        )

        val result = executeCommand(context, args, 0L, null)
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
        init(context)
        val ffmpegBin = getBinaryFile(context)
        if (ffmpegBin == null) {
            return@withContext ExecutionResult(false, -1, "FFmpeg no disponible", null)
        }
        if (outputFile.exists()) outputFile.delete()
        val safeInput = sanitizeFilePath(inputFile)
        val safeOutput = sanitizeFilePath(outputFile)
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
        executeCommand(context, args, 0L, null)
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
        if (!isAvailable(context)) {
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
    private fun executeCommand(
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
                this["LD_LIBRARY_PATH"] = getLibraryPath(context)
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

    private fun extractZip(zipFile: File, destDir: File) {
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
