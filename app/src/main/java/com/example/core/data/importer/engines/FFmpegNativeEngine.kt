package com.example.core.data.importer

import android.content.Context
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
            val hasDirectLibs = File(nativeLibDir, "libavcodec.so").exists() || File(nativeLibDir, "libavcodec_neon.so").exists()
            if (!hasDirectLibs && ffmpegZip.exists()) {
                val versionMarker = File(legacyFfmpegEnvDir, ".version")
                val currentSignature = "${ffmpegZip.length()}_${ffmpegZip.lastModified()}"
                val needExtraction = !legacyFfmpegEnvDir.exists() ||
                        !versionMarker.exists() ||
                        versionMarker.readText().trim() != currentSignature

                if (needExtraction) {
                    if (legacyFfmpegEnvDir.exists()) legacyFfmpegEnvDir.deleteRecursively()
                    legacyFfmpegEnvDir.mkdirs()
                    FFmpegCommandExecutor.extractZip(ffmpegZip, legacyFfmpegEnvDir)
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

    suspend fun extractAudio(
        context: Context,
        inputFile: File,
        outputFile: File,
        audioBitrate: String = "256k",
        targetFormat: String = "m4a",
        totalDurationMs: Long = 0L,
        onProgress: ((Float) -> Unit)? = null
    ): ExecutionResult = withContext(Dispatchers.IO) { FFmpegAudioOperations.extractAudio(context, inputFile, outputFile, audioBitrate, targetFormat, totalDurationMs, onProgress) }

    suspend fun mergeAudioAndVideo(
        context: Context,
        videoFile: File,
        audioFile: File,
        outputFile: File,
        totalDurationMs: Long = 0L,
        onProgress: ((Float) -> Unit)? = null
    ): ExecutionResult = withContext(Dispatchers.IO) { FFmpegAudioOperations.mergeAudioAndVideo(context, videoFile, audioFile, outputFile, totalDurationMs, onProgress) }

    suspend fun mergeVideoCanvasWithTrackAudio(
        context: Context,
        videoFile: File,
        audioFile: File,
        outputFile: File,
        isVideoLoop: Boolean = false,
        totalDurationMs: Long = 0L
    ): ExecutionResult = withContext(Dispatchers.IO) { FFmpegAudioOperations.mergeVideoCanvasWithTrackAudio(context, videoFile, audioFile, outputFile, isVideoLoop, totalDurationMs) }

    suspend fun trimAudioLossless(
        context: Context,
        inputFile: File,
        outputFile: File,
        startMs: Long,
        durationMs: Long
    ): ExecutionResult = withContext(Dispatchers.IO) { FFmpegAudioOperations.trimAudioLossless(context, inputFile, outputFile, startMs, durationMs) }

    suspend fun createBoomerangLoopVideo(
        context: Context,
        inputFile: File,
        outputFile: File,
        maxSourceDurationSec: Float = 10.0f,
        targetFps: Int = 30
    ): ExecutionResult = withContext(Dispatchers.IO) { FFmpegCanvasOperations.createBoomerangLoopVideo(context, inputFile, outputFile, maxSourceDurationSec, targetFps) }

    suspend fun createSeamlessLoopVideo(
        context: Context,
        inputFile: File,
        outputFile: File,
        crossfadeDurationSec: Float = 0.8f,
        targetFps: Int = 30
    ): ExecutionResult = withContext(Dispatchers.IO) { FFmpegCanvasOperations.createSeamlessLoopVideo(context, inputFile, outputFile, crossfadeDurationSec, targetFps) }

    suspend fun optimizeVideoForInstantSync(
        context: Context,
        inputFile: File,
        outputFile: File,
        gopSize: Int = 30,
        targetFps: Int = 30
    ): ExecutionResult = withContext(Dispatchers.IO) { FFmpegCanvasOperations.optimizeVideoForInstantSync(context, inputFile, outputFile, gopSize, targetFps) }

    fun probeVideoDimensions(
        videoFile: File
    ): Pair<Int, Int>? = FFmpegCanvasOperations.probeVideoDimensions(videoFile)

    suspend fun createVerticalCanvasFromHorizontalVideo(
        context: Context,
        inputFile: File,
        outputFile: File,
        targetWidth: Int = 480,
        targetHeight: Int = 854,
        targetFps: Int = 30
    ): ExecutionResult = withContext(Dispatchers.IO) { FFmpegCanvasOperations.createVerticalCanvasFromHorizontalVideo(context, inputFile, outputFile, targetWidth, targetHeight, targetFps) }

    suspend fun stripAudioAndMetadata(
        context: Context,
        inputFile: File,
        outputFile: File
    ): ExecutionResult = withContext(Dispatchers.IO) { FFmpegCanvasOperations.stripAudioAndMetadata(context, inputFile, outputFile) }

    suspend fun processVideoForCanvas(
        context: Context,
        inputFile: File,
        outputFile: File,
        isLoop: Boolean,
        loopStyle: CanvasLoopStyle = CanvasLoopStyle.CROSSFADE,
        preferVerticalCanvasOnHorizontal: Boolean = false
    ): ExecutionResult = withContext(Dispatchers.IO) { FFmpegCanvasOperations.processVideoForCanvas(context, inputFile, outputFile, isLoop, loopStyle, preferVerticalCanvasOnHorizontal) }

}
