package com.example.core.data.importer.mixtape

import android.content.Context
import android.net.Uri
import com.example.core.data.importer.FFmpegNativeEngine
import com.example.core.data.local.AppDatabase
import com.example.core.data.repository.MusicRepository
import com.example.core.data.storage.AppStorageManager
import com.example.debug.AuraDebugManager
import com.example.core.model.AutoRemixPairRecommendation
import com.example.core.model.AutoRemixStyle
import com.example.core.model.MixtapeMetadata
import com.example.core.model.Track
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aura Music - Motor de Auto-Remix y Mashup Inteligente para 2 Canciones Locales.
 *
 * Rol arquitectónico: fachada pública estable; delega la selección del par en
 * AutoRemixMatcher, los grafos FFmpeg en AutoRemixPlanner y el acabado en
 * AutoRemixPostProcessor.
 */
object SmartAutoRemixEngine {

    private const val TAG = "SmartAutoRemixEngine"

    /**
     * Filtra únicamente las canciones locales válidas (excluyendo pistas en streaming temporal).
     */
    fun filterLocalTracks(tracks: List<Track>): List<Track> =
        AutoRemixMatcher.filterLocalTracks(tracks)

    /**
     * Selecciona automáticamente las 2 canciones locales que mejor combinan para un Auto-Remix / Mashup.
     * Si [seedIteration] cambia, rota entre las mejores combinaciones puntuadas para ofrecer variedad al usuario.
     */
    fun findBestMatchingPair(
        tracks: List<Track>,
        seedIteration: Int = 0
    ): AutoRemixPairRecommendation? =
        AutoRemixMatcher.findBestMatchingPair(tracks, seedIteration)

    suspend fun createAutoRemix(
        context: Context,
        trackA: Track,
        trackB: Track,
        customTitle: String,
        style: AutoRemixStyle,
        crossfadeSeconds: Int = 6,
        onProgress: ((Float, String) -> Unit)? = null
    ): Track? = withContext(Dispatchers.IO) {
        if (trackA.isStreamingTrack || trackB.isStreamingTrack) {
            AuraDebugManager.logWarning(TAG, "El Auto-Remix está disponible por ahora solo para música local.")
            return@withContext null
        }

        val storageManager = AppStorageManager(context)
        val tempFiles = mutableListOf<File>()

        try {
            onProgress?.invoke(0.06f, "Inicializando motor FFmpeg nativo...")
            FFmpegNativeEngine.init(context)
            val ffmpegBinFile = FFmpegNativeEngine.getBinaryFile(context)
            if (ffmpegBinFile == null || !ffmpegBinFile.canExecute()) {
                AuraDebugManager.logError(TAG, "FFmpeg nativo no disponible para generar Auto-Remix.")
                return@withContext null
            }

            onProgress?.invoke(0.14f, "Preparando pistas locales: '${trackA.title}' y '${trackB.title}'...")
            val fileA = AutoRemixPlanner.prepareSafeLocalAudioFile(context, trackA, 0, tempFiles)
            val fileB = AutoRemixPlanner.prepareSafeLocalAudioFile(context, trackB, 1, tempFiles)
            if (fileA == null || !fileA.exists() || fileA.length() < 512L ||
                fileB == null || !fileB.exists() || fileB.length() < 512L
            ) {
                AuraDebugManager.logError(
                    TAG,
                    "No se pudieron leer los archivos de audio locales para el Auto-Remix (A=${fileA?.absolutePath}, B=${fileB?.absolutePath})."
                )
                return@withContext null
            }

            val timestamp = System.currentTimeMillis()
            val outputAudioFile = File(storageManager.songsDir, "aura_autoremix_${timestamp}.m4a")
            if (outputAudioFile.exists()) outputAudioFile.delete()
            val ffmpegBin = ffmpegBinFile.absolutePath

            val realDurAMs = AutoRemixPlanner.resolveRealAudioDurationMs(fileA, trackA.durationMs).coerceIn(12_000L, 360_000L)
            val realDurBMs = AutoRemixPlanner.resolveRealAudioDurationMs(fileB, trackB.durationMs).coerceIn(12_000L, 360_000L)

            val (primaryFilterComplex, expectedDurationMs, chapters) = when (style) {
                AutoRemixStyle.VOCAL_MASHUP -> AutoRemixPlanner.buildVocalMashupPlan(trackA, trackB, realDurAMs, realDurBMs, useCompatibleFiltersOnly = false)
                AutoRemixStyle.DJ_ALTERNATING_REMIX -> AutoRemixPlanner.buildAlternatingDjRemixPlan(
                    trackA = trackA,
                    trackB = trackB,
                    durAMs = realDurAMs,
                    durBMs = realDurBMs,
                    crossfadeSeconds = crossfadeSeconds.coerceIn(3, 10),
                    useCompatibleFiltersOnly = false
                )
            }

            val safeInputA = FFmpegNativeEngine.sanitizeFilePath(fileA)
            val safeInputB = FFmpegNativeEngine.sanitizeFilePath(fileB)
            val safeOutput = FFmpegNativeEngine.sanitizeFilePath(outputAudioFile)

            fun buildFfmpegCommand(filterGraph: String): Array<String> = arrayOf(
                ffmpegBin,
                "-y",
                "-hide_banner",
                "-loglevel", "info",
                "-i", safeInputA,
                "-i", safeInputB,
                "-filter_complex", filterGraph,
                "-map", "[outa]",
                "-vn",
                "-c:a", "aac",
                "-b:a", "192k",
                "-ar", "44100",
                "-ac", "2",
                "-movflags", "+faststart",
                safeOutput
            )

            val styleLabel = if (style == AutoRemixStyle.VOCAL_MASHUP) "Mashup Vocal + Base" else "Remix Alternado DJ"
            onProgress?.invoke(0.24f, "Renderizando $styleLabel en estudio...")

            var success = AutoRemixPlanner.executeFFmpeg(
                context = context,
                args = buildFfmpegCommand(primaryFilterComplex),
                totalDurationMs = expectedDurationMs,
                onProgress = { ffProgress ->
                    val mapped = 0.24f + (ffProgress * 0.58f)
                    onProgress?.invoke(
                        mapped.coerceIn(0.24f, 0.82f),
                        "Mezclando $styleLabel (${(ffProgress * 100).toInt()}%)..."
                    )
                }
            )

            // Si el grafo con ecualizadores paramétricos avanzados falló por algún filtro no soportado o formato particular,
            // ejecutamos de inmediato el grafo compatible de alta fidelidad (aformat + highpass/lowpass + atempo + amix/acrossfade).
            if (!success || !outputAudioFile.exists() || outputAudioFile.length() < 1024L) {
                AuraDebugManager.logWarning(
                    TAG,
                    "Reintentando Auto-Remix con grafo acústico universal compatible para '${trackA.title}' × '${trackB.title}'..."
                )
                if (outputAudioFile.exists()) outputAudioFile.delete()
                val (compatFilterComplex, _, _) = when (style) {
                    AutoRemixStyle.VOCAL_MASHUP -> AutoRemixPlanner.buildVocalMashupPlan(trackA, trackB, realDurAMs, realDurBMs, useCompatibleFiltersOnly = true)
                    AutoRemixStyle.DJ_ALTERNATING_REMIX -> AutoRemixPlanner.buildAlternatingDjRemixPlan(
                        trackA = trackA,
                        trackB = trackB,
                        durAMs = realDurAMs,
                        durBMs = realDurBMs,
                        crossfadeSeconds = crossfadeSeconds.coerceIn(3, 10),
                        useCompatibleFiltersOnly = true
                    )
                }
                success = AutoRemixPlanner.executeFFmpeg(
                    context = context,
                    args = buildFfmpegCommand(compatFilterComplex),
                    totalDurationMs = expectedDurationMs,
                    onProgress = { ffProgress ->
                        val mapped = 0.28f + (ffProgress * 0.54f)
                        onProgress?.invoke(
                            mapped.coerceIn(0.28f, 0.82f),
                            "Mezclando $styleLabel (${(ffProgress * 100).toInt()}%)..."
                        )
                    }
                )
            }

            if (!success || !outputAudioFile.exists() || outputAudioFile.length() < 1024L) {
                AuraDebugManager.logError(TAG, "FFmpeg no generó un archivo de Auto-Remix válido.")
                outputAudioFile.delete()
                return@withContext null
            }

            onProgress?.invoke(0.86f, "Generando carátula dividida en WebP sin pérdida...")
            val splitCoverFile = AutoRemixPostProcessor.generateSplitRemixArtwork(trackA, trackB, timestamp, storageManager)

            val defaultRemixTitle = if (style == AutoRemixStyle.VOCAL_MASHUP) {
                "${trackA.title} × ${trackB.title} (Aura Mashup)"
            } else {
                "${trackA.title} × ${trackB.title} (Aura DJ Remix)"
            }
            val titleClean = customTitle.trim().ifBlank { defaultRemixTitle }
            val artistClean = "${trackA.artist} × ${trackB.artist}"

            val actualOutDurationMs = AutoRemixPlanner.resolveRealAudioDurationMs(outputAudioFile, expectedDurationMs)
            val database = AppDatabase.getInstance(context)
            val repository = MusicRepository(database)

            val remixDomainTrack = Track(
                id = 0,
                title = titleClean,
                artist = artistClean,
                album = "Aura Auto-Remixes",
                durationMs = actualOutDurationMs,
                uriString = Uri.fromFile(outputAudioFile).toString(),
                albumArtPath = splitCoverFile?.absolutePath ?: trackA.albumArtPath ?: trackB.albumArtPath,
                videoUri = chapters.firstOrNull { !it.videoUri.isNullOrBlank() }?.videoUri,
                isVideoLoop = chapters.firstOrNull { !it.videoUri.isNullOrBlank() }?.isVideoLoop ?: false,
                mimeType = "audio/mp4",
                dateAdded = timestamp,
                folderName = "Auto-Remixes",
                fileSizeFormatted = "${(outputAudioFile.length() / (1024 * 1024)).coerceAtLeast(1)} MB"
            )

            val insertedTrack = repository.insertCustomTrack(context, remixDomainTrack)

            onProgress?.invoke(0.94f, "Sincronizando letras .LRC y capítulos reactivos...")
            AutoRemixPostProcessor.combineLyricsForRemix(storageManager, insertedTrack.id, chapters)

            val metadata = MixtapeMetadata(
                mixtapeId = insertedTrack.id,
                title = titleClean,
                totalDurationMs = actualOutDurationMs,
                crossfadeSeconds = crossfadeSeconds.coerceIn(3, 10),
                chapters = chapters
            )
            AutoRemixPostProcessor.saveRemixMetadata(storageManager, metadata)

            onProgress?.invoke(1.0f, "¡Auto-Remix creado con éxito!")
            AuraDebugManager.logInfo(TAG, "Auto-Remix '${insertedTrack.title}' creado exitosamente (${style.name}).")
            insertedTrack
        } catch (e: Exception) {
            AuraDebugManager.logError(TAG, "Error creando Auto-Remix: ${e.message}", e)
            null
        } finally {
            tempFiles.forEach { file ->
                try { file.delete() } catch (_: Throwable) {}
            }
        }
    }
}
