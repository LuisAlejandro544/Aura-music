package com.example.core.playback

import com.example.core.model.EqualizerPreset
import com.example.core.model.ReverbConfig
import com.example.core.model.ReverbPreset

/**
 * Reverb, crossfeed, balance, normalización y DJ.
 * Rol arquitectónico: pieza de AudioEffectManager (fachada pública intacta).
 */
internal class ReverbCrossfeedControls(private val st: AudioEffectState) {
    fun setReverbEnabled(enabled: Boolean) {
        val currentPreset = if (enabled && st._reverbConfig.value.preset == ReverbPreset.OFF) {
            ReverbPreset.ROOM
        } else if (!enabled) {
            ReverbPreset.OFF
        } else {
            st._reverbConfig.value.preset
        }
        val targetRoomSize = if (enabled && st._reverbConfig.value.preset == ReverbPreset.OFF) currentPreset.defaultRoomSize else st._reverbConfig.value.roomSize
        val targetDecayMs = if (enabled && st._reverbConfig.value.preset == ReverbPreset.OFF) currentPreset.defaultDecayMs else st._reverbConfig.value.decayMs
        val targetLevelDb = if (enabled && st._reverbConfig.value.preset == ReverbPreset.OFF) currentPreset.defaultLevelDb else st._reverbConfig.value.reverbLevelDb

        val cfg = st._reverbConfig.value.copy(
            isEnabled = enabled,
            preset = currentPreset,
            roomSize = targetRoomSize,
            decayMs = targetDecayMs,
            reverbLevelDb = targetLevelDb
        )
        st._reverbConfig.value = cfg
        NativeAudioEngine.setReverbParameters(enabled, cfg.roomSize, cfg.decayMs, cfg.reverbLevelDb)
    }

    fun setReverbPreset(preset: ReverbPreset) {
        val isEnabled = preset != ReverbPreset.OFF
        val cfg = st._reverbConfig.value.copy(
            isEnabled = isEnabled,
            preset = preset,
            roomSize = preset.defaultRoomSize,
            decayMs = preset.defaultDecayMs,
            reverbLevelDb = preset.defaultLevelDb
        )
        st._reverbConfig.value = cfg
        NativeAudioEngine.setReverbParameters(isEnabled, cfg.roomSize, cfg.decayMs, cfg.reverbLevelDb)
    }

    fun setReverbCustomParameters(roomSize: Float, decayMs: Int, levelDb: Float) {
        val cfg = st._reverbConfig.value.copy(
            roomSize = roomSize.coerceIn(0.1f, 2.0f),
            decayMs = decayMs.coerceIn(100, 6000),
            reverbLevelDb = levelDb.coerceIn(-24.0f, 6.0f)
        )
        st._reverbConfig.value = cfg
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
        val updated = st._volumeNormalizationConfig.value.copy(enabled = enabled)
        st._volumeNormalizationConfig.value = updated
        NativeAudioEngine.setVolumeNormalization(updated.enabled, updated.targetLufs, updated.mode)
    }

    fun setVolumeNormalizationMode(mode: Int) {
        val target = when (mode) {
            0 -> -18.0f
            2 -> -11.0f
            else -> -14.0f
        }
        val updated = st._volumeNormalizationConfig.value.copy(mode = mode, targetLufs = target)
        st._volumeNormalizationConfig.value = updated
        NativeAudioEngine.setVolumeNormalization(updated.enabled, updated.targetLufs, updated.mode)
    }

    fun setDjAutomixTransition(enabled: Boolean, progress: Float) {
        NativeAudioEngine.setDjAutomixTransition(enabled, progress)
    }

    fun flushBuffers() {
        NativeAudioEngine.flushBuffers()
    }
}
