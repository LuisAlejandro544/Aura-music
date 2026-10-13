package com.example.core.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import com.example.core.model.EqualizerBand
import com.example.core.model.EqualizerPreset
import com.example.core.model.EqualizerScopeMode

/**
 * Ecualizador de 10 bandas, alcance y sesión de hardware.
 * Rol arquitectónico: pieza de AudioEffectManager (fachada pública intacta).
 */
internal class EqualizerControls(private val st: AudioEffectState) {
    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == st.currentSessionId) return
        st.releaseHardware()
        st.currentSessionId = audioSessionId

        try {
            st.hardwareEqualizer = Equalizer(0, audioSessionId).apply {
                enabled = st._isEnabled.value
            }
            st.hardwareBassBoost = BassBoost(0, audioSessionId).apply {
                enabled = st._isEnabled.value
                if (strengthSupported) {
                    setStrength(st._bassBoostLevel.value.toShort())
                }
            }
        } catch (ignored: Exception) {}

        st.syncNative()
    }

    fun setEqScopeMode(mode: EqualizerScopeMode, currentTrackId: Long? = null) {
        if (st._eqScopeMode.value == mode) return
        if (mode == EqualizerScopeMode.CURRENT_TRACK_ONLY) {
            // Guardar el estado base actual para restaurarlo cuando cambie la canción
            st.savedGlobalEnabled = st._isEnabled.value
            st.savedGlobalBands = st._bands.value
            st.savedGlobalBassBoost = st._bassBoostLevel.value
            st.savedGlobalPreset = st._currentPreset.value
            st.activeCustomTrackId = currentTrackId
        } else {
            // Al pasar a modo "Para todas las siguientes", el estado actual se consolida como global
            st.savedGlobalEnabled = st._isEnabled.value
            st.savedGlobalBands = st._bands.value
            st.savedGlobalBassBoost = st._bassBoostLevel.value
            st.savedGlobalPreset = st._currentPreset.value
            st.activeCustomTrackId = null
        }
        st._eqScopeMode.value = mode
    }

    fun onTrackChanged(newTrackId: Long?) {
        if (newTrackId == null) return
        if (st._eqScopeMode.value == EqualizerScopeMode.CURRENT_TRACK_ONLY) {
            if (st.activeCustomTrackId == null || st.activeCustomTrackId != newTrackId) {
                // Restaurar el estado base previo y reiniciar el alcance para la nueva canción
                applyEqState(
                    enabled = st.savedGlobalEnabled,
                    bands = st.savedGlobalBands,
                    bassBoost = st.savedGlobalBassBoost,
                    preset = st.savedGlobalPreset
                )
                st._eqScopeMode.value = EqualizerScopeMode.GLOBAL_ALL_TRACKS
            }
            st.activeCustomTrackId = newTrackId
        } else {
            st.activeCustomTrackId = newTrackId
        }
    }

    fun applyEqState(
        enabled: Boolean,
        bands: List<EqualizerBand>,
        bassBoost: Int,
        preset: EqualizerPreset
    ) {
        st._isEnabled.value = enabled
        st._bands.value = bands
        st._bassBoostLevel.value = bassBoost
        st._currentPreset.value = preset

        NativeAudioEngine.setDspEnabled(enabled)
        bands.forEach { band ->
            NativeAudioEngine.setBandGain(band.index, band.levelMb / 100.0f)
        }
        NativeAudioEngine.setBassBoost(bassBoost / 1000.0f)

        try {
            st.hardwareEqualizer?.enabled = enabled
            bands.forEach { band ->
                if (band.index < (st.hardwareEqualizer?.numberOfBands ?: 0)) {
                    st.hardwareEqualizer?.setBandLevel(band.index.toShort(), band.levelMb.toShort())
                }
            }
            st.hardwareBassBoost?.enabled = enabled
            st.hardwareBassBoost?.takeIf { it.strengthSupported }?.setStrength(bassBoost.toShort())
        } catch (ignored: Exception) {}
    }

    fun setEnabled(enabled: Boolean) {
        st._isEnabled.value = enabled
        if (st._eqScopeMode.value == EqualizerScopeMode.GLOBAL_ALL_TRACKS) {
            st.savedGlobalEnabled = enabled
        }
        NativeAudioEngine.setDspEnabled(enabled)
        try {
            st.hardwareEqualizer?.enabled = enabled
            st.hardwareBassBoost?.enabled = enabled
        } catch (ignored: Exception) {}
    }

    fun setBandLevel(bandIndex: Int, levelMb: Int) {
        val currentList = st._bands.value.toMutableList()
        val index = currentList.indexOfFirst { it.index == bandIndex }
        if (index != -1) {
            val clamped = levelMb.coerceIn(-1500, 1500)
            currentList[index] = currentList[index].copy(levelMb = clamped)
            st._bands.value = currentList
            if (st._eqScopeMode.value == EqualizerScopeMode.GLOBAL_ALL_TRACKS) {
                st.savedGlobalBands = currentList
            }

            // Actualizar ganancia en dB en el motor nativo C++20 (-15.0 dB a +15.0 dB)
            val gainDb = clamped / 100.0f
            NativeAudioEngine.setBandGain(bandIndex, gainDb)

            // Intentar replicar en hardware de forma complementaria
            try {
                if (bandIndex < (st.hardwareEqualizer?.numberOfBands ?: 0)) {
                    st.hardwareEqualizer?.setBandLevel(bandIndex.toShort(), clamped.toShort())
                }
            } catch (ignored: Exception) {}
        }
    }

    fun setBassBoost(level: Int) {
        val clamped = level.coerceIn(0, 1000)
        st._bassBoostLevel.value = clamped
        if (st._eqScopeMode.value == EqualizerScopeMode.GLOBAL_ALL_TRACKS) {
            st.savedGlobalBassBoost = clamped
        }

        // Actualizar nivel normalizado en C++20 (0.0 a 1.0)
        NativeAudioEngine.setBassBoost(clamped / 1000.0f)

        try {
            st.hardwareBassBoost?.takeIf { it.strengthSupported }?.setStrength(clamped.toShort())
        } catch (ignored: Exception) {}
    }

    fun applyPreset(preset: EqualizerPreset) {
        st._currentPreset.value = preset
        if (st._eqScopeMode.value == EqualizerScopeMode.GLOBAL_ALL_TRACKS) {
            st.savedGlobalPreset = preset
        }
        preset.bandLevels.forEachIndexed { index, level ->
            setBandLevel(index, level)
        }
        setBassBoost(preset.bassBoost)
    }
}
