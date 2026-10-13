package com.example.core.playback

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.example.AuraApplication
import com.example.core.model.ABLoopState
import com.example.core.model.RepeatMode
import com.example.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Rastreador de progreso y disparador de transiciones para AuraAudioPlayer.
 * Rol arquitectónico: sondea ExoPlayer (75/180/1000ms), actualiza posición/duración,
 * ejecuta el bucle A-B, la pre-descarga de streaming similar y el disparo del
 * Crossfade Dual-Deck / Smart AutoMix DJ al final de cada pista.
 */
internal class PlaybackProgressTracker(
    private val scope: CoroutineScope,
    private val appContext: Context,
    private val getPlayer: () -> ExoPlayer?,
    private val getTrack: () -> Track?,
    private val getDuration: () -> Long,
    private val setDuration: (Long) -> Unit,
    private val setPosition: (Long) -> Unit,
    private val getAbLoopState: () -> ABLoopState,
    private val getCrossfadeSeconds: () -> Int,
    private val isDjAutomixEnabled: () -> Boolean,
    private val isDjEqCurveEnabled: () -> Boolean,
    private val getQueueSize: () -> Int,
    private val getRepeatMode: () -> RepeatMode,
    private val onDjAutomixTransition: (Boolean, Float) -> Unit,
    private val getNearEndPrefetch: () -> ((Track) -> Unit)?,
    private val onTrackEnded: (Boolean) -> Unit
) {

    private var progressJob: Job? = null
    private var widgetProgressTickCounter = 0
    private var crossfadeTriggeredForTrackId: Long = -1L
    private var streamingPrefetchTriggeredForId: Long = 0L
    private var isTransitioningTrack = false

    fun resetForNewTrack() {
        crossfadeTriggeredForTrackId = -1L
        streamingPrefetchTriggeredForId = 0L
    }

    fun start() {
        progressJob?.cancel()
        widgetProgressTickCounter = 0
        progressJob = scope.launch {

        progressJob?.cancel()
        widgetProgressTickCounter = 0
        progressJob = scope.launch {
            while (isActive) {
                val loopState = getAbLoopState()
                val isBackgroundGaming = AuraApplication.shouldSuspendBackgroundVisuals()
                var nextDelayMs = if (loopState.isLooping) 75L else 180L

                getPlayer()?.let { player ->
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = if (player.duration > 0) player.duration else getDuration()
                    if (player.duration > 0) {
                        setDuration(player.duration)
                    }

                    val crossfadeSecs = getCrossfadeSeconds()
                    val isCurrentLocal = getTrack()?.isStreamingTrack != true
                    val isDjMix = isDjAutomixEnabled() && isCurrentLocal
                    val effectiveCrossfadeSecs = if (isDjMix && crossfadeSecs == 0) 6 else crossfadeSecs
                    val remainingMs = if (dur > 0L) (dur - pos) else Long.MAX_VALUE
                    val crossfadeWindowMs = (effectiveCrossfadeSecs * 1000L).coerceAtLeast(4500L)
                    val isNearTransitionOrEnd = dur > 0L && remainingMs <= (crossfadeWindowMs + 1500L)

                    // Si estamos en segundo plano mientras el usuario juega, solo actualizamos los flujos
                    // visuales de alta frecuencia si realmente hay una transición activa, un bucle A-B o Mixtape
                    if (!isBackgroundGaming || loopState.isLooping || isNearTransitionOrEnd) {
                        setPosition(pos)
                    }

                    widgetProgressTickCounter++
                    val widgetTickThreshold = if (isBackgroundGaming) 5 else 12
                    if (widgetProgressTickCounter >= widgetTickThreshold) {
                        widgetProgressTickCounter = 0
                        setPosition(pos)
                        com.example.widget.AuraMusicWidgetProvider.pushPlaybackProgress(
                            context = appContext,
                            positionMs = pos,
                            durationMs = getDuration()
                        )
                    }

                    if (loopState.isLooping) {
                        val aMs = loopState.pointAMs ?: 0L
                        val bMs = loopState.pointBMs ?: Long.MAX_VALUE
                        if (pos >= bMs || pos < (aMs - 350L)) {
                            player.seekTo(aMs)
                            setPosition(aMs)
                        }
                    } else {
                        val curTrack = getTrack()
                        val curTrackId = curTrack?.id ?: -1L
                        val crossfadeMs = (effectiveCrossfadeSecs * 1000L).coerceIn(0L, 12000L)

                        // Si es una pista en modo Streaming y supera el 50% (o le quedan 20s),
                        // se avisa una sola vez al coordinador para calentar el vecino de la cola
                        // (capas A+B+C Head-Start) + consultar la canción similar de radio.
                        // El calentador post-play ya corrió al arrancar; esto es red de seguridad
                        // si la cola cambió a mitad de pista.
                        if (curTrack != null && curTrack.isStreamingTrack &&
                            streamingPrefetchTriggeredForId != curTrackId &&
                            dur > 5000L && (remainingMs in 1..20000L || pos > (dur * 50L / 100L))
                        ) {
                            streamingPrefetchTriggeredForId = curTrackId
                            getNearEndPrefetch()?.invoke(curTrack)
                        }

                        // Si el Crossfade profesional o Smart AutoMix DJ (música local) está activo y la pista entra
                        // en la ventana de los últimos N segundos (y dura al menos el doble del crossfade), disparamos el
                        // cruce Dual-Deck superpuesto: la canción saliente sigue sonando en el Deck B mientras
                        // la siguiente canción arranca simultáneamente en el Deck A sin pausa alguna.
                        val canOverlapNext = effectiveCrossfadeSecs > 0 &&
                            dur > (crossfadeMs + 4000L) &&
                            pos > 2500L &&
                            getRepeatMode() != RepeatMode.ONE &&
                            getQueueSize() > 1 &&
                            !isTransitioningTrack &&
                            crossfadeTriggeredForTrackId != curTrackId

                        // Smart Outro Cue Point (Smart AutoMix DJ para música local):
                        // Detecta cuando la energía vocal/instrumental de la canción saliente cae en el tramo final
                        // (o alcanza la ventana armónica de los últimos segundos) para iniciar la mezcla sin choques.
                        val currentIntensity = if (isDjMix) NativeAudioEngine.getAudioIntensity() else 0.25f
                        val shouldTriggerEarlyOutro = isDjMix &&
                            remainingMs in 800L..(crossfadeMs + 4500L) &&
                            (currentIntensity < 0.095f || remainingMs <= (crossfadeMs + 1200L))

                        val shouldTriggerCrossfadeWindow = remainingMs in 300L..crossfadeMs

                        if (canOverlapNext && player.isPlaying && (shouldTriggerCrossfadeWindow || shouldTriggerEarlyOutro)) {
                            crossfadeTriggeredForTrackId = curTrackId
                            isTransitioningTrack = true
                            if (isDjEqCurveEnabled() && isCurrentLocal) {
                                onDjAutomixTransition(true, 0.35f)
                            }
                            scope.launch(Dispatchers.Main) {
                                onTrackEnded(true)
                                delay(650L)
                                isTransitioningTrack = false
                            }
                        } else if (dur > 0 && player.isPlaying && !isTransitioningTrack) {
                            if (remainingMs in 1..250L) {
                                isTransitioningTrack = true
                                scope.launch(Dispatchers.Main) {
                                    onTrackEnded(false)
                                    delay(600L)
                                    isTransitioningTrack = false
                                }
                            }
                        }
                    }

                    // Si la app está en segundo plano (Modo Juego) y el usuario NO está usando A-B Loop
                    // ni está en los segundos finales de Crossfade/DJ Automix, espaciamos el sondeo a 1000 ms
                    // para reducir más de un 80% los despertares de CPU en el hilo principal.
                    nextDelayMs = when {
                        loopState.isLooping -> 75L
                        isBackgroundGaming && !isNearTransitionOrEnd -> 1000L
                        else -> 180L
                    }
                }
                delay(nextDelayMs)
            }
        }
    
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
    }
}
