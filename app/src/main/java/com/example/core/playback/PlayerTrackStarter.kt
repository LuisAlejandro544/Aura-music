package com.example.core.playback

import com.example.core.data.importer.AudioSilenceTrimmer
import com.example.core.model.Track

/**
 * Arranque de pistas (cola o directa) con handoff dual-deck y DSP.
 * Rol arquitectónico: extensiones internas de AuraAudioPlayer; la API pública no cambia.
 */
internal fun AuraAudioPlayer.playTrackListInternal(tracks: List<Track>, startIndex: Int = 0) {
    val selected = queueController.setQueue(tracks, startIndex) ?: return
    playTrackInternal(selected)
}

internal fun AuraAudioPlayer.playTrackInternal(track: Track, isAutoCrossfadeOverlap: Boolean = false) {
    if (track.isStreamingTrack && isUnresolvedStreamingPageUrl(track.uriString)) {
        val handled = onStreamingTrackRequestedCallback?.invoke(track, isAutoCrossfadeOverlap) ?: false
        if (handled) {
            return
        }
    }

    val player = exoPlayer ?: return
    val previousTrackId = _currentTrack.value?.id
    val handoff = dualDeckTransitions.prepareHandoff(track)

    _currentTrack.value = track
    _currentPosition.value = 0L
    _duration.value = track.durationMs
    _isBuffering.value = track.isStreamingTrack && (track.uriString.startsWith("http://", ignoreCase = true) || track.uriString.startsWith("https://", ignoreCase = true))
    _isNetworkStalled.value = false
    progressTracker.resetForNewTrack()
    abLoopController.reset()
    // En modo Streaming omitimos por ahora el motor C++20 DSP; en modo local lo mantenemos 100% activo
    NativeAudioProcessor.isStreamingBypassActive = track.isStreamingTrack
    if (previousTrackId != track.id && !track.isStreamingTrack) {
        effectManager.onTrackChanged(track.id)
    }
    effectManager.flushBuffers()
    com.example.widget.AuraMusicWidgetProvider.pushPlaybackState(
        context = context,
        track = track,
        isPlaying = true,
        positionMs = 0L,
        durationMs = track.durationMs
    )

    try {
        val mediaItem = TrackMediaFactory.buildTrackMedia(track) { uri ->
            AudioSilenceTrimmer.getClippingBounds(context, uri)
        }.mediaItem
        player.setMediaItem(mediaItem)
        player.playbackParameters = androidx.media3.common.PlaybackParameters(_playbackSpeed.value, _playbackPitch.value)
        player.prepare()
        ensurePlaybackServiceStartedInternal()
        player.play()

        dualDeckTransitions.startEntryTransition(handoff, isAutoCrossfadeOverlap)
    } catch (e: Exception) {
        _playbackError.value = "No se pudo cargar la pista: ${e.message}"
    }
}
