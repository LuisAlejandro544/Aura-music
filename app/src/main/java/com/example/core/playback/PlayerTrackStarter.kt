package com.example.core.playback

import com.example.core.data.importer.AudioSilenceTrimmer
import com.example.core.model.Track

/**
 * Arranque de pistas (cola o directa) con handoff dual-deck y DSP.
 * Rol arquitectónico: extensiones internas de AuraAudioPlayer; la API pública no cambia.
 */
internal fun AuraAudioPlayer.playTrackListInternal(tracks: List<Track>, startIndex: Int = 0) {
    try { clearStreamingPrebufferInternal() } catch (_: Throwable) {}
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
    progressTracker.resetForNewTrack()
    abLoopController.reset()
    // Modo Streaming Fases 1-3: DSP C++20 activo para EQ, Normalización y 8D/16D.
    // Fase 4 (Reverb, VocalClarity, Crossfeed/Balance, Bit-Perfect, DJ) en bypass nativo.
    NativeAudioProcessor.isStreamingBypassActive = false
    if (previousTrackId != track.id) {
        effectManager.onTrackChanged(track.id)
    }
    effectManager.flushBuffers()
    effectManager.applyStreamingPolicy(track.isStreamingTrack)
    com.example.widget.AuraMusicWidgetProvider.pushPlaybackState(
        context = context,
        track = track,
        isPlaying = true,
        positionMs = 0L,
        durationMs = track.durationMs
    )

    // Capa C: si el vecino ya está prebuferizado como MediaItem 1 (URL fresca del
    // calentador o caché), saltar con seekToNext() en 0ms en vez de prepare() (200-400ms).
    // El handoff Dual-Deck ya dejó la saliente en el Deck B, así con crossfade > 0
    // ambas suenan superpuestas con curva de igual potencia (gapless + crossfade).
    // Con crossfade = 0 es corte gapless puro. Ante 403 el recovery regenera el flujo.
    if (track.isStreamingTrack && hasStreamingPrebufferForInternal(track)) {
        _isBuffering.value = false
        _isNetworkStalled.value = false
        try {
            player.playbackParameters = androidx.media3.common.PlaybackParameters(_playbackSpeed.value, _playbackPitch.value)
            ensurePlaybackServiceStartedInternal()
            val consumed = consumeStreamingPrebufferInternal()
            if (consumed) {
                try { player.playbackParameters = androidx.media3.common.PlaybackParameters(_playbackSpeed.value, _playbackPitch.value) } catch (_: Throwable) {}
                dualDeckTransitions.startEntryTransition(handoff, isAutoCrossfadeOverlap)
                return
            }
        } catch (_: Throwable) {
            // Caer al arranque normal si el seek falla (p. ej. token expirado a mitad).
        }
    }

    _isBuffering.value = track.isStreamingTrack && (track.uriString.startsWith("http://", ignoreCase = true) || track.uriString.startsWith("https://", ignoreCase = true))
    _isNetworkStalled.value = false

    try {
        // La pista cambió a una no prebuferizada: purgar el siguiente obsoleto para
        // no gastar batería ni arriesgar un seek a URL vieja (403).
        try { clearStreamingPrebufferInternal() } catch (_: Throwable) {}
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
