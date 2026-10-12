package com.example.core.playback

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.core.model.Track

/**
 * Listener de estado de ExoPlayer para AuraAudioPlayer.
 * Rol arquitectónico: refleja buffering, duración, errores y cambios de reproducción
 * en los flujos de la fachada sin acoplarla a Player.Listener.
 */
internal class PlayerStateListener(
    private val appContext: Context,
    private val player: ExoPlayer,
    private val getTrack: () -> Track?,
    private val getDuration: () -> Long,
    private val setPlaying: (Boolean) -> Unit,
    private val setDuration: (Long) -> Unit,
    private val setBuffering: (Boolean) -> Unit,
    private val setNetworkStalled: (Boolean) -> Unit,
    private val setPlaybackError: (String?) -> Unit,
    private val attachAudioSession: (Int) -> Unit,
    private val onTrackEnded: () -> Unit,
    private val onPlayerError: (PlaybackException) -> Unit,
    private val onStartTracking: () -> Unit,
    private val onStopTracking: () -> Unit
) : Player.Listener {

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        setPlaying(isPlaying)
        com.example.widget.AuraMusicWidgetProvider.pushPlaybackState(
            context = appContext,
            track = getTrack(),
            isPlaying = isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.coerceAtLeast(getDuration())
        )
        if (isPlaying) {
            onStartTracking()
        } else {
            onStopTracking()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> {
                setBuffering(false)
                setNetworkStalled(false)
                setDuration(player.duration.coerceAtLeast(0L))
                setPlaybackError(null)
                attachAudioSession(player.audioSessionId)
                com.example.widget.AuraMusicWidgetProvider.pushPlaybackState(
                    context = appContext,
                    track = getTrack(),
                    isPlaying = player.isPlaying,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = getDuration()
                )
            }
            Player.STATE_ENDED -> {
                setBuffering(false)
                onTrackEnded()
            }
            Player.STATE_IDLE -> {
                setBuffering(false)
            }
            Player.STATE_BUFFERING -> {
                val cur = getTrack()
                val isRemoteStream = cur?.isStreamingTrack == true &&
                    (cur.uriString.startsWith("http://", ignoreCase = true) ||
                        cur.uriString.startsWith("https://", ignoreCase = true))
                setBuffering(isRemoteStream)
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        onPlayerError(error)
    }
}
