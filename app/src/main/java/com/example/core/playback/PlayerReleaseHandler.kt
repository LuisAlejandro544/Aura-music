package com.example.core.playback

import kotlinx.coroutines.cancel

/**
 * Liberación ordenada de reproductores, scopes y DSP.
 * Rol arquitectónico: extensiones internas de AuraAudioPlayer; la API pública no cambia.
 */
internal fun AuraAudioPlayer.startProgressTrackingInternal() = progressTracker.start()

internal fun AuraAudioPlayer.stopProgressTrackingInternal() {
    progressTracker.stop()
    playbackParamsManager.cancel()
}

internal fun AuraAudioPlayer.releaseInternal() {
    stopProgressTrackingInternal()
    fadeController.stopOutgoingDeckImmediately(outgoingExoPlayer)
    fadeController.cancel()
    playerScope.cancel()
    mediaSessionBridge?.release()
    mediaSessionBridge = null
    effectManager.flushBuffers()
    effectManager.release()
    try {
        outgoingExoPlayer?.release()
    } catch (_: Throwable) {}
    outgoingExoPlayer = null
    exoPlayer?.release()
    exoPlayer = null
}
