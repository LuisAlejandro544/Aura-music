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
    val is16DMode: Boolean = false,        // false = 8D Clásico (Órbita 360°), true = 16D Multi-Órbita (Doble Capa)
    val orbitSpeedSeconds: Float = 10.0f,  // Rango de 4 a 30 segundos por rotación
    val spatialIntensity: Float = 0.85f,   // Amplitud espacial de 0.0 a 1.0
    val roomDepth: Float = 0.35f           // Sensación de acústica de sala de 0.0 a 1.0
)

/**
 * Configuración del Clarificador de Voces HD en C++20 (VocalClarityProcessor).
 * Realza el canal central (Mid) y aplica curvas de presencia (2.8 kHz) y articulación (5.2 kHz)
 * atenuando resonancias opacas (260 Hz) para máxima inteligibilidad vocal.
 */
data class VocalClarityConfig(
    val enabled: Boolean = false,
    val strength: Float = 0.65f            // Intensidad de claridad vocal de 0.0f a 1.0f
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
    val crossfadeSeconds: Int = 0, // 0 = Gapless directo; 1..15 = segundos de fundido cruzado
    val isGapless: Boolean = true,
    val isDjAutomixEnabled: Boolean = false,
    val isDjEqCurveEnabled: Boolean = true
)

/**
 * Configuración de Normalización de Volumen Inteligente (Loudness Normalizer estilo Spotify / EBU R128).
 * Nivela automáticamente la energía acústica entre canciones dispares para eliminar saltos bruscos de volumen.
 * - targetLufs: Nivel objetivo (-14.0f es el estándar oficial de Spotify).
 * - mode: 0 = Sutil (-18 LUFS), 1 = Estándar Spotify (-14 LUFS), 2 = Alto (-11 LUFS).
 */
data class VolumeNormalizationConfig(
    val enabled: Boolean = false,
    val targetLufs: Float = -14.0f,
    val mode: Int = 1 // 0 = Sutil (-18 LUFS), 1 = Estándar Spotify (-14 LUFS), 2 = Alto (-11 LUFS)
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

