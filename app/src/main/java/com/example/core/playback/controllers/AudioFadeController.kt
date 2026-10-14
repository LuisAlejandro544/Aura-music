package com.example.core.playback.controllers

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
        try { player.volume = 0.05f * baseVolume } catch (_: Throwable) {}
        fadeInJob = scope.launch(Dispatchers.Main) {
            try {
            val steps = 25
            val delayStep = (durationMs / steps).coerceAtLeast(25L)
            for (i in 1..steps) {
                delay(delayStep)
                if (!isActive) break
                val progress = (i.toFloat() / steps.toFloat()).coerceIn(0.0f, 1.0f)
                // Curva sinusoidal de igual potencia para subida orgánica de estudio
                val factor = sin(progress * (PI / 2.0)).toFloat().coerceIn(0.05f, 1.0f)
                try { player.volume = (baseVolume * factor).coerceIn(0.0f, 1.0f) } catch (_: Throwable) { break }
            }
            } finally {
                // FIX: si el Job se cancela a mitad de rampa, no quedar atrapado en volumen bajo.
                try { if (player.playbackState != androidx.media3.common.Player.STATE_IDLE) player.volume = baseVolume } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Ejecuta una mezcla superpuesta real de estudio (Dual-Deck Crossfade) entre [outgoingPlayer]
     * (Canción A que está saliendo) e [incomingPlayer] (Canción B que empieza),
     * aplicando dos rampas progresivas 100% simultáneas:
     * - Canción A desciende progresivamente de 100% (baseVolume) a 0% (0.0f).
     * - Canción B asciende progresivamente de 0% (0.0f) a 100% (baseVolume) en el mismo instante.
     */
    fun startDualDeckCrossfade(
        durationMs: Long,
        outgoingPlayer: ExoPlayer?,
        incomingPlayer: ExoPlayer?,
        onProgressStep: ((Float) -> Unit)? = null,
        onCrossfadeCompleted: () -> Unit = {}
    ) {
        val inPlayer = incomingPlayer ?: return
        fadeInJob?.cancel()
        dualCrossfadeJob?.cancel()

        val safeDurationMs = durationMs.coerceIn(800L, 12000L)
        val isOutgoingReadyOrStarting = outgoingPlayer != null &&
            (outgoingPlayer.isPlaying || outgoingPlayer.playWhenReady || outgoingPlayer.playbackState == androidx.media3.common.Player.STATE_BUFFERING)
        if (outgoingPlayer == null || !isOutgoingReadyOrStarting) {
            startSmoothFadeIn(safeDurationMs.coerceAtMost(2500L), inPlayer)
            // FIX: no completar sincrónico antes del fade; esperar al Job.
            scope.launch(Dispatchers.Main) {
                try { fadeInJob?.join() } catch (_: Throwable) {}
                if (isActive) onCrossfadeCompleted()
            }
            return
        }

        // La Canción A inicia al 100% del volumen base y la Canción B inicia en 0%
        val startVol = baseVolume.coerceIn(0.0f, 1.0f)
        try {
            outgoingPlayer.volume = startVol
        } catch (_: Throwable) {}
        try { inPlayer.volume = 0.0f } catch (_: Throwable) { return }

        dualCrossfadeJob = scope.launch(Dispatchers.Main) {
            try {
            // Esperar un breve instante (máx 400ms) si los reproductores aún están preparando el primer buffer PCM
            // para que ambas rampas (100% -> 0% y 0% -> 100%) arranquen exactamente al unísono
            var waitTicks = 0
            while (
                isActive &&
                (!inPlayer.isPlaying || !outgoingPlayer.isPlaying) &&
                (inPlayer.playbackState == androidx.media3.common.Player.STATE_BUFFERING ||
                    outgoingPlayer.playbackState == androidx.media3.common.Player.STATE_BUFFERING) &&
                waitTicks < 16
            ) {
                delay(25L)
                waitTicks++
            }

            val steps = (safeDurationMs / 35L).toInt().coerceIn(25, 240)
            val stepDelay = (safeDurationMs / steps).coerceAtLeast(20L)

            for (step in 1..steps) {
                if (!isActive) break
                val t = (step.toFloat() / steps.toFloat()).coerceIn(0.0f, 1.0f)
                // Curva de igual potencia / rampa simultánea:
                // Canción B sube de 0% a 100% mientras Canción A baja de 100% a 0%
                val inGain = sin(t * (PI / 2.0)).toFloat().coerceIn(0.0f, 1.0f)
                val outGain = cos(t * (PI / 2.0)).toFloat().coerceIn(0.0f, 1.0f)

                val currentBase = baseVolume.coerceIn(0.0f, 1.0f)
                try { inPlayer.volume = (currentBase * inGain).coerceIn(0.0f, 1.0f) } catch (_: Throwable) { break }
                try {
                    outgoingPlayer.volume = (currentBase * outGain).coerceIn(0.0f, 1.0f)
                } catch (_: Throwable) {}

                onProgressStep?.invoke(t)
                delay(stepDelay)
            }

            try { inPlayer.volume = baseVolume } catch (_: Throwable) {}
            try {
                outgoingPlayer.volume = 0.0f
                outgoingPlayer.pause()
                outgoingPlayer.stop()
                outgoingPlayer.clearMediaItems()
                outgoingPlayer.volume = baseVolume
            } catch (_: Throwable) {}
            } finally {
                // FIX: skip a mitad de cruce no deja la entrante baja.
                try { if (inPlayer.playbackState != androidx.media3.common.Player.STATE_IDLE) inPlayer.volume = baseVolume } catch (_: Throwable) {}
            }
            // Solo marcar completo si no se canceló (evita liberar Deck B antes de tiempo).
            if (isActive) onCrossfadeCompleted()
        }
    }

    fun applyFadeOut(remainingMs: Long, crossfadeMs: Long, exoPlayer: ExoPlayer?) {
        val player = exoPlayer ?: return
        if (crossfadeMs > 0 && player.duration > 0 && player.isPlaying && fadeInJob?.isActive != true && dualCrossfadeJob?.isActive != true) {
            if (remainingMs in 0..crossfadeMs) {
                val t = (1.0f - (remainingMs.toFloat() / crossfadeMs.toFloat())).coerceIn(0.0f, 1.0f)
                val factor = (1.0f - t).coerceIn(0.0f, 1.0f)
                try { player.volume = (baseVolume * factor).coerceIn(0.0f, 1.0f) } catch (_: Throwable) {}
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
        try { exoPlayer?.volume = baseVolume } catch (_: Throwable) {}
    }

    fun cancel() {
        fadeInJob?.cancel()
        fadeInJob = null
        dualCrossfadeJob?.cancel()
        dualCrossfadeJob = null
    }
}

