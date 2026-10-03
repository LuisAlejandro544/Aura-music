package com.example.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.example.debug.AuraDebugManager
import com.example.model.HeadphoneConfig
import com.example.model.HeadsetButtonAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Controlador de hardware y automatizaciones para Auriculares / Audífonos en Aura Music.
 * - Detecta en tiempo real la conexión/desconexión de auriculares (cable 3.5mm, USB-C, Bluetooth).
 * - Sincroniza con el motor C++20 para que el filtro Crossfeed solo se aplique con audífonos conectados.
 * - Implementa protección contra desconexión involuntaria ("Becoming Noisy" Guard).
 * - Gestiona memoria de volumen dedicada (audífonos vs altavoz).
 * - Interpreta pulsaciones físicas simples, dobles, triples y prolongadas de auriculares.
 */
class HeadphoneController(
    private val context: Context,
    private val onPauseRequested: () -> Unit,
    private val onPlayRequested: () -> Unit,
    private val onTogglePlayPauseRequested: () -> Unit,
    private val onNextRequested: () -> Unit,
    private val onPrevRequested: () -> Unit,
    private val onToggleFavoriteRequested: () -> Unit,
    private val onSeekByOffset: (Long) -> Unit,
    private val onVolumeChangeRequested: (Float) -> Unit,
    private val getCurrentVolume: () -> Float
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _config = MutableStateFlow(HeadphoneConfig())
    val config: StateFlow<HeadphoneConfig> = _config.asStateFlow()

    private var clickCount = 0
    private var clickJob: Job? = null
    private var lastDownTime = 0L

    // BroadcastReceiver para "Becoming Noisy" (desconexión física o bluetooth)
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (_config.value.isBecomingNoisyGuardEnabled) {
                    AuraDebugManager.logInfo(
                        "HeadphoneController",
                        "Desconexión de auriculares detectada (Becoming Noisy): Pausando reproducción."
                    )
                    onPauseRequested()
                }
            }
        }
    }

    // AudioDeviceCallback para monitorear en tiempo real la conexión/desconexión
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            evaluateConnectedDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            evaluateConnectedDevices()
        }
    }

    init {
        // Registrar callback de dispositivos de audio
        try {
            audioManager?.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        } catch (e: Throwable) {
            AuraDebugManager.logWarning("HeadphoneController", "No se pudo registrar AudioDeviceCallback: ${e.message}")
        }

        // Registrar receptor de Becoming Noisy
        try {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            context.registerReceiver(becomingNoisyReceiver, filter)
        } catch (e: Throwable) {
            AuraDebugManager.logWarning("HeadphoneController", "No se pudo registrar BecomingNoisyReceiver: ${e.message}")
        }

        // Evaluación inicial del estado de audífonos
        evaluateConnectedDevices(isInitial = true)
    }

    /**
     * Evalúa si hay audífonos conectados por cable, USB o Bluetooth y sincroniza el DSP en C++20.
     */
    fun evaluateConnectedDevices(isInitial: Boolean = false) {
        val am = audioManager ?: return
        var isHeadphone = false
        var deviceName = "Altavoz del Teléfono"

        try {
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                        isHeadphone = true
                        deviceName = "Auriculares Cableados (3.5mm)"
                        break
                    }
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_DEVICE -> {
                        isHeadphone = true
                        deviceName = "Auriculares USB-C / DAC"
                        break
                    }
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_HEARING_AID,
                    26, // AudioDeviceInfo.TYPE_BLE_HEADSET
                    27  // AudioDeviceInfo.TYPE_BLE_SPEAKER
                    -> {
                        isHeadphone = true
                        deviceName = "Auriculares Bluetooth"
                        break
                    }
                }
            }
        } catch (e: Throwable) {
            AuraDebugManager.logWarning("HeadphoneController", "Error al consultar dispositivos de audio: ${e.message}")
        }

        val wasConnected = _config.value.isHeadphoneConnected
        val currentCfg = _config.value
        val newCfg = currentCfg.copy(
            isHeadphoneConnected = isHeadphone,
            connectedDeviceName = deviceName
        )
        _config.value = newCfg

        // Sincronizar con el motor C++20 (el Crossfeed solo se activa si hay audífonos conectados)
        NativeAudioEngine.setCrossfeedHeadphonesConnected(isHeadphone)

        // Manejo de Memoria de Volumen Dedicada
        if (!isInitial && wasConnected != isHeadphone && currentCfg.isDedicatedVolumeMemoryEnabled) {
            if (isHeadphone) {
                // Se acaban de conectar los auriculares: guardar volumen de altavoz y restaurar volumen de audífonos
                val currentVol = getCurrentVolume()
                _config.value = _config.value.copy(speakerVolumeLevel = currentVol)
                onVolumeChangeRequested(currentCfg.headphoneVolumeLevel)
                AuraDebugManager.logInfo("HeadphoneController", "Audífonos conectados: Volumen restaurado a ${(currentCfg.headphoneVolumeLevel * 100).toInt()}%")
            } else {
                // Se acaban de desconectar los auriculares: guardar volumen de audífonos y restaurar volumen de altavoz
                val currentVol = getCurrentVolume()
                _config.value = _config.value.copy(headphoneVolumeLevel = currentVol)
                onVolumeChangeRequested(currentCfg.speakerVolumeLevel)
                AuraDebugManager.logInfo("HeadphoneController", "Audífonos desconectados: Volumen de altavoz restaurado a ${(currentCfg.speakerVolumeLevel * 100).toInt()}%")
            }
        }
    }

    /**
     * Actualiza la configuración global de auriculares y propaga cambios acústicos a C++20.
     */
    fun updateConfig(newConfig: HeadphoneConfig) {
        val prev = _config.value
        _config.value = newConfig.copy(
            isHeadphoneConnected = prev.isHeadphoneConnected,
            connectedDeviceName = prev.connectedDeviceName
        )

        // Sincronizar Crossfeed C++20
        NativeAudioEngine.setCrossfeedEnabled(newConfig.isCrossfeedEnabled)
        NativeAudioEngine.setCrossfeedStrength(newConfig.crossfeedStrength)
        NativeAudioEngine.setCrossfeedHeadphonesConnected(prev.isHeadphoneConnected)

        // Sincronizar Balance Estéreo C++20
        NativeAudioEngine.setBalanceEnabled(newConfig.isBalanceControlEnabled)
        NativeAudioEngine.setStereoBalance(newConfig.stereoBalance)
    }

    fun setCrossfeedEnabled(enabled: Boolean) {
        updateConfig(_config.value.copy(isCrossfeedEnabled = enabled))
    }

    fun setCrossfeedStrength(strengthMode: Int) {
        updateConfig(_config.value.copy(crossfeedStrength = strengthMode))
    }

    fun setBalanceControlEnabled(enabled: Boolean) {
        updateConfig(_config.value.copy(isBalanceControlEnabled = enabled))
    }

    fun setStereoBalance(balance: Float) {
        updateConfig(_config.value.copy(stereoBalance = balance.coerceIn(-1.0f, 1.0f)))
    }

    fun setBecomingNoisyGuardEnabled(enabled: Boolean) {
        updateConfig(_config.value.copy(isBecomingNoisyGuardEnabled = enabled))
    }

    fun setFadeInOnResumeEnabled(enabled: Boolean) {
        updateConfig(_config.value.copy(isFadeInOnResumeEnabled = enabled))
    }

    fun setDedicatedVolumeMemoryEnabled(enabled: Boolean) {
        updateConfig(_config.value.copy(isDedicatedVolumeMemoryEnabled = enabled))
    }

    fun setHeadsetControlsEnabled(enabled: Boolean) {
        updateConfig(_config.value.copy(isHeadsetControlsEnabled = enabled))
    }

    fun setSingleClickAction(action: HeadsetButtonAction) {
        updateConfig(_config.value.copy(singleClickAction = action))
    }

    fun setDoubleClickAction(action: HeadsetButtonAction) {
        updateConfig(_config.value.copy(doubleClickAction = action))
    }

    fun setTripleClickAction(action: HeadsetButtonAction) {
        updateConfig(_config.value.copy(tripleClickAction = action))
    }

    fun setLongClickAction(action: HeadsetButtonAction) {
        updateConfig(_config.value.copy(longClickAction = action))
    }

    /**
     * Intercepta eventos de teclas físicas de auriculares (Media Key Events).
     * Soporta detección de pulsaciones simples, dobles, triples y pulsación prolongada.
     */
    fun onKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (!_config.value.isHeadsetControlsEnabled) return false

        val isHeadsetKey = keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE

        if (!isHeadsetKey) {
            // Teclas directas de siguiente/anterior
            if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT && event.action == KeyEvent.ACTION_DOWN) {
                onNextRequested()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS && event.action == KeyEvent.ACTION_DOWN) {
                onPrevRequested()
                return true
            }
            return false
        }

        // Manejo de pulsación simple/múltiple o prolongada
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) {
                lastDownTime = System.currentTimeMillis()
            } else if (event.repeatCount > 0 && (System.currentTimeMillis() - lastDownTime) > 700) {
                // Pulsación prolongada detectada
                clickJob?.cancel()
                clickCount = 0
                executeAction(_config.value.longClickAction)
                return true
            }
            return true
        } else if (event.action == KeyEvent.ACTION_UP) {
            val pressDuration = System.currentTimeMillis() - lastDownTime
            if (pressDuration >= 700) {
                // Ya procesado como clic largo
                return true
            }

            clickCount++
            clickJob?.cancel()
            clickJob = scope.launch {
                delay(380) // Ventana de espera para clics rápidos
                val count = clickCount
                clickCount = 0
                when (count) {
                    1 -> executeAction(_config.value.singleClickAction)
                    2 -> executeAction(_config.value.doubleClickAction)
                    3 -> executeAction(_config.value.tripleClickAction)
                    else -> executeAction(_config.value.singleClickAction)
                }
            }
            return true
        }

        return false
    }

    private fun executeAction(action: HeadsetButtonAction) {
        when (action) {
            HeadsetButtonAction.PLAY_PAUSE -> onTogglePlayPauseRequested()
            HeadsetButtonAction.NEXT_TRACK -> onNextRequested()
            HeadsetButtonAction.PREV_TRACK -> onPrevRequested()
            HeadsetButtonAction.TOGGLE_FAVORITE -> onToggleFavoriteRequested()
            HeadsetButtonAction.SEEK_FORWARD_15 -> onSeekByOffset(15000L)
            HeadsetButtonAction.SEEK_BACKWARD_15 -> onSeekByOffset(-15000L)
            HeadsetButtonAction.NONE -> {}
        }
    }

    fun release() {
        try {
            audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (ignored: Throwable) {}
        try {
            context.unregisterReceiver(becomingNoisyReceiver)
        } catch (ignored: Throwable) {}
        clickJob?.cancel()
    }
}
