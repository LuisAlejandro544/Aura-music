package com.example.core.playback

import com.example.core.model.RepeatMode

/**
 * Transporte (play/pausa/saltos/cola) y fin de pista con repetición.
 * Rol arquitectónico: extensiones internas de AuraAudioPlayer; la API pública no cambia.
 */
internal fun AuraAudioPlayer.pauseInternal() {
    fadeController.stopOutgoingDeckImmediately(outgoingExoPlayer)
    exoPlayer?.pause()
    effectManager.flushBuffers()
}

internal fun AuraAudioPlayer.playInternal() {
    val player = exoPlayer ?: return
    if (_currentTrack.value == null && queue.value.isNotEmpty()) {
        playTrackListInternal(queue.value, 0)
        return
    }
    ensurePlaybackServiceStartedInternal()
    player.play()
    if (fadeController.isFadeInOnResume) {
        fadeController.startSmoothFadeIn(1200L, player)
    } else {
        fadeController.restoreFullVolume(player)
    }
}

internal fun AuraAudioPlayer.togglePlayPauseInternal() {
    val player = exoPlayer ?: return
    if (player.isPlaying) {
        fadeController.stopOutgoingDeckImmediately(outgoingExoPlayer)
        player.pause()
        effectManager.flushBuffers()
    } else {
        if (_currentTrack.value == null && queue.value.isNotEmpty()) {
            playTrackListInternal(queue.value, 0)
        } else {
            ensurePlaybackServiceStartedInternal()
            player.play()
            if (fadeController.isFadeInOnResume) {
                fadeController.startSmoothFadeIn(1200L, player)
            } else {
                fadeController.restoreFullVolume(player)
            }
        }
    }
}

internal fun AuraAudioPlayer.seekToInternal(positionMs: Long) {
    val player = exoPlayer ?: return
    fadeController.stopOutgoingDeckImmediately(outgoingExoPlayer)
    val clamped = positionMs.coerceIn(0L, _duration.value)
    effectManager.flushBuffers()
    player.seekTo(clamped)
    _currentPosition.value = clamped
}

internal fun AuraAudioPlayer.playNextInternal(isAutoCrossfadeOverlap: Boolean = false) {
    val q = queue.value
    if (q.isEmpty()) return

    if (repeatMode.value == RepeatMode.ONE) {
        _currentTrack.value?.let { playTrackInternal(it, isAutoCrossfadeOverlap = false) }
        return
    }

    val nextIndex = queueController.getNextIndex() ?: return
    val nextTrack = q[nextIndex]
    if (nextTrack.isStreamingTrack) {
        val intercepted = onStreamingTrackRequestedCallback?.invoke(nextTrack, isAutoCrossfadeOverlap) ?: false
        if (intercepted) return
    }
    playTrackInternal(nextTrack, isAutoCrossfadeOverlap = isAutoCrossfadeOverlap)
}

internal fun AuraAudioPlayer.playPreviousInternal() {
    val player = exoPlayer ?: return
    if (player.currentPosition > 3000L) {
        seekToInternal(0L)
        return
    }

    val q = queue.value
    if (q.isEmpty()) return

    val prevIndex = queueController.getPreviousIndex() ?: return
    val prevTrack = q[prevIndex]
    if (prevTrack.isStreamingTrack) {
        val intercepted = onStreamingTrackRequestedCallback?.invoke(prevTrack, false) ?: false
        if (intercepted) return
    }
    playTrackInternal(prevTrack, isAutoCrossfadeOverlap = false)
}

internal fun AuraAudioPlayer.handleTrackEndedInternal(isAutoCrossfadeOverlap: Boolean = false) {
    when (repeatMode.value) {
        RepeatMode.ONE -> {
            seekToInternal(0L)
            exoPlayer?.play()
            startSmoothFadeIn(1200L)
        }
        RepeatMode.ALL, RepeatMode.OFF -> {
            playNextInternal(isAutoCrossfadeOverlap = isAutoCrossfadeOverlap)
        }
    }
}
