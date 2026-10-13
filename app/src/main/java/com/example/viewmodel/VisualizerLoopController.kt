package com.example.viewmodel

import com.example.core.model.NowPlayingDesignMode
import com.example.core.model.Track
import com.example.core.model.VideoDisplayMode
import com.example.core.playback.NativeAudioEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Bucle de espectro FFT C++20 para MusicViewModel.
 * Rol arquitectónico: muestrea las 28 bandas del visualizador e intensidad
 * en Dispatchers.Default, con suspensión profunda en 2º plano (Modo Juego),
 * pausa en Modo Cinemático (sin barras) y frecuencia reducida en streaming
 * con Video Canvas para ahorrar CPU y batería.
 */
class VisualizerLoopController(
    private val bandsFlow: MutableStateFlow<FloatArray>,
    private val intensityFlow: MutableStateFlow<Float>
) {

    fun start(
        scope: CoroutineScope,
        isPlaying: () -> Boolean,
        isExpanded: () -> Boolean,
        getCurrentTrack: () -> Track?,
        getPositionMs: () -> Long,
        getVideoDisplayMode: () -> VideoDisplayMode,
        getDesignMode: () -> NowPlayingDesignMode
    ) {
        scope.launch(Dispatchers.Default) {
            val buffer = FloatArray(28)
            var phase = 0.0
            while (true) {
                val isBackgroundSuspended = com.example.AuraApplication.shouldSuspendBackgroundVisuals()
                if (isBackgroundSuspended) {
                    delay(850L)
                    continue
                }

                val playing = isPlaying()
                val expanded = isExpanded()
                if (playing && expanded) {
                    val currentTrk = getCurrentTrack()
                    val hasVideo = !currentTrk?.videoUri.isNullOrEmpty()
                    val isFullscreenVid = hasVideo && getVideoDisplayMode().isFullscreen
                    val isCinematicMode = when (getDesignMode()) {
                        NowPlayingDesignMode.CINEMATIC_CANVAS -> true
                        NowPlayingDesignMode.CLASSIC -> false
                        NowPlayingDesignMode.AUTO -> isFullscreenVid
                    }

                    if (currentTrk?.isStreamingTrack == true && isCinematicMode) {
                        intensityFlow.value = NativeAudioEngine.getAudioIntensity().coerceIn(0.15f, 0.85f)
                        delay(220L)
                        continue
                    }

                    var liveIntensity = NativeAudioEngine.getAudioIntensity()

                    // Muestrear las 28 bandas cuando estamos en Modo Clásico (donde existe AudioVisualizer)
                    if (!isCinematicMode) {
                        NativeAudioEngine.getVisualizerBands(buffer)

                        // Si por alguna ruta de decodificación aún no hay variación entre bandas, aplicar pulso rítmico en tiempo real
                        var minB = 1.0f
                        var maxB = 0.0f
                        for (b in buffer) {
                            if (b < minB) minB = b
                            if (b > maxB) maxB = b
                        }
                        if ((maxB - minB) < 0.015f || maxB <= 0.19f) {
                            phase += 0.25
                            val posSec = (getPositionMs() / 1000.0)
                            val beatPulse = (kotlin.math.sin(posSec * 7.2 + phase * 0.65) * 0.5 + 0.5).toFloat()
                            for (i in 0 until 28) {
                                val freqWeight = 1.0f - (i / 36.0f)
                                val w1 = kotlin.math.sin(phase * 1.35 + i * 0.48).toFloat() * 0.5f + 0.5f
                                val w2 = kotlin.math.cos(phase * 0.85 - i * 0.31 + posSec * 4.2).toFloat() * 0.5f + 0.5f
                                buffer[i] = (0.14f + (w1 * 0.44f + w2 * 0.32f + beatPulse * 0.24f) * freqWeight)
                                    .coerceIn(0.10f, 0.94f)
                            }
                            liveIntensity = (0.22f + beatPulse * 0.45f).coerceIn(0.18f, 0.82f)
                        }

                        bandsFlow.value = buffer.copyOf()
                    }
                    intensityFlow.value = liveIntensity
                    delay(if (isCinematicMode) 110L else 36L)
                } else {
                    var needsDecay = false
                    for (i in buffer.indices) {
                        if (buffer[i] > 0.08f) {
                            buffer[i] = buffer[i] * 0.82f
                            needsDecay = true
                        }
                    }
                    if (intensityFlow.value > 0.06f) {
                        intensityFlow.value = intensityFlow.value * 0.80f
                        needsDecay = true
                    }
                    if (needsDecay) {
                        bandsFlow.value = buffer.copyOf()
                        delay(45L)
                    } else {
                        delay(350L)
                    }
                }
            }
        }
    }
}
