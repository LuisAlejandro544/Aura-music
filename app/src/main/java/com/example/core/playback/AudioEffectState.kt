package com.example.core.playback


import android.media.audiofx.BassBoost
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import com.example.core.model.EqualizerBand
import com.example.core.model.EqualizerPreset
import com.example.core.model.EqualizerScopeMode
import com.example.core.model.ReverbConfig
import com.example.core.model.ReverbPreset
import com.example.core.model.Spatial8DConfig
import com.example.core.model.VocalClarityConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado compartido del motor de efectos: hardware, flows y snapshots de alcance.
 * Rol arquitectónico: pieza de AudioEffectManager (fachada pública intacta).
 */
internal class AudioEffectState {

    var hardwareEqualizer: Equalizer? = null
    var hardwareBassBoost: BassBoost? = null
    var currentSessionId: Int = 0

    // Por defecto el ecualizador inicia desactivado (false)
    val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    val _eqScopeMode = MutableStateFlow(EqualizerScopeMode.GLOBAL_ALL_TRACKS)
    val eqScopeMode: StateFlow<EqualizerScopeMode> = _eqScopeMode.asStateFlow()

    // Snapshot del estado global para restaurar cuando el usuario elige "Solo esta canción"
    var savedGlobalEnabled: Boolean = false
    var savedGlobalBands: List<EqualizerBand> = EqualizerPreset.DEFAULT_10_BANDS
    var savedGlobalBassBoost: Int = 0
    var savedGlobalPreset: EqualizerPreset = EqualizerPreset.PRESETS.first()
    var activeCustomTrackId: Long? = null

    val _bands = MutableStateFlow<List<EqualizerBand>>(EqualizerPreset.DEFAULT_10_BANDS)
    val bands: StateFlow<List<EqualizerBand>> = _bands.asStateFlow()

    val _bassBoostLevel = MutableStateFlow(0) // 0 a 1000
    val bassBoostLevel: StateFlow<Int> = _bassBoostLevel.asStateFlow()

    val _currentPreset = MutableStateFlow(EqualizerPreset.PRESETS.first())
    val currentPreset: StateFlow<EqualizerPreset> = _currentPreset.asStateFlow()

    val _spatial8DConfig = MutableStateFlow(Spatial8DConfig())
    val spatial8DConfig: StateFlow<Spatial8DConfig> = _spatial8DConfig.asStateFlow()

    val _vocalClarityConfig = MutableStateFlow(VocalClarityConfig())
    val vocalClarityConfig: StateFlow<VocalClarityConfig> = _vocalClarityConfig.asStateFlow()

    val _reverbConfig = MutableStateFlow(ReverbConfig())
    val reverbConfig: StateFlow<ReverbConfig> = _reverbConfig.asStateFlow()

    val _volumeNormalizationConfig = MutableStateFlow(com.example.core.model.VolumeNormalizationConfig())
    val volumeNormalizationConfig: StateFlow<com.example.core.model.VolumeNormalizationConfig> = _volumeNormalizationConfig.asStateFlow()

    init {
        // Inicializar motor DSP nativo C++20 con el ecualizador desactivado por defecto
        NativeAudioEngine.setDspEnabled(false)
        syncNative()
    }


    fun syncNative() {

        NativeAudioEngine.setDspEnabled(_isEnabled.value)
        _bands.value.forEach { band ->
            NativeAudioEngine.setBandGain(band.index, band.levelMb / 100.0f)
        }
        NativeAudioEngine.setBassBoost(_bassBoostLevel.value / 1000.0f)
        NativeAudioEngine.setEightDEnabled(_spatial8DConfig.value.enabled)
        NativeAudioEngine.setEightD16DMode(_spatial8DConfig.value.is16DMode)
        NativeAudioEngine.setEightDOrbitSpeed(_spatial8DConfig.value.orbitSpeedSeconds)
        NativeAudioEngine.setEightDSpatialIntensity(_spatial8DConfig.value.spatialIntensity)
        NativeAudioEngine.setEightDRoomDepth(_spatial8DConfig.value.roomDepth)

        val vc = _vocalClarityConfig.value
        NativeAudioEngine.setVocalClarityParameters(vc.enabled, vc.strength)

        val rev = _reverbConfig.value
        NativeAudioEngine.setReverbParameters(rev.isEnabled, rev.roomSize, rev.decayMs, rev.reverbLevelDb)

        val vn = _volumeNormalizationConfig.value
        NativeAudioEngine.setVolumeNormalization(vn.enabled, vn.targetLufs, vn.mode)
    
    }

    fun releaseHardware() {

        try {
            hardwareEqualizer?.release()
            hardwareBassBoost?.release()
        } catch (ignored: Exception) {}
        hardwareEqualizer = null
        hardwareBassBoost = null
        currentSessionId = 0
    
    }
}
