package com.example.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import com.example.model.EqualizerBand
import com.example.model.EqualizerPreset
import com.example.model.EqualizerScopeMode
import com.example.model.ReverbConfig
import com.example.model.ReverbPreset
import com.example.model.Spatial8DConfig
import com.example.model.VocalClarityConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestor avanzado de efectos acústicos impulsado por el motor nativo C++20 (auramusic_dsp).
 * Controla:
 * - Ecualizador paramétrico de 10 bandas (desactivado por defecto) y alcance configurable (Solo esta canción vs Para todas las siguientes).
 * - Clarificador de Voces HD (aislamiento Mid-Side y realce de presencia vocal en C++20).
 * - Motor de Audio Espacial 8D (Órbita 360°) y 16D (Multi-Órbita Doble Capa) Binaural.
 * - Suite Reverb Híbrida C++20 (Presets ambientales + Ajuste libre de tamaño, decay y wet).
 * - Crossfeed y balance estéreo fino L/R.
 */
class AudioEffectManager {

    private var hardwareEqualizer: Equalizer? = null
    private var hardwareBassBoost: BassBoost? = null
    private var currentSessionId: Int = 0

    // Por defecto el ecualizador inicia desactivado (false)
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _eqScopeMode = MutableStateFlow(EqualizerScopeMode.GLOBAL_ALL_TRACKS)
    val eqScopeMode: StateFlow<EqualizerScopeMode> = _eqScopeMode.asStateFlow()

    // Snapshot del estado global para restaurar cuando el usuario elige "Solo esta canción"
    private var savedGlobalEnabled: Boolean = false
    private var savedGlobalBands: List<EqualizerBand> = EqualizerPreset.DEFAULT_10_BANDS
    private var savedGlobalBassBoost: Int = 0
    private var savedGlobalPreset: EqualizerPreset = EqualizerPreset.PRESETS.first()
    private var activeCustomTrackId: Long? = null

    private val _bands = MutableStateFlow<List<EqualizerBand>>(EqualizerPreset.DEFAULT_10_BANDS)
    val bands: StateFlow<List<EqualizerBand>> = _bands.asStateFlow()

    private val _bassBoostLevel = MutableStateFlow(0) // 0 a 1000
    val bassBoostLevel: StateFlow<Int> = _bassBoostLevel.asStateFlow()

    private val _currentPreset = MutableStateFlow(EqualizerPreset.PRESETS.first())
    val currentPreset: StateFlow<EqualizerPreset> = _currentPreset.asStateFlow()

    private val _spatial8DConfig = MutableStateFlow(Spatial8DConfig())
    val spatial8DConfig: StateFlow<Spatial8DConfig> = _spatial8DConfig.asStateFlow()

    private val _vocalClarityConfig = MutableStateFlow(VocalClarityConfig())
    val vocalClarityConfig: StateFlow<VocalClarityConfig> = _vocalClarityConfig.asStateFlow()

    private val _reverbConfig = MutableStateFlow(ReverbConfig())
    val reverbConfig: StateFlow<ReverbConfig> = _reverbConfig.asStateFlow()

    private val _volumeNormalizationConfig = MutableStateFlow(com.example.model.VolumeNormalizationConfig())
    val volumeNormalizationConfig: StateFlow<com.example.model.VolumeNormalizationConfig> = _volumeNormalizationConfig.asStateFlow()

    init {
        // Inicializar motor DSP nativo C++20 con el ecualizador desactivado por defecto
        NativeAudioEngine.setDspEnabled(false)
        syncWithNativeEngine()
    }

    /**
     * Vincula la sesión de audio de hardware si el dispositivo la soporta
     * para acompañamiento de ecualización y refuerzo de graves.
     * Nota: El Reverb se procesa exclusivamente en el motor C++20 (ReverbProcessor)
     * para evitar que el driver LVREV (EnvironmentalReverb/PresetReverb) de Android
     * silencie la señal directa (dry) o retenga el canal al desactivarse.
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

    fun setEqScopeMode(mode: EqualizerScopeMode, currentTrackId: Long? = null) {
        if (_eqScopeMode.value == mode) return
        if (mode == EqualizerScopeMode.CURRENT_TRACK_ONLY) {
            // Guardar el estado base actual para restaurarlo cuando cambie la canción
            savedGlobalEnabled = _isEnabled.value
            savedGlobalBands = _bands.value
            savedGlobalBassBoost = _bassBoostLevel.value
            savedGlobalPreset = _currentPreset.value
            activeCustomTrackId = currentTrackId
        } else {
            // Al pasar a modo "Para todas las siguientes", el estado actual se consolida como global
            savedGlobalEnabled = _isEnabled.value
            savedGlobalBands = _bands.value
            savedGlobalBassBoost = _bassBoostLevel.value
            savedGlobalPreset = _currentPreset.value
            activeCustomTrackId = null
        }
        _eqScopeMode.value = mode
    }

    /**
     * Notifica al gestor que ha cambiado la canción en reproducción.
     * Si el alcance del ecualizador está en [EqualizerScopeMode.CURRENT_TRACK_ONLY] y el ID de pista cambió,
     * restaura automáticamente el estado global previo (desactivado por defecto) para las siguientes canciones.
     */
    fun onTrackChanged(newTrackId: Long?) {
        if (newTrackId == null) return
        if (_eqScopeMode.value == EqualizerScopeMode.CURRENT_TRACK_ONLY) {
            if (activeCustomTrackId == null || activeCustomTrackId != newTrackId) {
                // Restaurar el estado base previo y reiniciar el alcance para la nueva canción
                applyInternalEqState(
                    enabled = savedGlobalEnabled,
                    bands = savedGlobalBands,
                    bassBoost = savedGlobalBassBoost,
                    preset = savedGlobalPreset
                )
                _eqScopeMode.value = EqualizerScopeMode.GLOBAL_ALL_TRACKS
            }
            activeCustomTrackId = newTrackId
        } else {
            activeCustomTrackId = newTrackId
        }
    }

    private fun applyInternalEqState(
        enabled: Boolean,
        bands: List<EqualizerBand>,
        bassBoost: Int,
        preset: EqualizerPreset
    ) {
        _isEnabled.value = enabled
        _bands.value = bands
        _bassBoostLevel.value = bassBoost
        _currentPreset.value = preset

        NativeAudioEngine.setDspEnabled(enabled)
        bands.forEach { band ->
            NativeAudioEngine.setBandGain(band.index, band.levelMb / 100.0f)
        }
        NativeAudioEngine.setBassBoost(bassBoost / 1000.0f)

        try {
            hardwareEqualizer?.enabled = enabled
            bands.forEach { band ->
                if (band.index < (hardwareEqualizer?.numberOfBands ?: 0)) {
                    hardwareEqualizer?.setBandLevel(band.index.toShort(), band.levelMb.toShort())
                }
            }
            hardwareBassBoost?.enabled = enabled
            hardwareBassBoost?.takeIf { it.strengthSupported }?.setStrength(bassBoost.toShort())
        } catch (ignored: Exception) {}
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        if (_eqScopeMode.value == EqualizerScopeMode.GLOBAL_ALL_TRACKS) {
            savedGlobalEnabled = enabled
        }
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
            if (_eqScopeMode.value == EqualizerScopeMode.GLOBAL_ALL_TRACKS) {
                savedGlobalBands = currentList
            }

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
        if (_eqScopeMode.value == EqualizerScopeMode.GLOBAL_ALL_TRACKS) {
            savedGlobalBassBoost = clamped
        }

        // Actualizar nivel normalizado en C++20 (0.0 a 1.0)
        NativeAudioEngine.setBassBoost(clamped / 1000.0f)

        try {
            hardwareBassBoost?.takeIf { it.strengthSupported }?.setStrength(clamped.toShort())
        } catch (ignored: Exception) {}
    }

    fun applyPreset(preset: EqualizerPreset) {
        _currentPreset.value = preset
        if (_eqScopeMode.value == EqualizerScopeMode.GLOBAL_ALL_TRACKS) {
            savedGlobalPreset = preset
        }
        preset.bandLevels.forEachIndexed { index, level ->
            setBandLevel(index, level)
        }
        setBassBoost(preset.bassBoost)
    }

    fun set8DEnabled(enabled: Boolean) {
        _spatial8DConfig.value = _spatial8DConfig.value.copy(enabled = enabled)
        NativeAudioEngine.setEightDEnabled(enabled)
    }

    fun set8DMode16D(is16DMode: Boolean) {
        _spatial8DConfig.value = _spatial8DConfig.value.copy(is16DMode = is16DMode)
        NativeAudioEngine.setEightD16DMode(is16DMode)
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

    // --- Control de Clarificador de Voces HD (100% C++20 en tiempo real) ---
    fun setVocalClarityEnabled(enabled: Boolean) {
        val cfg = _vocalClarityConfig.value.copy(enabled = enabled)
        _vocalClarityConfig.value = cfg
        NativeAudioEngine.setVocalClarityParameters(cfg.enabled, cfg.strength)
    }

    fun setVocalClarityStrength(strength: Float) {
        val clamped = strength.coerceIn(0.0f, 1.0f)
        val cfg = _vocalClarityConfig.value.copy(strength = clamped)
        _vocalClarityConfig.value = cfg
        NativeAudioEngine.setVocalClarityParameters(cfg.enabled, cfg.strength)
    }

    // --- Control de Suite Reverb Acústica (100% C++20 en tiempo real, sin bloqueo LVREV) ---
    fun setReverbEnabled(enabled: Boolean) {
        val currentPreset = if (enabled && _reverbConfig.value.preset == ReverbPreset.OFF) {
            ReverbPreset.ROOM
        } else if (!enabled) {
            ReverbPreset.OFF
        } else {
            _reverbConfig.value.preset
        }
        val targetRoomSize = if (enabled && _reverbConfig.value.preset == ReverbPreset.OFF) currentPreset.defaultRoomSize else _reverbConfig.value.roomSize
        val targetDecayMs = if (enabled && _reverbConfig.value.preset == ReverbPreset.OFF) currentPreset.defaultDecayMs else _reverbConfig.value.decayMs
        val targetLevelDb = if (enabled && _reverbConfig.value.preset == ReverbPreset.OFF) currentPreset.defaultLevelDb else _reverbConfig.value.reverbLevelDb

        val cfg = _reverbConfig.value.copy(
            isEnabled = enabled,
            preset = currentPreset,
            roomSize = targetRoomSize,
            decayMs = targetDecayMs,
            reverbLevelDb = targetLevelDb
        )
        _reverbConfig.value = cfg
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
        NativeAudioEngine.setReverbParameters(isEnabled, cfg.roomSize, cfg.decayMs, cfg.reverbLevelDb)
    }

    fun setReverbCustomParameters(roomSize: Float, decayMs: Int, levelDb: Float) {
        val cfg = _reverbConfig.value.copy(
            roomSize = roomSize.coerceIn(0.1f, 2.0f),
            decayMs = decayMs.coerceIn(100, 6000),
            reverbLevelDb = levelDb.coerceIn(-24.0f, 6.0f)
        )
        _reverbConfig.value = cfg
        NativeAudioEngine.setReverbParameters(cfg.isEnabled, cfg.roomSize, cfg.decayMs, cfg.reverbLevelDb)
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

    fun setVolumeNormalizationEnabled(enabled: Boolean) {
        val updated = _volumeNormalizationConfig.value.copy(enabled = enabled)
        _volumeNormalizationConfig.value = updated
        NativeAudioEngine.setVolumeNormalization(updated.enabled, updated.targetLufs, updated.mode)
    }

    fun setVolumeNormalizationMode(mode: Int) {
        val target = when (mode) {
            0 -> -18.0f
            2 -> -11.0f
            else -> -14.0f
        }
        val updated = _volumeNormalizationConfig.value.copy(mode = mode, targetLufs = target)
        _volumeNormalizationConfig.value = updated
        NativeAudioEngine.setVolumeNormalization(updated.enabled, updated.targetLufs, updated.mode)
    }

    fun setDjAutomixTransition(enabled: Boolean, progress: Float) {
        NativeAudioEngine.setDjAutomixTransition(enabled, progress)
    }

    /**
     * Limpieza Atómica de Buffers (Buffer Flushing):
     * Pone a cero los acumuladores de los filtros IIR Bi-cuadráticos (10 bandas),
     * filtros de graves, clarificador vocal, líneas de retardo de Crossfeed y colas de Reverb en C++20.
     * Erradica de forma definitiva cualquier pop o chasquido digital residual y colas de eco.
     */
    fun flushBuffers() {
        NativeAudioEngine.flushBuffers()
    }

    private fun syncWithNativeEngine() {
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
