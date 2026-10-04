package com.example.data.importer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Motor inteligente de detección y eliminación de silencios iniciales y finales para Aura Music.
 *
 * Rol arquitectónico:
 * 1. Escanea mediante decodificación PCM rápida (MediaExtractor + MediaCodec en Dispatchers.IO)
 *    los primeros y últimos segundos de una pista de audio para medir la energía RMS en decibelios (dBFS).
 * 2. Identifica con precisión dónde comienza realmente la música (fin del silencio inicial)
 *    y dónde concluye antes del silencio muerto final.
 * 3. Para contenedores compatibles (M4A / MP4 / AAC), realiza un recorte físico sin pérdida
 *    (Direct Stream Copy con MediaMuxer) ajustando los tiempos de presentación a 0:00.
 * 4. Además, registra de forma persistente los límites de recorte (startMs / endMs) para que
 *    AuraAudioPlayer aplique MediaItem.ClippingConfiguration en cualquier formato (MP3, FLAC, OGG, WAV, OPUS)
 *    sin recodificar ni perder calidad acústica.
 */
object AudioSilenceTrimmer {

    private const val SILENCE_THRESHOLD_DB = -42.0 // Umbral por debajo del cual se considera silencio
    private const val MAX_SCAN_EDGE_US = 15_000_000L // Máximo 15 segundos de escaneo en cada extremo
    private const val MIN_TRIM_START_MS = 180L // Recortar inicio solo si hay al menos 180ms de silencio
    private const val MIN_TRIM_END_MS = 220L // Recortar final solo si hay al menos 220ms de silencio
    private const val ATTACK_GUARD_US = 50_000L // 50ms de margen antes del primer golpe musical
    private const val RELEASE_GUARD_US = 140_000L // 140ms de margen tras la última caída sonora
    private const val OFFSETS_FILE_NAME = "aura_silence_trim_offsets.json"

    data class SilenceTrimResult(
        val wasTrimmed: Boolean,
        val startTrimMs: Long,
        val endTrimMs: Long,
        val newDurationMs: Long,
        val outputUriString: String
    )

    data class ClippingBounds(
        val startMs: Long,
        val endMs: Long
    )

    /**
     * Analiza un archivo de audio local en `songs/` y, si tiene silencios al inicio o al final,
     * lo recorta físicamente (cuando es contenedor M4A/MP4) y/o registra sus límites exactos de reproducción.
     */
    suspend fun processLocalAudioFile(
        context: Context,
        audioFile: File,
        originalDurationMs: Long
    ): SilenceTrimResult = withContext(Dispatchers.IO) {
        val fileUri = Uri.fromFile(audioFile)
        val totalDurationMs = if (originalDurationMs > 0L) {
            originalDurationMs
        } else {
            extractDurationMs(context, fileUri)
        }

        if (totalDurationMs < 2500L) {
            return@withContext SilenceTrimResult(
                wasTrimmed = false,
                startTrimMs = 0L,
                endTrimMs = totalDurationMs,
                newDurationMs = totalDurationMs,
                outputUriString = fileUri.toString()
            )
        }

        val detected = detectSilenceBoundsUs(context, fileUri, totalDurationMs * 1000L)
        val startMs = (detected.first / 1000L).coerceIn(0L, totalDurationMs / 3)
        val endMs = (detected.second / 1000L).coerceIn((totalDurationMs * 2) / 3, totalDurationMs)

        val hasLeadingSilence = startMs >= MIN_TRIM_START_MS
        val hasTrailingSilence = (totalDurationMs - endMs) >= MIN_TRIM_END_MS

        if (!hasLeadingSilence && !hasTrailingSilence) {
            return@withContext SilenceTrimResult(
                wasTrimmed = false,
                startTrimMs = 0L,
                endTrimMs = totalDurationMs,
                newDurationMs = totalDurationMs,
                outputUriString = fileUri.toString()
            )
        }

        val effectiveStartMs = if (hasLeadingSilence) startMs else 0L
        val effectiveEndMs = if (hasTrailingSilence) endMs else totalDurationMs
        val trimmedDurationMs = (effectiveEndMs - effectiveStartMs).coerceAtLeast(1000L)

        // Intentar recorte físico sin pérdida por Direct Stream Copy si es contenedor MP4/M4A
        val tempTrimmedFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}_trimmed.m4a")
        val physicalSuccess = muxTrimmedAudioStream(
            context = context,
            sourceUri = fileUri,
            outputFile = tempTrimmedFile,
            startUs = effectiveStartMs * 1000L,
            endUs = effectiveEndMs * 1000L
        )

        if (physicalSuccess && tempTrimmedFile.exists() && tempTrimmedFile.length() > 1024L) {
            try {
                tempTrimmedFile.copyTo(audioFile, overwrite = true)
                tempTrimmedFile.delete()
                removeClippingBounds(context, fileUri.toString())
                AuraDebugManager.logInfo(
                    "AudioSilenceTrimmer",
                    "Silencio recortado físicamente: inicio=${effectiveStartMs}ms, fin=${totalDurationMs - effectiveEndMs}ms"
                )
                return@withContext SilenceTrimResult(
                    wasTrimmed = true,
                    startTrimMs = effectiveStartMs,
                    endTrimMs = effectiveEndMs,
                    newDurationMs = trimmedDurationMs,
                    outputUriString = fileUri.toString()
                )
            } catch (_: Throwable) {
                tempTrimmedFile.delete()
            }
        } else {
            tempTrimmedFile.delete()
        }

        // Respaldo universal sin pérdida mediante ClippingConfiguration persistido para cualquier códec
        saveClippingBounds(context, fileUri.toString(), effectiveStartMs, effectiveEndMs)
        AuraDebugManager.logInfo(
            "AudioSilenceTrimmer",
            "Límites de silencio registrados sin recodificación: inicio=${effectiveStartMs}ms, fin=${effectiveEndMs}ms"
        )

        SilenceTrimResult(
            wasTrimmed = true,
            startTrimMs = effectiveStartMs,
            endTrimMs = effectiveEndMs,
            newDurationMs = trimmedDurationMs,
            outputUriString = fileUri.toString()
        )
    }

    /**
     * Analiza una URI externa o SAF y registra/aplica el recorte de silencios al inicio y final.
     */
    suspend fun processUriForSilenceTrim(
        context: Context,
        uri: Uri,
        originalDurationMs: Long
    ): SilenceTrimResult = withContext(Dispatchers.IO) {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            if (file.exists()) {
                return@withContext processLocalAudioFile(context, file, originalDurationMs)
            }
        }

        val totalDurationMs = if (originalDurationMs > 0L) {
            originalDurationMs
        } else {
            extractDurationMs(context, uri)
        }

        if (totalDurationMs < 2500L) {
            return@withContext SilenceTrimResult(
                wasTrimmed = false,
                startTrimMs = 0L,
                endTrimMs = totalDurationMs,
                newDurationMs = totalDurationMs,
                outputUriString = uri.toString()
            )
        }

        val detected = detectSilenceBoundsUs(context, uri, totalDurationMs * 1000L)
        val startMs = (detected.first / 1000L).coerceIn(0L, totalDurationMs / 3)
        val endMs = (detected.second / 1000L).coerceIn((totalDurationMs * 2) / 3, totalDurationMs)

        val hasLeadingSilence = startMs >= MIN_TRIM_START_MS
        val hasTrailingSilence = (totalDurationMs - endMs) >= MIN_TRIM_END_MS

        if (!hasLeadingSilence && !hasTrailingSilence) {
            return@withContext SilenceTrimResult(
                wasTrimmed = false,
                startTrimMs = 0L,
                endTrimMs = totalDurationMs,
                newDurationMs = totalDurationMs,
                outputUriString = uri.toString()
            )
        }

        val effectiveStartMs = if (hasLeadingSilence) startMs else 0L
        val effectiveEndMs = if (hasTrailingSilence) endMs else totalDurationMs
        val trimmedDurationMs = (effectiveEndMs - effectiveStartMs).coerceAtLeast(1000L)

        saveClippingBounds(context, uri.toString(), effectiveStartMs, effectiveEndMs)

        SilenceTrimResult(
            wasTrimmed = true,
            startTrimMs = effectiveStartMs,
            endTrimMs = effectiveEndMs,
            newDurationMs = trimmedDurationMs,
            outputUriString = uri.toString()
        )
    }

    /**
     * Detecta los tiempos exactos en microsegundos (startUs, endUs) donde hay señal acústica real.
     */
    private fun detectSilenceBoundsUs(
        context: Context,
        uri: Uri,
        totalDurationUs: Long
    ): Pair<Long, Long> {
        val leadingEndUs = scanLeadingAudioStartUs(context, uri, totalDurationUs)
        val trailingStartUs = scanTrailingAudioEndUs(context, uri, totalDurationUs)

        val safeStartUs = (leadingEndUs - ATTACK_GUARD_US).coerceAtLeast(0L)
        val safeEndUs = (trailingStartUs + RELEASE_GUARD_US).coerceAtMost(totalDurationUs)

        return if (safeEndUs > safeStartUs + 1_000_000L) {
            safeStartUs to safeEndUs
        } else {
            0L to totalDurationUs
        }
    }

    /**
     * Decodifica los primeros segundos de la canción para hallar el primer bloque PCM que supera el umbral dB.
     */
    private fun scanLeadingAudioStartUs(
        context: Context,
        uri: Uri,
        totalDurationUs: Long
    ): Long {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

            val trackIndex = selectAudioTrack(extractor)
            if (trackIndex < 0) return 0L

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return 0L

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val maxScanUs = MAX_SCAN_EDGE_US.coerceAtMost(totalDurationUs / 3)
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(4000L)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            val sampleTimeUs = extractor.sampleTime
                            if (sampleSize < 0 || sampleTimeUs > maxScanUs) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, sampleTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 4000L)
                if (outIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val db = calculateBufferRmsDb(outputBuffer, bufferInfo)
                        val ptsUs = bufferInfo.presentationTimeUs.coerceAtLeast(0L)
                        codec.releaseOutputBuffer(outIndex, false)
                        if (db > SILENCE_THRESHOLD_DB) {
                            return ptsUs
                        }
                    } else {
                        codec.releaseOutputBuffer(outIndex, false)
                    }
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    break
                }
            }
        } catch (_: Throwable) {
            return 0L
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try { extractor?.release() } catch (_: Throwable) {}
        }
        return 0L
    }

    /**
     * Decodifica el tramo final de la canción para hallar el último bloque PCM con energía acústica real.
     */
    private fun scanTrailingAudioEndUs(
        context: Context,
        uri: Uri,
        totalDurationUs: Long
    ): Long {
        if (totalDurationUs <= 3_000_000L) return totalDurationUs

        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

            val trackIndex = selectAudioTrack(extractor)
            if (trackIndex < 0) return totalDurationUs

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return totalDurationUs

            val seekStartUs = (totalDurationUs - MAX_SCAN_EDGE_US).coerceAtLeast(totalDurationUs * 2 / 3)
            extractor.seekTo(seekStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var lastActiveUs = totalDurationUs
            var foundAnySample = false
            var lastLoudUs = seekStartUs
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(4000L)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val sampleTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(inIndex, 0, sampleSize, sampleTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 4000L)
                if (outIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        foundAnySample = true
                        val db = calculateBufferRmsDb(outputBuffer, bufferInfo)
                        val ptsUs = bufferInfo.presentationTimeUs.coerceAtLeast(seekStartUs)
                        if (db > SILENCE_THRESHOLD_DB) {
                            lastLoudUs = ptsUs
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    break
                }
            }

            if (foundAnySample) {
                lastActiveUs = lastLoudUs.coerceIn(seekStartUs, totalDurationUs)
            }
            return lastActiveUs
        } catch (_: Throwable) {
            return totalDurationUs
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try { extractor?.release() } catch (_: Throwable) {}
        }
    }

    /**
     * Calcula la energía RMS en decibelios (dBFS) de un ByteBuffer PCM de 16 bits.
     */
    private fun calculateBufferRmsDb(buffer: ByteBuffer, info: MediaCodec.BufferInfo): Double {
        if (info.size < 2) return -96.0
        val dup = buffer.duplicate()
        dup.position(info.offset)
        dup.limit(info.offset + info.size)
        dup.order(ByteOrder.LITTLE_ENDIAN)

        val shortBuffer = dup.asShortBuffer()
        val count = shortBuffer.remaining()
        if (count <= 0) return -96.0

        var sumSquares = 0.0
        val step = (count / 256).coerceAtLeast(1)
        var samplesCounted = 0
        var i = 0
        while (i < count) {
            val sample = shortBuffer.get(i).toDouble() / 32768.0
            sumSquares += sample * sample
            samplesCounted++
            i += step
        }

        if (samplesCounted == 0) return -96.0
        val rms = sqrt(sumSquares / samplesCounted)
        return if (rms <= 1e-5) -96.0 else 20.0 * log10(rms)
    }

    /**
     * Realiza un recorte sin pérdida (Direct Stream Copy) entre [startUs] y [endUs] en contenedor MPEG-4 (.m4a).
     */
    private fun muxTrimmedAudioStream(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        startUs: Long,
        endUs: Long
    ): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, sourceUri, null)

            val trackIndex = selectAudioTrack(extractor)
            if (trackIndex < 0) return false

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            // MediaMuxer MPEG_4 soporta de forma nativa flujos AAC / MP4A
            if (!mime.contains("mp4a", ignoreCase = true) && !mime.contains("aac", ignoreCase = true)) {
                return false
            }

            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(format)
            muxer.start()

            val maxBufferSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(64 * 1024)
            } else {
                128 * 1024
            }

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var baseSampleTimeUs = -1L
            var wroteSamples = 0

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break

                if (sampleTimeUs >= startUs) {
                    if (baseSampleTimeUs < 0L) {
                        baseSampleTimeUs = sampleTimeUs
                    }
                    bufferInfo.presentationTimeUs = (sampleTimeUs - baseSampleTimeUs).coerceAtLeast(0L)
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                    wroteSamples++
                }
                extractor.advance()
            }

            muxer.stop()
            return wroteSamples > 5
        } catch (_: Throwable) {
            return false
        } finally {
            try { extractor?.release() } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                extractor.selectTrack(i)
                return i
            }
        }
        return -1
    }

    private fun extractDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    /**
     * Guarda los límites de recorte de silencio para una URI específica.
     */
    @Synchronized
    fun saveClippingBounds(context: Context, uriString: String, startMs: Long, endMs: Long) {
        try {
            val file = File(context.filesDir, OFFSETS_FILE_NAME)
            val root = if (file.exists()) {
                JSONObject(file.readText())
            } else {
                JSONObject()
            }
            val entry = JSONObject().apply {
                put("startMs", startMs)
                put("endMs", endMs)
            }
            root.put(uriString, entry)
            file.writeText(root.toString())
        } catch (_: Throwable) {}
    }

    /**
     * Obtiene los límites de recorte de silencio registrados para una URI, o `null` si no aplica.
     */
    @Synchronized
    fun getClippingBounds(context: Context, uriString: String): ClippingBounds? {
        return try {
            val file = File(context.filesDir, OFFSETS_FILE_NAME)
            if (!file.exists()) return null
            val root = JSONObject(file.readText())
            if (!root.has(uriString)) return null
            val obj = root.getJSONObject(uriString)
            val startMs = obj.optLong("startMs", 0L)
            val endMs = obj.optLong("endMs", 0L)
            if (endMs > startMs) ClippingBounds(startMs, endMs) else null
        } catch (_: Throwable) {
            null
        }
    }

    @Synchronized
    fun removeClippingBounds(context: Context, uriString: String) {
        try {
            val file = File(context.filesDir, OFFSETS_FILE_NAME)
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            if (root.has(uriString)) {
                root.remove(uriString)
                file.writeText(root.toString())
            }
        } catch (_: Throwable) {}
    }
}
