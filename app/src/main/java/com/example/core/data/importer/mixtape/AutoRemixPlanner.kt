package com.example.core.data.importer.mixtape

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.core.data.importer.FFmpegNativeEngine
import com.example.core.data.importer.IncomingMediaHandler
import com.example.debug.AuraDebugManager
import com.example.core.model.MixtapeChapter
import com.example.core.model.Track
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

/**
 * Planificador de grafos FFmpeg para Auto-Remix.
 * Rol arquitectónico: construye planes Mashup/DJ, prepara audio local seguro
 * y ejecuta FFmpeg con progreso en vivo.
 */
internal object AutoRemixPlanner {

    private const val TAG = "SmartAutoRemixEngine"

    internal fun resolveRealAudioDurationMs(audioFile: File, fallbackDurationMs: Long): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(audioFile.absolutePath)
            val extracted = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (extracted != null && extracted > 3000L) {
                extracted
            } else {
                fallbackDurationMs.takeIf { it > 3000L } ?: 60_000L
            }
        } catch (_: Throwable) {
            fallbackDurationMs.takeIf { it > 3000L } ?: 60_000L
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    internal fun buildVocalMashupPlan(
        trackA: Track,
        trackB: Track,
        durAMs: Long,
        durBMs: Long,
        useCompatibleFiltersOnly: Boolean
    ): Triple<String, Long, List<MixtapeChapter>> {
        val targetDurationSec = (min(durAMs, durBMs).coerceIn(12_000L, 210_000L) / 1000L).toInt().coerceAtLeast(10)
        val targetDurationMs = targetDurationSec * 1000L

        val rawRatio = durBMs.toDouble() / durAMs.toDouble()
        val safeTempoB = rawRatio.coerceIn(0.94, 1.06)
        val tempoFilterB = String.format(Locale.US, "atempo=%.3f,", safeTempoB)

        val filter = StringBuilder()
        if (!useCompatibleFiltersOnly) {
            // Rama A: Enfoque Vocal (Canción A)
            filter.append("[0:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo,")
            filter.append("highpass=f=210,lowpass=f=7200,")
            filter.append("equalizer=f=1850:t=q:w=1.2:g=4.5,equalizer=f=3400:t=q:w=1.4:g=3.5,")
            filter.append("atrim=0:$targetDurationSec,asetpts=PTS-STARTPTS,volume=1.08[voca];")

            // Rama B: Base Rítmica e Instrumental (Canción B) con bolsillo vocal atenuado
            filter.append("[1:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo,")
            filter.append(tempoFilterB)
            filter.append("equalizer=f=65:t=q:w=1.1:g=4.2,equalizer=f=125:t=q:w=1.2:g=2.8,")
            filter.append("equalizer=f=1800:t=q:w=1.0:g=-7.5,equalizer=f=3200:t=q:w=1.1:g=-6.5,")
            filter.append("atrim=0:$targetDurationSec,asetpts=PTS-STARTPTS,volume=0.88[beatb];")
        } else {
            // Grafo universal compatible (100% libre de filtros opcionales específicos)
            filter.append("[0:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo,")
            filter.append("highpass=f=220,lowpass=f=7000,")
            filter.append("atrim=0:$targetDurationSec,asetpts=PTS-STARTPTS,volume=1.12[voca];")

            filter.append("[1:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo,")
            filter.append(tempoFilterB)
            filter.append("lowpass=f=3800,")
            filter.append("atrim=0:$targetDurationSec,asetpts=PTS-STARTPTS,volume=0.86[beatb];")
        }

        val fadeOutStart = (targetDurationSec - 3).coerceAtLeast(3)
        filter.append("[voca][beatb]amix=inputs=2:duration=shortest:dropout_transition=2,")
        filter.append("afade=t=in:st=0:d=2,afade=t=out:st=$fadeOutStart:d=3[outa]")

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

    internal fun buildAlternatingDjRemixPlan(
        trackA: Track,
        trackB: Track,
        durAMs: Long,
        durBMs: Long,
        crossfadeSeconds: Int,
        useCompatibleFiltersOnly: Boolean
    ): Triple<String, Long, List<MixtapeChapter>> {
        val totalASec = (durAMs / 1000L).toInt().coerceAtLeast(12)
        val totalBSec = (durBMs / 1000L).toInt().coerceAtLeast(12)

        val segASec = min(28, (totalASec / 2).coerceAtLeast(6))
        val segBSec = min(28, (totalBSec / 2).coerceAtLeast(6))
        val maxSafeCf = (min(segASec, segBSec) / 2).coerceAtLeast(2)
        val cf = crossfadeSeconds.coerceIn(2, min(8, maxSafeCf))

        val a1Start = 0
        val a1End = min(totalASec, segASec).coerceAtLeast(cf + 2)

        val b1Start = min(totalBSec / 5, max(0, totalBSec - segBSec))
        val b1End = min(totalBSec, b1Start + segBSec).coerceAtLeast(b1Start + cf + 2)

        val a2Start = min(totalASec / 2, max(0, totalASec - segASec))
        val a2End = min(totalASec, a2Start + segASec).coerceAtLeast(a2Start + cf + 2)

        val b2Start = min(totalBSec / 2, max(0, totalBSec - segBSec))
        val b2End = min(totalBSec, b2Start + segBSec).coerceAtLeast(b2Start + cf + 2)

        val rawRatio = durBMs.toDouble() / durAMs.toDouble()
        val safeTempoB = rawRatio.coerceIn(0.95, 1.05)
        val tempoFilterB = String.format(Locale.US, "atempo=%.3f,", safeTempoB)

        val filter = StringBuilder()
        // Segmento 1 (Intro/Verso Canción A)
        filter.append("[0:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo,")
        filter.append("atrim=$a1Start:$a1End,asetpts=PTS-STARTPTS[s0];")
        // Segmento 2 (Drop Canción B sincronizado)
        filter.append("[1:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo,")
        filter.append("atrim=$b1Start:$b1End,asetpts=PTS-STARTPTS,$tempoFilterB")
        if (!useCompatibleFiltersOnly) {
            filter.append("equalizer=f=75:t=q:w=1.1:g=2.5[s1];")
        } else {
            filter.append("volume=1.02[s1];")
        }
        // Segmento 3 (Coro/Clímax Canción A)
        filter.append("[0:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo,")
        filter.append("atrim=$a2Start:$a2End,asetpts=PTS-STARTPTS,")
        if (!useCompatibleFiltersOnly) {
            filter.append("equalizer=f=2400:t=q:w=1.2:g=2.0[s2];")
        } else {
            filter.append("volume=1.02[s2];")
        }
        // Segmento 4 (Outro/Cierre Canción B)
        val fadeOutStart = (b2End - b2Start - 3).coerceAtLeast(2)
        filter.append("[1:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo,")
        filter.append("atrim=$b2Start:$b2End,asetpts=PTS-STARTPTS,$tempoFilterB")
        filter.append("afade=t=out:st=$fadeOutStart:d=2[s3];")

        // Encadenamiento con acrossfade DJ
        val curveType = if (!useCompatibleFiltersOnly) "qsin" else "tri"
        filter.append("[s0][s1]acrossfade=d=$cf:c1=$curveType:c2=$curveType[m01];")
        filter.append("[m01][s2]acrossfade=d=$cf:c1=$curveType:c2=$curveType[m02];")
        filter.append("[m02][s3]acrossfade=d=$cf:c1=$curveType:c2=$curveType[outa]")

        val d0Ms = (a1End - a1Start).coerceAtLeast(4) * 1000L
        val d1Ms = (b1End - b1Start).coerceAtLeast(4) * 1000L
        val d2Ms = (a2End - a2Start).coerceAtLeast(4) * 1000L
        val d3Ms = (b2End - b2Start).coerceAtLeast(4) * 1000L
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

        return Triple(filter.toString(), c3End.coerceAtLeast(10_000L), chapters)
    }

    internal fun prepareSafeLocalAudioFile(
        context: Context,
        track: Track,
        index: Int,
        tempFiles: MutableList<File>
    ): File? {
        val uriStr = track.uriString.trim()
        if (uriStr.isBlank()) return null

        if (uriStr.startsWith("/")) {
            val f = File(uriStr)
            if (AutoRemixMatcher.isSafeAudioFileForAutoRemix(context, f)) return f.canonicalFile
            return null
        } else if (uriStr.startsWith("file://", ignoreCase = true)) {
            val parsedPath = Uri.parse(uriStr).path ?: uriStr.removePrefix("file://")
            val f = File(parsedPath)
            if (AutoRemixMatcher.isSafeAudioFileForAutoRemix(context, f)) return f.canonicalFile
            return null
        }

        return try {
            val uri = Uri.parse(uriStr)
            val scheme = uri.scheme?.lowercase()
            if (scheme == "file") {
                val f = File(uri.path ?: "")
                if (AutoRemixMatcher.isSafeAudioFileForAutoRemix(context, f)) return f.canonicalFile
                return null
            }
            if (!IncomingMediaHandler.isSafeExternalUri(context, uri)) return null
            val tempFile = File(context.cacheDir, "autoremix_in_${index}_${System.currentTimeMillis()}.tmp")
            tempFiles.add(tempFile)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (tempFile.exists() && tempFile.length() > 512L) tempFile else null
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Error preparando archivo de entrada '${track.title}': ${e.message}")
            null
        }
    }

    internal fun executeFFmpeg(
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
                AuraDebugManager.logWarning(
                    TAG,
                    "FFmpeg Auto-Remix finalizó con código $exitCode. Salida: ${logBuilder.takeLast(350)}"
                )
                false
            }
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Excepción ejecutando FFmpeg en Auto-Remix: ${e.message}")
            false
        }
    }

}
