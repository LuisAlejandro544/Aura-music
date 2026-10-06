package com.example.data.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.debug.AuraDebugManager
import com.example.model.PackageUpdateState
import com.example.playback.AuraDownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

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
 */
object YtDlpAutoUpdater {

    private const val TAG = "YtDlpAutoUpdater"
    private const val GITHUB_RELEASES_API = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"

    private val updaterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updateMutex = Mutex()

    private val _packageUpdateState = MutableStateFlow<PackageUpdateState>(PackageUpdateState.Idle)
    val packageUpdateState: StateFlow<PackageUpdateState> = _packageUpdateState.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    sealed class UpdateResult {
        data class Updated(val previousVersion: String, val newVersion: String) : UpdateResult()
        data class AlreadyUpToDate(val currentVersion: String) : UpdateResult()
        data class Error(val message: String, val currentVersion: String) : UpdateResult()
    }

    /**
     * Indica si las descargas de video por yt-dlp están temporalmente bloqueadas
     * porque hay una descarga de paquetes en curso o pendiente de reinicio.
     */
    fun isYtDlpTemporarilyBlocked(): Boolean {
        return _packageUpdateState.value.isYtDlpTemporarilyLocked
    }

    /**
     * Obtiene el archivo ejecutable / paquete yt-dlp local si existe.
     */
    fun getYtDlpFile(context: Context): File {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) {
            binDir.mkdirs()
        }
        return File(binDir, "yt-dlp")
    }

    private fun getStagedYtDlpFile(context: Context): File {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()
        return File(binDir, "yt-dlp.staged")
    }

    private fun getStagedVersionFile(context: Context): File {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()
        return File(binDir, "ytdlp_version.staged")
    }

    /**
     * Aplica silenciosamente al arrancar la app cualquier actualización que haya quedado
     * descargada (.staged) de la sesión anterior cuando el usuario cerró la app.
     */
    @Synchronized
    fun applyStagedUpdateIfPresent(context: Context): Boolean {
        return try {
            val stagedFile = getStagedYtDlpFile(context)
            val stagedVersionFile = getStagedVersionFile(context)
            if (stagedFile.exists() && stagedFile.length() > 100_000L) {
                val targetFile = getYtDlpFile(context)
                val versionFile = File(context.filesDir, "bin/ytdlp_version.txt")
                val newVer = if (stagedVersionFile.exists()) {
                    stagedVersionFile.readText().trim().ifBlank { "Actualizado" }
                } else {
                    "Actualizado"
                }

                if (targetFile.exists()) targetFile.delete()
                val moved = stagedFile.renameTo(targetFile)
                if (!moved) {
                    stagedFile.copyTo(targetFile, overwrite = true)
                    stagedFile.delete()
                }
                targetFile.setExecutable(true, false)
                versionFile.writeText(newVer)
                if (stagedVersionFile.exists()) stagedVersionFile.delete()
                AuraDebugManager.logInfo(TAG, "Paquete yt-dlp ($newVer) aplicado exitosamente al iniciar la aplicación.")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "No se pudo aplicar paquete .staged al inicio: ${e.message}")
            false
        }
    }

    /**
     * Aplica de inmediato el paquete descargado y fuerza el reinicio limpio de la aplicación
     * para reflejar los nuevos paquetes sin que el usuario tenga que cerrarla manualmente.
     */
    fun applyPendingUpdateAndRestart(context: Context) {
        val appContext = context.applicationContext
        applyStagedUpdateIfPresent(appContext)
        AuraDownloadService.dismissPackageNotification(appContext)
        _packageUpdateState.value = PackageUpdateState.Idle

        try {
            val packageManager = appContext.packageManager
            val launchIntent = packageManager.getLaunchIntentForPackage(appContext.packageName)
            val componentName = launchIntent?.component
            if (componentName != null) {
                val restartIntent = Intent.makeRestartActivityTask(componentName)
                appContext.startActivity(restartIntent)
            }
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Error preparando intent de reinicio: ${e.message}")
        } finally {
            exitProcess(0)
        }
    }

    /**
     * Inicia la verificación asíncrona en segundo plano al abrir la aplicación.
     */
    fun startBackgroundStartupCheck(context: Context) {
        val appContext = context.applicationContext
        updaterScope.launch {
            checkAndUpdate(appContext, forceDownload = false, showNotifications = true)
        }
    }

    /**
     * Lee la versión actualmente instalada en el dispositivo.
     */
    fun getInstalledVersion(context: Context): String {
        val versionFile = File(context.filesDir, "bin/ytdlp_version.txt")
        return if (versionFile.exists()) {
            try {
                versionFile.readText().trim().ifBlank { "Sin instalar" }
            } catch (_: Exception) {
                "Sin instalar"
            }
        } else {
            val ytdlpFile = getYtDlpFile(context)
            if (ytdlpFile.exists() && ytdlpFile.length() > 0L) "Instalado (v. base)" else "No instalado"
        }
    }

    private fun resetToIdle(context: Context, showNotifications: Boolean) {
        if (showNotifications) {
            AuraDownloadService.dismissPackageNotification(context)
        }
        _packageUpdateState.value = PackageUpdateState.Idle
    }

    /**
     * Consulta GitHub y actualiza yt-dlp si hay una versión más reciente,
     * sincronizando el progreso con la notificación nativa y el estado reactivo de la UI.
     */
    suspend fun checkAndUpdate(
        context: Context,
        forceDownload: Boolean = false,
        showNotifications: Boolean = true
    ): UpdateResult = withContext(Dispatchers.IO) {
        updateMutex.withLock {
            val appContext = context.applicationContext
            // Si había un paquete .staged pendiente de una sesión previa, aplicarlo antes de verificar
            applyStagedUpdateIfPresent(appContext)
            YtDlpNativeEngine.init(appContext)

            val currentVersion = getInstalledVersion(appContext)
            val checkingMsg = "Verificando paquetes necesarios..."
            _packageUpdateState.value = PackageUpdateState.Checking(checkingMsg)
            if (showNotifications) AuraDownloadService.notifyPackageChecking(appContext, checkingMsg)
            AuraDebugManager.logInfo(TAG, "Comprobando actualizaciones de paquetes (yt-dlp actual: $currentVersion)...")

            try {
                val request = Request.Builder()
                    .url(GITHUB_RELEASES_API)
                    .header("User-Agent", "AuraMusic-Android-Client")
                    .header("Accept", "application/vnd.github.v3+json")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    val err = "Error al consultar GitHub API: HTTP ${response.code}"
                    AuraDebugManager.logWarning(TAG, err)
                    resetToIdle(appContext, showNotifications)
                    return@withLock UpdateResult.Error(err, currentVersion)
                }

                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    resetToIdle(appContext, showNotifications)
                    return@withLock UpdateResult.Error("Respuesta vacía de GitHub Releases.", currentVersion)
                }

                val json = JSONObject(body)
                val latestTagName = json.optString("tag_name", "").trim()
                if (latestTagName.isBlank()) {
                    resetToIdle(appContext, showNotifications)
                    return@withLock UpdateResult.Error("No se pudo obtener la versión de GitHub.", currentVersion)
                }

                // Comprobar si ya está al día
                if (!forceDownload && currentVersion.equals(latestTagName, ignoreCase = true) && getYtDlpFile(appContext).exists()) {
                    AuraDebugManager.logInfo(TAG, "Paquetes al día ($latestTagName).")
                    _packageUpdateState.value = PackageUpdateState.UpToDate(
                        version = latestTagName,
                        message = "Paquetes necesarios al día ($latestTagName)"
                    )
                    if (showNotifications) AuraDownloadService.notifyPackageUpToDate(appContext, latestTagName)
                    updaterScope.launch {
                        delay(2600L)
                        if (_packageUpdateState.value is PackageUpdateState.UpToDate) {
                            resetToIdle(appContext, true)
                        }
                    }
                    return@withLock UpdateResult.AlreadyUpToDate(latestTagName)
                }

                // Buscar la URL de descarga directa del asset 'yt-dlp' y el archivo SHA2-256SUMS
                var downloadUrl: String? = null
                var checksumsUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        when (asset.optString("name")) {
                            "yt-dlp" -> downloadUrl = asset.optString("browser_download_url")
                            "SHA2-256SUMS" -> checksumsUrl = asset.optString("browser_download_url")
                        }
                    }
                }

                if (downloadUrl.isNullOrBlank()) downloadUrl = "https://github.com/yt-dlp/yt-dlp/releases/download/$latestTagName/yt-dlp"
                if (checksumsUrl.isNullOrBlank()) checksumsUrl = "https://github.com/yt-dlp/yt-dlp/releases/download/$latestTagName/SHA2-256SUMS"

                _packageUpdateState.value = PackageUpdateState.Downloading(
                    progressPercent = 0,
                    version = latestTagName,
                    message = "Descargando actualización de paquetes ($latestTagName)..."
                )
                if (showNotifications) AuraDownloadService.notifyPackageDownloadProgress(appContext, 0, latestTagName)

                val expectedSha256 = fetchExpectedSha256(checksumsUrl, "yt-dlp")
                if (expectedSha256.isNullOrBlank()) {
                    val err = "No se pudo obtener el hash SHA-256 oficial para yt-dlp ($latestTagName). Cancelando."
                    AuraDebugManager.logError(TAG, err)
                    resetToIdle(appContext, showNotifications)
                    return@withLock UpdateResult.Error(err, currentVersion)
                }

                AuraDebugManager.logInfo(TAG, "Descargando paquete yt-dlp ($latestTagName) con SHA-256 ($expectedSha256)...")
                val downloadSuccess = downloadFileWithProgressAndVerification(
                    url = downloadUrl,
                    expectedSha256 = expectedSha256,
                    versionTag = latestTagName,
                    context = appContext,
                    showNotifications = showNotifications
                )

                if (downloadSuccess) {
                    val applied = applyStagedUpdateIfPresent(appContext)
                    if (applied) {
                        AuraDebugManager.logInfo(TAG, "¡Paquete yt-dlp ($latestTagName) aplicado en caliente con éxito!")
                        _packageUpdateState.value = PackageUpdateState.UpToDate(latestTagName, "Paquetes actualizados ($latestTagName)")
                        if (showNotifications) AuraDownloadService.notifyPackageUpToDate(appContext, latestTagName)
                        updaterScope.launch {
                            delay(2500L)
                            if (_packageUpdateState.value is PackageUpdateState.UpToDate) resetToIdle(appContext, true)
                        }
                        UpdateResult.Updated(currentVersion, latestTagName)
                    } else {
                        AuraDebugManager.logInfo(TAG, "Paquete yt-dlp ($latestTagName) preparado (.staged). Pendiente de reinicio.")
                        _packageUpdateState.value = PackageUpdateState.RestartRequired(latestTagName, "Paquetes descargados ($latestTagName).")
                        if (showNotifications) AuraDownloadService.notifyPackageRestartRequired(appContext, latestTagName)
                        UpdateResult.Updated(currentVersion, latestTagName)
                    }
                } else {
                    resetToIdle(appContext, showNotifications)
                    UpdateResult.Error("Fallo de integridad o descarga del paquete yt-dlp.", currentVersion)
                }
            } catch (e: Exception) {
                val msg = "Excepción al verificar/actualizar paquetes: ${e.message}"
                AuraDebugManager.logWarning(TAG, msg)
                resetToIdle(appContext, showNotifications)
                UpdateResult.Error(msg, currentVersion)
            }
        }
    }

    /**
     * Consulta el archivo oficial SHA2-256SUMS de GitHub Releases y extrae el hash del asset.
     */
    private fun fetchExpectedSha256(checksumsUrl: String, assetName: String): String? {
        return try {
            if (!isValidDownloadHost(checksumsUrl)) return null
            val req = Request.Builder().url(checksumsUrl).header("User-Agent", "AuraMusic-Android-Client").build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return null
            val content = resp.body?.string() ?: return null

            content.lineSequence().firstOrNull { line ->
                line.trim().endsWith(" $assetName") || line.trim().endsWith("*$assetName")
            }?.trim()?.split("\\s+".toRegex())?.firstOrNull()?.lowercase()
        } catch (_: Throwable) {
            null
        }
    }

    private fun isValidDownloadHost(url: String): Boolean {
        val host = Uri.parse(url).host?.lowercase() ?: return false
        return host == "github.com" || host.endsWith(".github.com") ||
                host == "objects.githubusercontent.com" || host.endsWith(".githubusercontent.com")
    }

    private fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buf = ByteArray(8192)
            var n: Int
            while (stream.read(buf).also { n = it } != -1) digest.update(buf, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Descarga el paquete reportando progreso en vivo hacia la notificación nativa y el banner UI,
     * valida su integridad SHA-256 y lo deja preparado (.staged) para su activación limpia.
     */
    private fun downloadFileWithProgressAndVerification(
        url: String,
        expectedSha256: String?,
        versionTag: String,
        context: Context,
        showNotifications: Boolean
    ): Boolean {
        if (!isValidDownloadHost(url)) {
            AuraDebugManager.logWarning(TAG, "Rechazada descarga desde dominio no confiable: $url")
            return false
        }

        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()

        val tempFile = File(binDir, "yt-dlp.tmp")
        val stagedFile = getStagedYtDlpFile(context)
        val stagedVersionFile = getStagedVersionFile(context)

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AuraMusic-Android-Client")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return false

            val body = response.body ?: return false
            val totalBytes = body.contentLength().takeIf { it > 0L } ?: 3_800_000L
            var downloadedBytes = 0L
            var lastReportedPct = -1

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        val pct = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 95)
                        if (pct >= lastReportedPct + 2 || lastReportedPct == -1) {
                            lastReportedPct = pct
                            _packageUpdateState.value = PackageUpdateState.Downloading(
                                progressPercent = pct,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                version = versionTag,
                                message = "Descargando actualización de paquetes ($pct%)..."
                            )
                            if (showNotifications) {
                                AuraDownloadService.notifyPackageDownloadProgress(context, pct, versionTag)
                            }
                        }
                    }
                }
            }

            if (!tempFile.exists() || tempFile.length() < 100_000L) {
                tempFile.delete()
                if (showNotifications) AuraDownloadService.dismissPackageNotification(context)
                return false
            }

            // Reportar progreso de verificación de hash SHA-256
            _packageUpdateState.value = PackageUpdateState.Downloading(
                progressPercent = 98,
                downloadedBytes = totalBytes,
                totalBytes = totalBytes,
                version = versionTag,
                message = "Verificando integridad SHA-256 (98%)..."
            )
            if (showNotifications) {
                AuraDownloadService.notifyPackageDownloadProgress(context, 98, versionTag)
            }

            if (expectedSha256.isNullOrBlank()) {
                AuraDebugManager.logError(
                    TAG,
                    "Rechazando actualización OTA: No se pudo obtener la suma criptográfica SHA-256 oficial para yt-dlp ($versionTag). Abortando por seguridad."
                )
                tempFile.delete()
                if (showNotifications) AuraDownloadService.dismissPackageNotification(context)
                return false
            }

            val computedHash = computeFileSha256(tempFile)
            if (!computedHash.equals(expectedSha256, ignoreCase = true)) {
                AuraDebugManager.logError(
                    TAG,
                    "Fallo de integridad SHA-256 en yt-dlp. Esperado: $expectedSha256, Calculado: $computedHash"
                )
                tempFile.delete()
                if (showNotifications) AuraDownloadService.dismissPackageNotification(context)
                return false
            }

            if (stagedFile.exists()) stagedFile.delete()
            val renamed = tempFile.renameTo(stagedFile)
            if (!renamed) {
                tempFile.copyTo(stagedFile, overwrite = true)
                tempFile.delete()
            }
            stagedFile.setExecutable(true, false)
            stagedVersionFile.writeText(versionTag)

            // Emitir 100% completado con éxito
            _packageUpdateState.value = PackageUpdateState.Downloading(
                progressPercent = 100,
                downloadedBytes = totalBytes,
                totalBytes = totalBytes,
                version = versionTag,
                message = "¡Paquete verificado exitosamente! (100%)"
            )
            if (showNotifications) {
                AuraDownloadService.notifyPackageDownloadProgress(context, 100, versionTag)
            }
            return true
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            AuraDebugManager.logWarning(TAG, "Error en downloadFileWithProgressAndVerification: ${e.message}")
            if (showNotifications) {
                AuraDownloadService.dismissPackageNotification(context)
            }
            return false
        }
    }
}
