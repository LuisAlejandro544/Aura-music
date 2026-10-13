package com.example.core.playback

import androidx.media3.exoplayer.ExoPlayer
import com.example.core.model.RepeatMode
import com.example.core.model.Track
import com.example.core.playback.controllers.AudioFadeController
import com.example.core.playback.controllers.PlayerQueueController

/**
 * Conductor de transporte para AuraAudioPlayer.
 * Rol arquitectónico: concentra pausa, reproducción, conmutación,
 * saltos de tiempo y avance/retroceso de cola (incluida la intercepción
 * de pistas de streaming sin resolver), sin acoplar la fachada a ExoPlayer.
 */
internal class PlayerTransportDriver(
    private val getPlayer: () -> ExoPlayer?,
    private val getOutgoingDeck: () -> ExoPlayer?,
    private val queueController: PlayerQueueController,
    private val fadeController: AudioFadeController,
    private val effectManager: AudioEffectManager,
    private val getRepeatMode: () -> RepeatMode,
    private val getQueue: () -> List<Track>,
    private val getCurrentTrack: () -> Track?,
    private val getDuration: () -> Long,
    private val setPosition: (Long) -> Unit,
    private val shouldInterceptStreaming: (Track, Boolean) -> Boolean,
    private val startTrack: (Track, Boolean) -> Unit,
    private val ensureServiceStarted: () -> Unit
) {

    fun playTrackList(tracks: List<Track>, startIndex: Int = 0) {
        val selected = queueController.setQueue(tracks, startIndex) ?: return
        startTrack(selected, false)
    }

    fun pause() {
        fadeController.stopOutgoingDeckImmediately(getOutgoingDeck())
        getPlayer()?.pause()
        effectManager.flushBuffers()
    }

    fun play() {
        val player = getPlayer() ?: return
        if (getCurrentTrack() == null && getQueue().isNotEmpty()) {
            playTrackList(getQueue(), 0)
            return
        }
        ensureServiceStarted()
        player.play()
        if (fadeController.isFadeInOnResume) {
            fadeController.startSmoothFadeIn(1200L, player)
        } else {
            fadeController.restoreFullVolume(player)
        }
    }

    fun togglePlayPause() {
        val player = getPlayer() ?: return
        if (player.isPlaying) {
            pause()
        } else {
            if (getCurrentTrack() == null && getQueue().isNotEmpty()) {
                playTrackList(getQueue(), 0)
            } else {
                ensureServiceStarted()
                player.play()
                if (fadeController.isFadeInOnResume) {
                    fadeController.startSmoothFadeIn(1200L, player)
                } else {
                    fadeController.restoreFullVolume(player)
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val player = getPlayer() ?: return
        fadeController.stopOutgoingDeckImmediately(getOutgoingDeck())
        val clamped = positionMs.coerceIn(0L, getDuration())
        effectManager.flushBuffers()
        player.seekTo(clamped)
        setPosition(clamped)
    }

    fun playNext(isAutoCrossfadeOverlap: Boolean = false) {
        val q = getQueue()
        if (q.isEmpty()) return

        if (getRepeatMode() == RepeatMode.ONE) {
            getCurrentTrack()?.let { startTrack(it, false) }
            return
        }

        val nextIndex = queueController.getNextIndex() ?: return
        val nextTrack = q[nextIndex]
        if (nextTrack.isStreamingTrack && shouldInterceptStreaming(nextTrack, isAutoCrossfadeOverlap)) {
            return
        }
        startTrack(nextTrack, isAutoCrossfadeOverlap)
    }

    fun playPrevious() {
        val player = getPlayer() ?: return
        if (player.currentPosition > 3000L) {
            seekTo(0L)
            return
        }

        val q = getQueue()
        if (q.isEmpty()) return

        val prevIndex = queueController.getPreviousIndex() ?: return
        val prevTrack = q[prevIndex]
        if (prevTrack.isStreamingTrack && shouldInterceptStreaming(prevTrack, false)) {
            return
        }
        startTrack(prevTrack, false)
    }

    fun handleTrackEnded(isAutoCrossfadeOverlap: Boolean = false) {
        when (getRepeatMode()) {
            RepeatMode.ONE -> {
                seekTo(0L)
                getPlayer()?.play()
                fadeController.startSmoothFadeIn(1200L, getPlayer())
            }
            RepeatMode.ALL, RepeatMode.OFF -> {
                playNext(isAutoCrossfadeOverlap = isAutoCrossfadeOverlap)
            }
        }
    }
}
