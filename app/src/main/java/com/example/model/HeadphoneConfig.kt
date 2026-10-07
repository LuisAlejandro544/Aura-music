package com.example.model

/**
 * Modelos de datos para la gestión avanzada de Audífonos y Auriculares en Aura Music:
 * - Filtro Crossfeed acústico (Bauer / Chu Moy) en C++20 exclusivo para auriculares.
 * - Balance Estéreo Fino (L/R) con ajuste en tiempo real.
 * - Protección contra Desconexiones ("Becoming Noisy" Guard).
 * - Reanudación con Fade-In Suave (Volumen Progresivo).
 * - Memoria de Volumen Dedicada para Audífonos vs Altavoz.
 * - Mapeo de botones físicos y gestos de auriculares (Headset Controls).
 */

enum class HeadsetButtonAction(val label: String, val description: String) {
    PLAY_PAUSE("Reproducir / Pausar", "Alterna entre reproducir y pausar la pista actual"),
    NEXT_TRACK("Pista siguiente", "Salta a la siguiente canción en la cola"),
    PREV_TRACK("Pista anterior", "Vuelve a la canción anterior o reinicia la actual"),
    TOGGLE_FAVORITE("Me Gusta / Favorito ❤️", "Añade o retira la canción de tus favoritos"),
    SEEK_FORWARD_15("Avanzar 15 segundos", "Adelanta 15s en la reproducción actual"),
    SEEK_BACKWARD_15("Retroceder 15 segundos", "Retrocede 15s en la reproducción actual"),
    NONE("Sin acción", "Ignora esta combinación de clics")
}

enum class CrossfeedStrengthPreset(val mode: Int, val title: String, val subtitle: String) {
    SUBTLE(0, "Sutil (Bauer 4.5 dB)", "Corte a 700 Hz y retardo de 250 µs para una espacialidad sutil"),
    MODERATE(1, "Moderado (Chu Moy Classic)", "Calibración equilibrada recomendada para la mayoría de audífonos"),
    INTENSE(2, "Intenso (Monitores de Estudio)", "Mayor alimentación cruzada (340 µs) para eliminar fatiga auditiva extrema")
}

enum class HiResTargetPreset(val mode: Int, val title: String, val subtitle: String) {
    NATIVE_1_1(0, "Nativo 1:1 (Sin Resampling)", "Conserva exactamente la tasa de muestreo original de cada archivo"),
    HIRES_96K(1, "Hi-Res 96 kHz / 24-bit", "Optimiza la tubería acústica hacia resolución de estudio de 96 kHz"),
    ULTRA_192K(2, "Ultra Hi-Res 192 kHz / 32-bit", "Máxima resolución para DACs USB-C externos de alta gama")
}

data class HeadphoneConfig(
    // Acústica DSP C++20
    val isCrossfeedEnabled: Boolean = false,
    val crossfeedStrength: Int = 1, // 0 = Sutil, 1 = Moderado, 2 = Intenso
    val isBalanceControlEnabled: Boolean = false,
    val stereoBalance: Float = 0.0f, // -1.0f (100% L) .. 0.0f (Centro) .. +1.0f (100% R)

    // Fase 7: Modo Bit-Perfect, AAudio Ultra-Baja Latencia y DAC USB Exclusivo
    val isBitPerfectEnabled: Boolean = false,
    val isLowLatencyAAudioEnabled: Boolean = true,
    val isUsbDacExclusiveEnabled: Boolean = true,
    val hiResTargetMode: Int = 0, // 0 = Nativo 1:1, 1 = 96 kHz/24-bit, 2 = 192 kHz/32-bit

    // Automatizaciones y Seguridad
    val isBecomingNoisyGuardEnabled: Boolean = true,
    val isFadeInOnResumeEnabled: Boolean = true,
    val isDedicatedVolumeMemoryEnabled: Boolean = true,
    val headphoneVolumeLevel: Float = 0.70f,
    val speakerVolumeLevel: Float = 0.85f,

    // Botones Físicos y Gestos de Auriculares
    val isHeadsetControlsEnabled: Boolean = true,
    val singleClickAction: HeadsetButtonAction = HeadsetButtonAction.PLAY_PAUSE,
    val doubleClickAction: HeadsetButtonAction = HeadsetButtonAction.NEXT_TRACK,
    val tripleClickAction: HeadsetButtonAction = HeadsetButtonAction.PREV_TRACK,
    val longClickAction: HeadsetButtonAction = HeadsetButtonAction.TOGGLE_FAVORITE,

    // Estado de Hardware en Vivo
    val isHeadphoneConnected: Boolean = false,
    val connectedDeviceName: String = "Altavoz del Teléfono"
) {
    // Retorna si el Crossfeed está acústicamente activo en este milisegundo
    val isCrossfeedAcousticallyActive: Boolean
        get() = isCrossfeedEnabled && isHeadphoneConnected

    // Formato porcentual del balance estéreo
    val formattedBalance: String
        get() = when {
            kotlin.math.abs(stereoBalance) < 0.01f -> "Centro (L 50% | R 50%)"
            stereoBalance < 0.0f -> {
                val pctL = (50 + (-stereoBalance * 50)).toInt()
                val pctR = 100 - pctL
                "L $pctL% | R $pctR%"
            }
            else -> {
                val pctR = (50 + (stereoBalance * 50)).toInt()
                val pctL = 100 - pctR
                "L $pctL% | R $pctR%"
            }
        }
}
