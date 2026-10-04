package com.example.model

/**
 * Modelos de datos para configuración de efectos de audio avanzados:
 * - Audio Espacial 8D Binaural para auriculares.
 * - Suite Reverb Híbrida (Presets ambientales + Personalización libre de tamaño, decay y wet).
 * - Temporizador de Apagado (Sleep Timer) con cuenta atrás y desvanecimiento suave de 10s.
 * - Transición de pistas (Crossfade configurable y Reproducción Gapless).
 */

enum class ReverbPreset(
    val title: String,
    val description: String,
    val androidPreset: Short,
    val defaultRoomSize: Float,
    val defaultDecayMs: Int,
    val defaultLevelDb: Float
) {
    OFF("Desactivado", "Sonido original directo", 0, 0.0f, 0, -60.0f),
    STUDIO("Estudio", "Acústica íntima y controlada", 1, 0.25f, 600, -10.0f),
    ROOM("Sala", "Sensación natural de habitación", 2, 0.45f, 1200, -6.0f),
    CLUB("Club", "Resonancia envolvente para directos", 6, 0.65f, 1800, -3.0f),
    HALL("Gran Hall", "Profundidad amplia de teatro", 4, 0.85f, 2600, -1.0f),
    CATHEDRAL("Catedral", "Larga resonancia y amplitud mística", 5, 1.0f, 4000, 1.0f),
    SPACE_ECHO("Eco Espacial", "Reflexiones extendidas cósmicas", 3, 1.2f, 5000, 2.0f);
}

data class ReverbConfig(
    val isEnabled: Boolean = false,
    val preset: ReverbPreset = ReverbPreset.OFF,
    val roomSize: Float = 0.5f,      // 0.1f a 2.0f
    val decayMs: Int = 1500,         // 200 a 6000 ms
    val reverbLevelDb: Float = -4.0f // -24.0f a +6.0f dB
)

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

/**
 * Estado del Repetidor de Segmento A-B (A-B Loop).
 * Permite fijar un punto de inicio A y un punto de fin B para ciclar continuamente
 * un fragmento de la canción actual.
 */
data class ABLoopState(
    val pointAMs: Long? = null,
    val pointBMs: Long? = null,
    val isEnabled: Boolean = false
) {
    val enabled: Boolean
        get() = isEnabled

    val isLooping: Boolean
        get() = isEnabled && pointAMs != null && pointBMs != null && pointBMs > pointAMs

    val isLoopingActive: Boolean
        get() = isLooping
}

