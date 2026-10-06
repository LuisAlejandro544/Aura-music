package com.example.data.importer.mixtape

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import com.example.data.importer.FFmpegNativeEngine
import com.example.data.local.AppDatabase
import com.example.data.repository.MusicRepository
import com.example.data.storage.AppStorageManager
import com.example.debug.AuraDebugManager
import com.example.model.MixtapeChapter
import com.example.model.MixtapeMetadata
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.regex.Pattern

/**
 * Motor nativo de creación y gestión de Mixtapes Continuos con FFmpeg en Aura Music.
 *
 * Características:
 * 1. Fusión de N canciones con fundido cruzado continuo (acrossfade en FFmpeg) en una sola pista M4A.
 * 2. Normalización de formato acústico (44.1 kHz, estéreo, coma flotante) previo al crossfade para cero ruidos.
 * 3. Cálculo matemático exacto de marcas de tiempo de capítulos para conmutar carátulas y Video Canvas en vivo.
 * 4. Concatenación inteligente de letras sincronizadas (.LRC) con ajuste de desfase temporal por capítulo.
 * 5. Generación de portada oficial en collage WebP sin pérdida a partir de las carátulas de las pistas unidas.
 * 6. Persistencia de metadatos de capítulos en metadata/mixtape_{id}.json.
 */
object MixtapeEngine {

    private const val TAG = "MixtapeEngine"
    private val LRC_TIMESTAMP_PATTERN = Pattern.compile("^\\[(\\d{1,3}):(\\d{2})[.:](\\d{2,3})\\](.*)")

    /**
     * Fusiona una lista de canciones en una pista continua con fundido cruzado.
     */
    suspend fun createMixtape(
        context: Context,
        tracks: List<Track>,
        mixtapeTitle: String,
        crossfadeSeconds: Int = 5,
        onProgress: ((Float, String) -> Unit)? = null
    ): Track? = withContext(Dispatchers.IO) {
        if (tracks.size < 2) {
            AuraDebugManager.logWarning(TAG, "Se requieren al menos 2 canciones para crear un Mixtape.")
            return@withContext null
        }

        FFmpegNativeEngine.init(context)
        val ffmpegBin = FFmpegNativeEngine.getBinaryFile(context)
        if (ffmpegBin == null) {
            AuraDebugManager.logError(TAG, "FFmpeg nativo no disponible para generar Mixtape.")
            return@withContext null
        }

        val storageManager = AppStorageManager(context)
        val timestamp = System.currentTimeMillis()
        val tempFiles = mutableListOf<File>()

        try {
            onProgress?.invoke(0.05f, "Preparando archivos de audio...")

            // 1. Resolver y preparar los archivos de entrada locales
            val inputFiles = mutableListOf<File>()
            for ((index, track) in tracks.withIndex() ) {
                val preparedFile = prepareAudioInputFile(context, track, index, tempFiles)
                if (preparedFile == null || !preparedFile.exists() || preparedFile.length() < 1024L) {
                    AuraDebugManager.logError(TAG, "No se pudo leer el audio para: ${track.title}")
                    return@withContext null
                }
                inputFiles.add(preparedFile)
            }

            // 2. Determinar duración segura del crossfade
            val minDurationMs = tracks.minOfOrNull { it.durationMs.takeIf { d -> d > 0 } ?: 30_000L } ?: 30_000L
            val maxAllowedCrossfadeSec = ((minDurationMs / 2000L).coerceAtLeast(1L)).toInt()
            val safeCrossfadeSec = crossfadeSeconds.coerceIn(1, maxAllowedCrossfadeSec.coerceAtMost(12))

            onProgress?.invoke(0.15f, "Configurando mezcla continua en FFmpeg...")

            // 3. Construir filter_complex de FFmpeg con aformat y acrossfade encadenado
            val n = inputFiles.size
            val filterBuilder = StringBuilder()

            // Pre-formatear cada flujo a 44100Hz estéreo para evitar desajustes
            for (i in 0 until n) {
                filterBuilder.append("[$i:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo[s$i];")
            }

            // Encadenar acrossfade
            if (n == 2) {
                filterBuilder.append("[s0][s1]acrossfade=d=$safeCrossfadeSec:c1=tri:c2=tri[aout]")
            } else {
                filterBuilder.append("[s0][s1]acrossfade=d=$safeCrossfadeSec:c1=tri:c2=tri[a1];")
                for (i in 2 until n) {
                    val prevLabel = "a${i - 1}"
                    val outLabel = if (i == n - 1) "aout" else "a$i"
                    filterBuilder.append("[$prevLabel][s$i]acrossfade=d=$safeCrossfadeSec:c1=tri:c2=tri[$outLabel]")
                    if (i < n - 1) filterBuilder.append(";")
                }
            }

            // Archivo de salida M4A
            val outputAudioFile = File(storageManager.songsDir, "mixtape_${timestamp}.m4a")
            if (outputAudioFile.exists()) outputAudioFile.delete()

            // Argumentos de FFmpeg
            val argsList = mutableListOf<String>()
            argsList.add(ffmpegBin.absolutePath)
            argsList.add("-y")

            for (inputFile in inputFiles) {
                argsList.add("-i")
                argsList.add(FFmpegNativeEngine.sanitizeFilePath(inputFile))
            }

            argsList.add("-filter_complex")
            argsList.add(filterBuilder.toString())
            argsList.add("-map")
            argsList.add("[aout]")
            argsList.add("-c:a")
            argsList.add("aac")
            argsList.add("-b:a")
            argsList.add("256k")
            argsList.add("-movflags")
            argsList.add("+faststart")
            argsList.add(FFmpegNativeEngine.sanitizeFilePath(outputAudioFile))

            onProgress?.invoke(0.25f, "Fusionando canciones con crossfade...")

            // 4. Ejecutar FFmpeg y monitorear avance
            val expectedDurationMs = calculateExpectedDurationMs(tracks, safeCrossfadeSec)
            val success = executeFFmpeg(context, argsList.toTypedArray(), expectedDurationMs) { p ->
                val scaled = 0.25f + (p * 0.50f)
                onProgress?.invoke(scaled, "Fusionando canciones con crossfade (${(p * 100).toInt()}%)...")
            }

            if (!success || !outputAudioFile.exists() || outputAudioFile.length() < 10_000L) {
                AuraDebugManager.logError(TAG, "Fallo al procesar el mixtape con FFmpeg.")
                return@withContext null
            }

            onProgress?.invoke(0.80f, "Generando portada en collage...")

            // 5. Generar carátula oficial en collage WebP a partir de las pistas seleccionadas
            val collageArtworkFile = generateCollageArtwork(context, tracks, timestamp, storageManager)

            onProgress?.invoke(0.88f, "Calculando capítulos y sincronizando letras...")

            // 6. Calcular marcas de capítulos para conmutación reactiva en tiempo real
            val chapters = computeChapters(tracks, safeCrossfadeSec)

            // 7. Guardar en Room Database
            val titleClean = mixtapeTitle.trim().ifBlank { "Aura Continuous Mix" }
            val database = AppDatabase.getInstance(context)
            val repository = MusicRepository(database)

            val mixtapeDomainTrack = Track(
                id = 0,
                title = titleClean,
                artist = "Varios Artistas • Aura Continuous Mix",
                album = "Aura Mixtapes",
                durationMs = expectedDurationMs,
                uriString = Uri.fromFile(outputAudioFile).toString(),
                albumArtPath = collageArtworkFile?.absolutePath,
                videoUri = chapters.firstOrNull { !it.videoUri.isNullOrBlank() }?.videoUri,
                isVideoLoop = chapters.firstOrNull { !it.videoUri.isNullOrBlank() }?.isVideoLoop ?: false,
                mimeType = "audio/mp4",
                dateAdded = timestamp,
                folderName = "Mixtapes",
                fileSizeFormatted = "${outputAudioFile.length() / (1024 * 1024)} MB"
            )

            val insertedTrack = repository.insertCustomTrack(context, mixtapeDomainTrack)

            // 8. Concatenar letras sincronizadas con offset temporal
            combineLyricsForMixtape(storageManager, insertedTrack.id, tracks, chapters)

            // 9. Guardar metadatos JSON completos del mixtape
            val metadata = MixtapeMetadata(
                mixtapeId = insertedTrack.id,
                title = titleClean,
                totalDurationMs = expectedDurationMs,
                crossfadeSeconds = safeCrossfadeSec,
                chapters = chapters
            )
            saveMixtapeMetadata(storageManager, metadata)

            onProgress?.invoke(1.0f, "¡Mixtape creado con éxito!")
            AuraDebugManager.logInfo(TAG, "Mixtape '${insertedTrack.title}' (${insertedTrack.id}) creado exitosamente con ${chapters.size} capítulos.")
            insertedTrack
        } catch (e: Exception) {
            AuraDebugManager.logError(TAG, "Excepción creando Mixtape: ${e.message}")
            null
        } finally {
            // Limpieza de archivos temporales
            tempFiles.forEach { file ->
                try { file.delete() } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Prepara un archivo de audio local seguro para FFmpeg a partir de una pista.
     */
    private fun prepareAudioInputFile(
        context: Context,
        track: Track,
        index: Int,
        tempFiles: MutableList<File>
    ): File? {
        val uriStr = track.uriString
        if (uriStr.startsWith("/")) {
            val f = File(uriStr)
            if (f.exists() && f.canRead()) return f
        } else if (uriStr.startsWith("file://")) {
            val f = File(Uri.parse(uriStr).path ?: "")
            if (f.exists() && f.canRead()) return f
        }

        // Si es content:// o no se pudo leer directamente, copiar a temporal en cache
        return try {
            val uri = Uri.parse(uriStr)
            val tempFile = File(context.cacheDir, "mixtape_in_${index}_${System.currentTimeMillis()}.tmp")
            tempFiles.add(tempFile)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (tempFile.exists() && tempFile.length() > 0) tempFile else null
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Error preparando audio de '${track.title}': ${e.message}")
            null
        }
    }

    /**
     * Calcula la duración esperada del mixtape restando los fundidos cruzados.
     */
    private fun calculateExpectedDurationMs(tracks: List<Track>, crossfadeSec: Int): Long {
        val sumDurations = tracks.sumOf { it.durationMs.coerceAtLeast(10_000L) }
        val crossfadeOverlapTotal = (tracks.size - 1) * (crossfadeSec * 1000L)
        return (sumDurations - crossfadeOverlapTotal).coerceAtLeast(10_000L)
    }

    /**
     * Calcula las marcas temporales de cada capítulo en la pista continua.
     */
    private fun computeChapters(tracks: List<Track>, crossfadeSec: Int): List<MixtapeChapter> {
        val chapters = mutableListOf<MixtapeChapter>()
        var currentOffsetMs = 0L
        val crossfadeMs = crossfadeSec * 1000L

        for ((index, track) in tracks.withIndex()) {
            val trackDur = track.durationMs.coerceAtLeast(10_000L)
            val startMs = currentOffsetMs
            val endMs = if (index == tracks.size - 1) {
                startMs + trackDur
            } else {
                startMs + trackDur
            }

            chapters.add(
                MixtapeChapter(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    albumArtPath = track.albumArtPath,
                    videoUri = track.videoUri,
                    isVideoLoop = track.isVideoLoop,
                    startMs = startMs,
                    endMs = endMs
                )
            )

            // El siguiente capítulo comienza antes de que termine el actual por la duración del fundido cruzado
            currentOffsetMs = (endMs - crossfadeMs).coerceAtLeast(startMs + 1000L)
        }

        return chapters
    }

    /**
     * Genera la portada oficial en collage WebP combinando de 1 a 4 carátulas de las pistas seleccionadas.
     */
    private suspend fun generateCollageArtwork(
        context: Context,
        tracks: List<Track>,
        timestamp: Long,
        storageManager: AppStorageManager
    ): File? {
        return try {
            val distinctArtTracks = tracks.filter { !it.albumArtPath.isNullOrBlank() && File(it.albumArtPath).exists() }
                .take(4)

            val targetSize = 512
            val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            if (distinctArtTracks.isEmpty()) {
                // Fondo oscuro con gradiente
                paint.color = android.graphics.Color.DKGRAY
                canvas.drawRect(0f, 0f, targetSize.toFloat(), targetSize.toFloat(), paint)
            } else {
                val decodedBitmaps = distinctArtTracks.mapNotNull { track ->
                    try {
                        BitmapFactory.decodeFile(track.albumArtPath)
                    } catch (_: Throwable) { null }
                }

                when (decodedBitmaps.size) {
                    1 -> {
                        canvas.drawBitmap(decodedBitmaps[0], null, Rect(0, 0, targetSize, targetSize), paint)
                    }
                    2 -> {
                        canvas.drawBitmap(decodedBitmaps[0], null, Rect(0, 0, targetSize / 2, targetSize), paint)
                        canvas.drawBitmap(decodedBitmaps[1], null, Rect(targetSize / 2, 0, targetSize, targetSize), paint)
                    }
                    3 -> {
                        canvas.drawBitmap(decodedBitmaps[0], null, Rect(0, 0, targetSize / 2, targetSize / 2), paint)
                        canvas.drawBitmap(decodedBitmaps[1], null, Rect(targetSize / 2, 0, targetSize, targetSize / 2), paint)
                        canvas.drawBitmap(decodedBitmaps[2], null, Rect(0, targetSize / 2, targetSize, targetSize), paint)
                    }
                    else -> {
                        // 4 fotos en cuadrícula 2x2
                        canvas.drawBitmap(decodedBitmaps[0], null, Rect(0, 0, targetSize / 2, targetSize / 2), paint)
                        canvas.drawBitmap(decodedBitmaps[1], null, Rect(targetSize / 2, 0, targetSize, targetSize / 2), paint)
                        canvas.drawBitmap(decodedBitmaps[2], null, Rect(0, targetSize / 2, targetSize / 2, targetSize), paint)
                        canvas.drawBitmap(decodedBitmaps[3], null, Rect(targetSize / 2, targetSize / 2, targetSize, targetSize), paint)
                    }
                }

                decodedBitmaps.forEach { it.recycle() }
            }

            val collageFile = storageManager.saveCoverAsWebp("mixtape_$timestamp", bitmap)
            bitmap.recycle()
            collageFile
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Error generando carátula de collage para mixtape: ${e.message}")
            null
        }
    }

    /**
     * Concatena las letras sincronizadas .LRC de cada canción sumando el offset en milisegundos del capítulo.
     */
    private suspend fun combineLyricsForMixtape(
        storageManager: AppStorageManager,
        mixtapeTrackId: Long,
        tracks: List<Track>,
        chapters: List<MixtapeChapter>
    ) {
        val masterLyricsBuilder = StringBuilder()
        masterLyricsBuilder.append("[ti:Aura Continuous Mix]\n")
        masterLyricsBuilder.append("[ar:Varios Artistas]\n")
        masterLyricsBuilder.append("[by:Aura Music Mixtape Maker]\n\n")

        var hasAnyLyrics = false

        for ((index, chapter) in chapters.withIndex()) {
            val originalLyrics = storageManager.getLyrics(chapter.trackId)
            masterLyricsBuilder.append("[${formatLrcTime(chapter.startMs)}] --- ${chapter.title} • ${chapter.artist} ---\n")

            if (!originalLyrics.isNullOrBlank()) {
                hasAnyLyrics = true
                val lines = originalLyrics.lines()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isBlank() || trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") || trimmed.startsWith("[al:")) continue

                    val matcher = LRC_TIMESTAMP_PATTERN.matcher(trimmed)
                    if (matcher.find()) {
                        val min = matcher.group(1)?.toLongOrNull() ?: 0L
                        val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                        val centisStr = matcher.group(3) ?: "00"
                        val centis = if (centisStr.length == 3) (centisStr.toLongOrNull() ?: 0L) / 10L else (centisStr.toLongOrNull() ?: 0L)
                        val text = matcher.group(4) ?: ""

                        val lineMs = (min * 60_000L) + (sec * 1000L) + (centis * 10L)
                        val adjustedMs = chapter.startMs + lineMs
                        masterLyricsBuilder.append("[${formatLrcTime(adjustedMs)}]$text\n")
                    }
                }
            }
            masterLyricsBuilder.append("\n")
        }

        if (hasAnyLyrics) {
            storageManager.saveLyrics(mixtapeTrackId, masterLyricsBuilder.toString())
        }
    }

    private fun formatLrcTime(ms: Long): String {
        val totalSec = ms / 1000L
        val min = totalSec / 60L
        val sec = totalSec % 60L
        val centis = (ms % 1000L) / 10L
        return String.format(java.util.Locale.US, "%02d:%02d.%02d", min, sec, centis)
    }

    /**
     * Guarda los metadatos JSON completos del mixtape en metadata/mixtape_{id}.json.
     */
    private fun saveMixtapeMetadata(storageManager: AppStorageManager, metadata: MixtapeMetadata) {
        try {
            val file = File(storageManager.metadataDir, "mixtape_${metadata.mixtapeId}.json")
            file.writeText(metadata.toJsonObject().toString(2))
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Error guardando metadatos JSON del mixtape: ${e.message}")
        }
    }

    /**
     * Lee los metadatos de un mixtape si la pista corresponde a una sesión continua.
     */
    fun getMixtapeMetadata(context: Context, trackId: Long): MixtapeMetadata? {
        val storageManager = AppStorageManager(context)
        val file = File(storageManager.metadataDir, "mixtape_${trackId}.json")
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            MixtapeMetadata.fromJsonObject(json)
        } catch (e: Exception) {
            null
        }
    }

    private fun executeFFmpeg(
        context: Context,
        args: Array<String>,
        totalDurationMs: Long,
        onProgress: ((Float) -> Unit)?
    ): Boolean {
        val timePattern = Pattern.compile("time=(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})")
        val logBuilder = StringBuilder()

        return try {
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
                        val matcher = timePattern.matcher(currentLine)
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
            if (exitCode == 0) {
                onProgress?.invoke(1.0f)
                true
            } else {
                AuraDebugManager.logWarning(TAG, "FFmpeg falló al generar Mixtape (código $exitCode). Log: ${logBuilder.takeLast(300)}")
                false
            }
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Error ejecutando proceso FFmpeg para Mixtape: ${e.message}")
            false
        }
    }
}
