package com.example.playback.controllers

import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Aura Music - Controlador Modular de Transiciones y Fundidos Profesionales (Dual-Deck Crossfade & Fade-In/Out)
 *
 * Responsabilidades:
 * - Mezcla superpuesta profesional de doble reproductor (Dual-Deck Crossfade) con curva de igual potencia
 *   (Equal-Power S-Curve: sin/cos) para que cuando una canción baje, la siguiente empiece simultáneamente
 *   sin caída de volumen en el centro ni pausa audible.
 * - Rampa de entrada progresiva (fade-in) suave al reanudar reproducción o cambiar de pista.
 * - Memoria del volumen base original evitando que la app se quede atrapada en volúmenes reducidos.
 * - Manejo seguro de corrutinas en el hilo principal (Main Thread) para evitar excepciones de ExoPlayer.
 */
class AudioFadeController(
    private val scope: CoroutineScope
) {

    private var baseVolume = 1.0f
    private var fadeInJob: Job? = null
    private var dualCrossfadeJob: Job? = null
    var isFadeInOnResume: Boolean = true
        private set

    val isFadeInActive: Boolean
        get() = fadeInJob?.isActive == true

    val isDualCrossfadeActive: Boolean
        get() = dualCrossfadeJob?.isActive == true

    fun setFadeInOnResumeEnabled(enabled: Boolean) {
        isFadeInOnResume = enabled
    }

    fun setVolume(volume: Float, exoPlayer: ExoPlayer?, outgoingPlayer: ExoPlayer? = null) {
        baseVolume = volume.coerceIn(0.0f, 1.0f)
        if (fadeInJob?.isActive != true && dualCrossfadeJob?.isActive != true) {
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
            val steps = 25
            val delayStep = (durationMs / steps).coerceAtLeast(25L)
            for (i in 1..steps) {
                delay(delayStep)
                if (!isActive) break
                val progress = (i.toFloat() / steps.toFloat()).coerceIn(0.0f, 1.0f)
                // Curva sinusoidal de igual potencia para subida orgánica de estudio
                val factor = sin(progress * (PI / 2.0)).toFloat().coerceIn(0.05f, 1.0f)
                player.volume = (baseVolume * factor).coerceIn(0.0f, 1.0f)
            }
            player.volume = baseVolume
        }
    }

    /**
     * Ejecuta una mezcla superpuesta real de estudio (Dual-Deck Crossfade) entre [outgoingPlayer]
     * (la canción que está terminando o saliendo) e [incomingPlayer] (la nueva canción que empieza),
     * aplicando una curva de igual potencia (coseno para la saliente, seno para la entrante).
     */
    fun startDualDeckCrossfade(
        durationMs: Long,
        outgoingPlayer: ExoPlayer?,
        incomingPlayer: ExoPlayer?,
        onCrossfadeCompleted: () -> Unit = {}
    ) {
        val inPlayer = incomingPlayer ?: return
        fadeInJob?.cancel()
        dualCrossfadeJob?.cancel()

        val safeDurationMs = durationMs.coerceIn(800L, 12000L)
        if (outgoingPlayer == null || !outgoingPlayer.isPlaying) {
            startSmoothFadeIn(safeDurationMs.coerceAtMost(2500L), inPlayer)
            onCrossfadeCompleted()
            return
        }

        val initialOutgoingVol = outgoingPlayer.volume.coerceIn(0.15f * baseVolume, baseVolume)
        inPlayer.volume = 0.02f * baseVolume

        dualCrossfadeJob = scope.launch(Dispatchers.Main) {
            val steps = (safeDurationMs / 40L).toInt().coerceIn(20, 200)
            val stepDelay = (safeDurationMs / steps).coerceAtLeast(25L)

            for (step in 1..steps) {
                if (!isActive) break
                val t = (step.toFloat() / steps.toFloat()).coerceIn(0.0f, 1.0f)
                // Curva Equal-Power (sin^2 + cos^2 = 1): mantiene la energía acústica constante en el cruce
                val inGain = sin(t * (PI / 2.0)).toFloat().coerceIn(0.0f, 1.0f)
                val outGain = cos(t * (PI / 2.0)).toFloat().coerceIn(0.0f, 1.0f)

                inPlayer.volume = (baseVolume * inGain).coerceIn(0.0f, 1.0f)
                try {
                    if (outgoingPlayer.isPlaying) {
                        outgoingPlayer.volume = (initialOutgoingVol * outGain).coerceIn(0.0f, 1.0f)
                    }
                } catch (_: Throwable) {}

                delay(stepDelay)
            }

            inPlayer.volume = baseVolume
            try {
                outgoingPlayer.pause()
                outgoingPlayer.stop()
                outgoingPlayer.clearMediaItems()
                outgoingPlayer.volume = baseVolume
            } catch (_: Throwable) {}
            onCrossfadeCompleted()
        }
    }

    fun applyFadeOut(remainingMs: Long, crossfadeMs: Long, exoPlayer: ExoPlayer?) {
        val player = exoPlayer ?: return
        if (crossfadeMs > 0 && player.duration > 0 && player.isPlaying && fadeInJob?.isActive != true && dualCrossfadeJob?.isActive != true) {
            if (remainingMs in 0..crossfadeMs) {
                val t = (1.0f - (remainingMs.toFloat() / crossfadeMs.toFloat())).coerceIn(0.0f, 1.0f)
                val factor = cos(t * (PI / 2.0)).toFloat().coerceIn(0.05f, 1.0f)
                player.volume = (baseVolume * factor).coerceIn(0.0f, 1.0f)
            }
        }
    }

    fun stopOutgoingDeckImmediately(outgoingPlayer: ExoPlayer?) {
        dualCrossfadeJob?.cancel()
        dualCrossfadeJob = null
        try {
            outgoingPlayer?.pause()
            outgoingPlayer?.stop()
            outgoingPlayer?.clearMediaItems()
        } catch (_: Throwable) {}
    }

    fun restoreFullVolume(exoPlayer: ExoPlayer?) {
        fadeInJob?.cancel()
        dualCrossfadeJob?.cancel()
        exoPlayer?.volume = baseVolume
    }

    fun cancel() {
        fadeInJob?.cancel()
        fadeInJob = null
        dualCrossfadeJob?.cancel()
        dualCrossfadeJob = null
    }
}

