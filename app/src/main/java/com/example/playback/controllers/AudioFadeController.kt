package com.example.playback.controllers

import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Aura Music - Controlador Modular de Transiciones y Fundidos de Volumen (Fade-In / Fade-Out)
 *
 * Responsabilidades:
 * - Atenuación progresiva (fade-out) suave al acercarse al final de pista si el crossfade está habilitado.
 * - Rampa de entrada progresiva (fade-in) suave al reanudar reproducción o cambiar de pista.
 * - Memoria del volumen base original evitando que la app se quede atrapada en volúmenes reducidos.
 * - Manejo seguro de corrutinas en el hilo principal (Main Thread) para evitar excepciones de ExoPlayer.
 */
class AudioFadeController(
    private val scope: CoroutineScope
) {

    private var baseVolume = 1.0f
    private var fadeInJob: Job? = null
    var isFadeInOnResume: Boolean = true
        private set

    val isFadeInActive: Boolean
        get() = fadeInJob?.isActive == true

    fun setFadeInOnResumeEnabled(enabled: Boolean) {
        isFadeInOnResume = enabled
    }

    fun setVolume(volume: Float, exoPlayer: ExoPlayer?) {
        baseVolume = volume.coerceIn(0.0f, 1.0f)
        if (fadeInJob?.isActive != true) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                exoPlayer?.volume = baseVolume
            } else {
                scope.launch(Dispatchers.Main) {
                    exoPlayer?.volume = baseVolume
                }
            }
        }
    }

    fun getVolume(): Float = baseVolume

    fun triggerSmoothFadeIn(durationMs: Long = 1200L, exoPlayer: ExoPlayer?) {
        startSmoothFadeIn(durationMs, exoPlayer)
    }

    fun startSmoothFadeIn(durationMs: Long = 1500L, exoPlayer: ExoPlayer?) {
        val player = exoPlayer ?: return
        fadeInJob?.cancel()
        player.volume = 0.05f * baseVolume
        fadeInJob = scope.launch(Dispatchers.Main) {
            val steps = 20
            val delayStep = (durationMs / steps).coerceAtLeast(30L)
            for (i in 1..steps) {
                delay(delayStep)
                if (!isActive) break
                val factor = (i.toFloat() / steps.toFloat()).coerceIn(0.05f, 1.0f)
                player.volume = (baseVolume * factor).coerceIn(0.0f, 1.0f)
            }
            player.volume = baseVolume
        }
    }

    fun applyFadeOut(remainingMs: Long, crossfadeMs: Long, exoPlayer: ExoPlayer?) {
        val player = exoPlayer ?: return
        if (crossfadeMs > 0 && player.duration > 0 && player.isPlaying && fadeInJob?.isActive != true) {
            if (remainingMs in 0..crossfadeMs) {
                val factor = (remainingMs.toFloat() / crossfadeMs.toFloat()).coerceIn(0.05f, 1.0f)
                player.volume = (baseVolume * factor).coerceIn(0.0f, 1.0f)
            }
        }
    }

    fun restoreFullVolume(exoPlayer: ExoPlayer?) {
        fadeInJob?.cancel()
        exoPlayer?.volume = baseVolume
    }

    fun cancel() {
        fadeInJob?.cancel()
        fadeInJob = null
    }
}
