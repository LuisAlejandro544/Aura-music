package com.example.core.playback

import com.example.core.model.Track

/**
 * Operaciones de cola (orden, favoritos, metadatos) con espejo en widgets.
 * Rol arquitectónico: extensiones internas de AuraAudioPlayer; la API pública no cambia.
 */
/**
 * Encola una canción similar de streaming o actualiza su URI en la cola (ej. cuando ya está en la caché de 30 min).
 * Si [updateCurrentTrackIfSame] es false, actualiza únicamente la cola para futuras repeticiones/vuelta atrás
 * sin recrear el reproductor activo en mitad de la canción.
 */
internal fun AuraAudioPlayer.appendOrUpdateStreamingTrackInternal(track: Track, updateCurrentTrackIfSame: Boolean = true) {
    queueController.appendOrUpdateTrack(track)
    if (updateCurrentTrackIfSame && _currentTrack.value?.id == track.id) {
        _currentTrack.value = track
    }
}

internal fun AuraAudioPlayer.toggleShuffleInternal() {
    queueController.toggleShuffle()
    // Capa C: con aleatorio el vecino es impredecible; purgar el prebuffer para
    // no gastar datos/batería ni arriesgar un seek a pista equivocada.
    try { clearStreamingPrebufferInternal() } catch (_: Throwable) {}
}

internal fun AuraAudioPlayer.cycleRepeatModeInternal() {
    queueController.cycleRepeatMode()
    try {
        if (repeatMode.value == com.example.core.model.RepeatMode.ONE) {
            clearStreamingPrebufferInternal()
        }
    } catch (_: Throwable) {}
}

internal fun AuraAudioPlayer.updateTrackFavoriteInternal(trackId: Long, isFavorite: Boolean) {
    val (updatedCurrent, _) = queueController.updateTrackFavorite(trackId, isFavorite, _currentTrack.value)
    _currentTrack.value = updatedCurrent
    com.example.widget.WidgetStateStore.updateFavoriteOnly(context, trackId, isFavorite)
    com.example.widget.AuraMusicWidgetProvider.refreshAllWidgets(context)
    com.example.widget.AuraLibraryWidgetProvider.refreshAllWidgets(context)
}

internal fun AuraAudioPlayer.removeTrackFromQueueInternal(trackId: Long) {
    queueController.removeTrack(trackId)
    if (_currentTrack.value?.id == trackId) {
        playNextInternal()
    }
}

internal fun AuraAudioPlayer.updateTrackMetadataInternal(
    trackId: Long,
    title: String,
    artist: String,
    album: String,
    albumArtPath: String? = null,
    updateArt: Boolean = false,
    videoUri: String? = null,
    isVideoLoop: Boolean = false,
    updateVideo: Boolean = false
) {
    val (updatedCurrent, _) = queueController.updateTrackMetadata(
        trackId = trackId,
        title = title,
        artist = artist,
        album = album,
        albumArtPath = albumArtPath,
        updateArt = updateArt,
        videoUri = videoUri,
        isVideoLoop = isVideoLoop,
        updateVideo = updateVideo,
        currentTrack = _currentTrack.value
    )
    _currentTrack.value = updatedCurrent
    if (updatedCurrent != null && updatedCurrent.id == trackId) {
        com.example.widget.AuraMusicWidgetProvider.pushPlaybackState(
            context = context,
            track = updatedCurrent,
            isPlaying = _isPlaying.value,
            positionMs = _currentPosition.value,
            durationMs = _duration.value
        )
    }
}
