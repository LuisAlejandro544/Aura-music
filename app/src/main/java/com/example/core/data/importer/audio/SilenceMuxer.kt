package com.example.core.data.importer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

/**
 * Recorte físico Direct Stream Copy a .m4a con MediaMuxer.
 * Rol arquitectónico: submódulo de AudioSilenceTrimmer (fachada pública intacta).
 */
internal object SilenceMuxer {
    fun muxTrimmed(
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

            val trackIndex = SilenceMediaUtils.selectAudioTrack(extractor)
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
}
