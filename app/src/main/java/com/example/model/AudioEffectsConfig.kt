package com.example.model

/**
 * Modelos de datos para configuración de efectos de audio avanzados:
 * - Audio Espacial 8D Binaural para auriculares.
 * - Temporizador de Apagado (Sleep Timer) con cuenta atrás y desvanecimiento suave de 10s.
 * - Transición de pistas (Crossfade configurable y Reproducción Gapless).
 */

data class Spatial8DConfig(
    val enabled: Boolean = false,
    val orbitSpeedSeconds: Float = 10.0f, // Rango de 4 a 30 segundos por rotación
    val spatialIntensity: Float = 0.85f,   // Amplitud espacial de 0.0 a 1.0
    val roomDepth: Float = 0.35f          // Sensación de acústica de sala de 0.0 a 1.0
)

data class SleepTimerState(
    val isActive: Boolean = false,
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0,
    val isFadingOut: Boolean = false
) {
    val formattedRemaining: String
        get() {
            if (remainingSeconds <= 0) return "0:00"
            val m = remainingSeconds / 60
            val s = remainingSeconds % 60
            return "%d:%02d".format(m, s)
        }
}

data class PlaybackTransitionConfig(
    val crossfadeSeconds: Int = 0, // 0 = Gapless directo; 1..12 = segundos de fundido cruzado
    val isGapless: Boolean = true
)
