package com.example.core.data.importer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Detección de límites por energía RMS dBFS con decodificación PCM rápida.
 * Rol arquitectónico: submódulo de AudioSilenceTrimmer (fachada pública intacta).
 */
internal object SilenceDetector {
    fun detectBoundsUs(
        context: Context,
        uri: Uri,
        totalDurationUs: Long
    ): Pair<Long, Long> {
        val leadingEndUs = scanLeading(context, uri, totalDurationUs)
        val trailingStartUs = scanTrailing(context, uri, totalDurationUs)

        val safeStartUs = (leadingEndUs - SilenceTrimConfig.ATTACK_GUARD_US).coerceAtLeast(0L)
        val safeEndUs = (trailingStartUs + SilenceTrimConfig.RELEASE_GUARD_US).coerceAtMost(totalDurationUs)

        return if (safeEndUs > safeStartUs + 1_000_000L) {
            safeStartUs to safeEndUs
        } else {
            0L to totalDurationUs
        }
    }

    private fun scanLeading(
        context: Context,
        uri: Uri,
        totalDurationUs: Long
    ): Long {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

            val trackIndex = SilenceMediaUtils.selectAudioTrack(extractor)
            if (trackIndex < 0) return 0L

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return 0L

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val maxScanUs = SilenceTrimConfig.MAX_SCAN_EDGE_US.coerceAtMost(totalDurationUs / 3)
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
                        val db = rmsDb(outputBuffer, bufferInfo)
                        val ptsUs = bufferInfo.presentationTimeUs.coerceAtLeast(0L)
                        codec.releaseOutputBuffer(outIndex, false)
                        if (db > SilenceTrimConfig.SILENCE_THRESHOLD_DB) {
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

    private fun scanTrailing(
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

            val trackIndex = SilenceMediaUtils.selectAudioTrack(extractor)
            if (trackIndex < 0) return totalDurationUs

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return totalDurationUs

            val seekStartUs = (totalDurationUs - SilenceTrimConfig.MAX_SCAN_EDGE_US).coerceAtLeast(totalDurationUs * 2 / 3)
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
                        val db = rmsDb(outputBuffer, bufferInfo)
                        val ptsUs = bufferInfo.presentationTimeUs.coerceAtLeast(seekStartUs)
                        if (db > SilenceTrimConfig.SILENCE_THRESHOLD_DB) {
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

    private fun rmsDb(buffer: ByteBuffer, info: MediaCodec.BufferInfo): Double {
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
}
