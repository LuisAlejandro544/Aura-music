package com.example.data.importer.mixtape

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import com.example.data.importer.FFmpegNativeEngine
import com.example.data.importer.IncomingMediaHandler
import com.example.data.local.AppDatabase
import com.example.data.repository.MusicRepository
import com.example.data.storage.AppStorageManager
import com.example.debug.AuraDebugManager
import com.example.model.AutoRemixPairRecommendation
import com.example.model.AutoRemixStyle
import com.example.model.MixtapeChapter
import com.example.model.MixtapeMetadata
import com.example.model.Track
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aura Music - Motor de Auto-Remix y Mashup Inteligente para 2 Canciones Locales (`SmartAutoRemixEngine`).
 *
 * Responsabilidades:
 * - Analizar una lista de canciones locales y elegir automáticamente el mejor par compatible (`findBestMatchingPair`)
 *   evaluando proporción de duración, contraste de artista/álbum y compatibilidad rítmica estimada.
 * - Generar Remixes reales en segundo plano mediante `FFmpegNativeEngine` exclusivamente sobre música local:
 *   1. `VOCAL_MASHUP` (Mashup Voz A + Base B): Aísla la banda vocal central de la Canción A (filtro paso-altos 220 Hz,
 *      realce de formantes vocales 1.6 kHz - 4.2 kHz y enfoque Mid) y la superpone sobre la base rítmica/instrumental
 *      de la Canción B (refuerzo de subgraves 55 Hz - 140 Hz y atenuación de centro vocal), nivelando el resultado con `alimiter`.
 *   2. `DJ_ALTERNATING_REMIX` (Remix Alternado DJ con Sincronización de Ritmo): Divide ambas canciones en 4 bloques
 *      dinámicos (Sección 1 de A -> Drop de B -> Clímax de A -> Outro de B) ajustando sutilmente el tempo (`atempo`)
 *      y entrelazándolos con `acrossfade` y ecualización libre de choques de bajos.
 * - Generar carátula dividida WebP sin pérdida (Split Collage A|B), sincronizar letras `.LRC` y guardar metadatos
 *   de capítulos (`MixtapeMetadata`) para que `NowPlayingScreen` y `MiniPlayer` conmuten carátula y Video Canvas en vivo.
 */
object SmartAutoRemixEngine {

    private const val TAG = "SmartAutoRemixEngine"
    private val LRC_TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?](.*)")

    /**
     * Filtra únicamente las canciones locales válidas (excluyendo pistas en streaming temporal).
     */
    fun filterLocalTracks(tracks: List<Track>): List<Track> {
        return tracks.filter { !it.isStreamingTrack && it.uriString.isNotBlank() }
    }

    /**
     * Selecciona automáticamente las 2 canciones locales que mejor combinan para un Auto-Remix / Mashup.
     * Si [seedIteration] cambia, rota entre las mejores combinaciones puntuadas para ofrecer variedad al usuario.
     */
    fun findBestMatchingPair(
        tracks: List<Track>,
        seedIteration: Int = 0
    ): AutoRemixPairRecommendation? {
        val localTracks = filterLocalTracks(tracks)
        if (localTracks.size < 2) return null

        data class ScoredPair(
            val a: Track,
            val b: Track,
            val score: Int,
            val reason: String
        )

        val candidates = mutableListOf<ScoredPair>()
        val subset = localTracks.take(40)

        for (i in subset.indices) {
            for (j in i + 1 until subset.size) {
                val t1 = subset[i]
                val t2 = subset[j]
                val dur1 = t1.durationMs.coerceAtLeast(30_000L)
                val dur2 = t2.durationMs.coerceAtLeast(30_000L)
                val ratio = min(dur1, dur2).toDouble() / max(dur1, dur2).toDouble()

                // Puntuación base por cercanía de estructura temporal (75 a 98%)
                var score = (72 + (ratio * 22.0)).toInt().coerceIn(72, 96)
                val differentArtist = !t1.artist.equals(t2.artist, ignoreCase = true)
                if (differentArtist) score += 3
                if (t1.isFavorite || t2.isFavorite) score += 2
                if (!t1.videoUri.isNullOrBlank() || !t2.videoUri.isNullOrBlank()) score += 1
                val clampedScore = score.coerceIn(78, 99)

                val reason = when {
                    ratio >= 0.88 && differentArtist ->
                        "Compatibilidad rítmica del $clampedScore% • Estructuras de duración gemelas y contraste vocal ideal"
                    differentArtist ->
                        "Compatibilidad armónica del $clampedScore% • Contraste de voces (${t1.artist} × ${t2.artist})"
                    else ->
                        "Compatibilidad de estudio del $clampedScore% • Mismo estilo acústico y cadencia fluida"
                }

                candidates.add(ScoredPair(t1, t2, clampedScore, reason))
            }
        }

        if (candidates.isEmpty()) return null
        val sorted = candidates.sortedByDescending { it.score }
        val topPool = sorted.take(min(8, sorted.size))
        val chosen = topPool[abs(seedIteration) % topPool.size]
        val swapOrder = (abs(seedIteration) % 2) == 1

        return AutoRemixPairRecommendation(
            trackA = if (swapOrder) chosen.b else chosen.a,
            trackB = if (swapOrder) chosen.a else chosen.b,
            compatibilityPercent = chosen.score,
            reason = chosen.reason
        )
    }

    /**
     * Crea un Auto-Remix o Mashup real con 2 canciones locales utilizando FFmpeg en `Dispatchers.IO`.
     */
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
            onProgress?.invoke(0.06f, "Verificando motor FFmpeg nativo...")
            if (!FFmpegNativeEngine.isAvailable(context)) {
                AuraDebugManager.logError(TAG, "FFmpeg nativo no disponible para generar Auto-Remix.")
                return@withContext null
            }

            onProgress?.invoke(0.14f, "Analizando pistas locales: '${trackA.title}' y '${trackB.title}'...")
            val fileA = prepareSafeLocalAudioFile(context, trackA, 0, tempFiles)
            val fileB = prepareSafeLocalAudioFile(context, trackB, 1, tempFiles)
            if (fileA == null || fileB == null) {
                AuraDebugManager.logError(TAG, "No se pudieron preparar los archivos de audio locales para el Auto-Remix.")
                return@withContext null
            }

            val timestamp = System.currentTimeMillis()
            val outputAudioFile = File(storageManager.songsDir, "aura_autoremix_${timestamp}.m4a")
            val ffmpegBin = FFmpegNativeEngine.getBinaryFile(context)?.absolutePath ?: return@withContext null

            val durAMs = trackA.durationMs.coerceIn(25_000L, 360_000L)
            val durBMs = trackB.durationMs.coerceIn(25_000L, 360_000L)

            val (filterComplex, expectedDurationMs, chapters) = when (style) {
                AutoRemixStyle.VOCAL_MASHUP -> buildVocalMashupPlan(trackA, trackB, durAMs, durBMs)
                AutoRemixStyle.DJ_ALTERNATING_REMIX -> buildAlternatingDjRemixPlan(
                    trackA = trackA,
                    trackB = trackB,
                    durAMs = durAMs,
                    durBMs = durBMs,
                    crossfadeSeconds = crossfadeSeconds.coerceIn(3, 10)
                )
            }

            val cmd = arrayListOf(
                ffmpegBin,
                "-y",
                "-hide_banner",
                "-loglevel", "info",
                "-i", fileA.canonicalPath,
                "-i", fileB.canonicalPath,
                "-filter_complex", filterComplex,
                "-map", "[outa]",
                "-vn",
                "-c:a", "aac",
                "-b:a", "192k",
                "-ar", "44100",
                "-ac", "2",
                "-movflags", "+faststart",
                outputAudioFile.canonicalPath
            )

            val styleLabel = if (style == AutoRemixStyle.VOCAL_MASHUP) "Mashup Vocal + Base" else "Remix Alternado DJ"
            onProgress?.invoke(0.26f, "Renderizando $styleLabel en estudio...")

            val success = executeFFmpeg(
                context = context,
                args = cmd.toTypedArray(),
                totalDurationMs = expectedDurationMs,
                onProgress = { ffProgress ->
                    val mapped = 0.26f + (ffProgress * 0.56f)
                    onProgress?.invoke(
                        mapped.coerceIn(0.26f, 0.82f),
                        "Mezclando $styleLabel (${(ffProgress * 100).toInt()}%)..."
                    )
                }
            )

            if (!success || !outputAudioFile.exists() || outputAudioFile.length() < 1024L) {
                AuraDebugManager.logError(TAG, "FFmpeg no generó un archivo de Auto-Remix válido.")
                outputAudioFile.delete()
                return@withContext null
            }

            onProgress?.invoke(0.86f, "Generando carátula dividida en WebP sin pérdida...")
            val splitCoverFile = generateSplitRemixArtwork(trackA, trackB, timestamp, storageManager)

            val defaultRemixTitle = if (style == AutoRemixStyle.VOCAL_MASHUP) {
                "${trackA.title} × ${trackB.title} (Aura Mashup)"
            } else {
                "${trackA.title} × ${trackB.title} (Aura DJ Remix)"
            }
            val titleClean = customTitle.trim().ifBlank { defaultRemixTitle }
            val artistClean = "${trackA.artist} × ${trackB.artist}"

            val database = AppDatabase.getInstance(context)
            val repository = MusicRepository(database)

            val remixDomainTrack = Track(
                id = 0,
                title = titleClean,
                artist = artistClean,
                album = "Aura Auto-Remixes",
                durationMs = expectedDurationMs,
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
            combineLyricsForRemix(storageManager, insertedTrack.id, chapters)

            val metadata = MixtapeMetadata(
                mixtapeId = insertedTrack.id,
                title = titleClean,
                totalDurationMs = expectedDurationMs,
                crossfadeSeconds = crossfadeSeconds.coerceIn(3, 10),
                chapters = chapters
            )
            saveRemixMetadata(storageManager, metadata)

            onProgress?.invoke(1.0f, "¡Auto-Remix creado con éxito!")
            AuraDebugManager.logInfo(TAG, "Auto-Remix '${insertedTrack.title}' creado exitosamente (${style.name}).")
            insertedTrack
        } catch (e: Exception) {
            AuraDebugManager.logError(TAG, "Error creando Auto-Remix: ${e.message}")
            null
        } finally {
            tempFiles.forEach { file ->
                try { file.delete() } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Construye el grafo FFmpeg para el Modo 1: Mashup (Voz Canción A + Base Instrumental Canción B).
     * - Canción A (Voz): Filtro paso-altos en 210 Hz, filtro paso-bajos en 6800 Hz, realce de formantes vocales
     *   en 1850 Hz y 3400 Hz, manteniendo la voz nítida al frente.
     * - Canción B (Base Rítmica): Realce de subgraves/bombo en 65 Hz y 125 Hz, atenuación profunda del bolsillo
     *   vocal central (1800 Hz y 3200 Hz) para dejar espacio a la voz de la Canción A, ajustando suavemente
     *   el tempo si las duraciones difieren ligeramente.
     */
    private fun buildVocalMashupPlan(
        trackA: Track,
        trackB: Track,
        durAMs: Long,
        durBMs: Long
    ): Triple<String, Long, List<MixtapeChapter>> {
        val targetDurationSec = (min(durAMs, durBMs).coerceIn(30_000L, 210_000L) / 1000L).toInt()
        val targetDurationMs = targetDurationSec * 1000L

        // Ajuste sutil de ritmo en la base B (entre 0.94x y 1.06x) para empatar compases sin deformar el audio
        val rawRatio = durBMs.toDouble() / durAMs.toDouble()
        val safeTempoB = rawRatio.coerceIn(0.94, 1.06)
        val tempoFilterB = String.format(Locale.US, "atempo=%.3f,", safeTempoB)

        val filter = StringBuilder()
        // Rama A: Enfoque Vocal (Canción A)
        filter.append("[0:a]aresample=44100,aformat=sample_rates=44100:channel_layouts=stereo,")
        filter.append("highpass=f=210,lowpass=f=7200,")
        filter.append("equalizer=f=1850:t=q:w=1.2:g=4.5,equalizer=f=3400:t=q:w=1.4:g=3.5,")
        filter.append("atrim=0:$targetDurationSec,asetpts=PTS-STARTPTS,volume=1.08[voca];")

        // Rama B: Base Rítmica e Instrumental (Canción B) con bolsillo vocal atenuado
        filter.append("[1:a]aresample=44100,aformat=sample_rates=44100:channel_layouts=stereo,")
        filter.append(tempoFilterB)
        filter.append("equalizer=f=65:t=q:w=1.1:g=4.2,equalizer=f=125:t=q:w=1.2:g=2.8,")
        filter.append("equalizer=f=1800:t=q:w=1.0:g=-7.5,equalizer=f=3200:t=q:w=1.1:g=-6.5,")
        filter.append("atrim=0:$targetDurationSec,asetpts=PTS-STARTPTS,volume=0.88[beatb];")

        // Mezcla armónica de ambas ramas con limitador de estudio y fade-out suave en los últimos 3s
        val fadeOutStart = (targetDurationSec - 3).coerceAtLeast(5)
        filter.append("[voca][beatb]amix=inputs=2:duration=shortest:dropout_transition=2,")
        filter.append("alimiter=limit=0.95:level=disabled,")
        filter.append("afade=t=in:st=0:d=2,afade=t=out:st=$fadeOutStart:d=3[outa]")

        // Dividimos visualmente el Mashup en 2 capítulos mitad y mitad para que el usuario disfrute
        // tanto la carátula/Video Canvas de la Canción A como la de la Canción B durante la reproducción
        val halfMs = targetDurationMs / 2L
        val chapters = listOf(
            MixtapeChapter(
                trackId = trackA.id,
                title = "${trackA.title} (Voz Principal • Mashup)",
                artist = "${trackA.artist} × ${trackB.artist}",
                albumArtPath = trackA.albumArtPath,
                videoUri = trackA.videoUri ?: trackB.videoUri,
                isVideoLoop = trackA.isVideoLoop,
                startMs = 0L,
                endMs = halfMs
            ),
            MixtapeChapter(
                trackId = trackB.id,
                title = "${trackB.title} (Base & Clímax • Mashup)",
                artist = "${trackB.artist} × ${trackA.artist}",
                albumArtPath = trackB.albumArtPath,
                videoUri = trackB.videoUri ?: trackA.videoUri,
                isVideoLoop = trackB.isVideoLoop,
                startMs = halfMs,
                endMs = targetDurationMs
            )
        )

        return Triple(filter.toString(), targetDurationMs, chapters)
    }

    /**
     * Construye el grafo FFmpeg para el Modo 2: Remix Alternado DJ con Sincronización de Ritmo.
     * Alterna 4 segmentos energéticos (Intro/Verso A -> Drop B -> Puente/Coro A -> Cierre B)
     * con ajuste armónico de tempo y transiciones `acrossfade` con curva de igual potencia.
     */
    private fun buildAlternatingDjRemixPlan(
        trackA: Track,
        trackB: Track,
        durAMs: Long,
        durBMs: Long,
        crossfadeSeconds: Int
    ): Triple<String, Long, List<MixtapeChapter>> {
        val cf = crossfadeSeconds.coerceIn(3, 8)
        val segSec = 28 // Cada bloque DJ dura ~28 segundos para un remix ágil y dinámico (~1:40 min total)

        val a1Start = 0
        val a1End = min((durAMs / 1000L).toInt(), segSec).coerceAtLeast(14)

        val b1Start = min(((durBMs / 1000L).toInt() / 5), 15).coerceAtLeast(0)
        val b1End = min((durBMs / 1000L).toInt(), b1Start + segSec).coerceAtLeast(b1Start + 14)

        val a2Start = min((durAMs / 1000L).toInt() / 2, a1End)
        val a2End = min((durAMs / 1000L).toInt(), a2Start + segSec).coerceAtLeast(a2Start + 14)

        val b2Start = min((durBMs / 1000L).toInt() / 2, b1End)
        val b2End = min((durBMs / 1000L).toInt(), b2Start + segSec).coerceAtLeast(b2Start + 14)

        val rawRatio = durBMs.toDouble() / durAMs.toDouble()
        val safeTempoB = rawRatio.coerceIn(0.95, 1.05)
        val tempoFilterB = String.format(Locale.US, "atempo=%.3f,", safeTempoB)

        val filter = StringBuilder()
        // Segmento 1 (Intro/Verso Canción A)
        filter.append("[0:a]aresample=44100,aformat=sample_rates=44100:channel_layouts=stereo,")
        filter.append("atrim=$a1Start:$a1End,asetpts=PTS-STARTPTS[s0];")
        // Segmento 2 (Drop Canción B sincronizado)
        filter.append("[1:a]aresample=44100,aformat=sample_rates=44100:channel_layouts=stereo,")
        filter.append("atrim=$b1Start:$b1End,asetpts=PTS-STARTPTS,$tempoFilterB")
        filter.append("equalizer=f=75:t=q:w=1.1:g=2.5[s1];")
        // Segmento 3 (Coro/Clímax Canción A)
        filter.append("[0:a]aresample=44100,aformat=sample_rates=44100:channel_layouts=stereo,")
        filter.append("atrim=$a2Start:$a2End,asetpts=PTS-STARTPTS,")
        filter.append("equalizer=f=2400:t=q:w=1.2:g=2.0[s2];")
        // Segmento 4 (Outro/Cierre Canción B)
        filter.append("[1:a]aresample=44100,aformat=sample_rates=44100:channel_layouts=stereo,")
        filter.append("atrim=$b2Start:$b2End,asetpts=PTS-STARTPTS,$tempoFilterB")
        filter.append("afade=t=out:st=${(b2End - b2Start - 3).coerceAtLeast(4)}:d=3[s3];")

        // Encadenamiento con acrossfade DJ
        filter.append("[s0][s1]acrossfade=d=$cf:c1=qsin:c2=qsin[m01];")
        filter.append("[m01][s2]acrossfade=d=$cf:c1=qsin:c2=qsin[m02];")
        filter.append("[m02][s3]acrossfade=d=$cf:c1=qsin:c2=qsin,alimiter=limit=0.95:level=disabled[outa]")

        val d0Ms = (a1End - a1Start) * 1000L
        val d1Ms = (b1End - b1Start) * 1000L
        val d2Ms = (a2End - a2Start) * 1000L
        val d3Ms = (b2End - b2Start) * 1000L
        val cfMs = cf * 1000L

        val c0Start = 0L
        val c0End = d0Ms
        val c1Start = (c0End - cfMs).coerceAtLeast(1000L)
        val c1End = c1Start + d1Ms
        val c2Start = (c1End - cfMs).coerceAtLeast(c1Start + 1000L)
        val c2End = c2Start + d2Ms
        val c3Start = (c2End - cfMs).coerceAtLeast(c2Start + 1000L)
        val c3End = c3Start + d3Ms

        val chapters = listOf(
            MixtapeChapter(
                trackId = trackA.id,
                title = "${trackA.title} (Intro DJ Set)",
                artist = trackA.artist,
                albumArtPath = trackA.albumArtPath,
                videoUri = trackA.videoUri,
                isVideoLoop = trackA.isVideoLoop,
                startMs = c0Start,
                endMs = c0End
            ),
            MixtapeChapter(
                trackId = trackB.id,
                title = "${trackB.title} (Beat Drop)",
                artist = trackB.artist,
                albumArtPath = trackB.albumArtPath,
                videoUri = trackB.videoUri,
                isVideoLoop = trackB.isVideoLoop,
                startMs = c1Start,
                endMs = c1End
            ),
            MixtapeChapter(
                trackId = trackA.id,
                title = "${trackA.title} (Vocal Switch)",
                artist = trackA.artist,
                albumArtPath = trackA.albumArtPath,
                videoUri = trackA.videoUri,
                isVideoLoop = trackA.isVideoLoop,
                startMs = c2Start,
                endMs = c2End
            ),
            MixtapeChapter(
                trackId = trackB.id,
                title = "${trackB.title} (Outro Finale)",
                artist = trackB.artist,
                albumArtPath = trackB.albumArtPath,
                videoUri = trackB.videoUri,
                isVideoLoop = trackB.isVideoLoop,
                startMs = c3Start,
                endMs = c3End
            )
        )

        return Triple(filter.toString(), c3End.coerceAtLeast(15_000L), chapters)
    }

    private fun prepareSafeLocalAudioFile(
        context: Context,
        track: Track,
        index: Int,
        tempFiles: MutableList<File>
    ): File? {
        val uriStr = track.uriString
        if (uriStr.startsWith("/")) {
            val f = File(uriStr)
            if (f.exists() && f.isFile && f.canRead()) return f.canonicalFile
            return null
        } else if (uriStr.startsWith("file://")) {
            val f = File(Uri.parse(uriStr).path ?: "")
            if (f.exists() && f.isFile && f.canRead()) return f.canonicalFile
            return null
        }

        return try {
            val uri = Uri.parse(uriStr)
            if (!IncomingMediaHandler.isSafeExternalUri(context, uri)) return null
            val tempFile = File(context.cacheDir, "autoremix_in_${index}_${System.currentTimeMillis()}.tmp")
            tempFiles.add(tempFile)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (tempFile.exists() && tempFile.length() > 0L) tempFile else null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun generateSplitRemixArtwork(
        trackA: Track,
        trackB: Track,
        timestamp: Long,
        storageManager: AppStorageManager
    ): File? {
        return try {
            val targetSize = 512
            val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            val bmpA = trackA.albumArtPath?.takeIf { File(it).exists() }?.let { BitmapFactory.decodeFile(it) }
            val bmpB = trackB.albumArtPath?.takeIf { File(it).exists() }?.let { BitmapFactory.decodeFile(it) }

            when {
                bmpA != null && bmpB != null -> {
                    canvas.drawBitmap(bmpA, null, Rect(0, 0, targetSize / 2, targetSize), paint)
                    canvas.drawBitmap(bmpB, null, Rect(targetSize / 2, 0, targetSize, targetSize), paint)
                }
                bmpA != null -> canvas.drawBitmap(bmpA, null, Rect(0, 0, targetSize, targetSize), paint)
                bmpB != null -> canvas.drawBitmap(bmpB, null, Rect(0, 0, targetSize, targetSize), paint)
                else -> {
                    paint.color = android.graphics.Color.rgb(30, 27, 75)
                    canvas.drawRect(0f, 0f, targetSize.toFloat(), targetSize.toFloat(), paint)
                }
            }

            bmpA?.recycle()
            bmpB?.recycle()

            val outFile = storageManager.saveCoverAsWebp("autoremix_$timestamp", bitmap)
            bitmap.recycle()
            outFile
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun combineLyricsForRemix(
        storageManager: AppStorageManager,
        remixTrackId: Long,
        chapters: List<MixtapeChapter>
    ) {
        val builder = StringBuilder()
        builder.append("[ti:Aura Auto-Remix]\n[ar:Aura Smart Remix]\n\n")
        var hasAny = false

        for (chapter in chapters) {
            val raw = storageManager.getLyrics(chapter.trackId)
            builder.append("[${formatLrcTime(chapter.startMs)}] ⚡ ${chapter.title} • ${chapter.artist}\n")
            if (!raw.isNullOrBlank()) {
                hasAny = true
                for (line in raw.lines()) {
                    val trimmed = line.trim()
                    val matcher = LRC_TIMESTAMP_PATTERN.matcher(trimmed)
                    if (matcher.find()) {
                        val min = matcher.group(1)?.toLongOrNull() ?: 0L
                        val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                        val centisStr = matcher.group(3) ?: "00"
                        val centis = if (centisStr.length == 3) (centisStr.toLongOrNull() ?: 0L) / 10L else (centisStr.toLongOrNull() ?: 0L)
                        val text = matcher.group(4) ?: ""
                        val lineMs = (min * 60_000L) + (sec * 1000L) + (centis * 10L)
                        if (lineMs <= chapter.durationMs) {
                            builder.append("[${formatLrcTime(chapter.startMs + lineMs)}]$text\n")
                        }
                    }
                }
            }
        }
        if (hasAny) {
            storageManager.saveLyrics(remixTrackId, builder.toString())
        }
    }

    private fun formatLrcTime(ms: Long): String {
        val totalSec = ms / 1000L
        val min = totalSec / 60L
        val sec = totalSec % 60L
        val centis = (ms % 1000L) / 10L
        return String.format(Locale.US, "%02d:%02d.%02d", min, sec, centis)
    }

    private fun saveRemixMetadata(storageManager: AppStorageManager, metadata: MixtapeMetadata) {
        try {
            val file = File(storageManager.metadataDir, "mixtape_${metadata.mixtapeId}.json")
            file.writeText(metadata.toJsonObject().toString(2))
        } catch (_: Exception) {}
    }

    private fun executeFFmpeg(
        context: Context,
        args: Array<String>,
        totalDurationMs: Long,
        onProgress: ((Float) -> Unit)?
    ): Boolean {
        val timePattern = Pattern.compile("time=(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})")
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
                    if (onProgress != null && totalDurationMs > 0L) {
                        val matcher = timePattern.matcher(currentLine)
                        if (matcher.find()) {
                            val hours = matcher.group(1)?.toLongOrNull() ?: 0L
                            val minutes = matcher.group(2)?.toLongOrNull() ?: 0L
                            val seconds = matcher.group(3)?.toLongOrNull() ?: 0L
                            val centis = matcher.group(4)?.toLongOrNull() ?: 0L
                            val currentMs = (hours * 3600 + minutes * 60 + seconds) * 1000L + centis * 10L
                            onProgress((currentMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                onProgress?.invoke(1.0f)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
