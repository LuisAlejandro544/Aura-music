package com.example.core.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.core.data.importer.AudioSilenceTrimmer
import com.example.core.model.Track
import java.io.File

/**
 * Fábrica de items multimedia para AuraAudioPlayer.
 * Rol arquitectónico: resuelve URIs, metadatos con carátula y recortes de silencio
 * sin acoplar la fachada a MediaItem ni a AudioSilenceTrimmer.
 */
internal object TrackMediaFactory {

    data class PreparedTrackMedia(
        val mediaItem: MediaItem,
        val clipBounds: AudioSilenceTrimmer.ClippingBounds?
    )

    fun resolveMediaUri(uriString: String): Uri =
        if (uriString.startsWith("/")) Uri.fromFile(File(uriString)) else Uri.parse(uriString)

    fun buildTrackMetadata(track: Track): MediaMetadata {
        val artworkUri = if (!track.albumArtPath.isNullOrBlank()) {
            if (track.albumArtPath.startsWith("http://", ignoreCase = true) ||
                track.albumArtPath.startsWith("https://", ignoreCase = true)
            ) {
                Uri.parse(track.albumArtPath)
            } else {
                val artFile = File(track.albumArtPath)
                if (artFile.exists()) Uri.fromFile(artFile) else null
            }
        } else {
            null
        }
        return MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .apply {
                if (artworkUri != null) {
                    setArtworkUri(artworkUri)
                }
            }
            .build()
    }

    fun buildTrackMedia(
        track: Track,
        resolveClipBounds: (String) -> AudioSilenceTrimmer.ClippingBounds?
    ): PreparedTrackMedia {
        val clipBounds = if (track.isStreamingTrack) {
            null
        } else {
            resolveClipBounds(track.uriString)
        }
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(resolveMediaUri(track.uriString))
            .setMediaMetadata(buildTrackMetadata(track))
        if (clipBounds != null && clipBounds.endMs > clipBounds.startMs) {
            mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clipBounds.startMs)
                    .setEndPositionMs(clipBounds.endMs)
                    .build()
            )
        }
        return PreparedTrackMedia(mediaItemBuilder.build(), clipBounds)
    }
}
