package com.example.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.core.data.importer.AudioSilenceTrimmer
import com.example.core.model.Track
import com.example.core.playback.controllers.AudioFadeController

/**
 * Gestor de transiciones Dual-Deck para AuraAudioPlayer.
 * Rol arquitectónico: ejecuta el traspaso superpuesto de la pista saliente al Deck B
 * y la entrada con crossfade de igual potencia o fade-in de la pista entrante.
 */
internal class DualDeckTransitionManager(
    private val fadeController: AudioFadeController,
    private val getPlayer: () -> ExoPlayer?,
    private val getOutgoingDeck: () -> ExoPlayer?,
    private val getCurrentTrack: () -> Track?,
    private val getCrossfadeSeconds: () -> Int,
    private val isDjAutomixEnabled: () -> Boolean,
    private val isDjEqCurveEnabled: () -> Boolean,
    private val resolveClipBounds: (String) -> AudioSilenceTrimmer.ClippingBounds?,
    private val onDjAutomixTransition: (Boolean, Float) -> Unit
) {

    data class Handoff(
        val effectiveCrossfadeSecs: Int,
        val dualDeckActive: Boolean,
        val isLocalPair: Boolean
    )

    /**
     * Transfiere la cola final de la pista saliente al Deck B auxiliar.
     * Debe invocarse antes de actualizar la pista actual en la fachada.
     */
    fun prepareHandoff(newTrack: Track): Handoff {
        val player = getPlayer()
        val outDeck = getOutgoingDeck()
        val previousTrack = getCurrentTrack()
        val isLocalPair = previousTrack != null && !previousTrack.isStreamingTrack && !newTrack.isStreamingTrack
        val effectiveCrossfadeSecs =
            if (isDjAutomixEnabled() && isLocalPair && getCrossfadeSeconds() == 0) 6 else getCrossfadeSeconds()

        // Si el Crossfade o Smart AutoMix DJ (música local) está activo y había una pista sonando
        // en el reproductor primario, transferimos su cola final al Deck B auxiliar para que ambas
        // suenen superpuestas.
        var succeeded = false
        if (player != null && effectiveCrossfadeSecs > 0 && outDeck != null &&
            previousTrack != null && previousTrack.id != newTrack.id &&
            (player.isPlaying || player.playWhenReady)
        ) {
            val outgoingPos = player.currentPosition.coerceAtLeast(0L)
            val outgoingDur = player.duration
            if (outgoingDur <= 0L || (outgoingDur - outgoingPos) > 350L) {
                try {
                    outDeck.stop()
                    outDeck.clearMediaItems()
                    val prevMediaUri = TrackMediaFactory.resolveMediaUri(previousTrack.uriString)
                    val prevClipBounds = if (previousTrack.isStreamingTrack) {
                        null
                    } else {
                        resolveClipBounds(previousTrack.uriString)
                    }
                    val absoluteSeekPos = (prevClipBounds?.startMs ?: 0L) + outgoingPos
                    val outItemBuilder = MediaItem.Builder().setUri(prevMediaUri)
                    if (prevClipBounds != null && prevClipBounds.endMs > absoluteSeekPos) {
                        outItemBuilder.setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(0L)
                                .setEndPositionMs(prevClipBounds.endMs)
                                .build()
                        )
                    }
                    outDeck.setMediaItem(outItemBuilder.build(), absoluteSeekPos)
                    outDeck.playbackParameters = player.playbackParameters
                    // La Canción A comienza la transición al 100% del volumen base para bajar
                    // simultáneamente de 100% a 0%.
                    outDeck.volume = fadeController.getVolume().coerceIn(0.0f, 1.0f)
                    outDeck.prepare()
                    outDeck.play()
                    succeeded = true
                } catch (_: Throwable) {
                    succeeded = false
                }
            }
        } else {
            fadeController.stopOutgoingDeckImmediately(outDeck)
        }
        return Handoff(effectiveCrossfadeSecs, succeeded, isLocalPair)
    }

    /** Inicia la entrada de la pista nueva: crossfade superpuesto o fade-in suave. */
    fun startEntryTransition(handoff: Handoff, isAutoCrossfadeOverlap: Boolean) {
        val player = getPlayer() ?: return
        val outDeck = getOutgoingDeck()
        if (handoff.dualDeckActive && outDeck != null) {
            val overlapDurationMs = if (isAutoCrossfadeOverlap) {
                (handoff.effectiveCrossfadeSecs * 1000L).coerceIn(1200L, 12000L)
            } else {
                // En cambio manual de canción con Crossfade activo, mezcla sedosa y rápida (1.6s a 3.0s).
                (handoff.effectiveCrossfadeSecs * 650L).coerceIn(1500L, 3200L)
            }
            val applyEqCurve = isDjEqCurveEnabled() && handoff.isLocalPair
            fadeController.startDualDeckCrossfade(
                durationMs = overlapDurationMs,
                outgoingPlayer = outDeck,
                incomingPlayer = player,
                onProgressStep = { progress ->
                    if (applyEqCurve) {
                        // Modulación progresiva Smart AutoMix DJ (pico de limpieza en el centro de la mezcla).
                        val bellCurve = kotlin.math.sin(progress * kotlin.math.PI).toFloat().coerceIn(0.15f, 1.0f)
                        onDjAutomixTransition(true, bellCurve)
                    }
                }
            ) {
                onDjAutomixTransition(false, 0.0f)
            }
        } else {
            val fadeDurationMs = if (handoff.effectiveCrossfadeSecs > 0) {
                (handoff.effectiveCrossfadeSecs * 1000L).coerceIn(1200L, 5000L)
            } else if (fadeController.isFadeInOnResume) {
                1500L
            } else {
                1000L
            }
            fadeController.startSmoothFadeIn(fadeDurationMs, player)
        }
    }
}
