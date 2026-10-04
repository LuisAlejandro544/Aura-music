package com.example.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.PresetReverb
import com.example.model.EqualizerBand
import com.example.model.EqualizerPreset
import com.example.model.ReverbConfig
import com.example.model.ReverbPreset
import com.example.model.Spatial8DConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestor avanzado de efectos acústicos impulsado por el motor nativo C++20 (auramusic_dsp).
 * Controla:
 * - Ecualizador paramétrico de 10 bandas y refuerzo de graves en tiempo real.
 * - Motor de Audio Espacial 8D Binaural.
 * - Suite Reverb Híbrida (Presets ambientales de sala/catedral/club + Ajuste libre de tamaño, decay y wet).
 * - Crossfeed y balance estéreo fino L/R.
 */
class AudioEffectManager {

    private var hardwareEqualizer: Equalizer? = null
    private var hardwareBassBoost: BassBoost? = null
    private var hardwarePresetReverb: PresetReverb? = null
    private var hardwareEnvReverb: EnvironmentalReverb? = null
    private var currentSessionId: Int = 0

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _bands = MutableStateFlow<List<EqualizerBand>>(EqualizerPreset.DEFAULT_10_BANDS)
    val bands: StateFlow<List<EqualizerBand>> = _bands.asStateFlow()

    private val _bassBoostLevel = MutableStateFlow(0) // 0 a 1000
    val bassBoostLevel: StateFlow<Int> = _bassBoostLevel.asStateFlow()

    private val _currentPreset = MutableStateFlow(EqualizerPreset.PRESETS.first())
    val currentPreset: StateFlow<EqualizerPreset> = _currentPreset.asStateFlow()

    private val _spatial8DConfig = MutableStateFlow(Spatial8DConfig())
    val spatial8DConfig: StateFlow<Spatial8DConfig> = _spatial8DConfig.asStateFlow()

    private val _reverbConfig = MutableStateFlow(ReverbConfig())
    val reverbConfig: StateFlow<ReverbConfig> = _reverbConfig.asStateFlow()

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

        try {
            hardwarePresetReverb = PresetReverb(0, audioSessionId).apply {
                enabled = _reverbConfig.value.isEnabled
                if (_reverbConfig.value.isEnabled && _reverbConfig.value.preset != ReverbPreset.OFF) {
                    preset = _reverbConfig.value.preset.androidPreset
                }
            }
        } catch (ignored: Exception) {}

        try {
            hardwareEnvReverb = EnvironmentalReverb(0, audioSessionId).apply {
                enabled = _reverbConfig.value.isEnabled
                decayTime = _reverbConfig.value.decayMs
                roomLevel = (_reverbConfig.value.reverbLevelDb * 100).toInt().coerceIn(-9000, 0).toShort()
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

    fun set8DEnabled(enabled: Boolean) {
        _spatial8DConfig.value = _spatial8DConfig.value.copy(enabled = enabled)
        NativeAudioEngine.setEightDEnabled(enabled)
    }

    fun set8DOrbitSpeed(speedSeconds: Float) {
        val clamped = speedSeconds.coerceIn(4f, 30f)
        _spatial8DConfig.value = _spatial8DConfig.value.copy(orbitSpeedSeconds = clamped)
        NativeAudioEngine.setEightDOrbitSpeed(clamped)
    }

    fun set8DSpatialIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0f, 1f)
        _spatial8DConfig.value = _spatial8DConfig.value.copy(spatialIntensity = clamped)
        NativeAudioEngine.setEightDSpatialIntensity(clamped)
    }

    fun set8DRoomDepth(depth: Float) {
        val clamped = depth.coerceIn(0f, 1f)
        _spatial8DConfig.value = _spatial8DConfig.value.copy(roomDepth = clamped)
        NativeAudioEngine.setEightDRoomDepth(clamped)
    }

    // --- Control de Suite Reverb Acústica ---
    fun setReverbEnabled(enabled: Boolean) {
        val cfg = _reverbConfig.value.copy(isEnabled = enabled)
        _reverbConfig.value = cfg
        applyReverbToHardware(cfg)
        NativeAudioEngine.setReverbParameters(enabled, cfg.roomSize, cfg.decayMs, cfg.reverbLevelDb)
    }

    fun setReverbPreset(preset: ReverbPreset) {
        val isEnabled = preset != ReverbPreset.OFF
        val cfg = _reverbConfig.value.copy(
            isEnabled = isEnabled,
            preset = preset,
            roomSize = preset.defaultRoomSize,
            decayMs = preset.defaultDecayMs,
            reverbLevelDb = preset.defaultLevelDb
        )
        _reverbConfig.value = cfg
        applyReverbToHardware(cfg)
        NativeAudioEngine.setReverbParameters(isEnabled, cfg.roomSize, cfg.decayMs, cfg.reverbLevelDb)
    }

    fun setReverbCustomParameters(roomSize: Float, decayMs: Int, levelDb: Float) {
        val cfg = _reverbConfig.value.copy(
            roomSize = roomSize.coerceIn(0.1f, 2.0f),
            decayMs = decayMs.coerceIn(100, 6000),
            reverbLevelDb = levelDb.coerceIn(-24.0f, 6.0f)
        )
        _reverbConfig.value = cfg
        applyReverbToHardware(cfg)
        NativeAudioEngine.setReverbParameters(cfg.isEnabled, cfg.roomSize, cfg.decayMs, cfg.reverbLevelDb)
    }

    private fun applyReverbToHardware(cfg: ReverbConfig) {
        try {
            hardwarePresetReverb?.enabled = cfg.isEnabled
            if (cfg.isEnabled && cfg.preset != ReverbPreset.OFF) {
                hardwarePresetReverb?.preset = cfg.preset.androidPreset
            }
        } catch (_: Exception) {}

        try {
            hardwareEnvReverb?.enabled = cfg.isEnabled
            if (cfg.isEnabled) {
                hardwareEnvReverb?.decayTime = cfg.decayMs
                hardwareEnvReverb?.roomLevel = (cfg.reverbLevelDb * 100).toInt().coerceIn(-9000, 0).toShort()
            }
        } catch (_: Exception) {}
    }

    fun setCrossfeedEnabled(enabled: Boolean) {
        NativeAudioEngine.setCrossfeedEnabled(enabled)
    }

    fun setCrossfeedStrength(strengthMode: Int) {
        NativeAudioEngine.setCrossfeedStrength(strengthMode)
    }

    fun setCrossfeedHeadphonesConnected(connected: Boolean) {
        NativeAudioEngine.setCrossfeedHeadphonesConnected(connected)
    }

    fun setBalanceEnabled(enabled: Boolean) {
        NativeAudioEngine.setBalanceEnabled(enabled)
    }

    fun setStereoBalance(balance: Float) {
        NativeAudioEngine.setStereoBalance(balance)
    }

    private fun syncWithNativeEngine() {
        NativeAudioEngine.setDspEnabled(_isEnabled.value)
        _bands.value.forEach { band ->
            NativeAudioEngine.setBandGain(band.index, band.levelMb / 100.0f)
        }
        NativeAudioEngine.setBassBoost(_bassBoostLevel.value / 1000.0f)
        NativeAudioEngine.setEightDEnabled(_spatial8DConfig.value.enabled)
        NativeAudioEngine.setEightDOrbitSpeed(_spatial8DConfig.value.orbitSpeedSeconds)
        NativeAudioEngine.setEightDSpatialIntensity(_spatial8DConfig.value.spatialIntensity)
        NativeAudioEngine.setEightDRoomDepth(_spatial8DConfig.value.roomDepth)

        val rev = _reverbConfig.value
        NativeAudioEngine.setReverbParameters(rev.isEnabled, rev.roomSize, rev.decayMs, rev.reverbLevelDb)
    }

    private fun releaseHardwareEffects() {
        try {
            hardwareEqualizer?.release()
            hardwareBassBoost?.release()
            hardwarePresetReverb?.release()
            hardwareEnvReverb?.release()
        } catch (ignored: Exception) {}
        hardwareEqualizer = null
        hardwareBassBoost = null
        hardwarePresetReverb = null
        hardwareEnvReverb = null
        currentSessionId = 0
    }

    fun release() {
        releaseHardwareEffects()
    }
}
