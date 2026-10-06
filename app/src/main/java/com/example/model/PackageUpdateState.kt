package com.example.model

/**
 * Representa el estado reactivo del motor de verificación y actualización transparente
 * de paquetes nativos (como yt-dlp) en segundo plano al iniciar Aura Music.
 *
 * Rol arquitectónico:
 * - Sincroniza en tiempo real la notificación nativa del sistema (AuraDownloadService)
 *   y el banner no intrusivo dentro de la interfaz principal (PackageUpdateBanner).
 * - Gobierna el bloqueo preventivo temporal de las descargas de video por yt-dlp
 *   mientras se descarga una actualización o está pendiente el reinicio de aplicación.
 */
sealed interface PackageUpdateState {

    /**
     * Indica si las descargas que dependen del paquete yt-dlp deben bloquearse temporalmente
     * para evitar errores de ejecución o colisiones de archivo hasta que se aplique la actualización.
     */
    val isYtDlpTemporarilyLocked: Boolean
        get() = this is Downloading || this is RestartRequired

    /**
     * Estado inactivo (sin notificación ni banner visible).
     */
    data object Idle : PackageUpdateState

    /**
     * Verificando en segundo plano si existe una versión más reciente de los paquetes necesarios.
     */
    data class Checking(
        val message: String = "Verificando paquetes necesarios..."
    ) : PackageUpdateState

    /**
     * Confirmación breve de que todos los paquetes están actualizados a la última versión.
     */
    data class UpToDate(
        val version: String,
        val message: String = "Paquetes al día ($version)"
    ) : PackageUpdateState

    /**
     * Descargando y verificando criptográficamente (SHA-256) una nueva versión del paquete.
     */
    data class Downloading(
        val progressPercent: Int,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val version: String = "",
        val message: String = "Descargando actualización de paquetes..."
    ) : PackageUpdateState

    /**
     * La actualización de paquetes ha finalizado su descarga y validación.
     * Se recomienda al usuario salir o pulsar "Actualizar y Reiniciar ahora" para aplicar los nuevos paquetes.
     */
    data class RestartRequired(
        val version: String,
        val message: String = "Paquetes descargados ($version). Reinicia o actualiza la app para aplicarlos."
    ) : PackageUpdateState
}
