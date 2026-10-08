package com.example.model

/**
 * Modelos de estado y metadatos para el Actualizador Automático de APK (OTA App Updater).
 *
 * Rol arquitectónico:
 * - Representa la información de una nueva versión disponible en GitHub Releases (Pre-Releases `-beta`
 *   para el canal Beta o Releases para el canal estable) sin importar cómo cambie el tag
 *   (ej: `v0.1.0-beta.1a` -> `v0.1.0-beta.2d` -> `v0.2.0-beta.1m`).
 * - Contiene el APK exacto para la arquitectura del teléfono (`arm64-v8a`, `armeabi-v7a` o `universal`),
 *   las notas de versión (`chanelog-beta.md`), el código estratégico y la suma SHA-256 opcional.
 */
data class AppUpdateReleaseInfo(
    val tagName: String,
    val releaseTitle: String,
    val isPrerelease: Boolean,
    val publishedAt: String,
    val changelogNotes: String,
    val matchedAbi: String,
    val apkAssetName: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val sha256SumsUrl: String?,
    val htmlReleaseUrl: String
) {
    val formattedSize: String
        get() {
            if (apkSizeBytes <= 0L) return "Tamaño dinámico"
            val mb = apkSizeBytes.toDouble() / (1024.0 * 1024.0)
            return String.format(java.util.Locale.US, "%.1f MB", mb)
        }
}

/**
 * Estados reactivos del ciclo de búsqueda, descarga, verificación SHA-256 e instalación del APK.
 */
sealed class AppUpdateState {
    /** Sin actividad o notificación descartada por el usuario. */
    data object Idle : AppUpdateState()

    /** Consultando la API de GitHub Releases en segundo plano. */
    data class Checking(
        val repositorySlug: String,
        val message: String = "Buscando nueva versión de Aura..."
    ) : AppUpdateState()

    /** La aplicación instalada ya se encuentra en la versión más reciente del canal. */
    data class UpToDate(
        val currentTag: String,
        val codename: String,
        val message: String = "Ya tienes la versión más reciente instalada ($currentTag • $codename)."
    ) : AppUpdateState()

    /** Se encontró una versión superior compatible con el canal y la arquitectura del dispositivo. */
    data class UpdateAvailable(
        val releaseInfo: AppUpdateReleaseInfo,
        val currentTag: String
    ) : AppUpdateState()

    /** Descargando el APK de la nueva versión con progreso en tiempo real. */
    data class Downloading(
        val releaseInfo: AppUpdateReleaseInfo,
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val statusMessage: String = "Descargando actualización..."
    ) : AppUpdateState()

    /** El APK fue descargado y verificado criptográficamente; listo para abrir el instalador de Android. */
    data class ReadyToInstall(
        val releaseInfo: AppUpdateReleaseInfo,
        val apkFilePath: String,
        val sha256Verified: Boolean
    ) : AppUpdateState()

    /** Ocurrió un aviso o error controlado durante la consulta o descarga. */
    data class Error(
        val message: String
    ) : AppUpdateState()
}
