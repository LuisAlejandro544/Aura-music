package com.example.viewmodel.delegates

import com.example.core.model.StreamingSearchItem
import com.example.core.model.Track

/**
 * Construcción de Tracks sintéticos y colas desde resultados.
 * Rol arquitectónico: extensiones internas de StreamingModeCoordinator; la API pública no cambia.
 */
internal fun StreamingModeCoordinator.buildStreamingTrackInternal(
    item: StreamingSearchItem,
    audioUri: String,
    videoUri: String?
): Track {
    return Track(
        id = item.syntheticTrackId,
        title = item.title,
        artist = item.artist,
        album = "${item.platform.badgeText} • ${item.album}",
        durationMs = item.durationSeconds * 1000L,
        uriString = audioUri,
        albumArtPath = item.thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/${item.videoId}/hqdefault.jpg" },
        videoUri = videoUri,
        isVideoLoop = false,
        mimeType = "audio/mp4",
        folderName = "STREAM:${item.platform.id}:${item.videoId}",
        fileSizeFormatted = item.platform.badgeText
    )
}

internal fun StreamingModeCoordinator.buildQueueTracksWithSelectedInternal(
    selectedItem: StreamingSearchItem,
    resolvedSelectedTrack: Track,
    contextQueue: List<StreamingSearchItem>
): List<Track> {
    val baseList = if (contextQueue.any { it.videoId == selectedItem.videoId }) {
        contextQueue
    } else {
        listOf(selectedItem) + contextQueue
    }
    return baseList.take(25).map { item ->
        if (item.videoId == selectedItem.videoId) {
            resolvedSelectedTrack
        } else {
            val cachedAudio = cacheManager.getCachedAudioFile(item.videoId)
            val cachedVideo = if (cacheManager.isStreamingVideoAllowedByNetwork()) {
                cacheManager.getCachedVideoFile(item.videoId)
            } else null
            buildStreamingTrackInternal(
                item = item,
                audioUri = cachedAudio?.absolutePath ?: item.canonicalWatchUrl,
                videoUri = cachedVideo?.absolutePath
            )
        }
    }
}
