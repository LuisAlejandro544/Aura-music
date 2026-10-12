package com.example.core.playback

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gestor con throttling de velocidad y tono para AuraAudioPlayer.
 * Rol arquitectónico: aplica PlaybackParameters en Dispatchers.Main con debounce de 40ms
 * para no saturar ExoPlayer ni pausar la canción accidentalmente.
 */
internal class PlaybackParametersManager(
    private val scope: CoroutineScope,
    private val getPlayer: () -> ExoPlayer?,
    private val getSpeed: () -> Float,
    private val getPitch: () -> Float,
    private val setSpeedPitch: (Float, Float) -> Unit
) {

    private var job: Job? = null

    fun schedule() {
        job?.cancel()
        job = scope.launch(Dispatchers.Main) {
            delay(40L)
            applyNow(getSpeed(), getPitch())
        }
    }

    fun reset() {
        job?.cancel()
        setSpeedPitch(1.0f, 1.0f)
        applyNow(1.0f, 1.0f)
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    private fun applyNow(speed: Float, pitch: Float) {
        val player = getPlayer() ?: return
        val current = player.playbackParameters
        if (kotlin.math.abs(current.speed - speed) > 0.01f || kotlin.math.abs(current.pitch - pitch) > 0.01f) {
            val wasPlaying = player.isPlaying || player.playWhenReady
            try {
                player.playbackParameters = PlaybackParameters(speed, pitch)
                if (wasPlaying && !player.isPlaying && player.playbackState != Player.STATE_ENDED) {
                    player.play()
                }
            } catch (t: Throwable) {
                AuraDebugManager.logWarning("ExoPlayer", "Fallo al aplicar velocidad/tono: ${t.message}", t)
            }
        }
    }
}
