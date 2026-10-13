package com.example.core.data.importer

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri

/**
 * Selección de pista de audio y duración vía MediaMetadataRetriever.
 * Rol arquitectónico: submódulo de AudioSilenceTrimmer (fachada pública intacta).
 */
internal object SilenceMediaUtils {
    fun selectAudioTrack(extractor: MediaExtractor): Int {
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

    fun extractDurationMs(context: Context, uri: Uri): Long {
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
}
