package com.example.model

/**
 * Modelo de configuración y presets para el ecualizador paramétrico de 10 bandas
 * y mejora acústica impulsada por el motor DSP C++20.
 */
data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val levelMb: Int // En milidecibelios (-1500 a +1500 mB, es decir, -15 dB a +15 dB)
) {
    fun displayFreq(): String {
        return if (centerFreqHz >= 1000) {
            "${centerFreqHz / 1000}k"
        } else {
            "$centerFreqHz"
        }
    }

    fun gainDbFormatted(): String {
        val db = levelMb / 100.0
        return if (db > 0) String.format("+%.1f dB", db) else String.format("%.1f dB", db)
    }
}

data class EqualizerPreset(
    val name: String,
    val bandLevels: List<Int>, // 10 bandas de ganancia en mB (-1500 a +1500)
    val bassBoost: Int = 0 // 0 a 1000
) {
    companion object {
        // Frecuencias ISO estándar: 31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz
        val DEFAULT_10_BANDS = listOf(
            EqualizerBand(0, 31, 0),
            EqualizerBand(1, 62, 0),
            EqualizerBand(2, 125, 0),
            EqualizerBand(3, 250, 0),
            EqualizerBand(4, 500, 0),
            EqualizerBand(5, 1000, 0),
            EqualizerBand(6, 2000, 0),
            EqualizerBand(7, 4000, 0),
            EqualizerBand(8, 8000, 0),
            EqualizerBand(9, 16000, 0)
        )

        val PRESETS = listOf(
            EqualizerPreset(
                name = "Plano / Hi-Fi Direct",
                bandLevels = listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                bassBoost = 0
            ),
            EqualizerPreset(
                name = "Refuerzo de Graves C++ (Bass Boost)",
                bandLevels = listOf(800, 700, 500, 200, 0, 0, 100, 200, 300, 300),
                bassBoost = 750
            ),
            EqualizerPreset(
                name = "Electrónica & Synthwave",
                bandLevels = listOf(700, 600, 300, 0, -100, 100, 300, 500, 700, 800),
                bassBoost = 600
            ),
            EqualizerPreset(
                name = "Rock & Metal",
                bandLevels = listOf(500, 400, 200, -100, -100, 100, 300, 400, 600, 600),
                bassBoost = 400
            ),
            EqualizerPreset(
                name = "Pop Vibrante",
                bandLevels = listOf(200, 300, 400, 300, 200, 400, 500, 400, 300, 300),
                bassBoost = 300
            ),
            EqualizerPreset(
                name = "Vocal & Acústico",
                bandLevels = listOf(-200, -100, 100, 300, 600, 700, 600, 400, 200, 0),
                bassBoost = 100
            ),
            EqualizerPreset(
                name = "Jazz & Audiófilo",
                bandLevels = listOf(300, 300, 200, 100, 200, 200, 300, 400, 500, 600),
                bassBoost = 250
            )
        )
    }
}
