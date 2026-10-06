package com.example.viewmodel.delegates

import com.example.model.EqualizerPreset
import com.example.model.ReverbPreset
import com.example.model.SleepTimerState
import com.example.playback.AudioEffectManager
import com.example.playback.AuraAudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coordinador modular para los efectos acústicos y temporizador de apagado de Aura Music.
 * Desacopla la gestión del ecualizador paramétrico de 10 bandas C++20, Audio Espacial 8D/16D,
 * Clarificador Vocal HD Mid-Side, Suite Reverb y Sleep Timer con atenuación suave de 10 segundos.
 */
class AudioEffectsCoordinator(
    private val effectManager: AudioEffectManager,
    private val audioPlayer: AuraAudioPlayer,
    private val coroutineScope: CoroutineScope
) {
    // Estados de ecualizador y efectos nativos
    val eqBands = effectManager.bands
    val bassBoostLevel = effectManager.bassBoostLevel
    val currentPreset = effectManager.currentPreset
    val isEqEnabled = effectManager.isEnabled
    val spatial8DConfig = effectManager.spatial8DConfig
    val vocalClarityConfig = effectManager.vocalClarityConfig
    val reverbConfig = effectManager.reverbConfig
    val volumeNormalizationConfig = effectManager.volumeNormalizationConfig
    val isDjAutomixEnabled = audioPlayer.isDjAutomixEnabled
    val isDjEqCurveEnabled = audioPlayer.isDjEqCurveEnabled

    // Estado del Temporizador de Apagado (Sleep Timer)
    private var sleepTimerJob: Job? = null
    private val _sleepTimerState = MutableStateFlow(SleepTimerState())
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()

    // Acciones de Ecualizador
    fun setEqEnabled(enabled: Boolean) = effectManager.setEnabled(enabled)

    fun setBandLevel(bandIndex: Int, levelMb: Int) = effectManager.setBandLevel(bandIndex, levelMb)

    fun setBassBoost(level: Int) = effectManager.setBassBoost(level)

    fun applyPreset(preset: EqualizerPreset) = effectManager.applyPreset(preset)

    // Acciones de Audio Espacial 8D / 16D C++20
    fun set8DEnabled(enabled: Boolean) = effectManager.set8DEnabled(enabled)

    fun set8DMode16D(is16DMode: Boolean) = effectManager.set8DMode16D(is16DMode)

    fun set8DOrbitSpeed(speedSeconds: Float) = effectManager.set8DOrbitSpeed(speedSeconds)

    fun set8DSpatialIntensity(intensity: Float) = effectManager.set8DSpatialIntensity(intensity)

    fun set8DRoomDepth(depth: Float) = effectManager.set8DRoomDepth(depth)

    // Acciones de Clarificador de Voces HD C++20 (Mid-Side)
    fun setVocalClarityEnabled(enabled: Boolean) = effectManager.setVocalClarityEnabled(enabled)

    fun setVocalClarityStrength(strength: Float) = effectManager.setVocalClarityStrength(strength)

    // Acciones de Suite Reverb Acústica
    fun setReverbEnabled(enabled: Boolean) = effectManager.setReverbEnabled(enabled)

    fun setReverbPreset(preset: ReverbPreset) = effectManager.setReverbPreset(preset)

    fun setReverbCustomParameters(roomSize: Float, decayMs: Int, levelDb: Float) =
        effectManager.setReverbCustomParameters(roomSize, decayMs, levelDb)

    // Acciones del Temporizador de Apagado (Sleep Timer con Fade-Out de 10s)
    fun startSleepTimer(minutes: Int) {
        if (minutes <= 0) return
        sleepTimerJob?.cancel()
        val totalSec = minutes * 60
        _sleepTimerState.value = SleepTimerState(
            isActive = true,
            totalSeconds = totalSec,
            remainingSeconds = totalSec,
            isFadingOut = false
        )
        audioPlayer.setVolume(1.0f)

        sleepTimerJob = coroutineScope.launch(Dispatchers.Main) {
            var currentRemaining = totalSec
            while (currentRemaining > 0) {
                delay(1000)
                currentRemaining--
                val isFading = currentRemaining in 1..10
                _sleepTimerState.value = _sleepTimerState.value.copy(
                    remainingSeconds = currentRemaining,
                    isFadingOut = isFading
                )

                if (isFading) {
                    val fadeFactor = (currentRemaining / 10.0f).coerceIn(0.0f, 1.0f)
                    audioPlayer.setVolume(fadeFactor)
                }
            }

            // Al cumplirse el tiempo, pausar reproducción y restaurar volumen para futuras reproducciones
            if (audioPlayer.isPlaying.value) {
                audioPlayer.togglePlayPause()
            }
            audioPlayer.setVolume(1.0f)
            _sleepTimerState.value = SleepTimerState()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        audioPlayer.setVolume(1.0f)
        _sleepTimerState.value = SleepTimerState()
    }

    fun addSleepTimerMinutes(extraMinutes: Int = 5) {
        val current = _sleepTimerState.value
        if (!current.isActive) {
            startSleepTimer(extraMinutes)
            return
        }
        val addedSec = extraMinutes * 60
        val newRemaining = current.remainingSeconds + addedSec
        val newTotal = current.totalSeconds + addedSec
        _sleepTimerState.value = current.copy(
            totalSeconds = newTotal,
            remainingSeconds = newRemaining,
            isFadingOut = false
        )
        audioPlayer.setVolume(1.0f)
    }

    // Normalización de Volumen Inteligente (Spotify -14 LUFS / EBU R128)
    fun setVolumeNormalizationEnabled(enabled: Boolean) = effectManager.setVolumeNormalizationEnabled(enabled)
    fun setVolumeNormalizationMode(mode: Int) = effectManager.setVolumeNormalizationMode(mode)

    // Automix Inteligente DJ & Curva de Ecualización en X
    fun setDjAutomixEnabled(enabled: Boolean) = audioPlayer.setDjAutomixEnabled(enabled)
    fun setDjEqCurveEnabled(enabled: Boolean) = audioPlayer.setDjEqCurveEnabled(enabled)

    fun release() {
        cancelSleepTimer()
    }
}
