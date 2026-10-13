package com.example.core.data.importer.mixtape

import android.content.Context
import android.net.Uri
import com.example.core.data.importer.FFmpegNativeEngine
import com.example.core.data.local.AppDatabase
import com.example.core.data.repository.MusicRepository
import com.example.core.data.storage.AppStorageManager
import com.example.debug.AuraDebugManager
import com.example.core.model.MixtapeChapter
import com.example.core.model.MixtapeMetadata
import com.example.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Orquestador de creación de mixtapes: prepara, fusiona, ilustra y registra.
 * Rol arquitectónico: pieza de MixtapeEngine (fachada pública intacta).
 */
internal object MixtapeCreator {
    private const val TAG = "MixtapeEngine"

    suspend fun create(
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
                val preparedFile = MixtapeAudioPrep.prepareInput(context, track, index, tempFiles)
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
            val expectedDurationMs = MixtapeChapters.expectedDurationMs(tracks, safeCrossfadeSec)
            val success = MixtapeFfmpegRunner.run(context, argsList.toTypedArray(), expectedDurationMs) { p ->
                val scaled = 0.25f + (p * 0.50f)
                onProgress?.invoke(scaled, "Fusionando canciones con crossfade (${(p * 100).toInt()}%)...")
            }

            if (!success || !outputAudioFile.exists() || outputAudioFile.length() < 10_000L) {
                AuraDebugManager.logError(TAG, "Fallo al procesar el mixtape con FFmpeg.")
                return@withContext null
            }

            onProgress?.invoke(0.80f, "Generando portada en collage...")

            // 5. Generar carátula oficial en collage WebP a partir de las pistas seleccionadas
            val collageArtworkFile = MixtapeArtwork.collageArtwork(context, tracks, timestamp, storageManager)

            onProgress?.invoke(0.88f, "Calculando capítulos y sincronizando letras...")

            // 6. Calcular marcas de capítulos para conmutación reactiva en tiempo real
            val chapters = MixtapeChapters.computeChapters(tracks, safeCrossfadeSec)

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
            MixtapeLyrics.combineLyrics(storageManager, insertedTrack.id, tracks, chapters)

            // 9. Guardar metadatos JSON completos del mixtape
            val metadata = MixtapeMetadata(
                mixtapeId = insertedTrack.id,
                title = titleClean,
                totalDurationMs = expectedDurationMs,
                crossfadeSeconds = safeCrossfadeSec,
                chapters = chapters
            )
            MixtapeMetadataStore.saveMetadata(storageManager, metadata)

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
}
