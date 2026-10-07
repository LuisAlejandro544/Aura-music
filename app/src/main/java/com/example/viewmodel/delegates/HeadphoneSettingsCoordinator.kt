package com.example.viewmodel.delegates

import com.example.model.HeadphoneConfig
import com.example.model.HeadsetButtonAction
import com.example.playback.AuraAudioPlayer
import com.example.playback.HeadphoneController
import kotlinx.coroutines.flow.StateFlow

/**
 * Aura Music - Coordinador Modular de Configuración y Gestión de Auriculares
 *
 * Responsabilidades:
 * - Control de parámetros de acústica DSP exclusiva (Crossfeed y Balance Estéreo L/R).
 * - Protección contra desconexiones involuntarias ("Becoming Noisy" Guard) y Fade-In suave.
 * - Memoria de volumen dedicada por conexión/desconexión de auriculares.
 * - Enrutamiento y asignación de gestos y clicks físicos de auriculares.
 */
class HeadphoneSettingsCoordinator(
    private val headphoneController: HeadphoneController,
    private val audioPlayer: AuraAudioPlayer
) {

    val headphoneConfig: StateFlow<HeadphoneConfig> = headphoneController.config

    fun updateHeadphoneConfig(newConfig: HeadphoneConfig) {
        headphoneController.updateConfig(newConfig)
        audioPlayer.setFadeInOnResumeEnabled(newConfig.isFadeInOnResumeEnabled)
    }

    fun setCrossfeedEnabled(enabled: Boolean) {
        headphoneController.setCrossfeedEnabled(enabled)
    }

    fun setCrossfeedStrength(strengthMode: Int) {
        headphoneController.setCrossfeedStrength(strengthMode)
    }

    fun setBalanceControlEnabled(enabled: Boolean) {
        headphoneController.setBalanceControlEnabled(enabled)
    }

    fun setStereoBalance(balance: Float) {
        headphoneController.setStereoBalance(balance)
    }

    fun setBitPerfectEnabled(enabled: Boolean) {
        headphoneController.setBitPerfectEnabled(enabled)
    }

    fun setLowLatencyAAudioEnabled(enabled: Boolean) {
        headphoneController.setLowLatencyAAudioEnabled(enabled)
    }

    fun setUsbDacExclusiveEnabled(enabled: Boolean) {
        headphoneController.setUsbDacExclusiveEnabled(enabled)
    }

    fun setHiResTargetMode(mode: Int) {
        headphoneController.setHiResTargetMode(mode)
    }

    fun setBecomingNoisyGuardEnabled(enabled: Boolean) {
        headphoneController.setBecomingNoisyGuardEnabled(enabled)
    }

    fun setFadeInOnResumeEnabled(enabled: Boolean) {
        headphoneController.setFadeInOnResumeEnabled(enabled)
        audioPlayer.setFadeInOnResumeEnabled(enabled)
    }

    fun setDedicatedVolumeMemoryEnabled(enabled: Boolean) {
        headphoneController.setDedicatedVolumeMemoryEnabled(enabled)
    }

    fun setHeadsetControlsEnabled(enabled: Boolean) {
        headphoneController.setHeadsetControlsEnabled(enabled)
    }

    fun setHeadsetSingleClickAction(action: HeadsetButtonAction) {
        headphoneController.setSingleClickAction(action)
    }

    fun setHeadsetDoubleClickAction(action: HeadsetButtonAction) {
        headphoneController.setDoubleClickAction(action)
    }

    fun setHeadsetTripleClickAction(action: HeadsetButtonAction) {
        headphoneController.setTripleClickAction(action)
    }

    fun setHeadsetLongClickAction(action: HeadsetButtonAction) {
        headphoneController.setLongClickAction(action)
    }

    fun setHeadsetAction(type: Int, action: HeadsetButtonAction) {
        when (type) {
            1 -> headphoneController.setSingleClickAction(action)
            2 -> headphoneController.setDoubleClickAction(action)
            3 -> headphoneController.setTripleClickAction(action)
            4 -> headphoneController.setLongClickAction(action)
        }
    }

    fun release() {
        headphoneController.release()
    }
}
