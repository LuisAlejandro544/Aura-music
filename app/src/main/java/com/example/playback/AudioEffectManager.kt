package com.example.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import com.example.model.EqualizerBand
import com.example.model.EqualizerPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestor avanzado de efectos acústicos impulsado por el motor nativo C++20 (auramusic_dsp).
 * Controla un ecualizador paramétrico de 10 bandas y refuerzo de graves en tiempo real.
 */
class AudioEffectManager {

    private var hardwareEqualizer: Equalizer? = null
    private var hardwareBassBoost: BassBoost? = null
    private var currentSessionId: Int = 0

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _bands = MutableStateFlow<List<EqualizerBand>>(EqualizerPreset.DEFAULT_10_BANDS)
    val bands: StateFlow<List<EqualizerBand>> = _bands.asStateFlow()

    private val _bassBoostLevel = MutableStateFlow(0) // 0 a 1000
    val bassBoostLevel: StateFlow<Int> = _bassBoostLevel.asStateFlow()

    private val _currentPreset = MutableStateFlow(EqualizerPreset.PRESETS.first())
    val currentPreset: StateFlow<EqualizerPreset> = _currentPreset.asStateFlow()

    init {
        // Inicializar motor DSP nativo C++20 con valores iniciales
        NativeAudioEngine.setDspEnabled(true)
        syncWithNativeEngine()
    }

    /**
     * Vincula la sesión de audio de hardware si el dispositivo la soporta
     * para acompañamiento de aceleración acústica.
     */
    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentSessionId) return
        releaseHardwareEffects()
        currentSessionId = audioSessionId

        try {
            hardwareEqualizer = Equalizer(0, audioSessionId).apply {
                enabled = _isEnabled.value
            }
            hardwareBassBoost = BassBoost(0, audioSessionId).apply {
                enabled = _isEnabled.value
                if (strengthSupported) {
                    setStrength(_bassBoostLevel.value.toShort())
                }
            }
        } catch (ignored: Exception) {}

        syncWithNativeEngine()
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        NativeAudioEngine.setDspEnabled(enabled)
        try {
            hardwareEqualizer?.enabled = enabled
            hardwareBassBoost?.enabled = enabled
        } catch (ignored: Exception) {}
    }

    fun setBandLevel(bandIndex: Int, levelMb: Int) {
        val currentList = _bands.value.toMutableList()
        val index = currentList.indexOfFirst { it.index == bandIndex }
        if (index != -1) {
            val clamped = levelMb.coerceIn(-1500, 1500)
            currentList[index] = currentList[index].copy(levelMb = clamped)
            _bands.value = currentList

            // Actualizar ganancia en dB en el motor nativo C++20 (-15.0 dB a +15.0 dB)
            val gainDb = clamped / 100.0f
            NativeAudioEngine.setBandGain(bandIndex, gainDb)

            // Intentar replicar en hardware de forma complementaria
            try {
                if (bandIndex < (hardwareEqualizer?.numberOfBands ?: 0)) {
                    hardwareEqualizer?.setBandLevel(bandIndex.toShort(), clamped.toShort())
                }
            } catch (ignored: Exception) {}
        }
    }

    fun setBassBoost(level: Int) {
        val clamped = level.coerceIn(0, 1000)
        _bassBoostLevel.value = clamped

        // Actualizar nivel normalizado en C++20 (0.0 a 1.0)
        NativeAudioEngine.setBassBoost(clamped / 1000.0f)

        try {
            hardwareBassBoost?.takeIf { it.strengthSupported }?.setStrength(clamped.toShort())
        } catch (ignored: Exception) {}
    }

    fun applyPreset(preset: EqualizerPreset) {
        _currentPreset.value = preset
        preset.bandLevels.forEachIndexed { index, level ->
            setBandLevel(index, level)
        }
        setBassBoost(preset.bassBoost)
    }

    private fun syncWithNativeEngine() {
        NativeAudioEngine.setDspEnabled(_isEnabled.value)
        _bands.value.forEach { band ->
            NativeAudioEngine.setBandGain(band.index, band.levelMb / 100.0f)
        }
        NativeAudioEngine.setBassBoost(_bassBoostLevel.value / 1000.0f)
    }

    private fun releaseHardwareEffects() {
        try {
            hardwareEqualizer?.release()
            hardwareBassBoost?.release()
        } catch (ignored: Exception) {}
        hardwareEqualizer = null
        hardwareBassBoost = null
        currentSessionId = 0
    }

    fun release() {
        releaseHardwareEffects()
    }
}
