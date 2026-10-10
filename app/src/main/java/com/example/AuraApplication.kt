package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import coil.imageLoader
import com.example.data.importer.FFmpegNativeEngine
import com.example.data.importer.YtDlpAutoUpdater
import com.example.data.importer.YtDlpNativeEngine
import com.example.data.updater.AppReleaseUpdater
import com.example.debug.AuraDebugManager
import com.example.ui.theme.ArtworkColorExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Clase Application principal de Aura Music.
 * Inicializa el sistema de telemetría y monitor de depuración global [AuraDebugManager],
 * prepara de forma asíncrona en segundo plano los motores nativos puros de FFmpeg y Python yt-dlp,
 * supervisa el ciclo de vida de primer/segundo plano ([isAppInForegroundFlow]) para activar el
 * ahorro inteligente de recursos mientras el usuario juega, y captura eventos de memoria del sistema.
 */
class AuraApplication : Application() {

    companion object {
        private val _isAppInForegroundFlow = MutableStateFlow(false)
        val isAppInForegroundFlow: StateFlow<Boolean> = _isAppInForegroundFlow.asStateFlow()

        @Volatile
        var isBackgroundGameModeEnabled: Boolean = true

        var isAppInForeground: Boolean
            get() = _isAppInForegroundFlow.value
            set(value) {
                _isAppInForegroundFlow.value = value
            }

        /**
         * Indica si la aplicación debe suspender procesos visuales/UI que el usuario no está viendo
         * porque se encuentra en segundo plano con el Modo Juego / Ahorro activo.
         */
        fun shouldSuspendBackgroundVisuals(): Boolean {
            return !_isAppInForegroundFlow.value && isBackgroundGameModeEnabled
        }
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val uiPrefs = getSharedPreferences("aura_music_ui_prefs", MODE_PRIVATE)
        isBackgroundGameModeEnabled = uiPrefs.getBoolean("pref_background_game_mode_enabled", true)

        // Inicializar el monitor y el capturador de excepciones no controladas (Crashes)
        AuraDebugManager.init(this)
        AuraDebugManager.logInfo("Application", "Aura Music iniciada correctamente.")

        // Inicialización asíncrona en segundo plano de los motores nativos (FFmpeg puro y entorno Python) y limpieza de residuos
        applicationScope.launch {
            try {
                com.example.data.storage.AppStorageManager.cleanupResidualFiles(applicationContext)
                FFmpegNativeEngine.init(applicationContext)
                YtDlpNativeEngine.init(applicationContext)
                // Chequeo en caliente OTA de yt-dlp sin bloquear la interfaz
                YtDlpAutoUpdater.checkAndUpdate(applicationContext, forceDownload = false)
                // Búsqueda silenciosa de nueva versión APK en GitHub Releases (Pre-Releases -beta)
                AppReleaseUpdater.checkForUpdates(applicationContext, manualCheck = false)
            } catch (e: Exception) {
                AuraDebugManager.logWarning("Application", "Aviso en inicialización de motores nativos: ${e.message}")
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        ArtworkColorExtractor.clearCache()
        try {
            imageLoader.memoryCache?.clear()
        } catch (_: Throwable) {}
        AuraDebugManager.logWarning(
            "Memory",
            "Dispositivo en estado crítico de memoria baja (onLowMemory). Se liberaron cachés de color y metadatos."
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                ArtworkColorExtractor.clearCache()
                try {
                    imageLoader.memoryCache?.clear()
                } catch (_: Throwable) {}
                AuraDebugManager.logWarning(
                    "Memory",
                    "Memoria del sistema bajo presión crítica (TRIM_MEMORY level: $level). Cachés liberadas."
                )
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                ArtworkColorExtractor.clearCache()
                try {
                    imageLoader.memoryCache?.clear()
                } catch (_: Throwable) {}
                AuraDebugManager.logWarning(
                    "Memory",
                    "Memoria del sistema bajo presión alta (TRIM_MEMORY level: $level). Cachés liberadas."
                )
            }
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                if (isBackgroundGameModeEnabled) {
                    ArtworkColorExtractor.trimForBackgroundGaming()
                    try {
                        imageLoader.memoryCache?.clear()
                    } catch (_: Throwable) {}
                } else {
                    ArtworkColorExtractor.releaseRetriever()
                }
                AuraDebugManager.logInfo(
                    "Memory",
                    "Interfaz oculta en segundo plano (TRIM_MEMORY_UI_HIDDEN level: 20). Recursos gráficos no usados liberados para optimizar juegos y multitarea."
                )
            }
        }
    }
}
