package com.example.core.playback

import com.example.core.model.EqualizerBand
import com.example.core.model.EqualizerPreset
import com.example.core.model.EqualizerScopeMode
import com.example.core.model.ReverbConfig
import com.example.core.model.ReverbPreset
import com.example.core.model.Spatial8DConfig
import com.example.core.model.VocalClarityConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * Gestor avanzado de efectos acústicos impulsado por el motor nativo C++20 (auramusic_dsp).
 * Controla:
 * - Ecualizador paramétrico de 10 bandas (desactivado por defecto) y alcance configurable (Solo esta canción vs Para todas las siguientes).
 * - Clarificador de Voces HD (aislamiento Mid-Side y realce de presencia vocal en C++20).
 * - Motor de Audio Espacial 8D (Órbita 360°) y 16D (Multi-Órbita Doble Capa) Binaural.
 * - Suite Reverb Híbrida C++20 (Presets ambientales + Ajuste libre de tamaño, decay y wet).
 * - Crossfeed y balance estéreo fino L/R.
 *
 * Rol arquitectónico: fachada pública intacta que delega en 4 piezas internas.
 */
class AudioEffectManager {

    private val st = AudioEffectState()
    private val eq = EqualizerControls(st)
    private val sv = SpatialVocalControls(st)
    private val rx = ReverbCrossfeedControls(st)

    val isEnabled: StateFlow<Boolean> get() = st.isEnabled
    val eqScopeMode: StateFlow<EqualizerScopeMode> get() = st.eqScopeMode
    val bands: StateFlow<List<EqualizerBand>> get() = st.bands
    val bassBoostLevel: StateFlow<Int> get() = st.bassBoostLevel
    val currentPreset: StateFlow<EqualizerPreset> get() = st.currentPreset
    val spatial8DConfig: StateFlow<Spatial8DConfig> get() = st.spatial8DConfig
    val vocalClarityConfig: StateFlow<VocalClarityConfig> get() = st.vocalClarityConfig
    val reverbConfig: StateFlow<ReverbConfig> get() = st.reverbConfig
    val volumeNormalizationConfig: StateFlow<com.example.core.model.VolumeNormalizationConfig> get() = st.volumeNormalizationConfig

    init {
        NativeAudioEngine.setDspEnabled(false)
        st.syncNative()
    }

    fun attachToSession(audioSessionId: Int) = eq.attachToSession(audioSessionId)
    fun setEqScopeMode(mode: EqualizerScopeMode, currentTrackId: Long? = null) = eq.setEqScopeMode(mode, currentTrackId)
    fun onTrackChanged(newTrackId: Long?) = eq.onTrackChanged(newTrackId)
    fun setEnabled(enabled: Boolean) = eq.setEnabled(enabled)
    fun setBandLevel(bandIndex: Int, levelMb: Int) = eq.setBandLevel(bandIndex, levelMb)
    fun setBassBoost(level: Int) = eq.setBassBoost(level)
    fun applyPreset(preset: EqualizerPreset) = eq.applyPreset(preset)
    fun set8DEnabled(enabled: Boolean) = sv.set8DEnabled(enabled)
    fun set8DMode16D(is16DMode: Boolean) = sv.set8DMode16D(is16DMode)
    fun set8DOrbitSpeed(speedSeconds: Float) = sv.set8DOrbitSpeed(speedSeconds)
    fun set8DSpatialIntensity(intensity: Float) = sv.set8DSpatialIntensity(intensity)
    fun set8DRoomDepth(depth: Float) = sv.set8DRoomDepth(depth)
    fun setVocalClarityEnabled(enabled: Boolean) = sv.setVocalClarityEnabled(enabled)
    fun setVocalClarityStrength(strength: Float) = sv.setVocalClarityStrength(strength)
    fun setReverbEnabled(enabled: Boolean) = rx.setReverbEnabled(enabled)
    fun setReverbPreset(preset: ReverbPreset) = rx.setReverbPreset(preset)
    fun setReverbCustomParameters(roomSize: Float, decayMs: Int, levelDb: Float) = rx.setReverbCustomParameters(roomSize, decayMs, levelDb)
    fun setCrossfeedEnabled(enabled: Boolean) = rx.setCrossfeedEnabled(enabled)
    fun setCrossfeedStrength(strengthMode: Int) = rx.setCrossfeedStrength(strengthMode)
    fun setCrossfeedHeadphonesConnected(connected: Boolean) = rx.setCrossfeedHeadphonesConnected(connected)
    fun setBalanceEnabled(enabled: Boolean) = rx.setBalanceEnabled(enabled)
    fun setStereoBalance(balance: Float) = rx.setStereoBalance(balance)
    fun setVolumeNormalizationEnabled(enabled: Boolean) = rx.setVolumeNormalizationEnabled(enabled)
    fun setVolumeNormalizationMode(mode: Int) = rx.setVolumeNormalizationMode(mode)
    fun setDjAutomixTransition(enabled: Boolean, progress: Float) = rx.setDjAutomixTransition(enabled, progress)
    fun flushBuffers() = rx.flushBuffers()
    fun release() = st.releaseHardware()

    /**
     * Política DSP para Modo Streaming (Fases 1-3 activas, Fase 4 en bypass).
     * En streaming se fuerza a OFF a nivel nativo (sin mutar StateFlows locales):
     * Reverb, VocalClarity, Crossfeed/Balance, Bit-Perfect y curva DJ.
     * EQ, Normalización y 8D/16D quedan intactos y audibles.
     * Al volver a local se restaura todo con syncNative().
     */
    fun applyStreamingPolicy(isStreaming: Boolean) {
        if (isStreaming) {
            try {
                NativeAudioEngine.streamingLockPhase4 = true
                val rev = st.reverbConfig.value
                // Los OFF pasan el candado (solo se bloquean los ON).
                NativeAudioEngine.setReverbParameters(false, rev.roomSize, rev.decayMs, rev.reverbLevelDb)
                NativeAudioEngine.setVocalClarityParameters(false, st.vocalClarityConfig.value.strength)
                NativeAudioEngine.setCrossfeedEnabled(false)
                NativeAudioEngine.setBalanceEnabled(false)
                NativeAudioEngine.setBitPerfectMode(false)
                NativeAudioEngine.setDjAutomixTransition(false, 0.0f)
            } catch (_: Throwable) {
                try { NativeAudioEngine.streamingLockPhase4 = true } catch (_: Throwable) {}
            }
        } else {
            try {
                NativeAudioEngine.streamingLockPhase4 = false
                st.syncNative()
            } catch (_: Throwable) {}
        }
    }
}
