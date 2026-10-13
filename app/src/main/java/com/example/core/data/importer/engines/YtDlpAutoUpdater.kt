package com.example.core.data.importer

import android.content.Context
import com.example.core.model.PackageUpdateState
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Gestor de Verificación y Actualización Transparente en Segundo Plano (OTA) para paquetes nativos (yt-dlp).
 *
 * Arquitectura y Principio de Operación:
 * 1. Al abrir Aura Music, aplica cualquier paquete previamente descargado (.staged) y lanza en segundo plano
 *    la verificación contra GitHub Releases sin bloquear el arranque de la interfaz.
 * 2. Muestra una notificación nativa en la barra de estado ("Verificando paquetes necesarios...") sincronizada
 *    con un indicador no intrusivo dentro de la app (PackageUpdateBanner).
 * 3. Si detecta una nueva versión de yt-dlp, actualiza la notificación nativa y el banner con una barra
 *    de progreso en tiempo real ("Descargando actualización de paquetes...") y bloquea temporalmente
 *    las descargas por yt-dlp para prevenir conflictos o fallos a mitad de actualización.
 * 4. Verifica criptográficamente el paquete descargado con SHA-256 (SHA2-256SUMS) y, al finalizar,
 *    recomienda al usuario salir de la app o pulsar "Actualizar y Reiniciar" para aplicar los nuevos paquetes.
 *
 * Rol arquitectónico: fachada pública estable; la lógica vive en módulos internos.
 */
object YtDlpAutoUpdater {

    sealed class UpdateResult {
        data class Updated(val previousVersion: String, val newVersion: String) : UpdateResult()
        data class AlreadyUpToDate(val currentVersion: String) : UpdateResult()
        data class Error(val message: String, val currentVersion: String) : UpdateResult()
    }

    val packageUpdateState: StateFlow<PackageUpdateState> = YtDlpUpdaterState.packageState.asStateFlow()

    /**
     * Indica si las descargas de video por yt-dlp están temporalmente bloqueadas
     * porque hay una descarga de paquetes en curso o pendiente de reinicio.
     */
    fun isYtDlpTemporarilyBlocked(): Boolean {
        return YtDlpUpdaterState.packageState.value.isYtDlpTemporarilyLocked
    }

    /**
     * Obtiene el archivo ejecutable / paquete yt-dlp activo:
     * - Si existe una actualización OTA descargada en `files/bin/yt-dlp`, devuelve esa versión actualizada.
     * - En caso contrario, devuelve directamente `nativeLibraryDir/libytdlp.so` sin duplicar 3MB en `filesDir`.
     */
    fun getYtDlpFile(context: Context): File = resolveYtDlpFile(context)

    /**
     * Aplica silenciosamente al arrancar la app cualquier actualización que haya quedado
     * descargada (.staged) de la sesión anterior cuando el usuario cerró la app.
     */
    @Synchronized
    fun applyStagedUpdateIfPresent(context: Context): Boolean = applyStagedYtDlpUpdate(context)

    /**
     * Aplica de inmediato el paquete descargado y fuerza el reinicio limpio de la aplicación
     * para reflejar los nuevos paquetes sin que el usuario tenga que cerrarla manualmente.
     */
    fun applyPendingUpdateAndRestart(context: Context) = applyPendingYtDlpUpdateAndRestart(context)

    /**
     * Inicia la verificación asíncrona en segundo plano al abrir la aplicación si está habilitada en Ajustes.
     */
    fun startBackgroundStartupCheck(context: Context) {
        val appContext = context.applicationContext
        if (!YtDlpCookieManager.isAutoCheckOtaStartupEnabled(appContext)) {
            AuraDebugManager.logInfo(YTDLP_UPDATER_TAG, "Verificación OTA al inicio omitida por configuración del usuario.")
            return
        }
        YtDlpUpdaterState.updaterScope.launch {
            checkAndUpdate(appContext, forceDownload = false, showNotifications = true)
        }
    }

    /**
     * Lee la versión actualmente instalada en el dispositivo.
     */
    fun getInstalledVersion(context: Context): String = readInstalledYtDlpVersion(context)

    /**
     * Consulta GitHub y actualiza yt-dlp si hay una versión más reciente,
     * sincronizando el progreso con la notificación nativa y el estado reactivo de la UI.
     */
    suspend fun checkAndUpdate(
        context: Context,
        forceDownload: Boolean = false,
        showNotifications: Boolean = true
    ): UpdateResult = performYtDlpCheckAndUpdate(context, forceDownload, showNotifications)

    /**
     * Diagnóstico y verificación nativa de ejecución CLI para yt-dlp.
     * Ejecuta `yt-dlp --version` en el subproceso Python nativo para confirmar
     * que la arquitectura (32/64 bits), librerías C++ y QuickJS operan sin bloqueos.
     */
    suspend fun testYtDlpCliExecution(context: Context): Pair<Boolean, String> = runYtDlpCliDiagnostics(context)
}
