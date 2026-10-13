package com.example.core.playback

import com.example.core.model.Spatial8DConfig
import com.example.core.model.VocalClarityConfig

/**
 * Audio 8D/16D y clarificador vocal Mid-Side.
 * Rol arquitectónico: pieza de AudioEffectManager (fachada pública intacta).
 */
internal class SpatialVocalControls(private val st: AudioEffectState) {
    fun set8DEnabled(enabled: Boolean) {
        st._spatial8DConfig.value = st._spatial8DConfig.value.copy(enabled = enabled)
        NativeAudioEngine.setEightDEnabled(enabled)
    }

    fun set8DMode16D(is16DMode: Boolean) {
        st._spatial8DConfig.value = st._spatial8DConfig.value.copy(is16DMode = is16DMode)
        NativeAudioEngine.setEightD16DMode(is16DMode)
    }

    fun set8DOrbitSpeed(speedSeconds: Float) {
        val clamped = speedSeconds.coerceIn(4f, 30f)
        st._spatial8DConfig.value = st._spatial8DConfig.value.copy(orbitSpeedSeconds = clamped)
        NativeAudioEngine.setEightDOrbitSpeed(clamped)
    }

    fun set8DSpatialIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0f, 1f)
        st._spatial8DConfig.value = st._spatial8DConfig.value.copy(spatialIntensity = clamped)
        NativeAudioEngine.setEightDSpatialIntensity(clamped)
    }

    fun set8DRoomDepth(depth: Float) {
        val clamped = depth.coerceIn(0f, 1f)
        st._spatial8DConfig.value = st._spatial8DConfig.value.copy(roomDepth = clamped)
        NativeAudioEngine.setEightDRoomDepth(clamped)
    }

    fun setVocalClarityEnabled(enabled: Boolean) {
        val cfg = st._vocalClarityConfig.value.copy(enabled = enabled)
        st._vocalClarityConfig.value = cfg
        NativeAudioEngine.setVocalClarityParameters(cfg.enabled, cfg.strength)
    }

    fun setVocalClarityStrength(strength: Float) {
        val clamped = strength.coerceIn(0.0f, 1.0f)
        val cfg = st._vocalClarityConfig.value.copy(strength = clamped)
        st._vocalClarityConfig.value = cfg
        NativeAudioEngine.setVocalClarityParameters(cfg.enabled, cfg.strength)
    }
}
