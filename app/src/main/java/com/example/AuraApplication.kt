package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.data.importer.FFmpegNativeEngine
import com.example.data.importer.YtDlpAutoUpdater
import com.example.data.importer.YtDlpNativeEngine
import com.example.debug.AuraDebugManager
import com.example.ui.theme.ArtworkColorExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Clase Application principal de Aura Music.
 * Inicializa el sistema de telemetría y monitor de depuración global [AuraDebugManager],
 * prepara de forma asíncrona en segundo plano los motores nativos puros de FFmpeg y Python yt-dlp,
 * supervisa el ciclo de vida y captura eventos críticos de memoria baja del sistema.
 */
class AuraApplication : Application() {

    companion object {
        @Volatile
        var isAppInForeground: Boolean = false
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Inicializar el monitor y el capturador de excepciones no controladas (Crashes)
        AuraDebugManager.init(this)
        AuraDebugManager.logInfo("Application", "Aura Music iniciada correctamente.")

        // Inicialización asíncrona en segundo plano de los motores nativos (FFmpeg puro y entorno Python)
        applicationScope.launch {
            try {
                FFmpegNativeEngine.init(applicationContext)
                YtDlpNativeEngine.init(applicationContext)
                // Chequeo en caliente OTA de yt-dlp sin bloquear la interfaz
                YtDlpAutoUpdater.checkAndUpdate(applicationContext, forceDownload = false)
            } catch (e: Exception) {
                AuraDebugManager.logWarning("Application", "Aviso en inicialización de motores nativos: ${e.message}")
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        ArtworkColorExtractor.clearCache()
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
                AuraDebugManager.logWarning(
                    "Memory",
                    "Memoria del sistema bajo presión crítica (TRIM_MEMORY level: $level). Cachés liberadas."
                )
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                ArtworkColorExtractor.clearCache()
                AuraDebugManager.logWarning(
                    "Memory",
                    "Memoria del sistema bajo presión alta (TRIM_MEMORY level: $level). Cachés liberadas."
                )
            }
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                ArtworkColorExtractor.releaseRetriever()
                AuraDebugManager.logInfo(
                    "Memory",
                    "Interfaz oculta en segundo plano (TRIM_MEMORY_UI_HIDDEN level: 20). Transición normal a reposo."
                )
            }
        }
    }
}
