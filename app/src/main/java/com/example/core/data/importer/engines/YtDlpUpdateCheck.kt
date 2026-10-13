package com.example.core.data.importer

import android.content.Context
import com.example.AuraApplication
import com.example.core.model.PackageUpdateState
import com.example.core.playback.AuraDownloadService
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.json.JSONObject

/**
 * Verificación contra GitHub Releases y orquestación de la actualización.
 * Rol arquitectónico: decide al día / descargar / error sin atrapar el estado.
 */
    internal suspend fun performYtDlpCheckAndUpdate(
        context: Context,
        forceDownload: Boolean = false,
        showNotifications: Boolean = true
    ): YtDlpAutoUpdater.UpdateResult = withContext(Dispatchers.IO) {
        YtDlpUpdaterState.updateMutex.withLock {
            val appContext = context.applicationContext
            // Si había un paquete .staged pendiente de una sesión previa, aplicarlo antes de verificar
            applyStagedYtDlpUpdate(appContext)
            YtDlpNativeEngine.init(appContext)

            val currentVersion = readInstalledYtDlpVersion(appContext)

            // Si no hay conexión activa a internet, no mostrar notificación ni quedarse colgado
            if (!YtDlpUpdaterState.hasActiveInternetConnection(appContext)) {
                YtDlpUpdaterState.resetToIdle(appContext, showNotifications)
                return@withLock YtDlpAutoUpdater.UpdateResult.Error("Sin conexión a Internet activa.", currentVersion)
            }

            // Solo mostrar la notificación transitoria "Verificando..." si el usuario está en la app o si se forzó;
            // si la app está en segundo plano se notificará únicamente si hay una descarga real en progreso.
            val notifyChecking = showNotifications && (AuraApplication.isAppInForeground || forceDownload)
            val checkingMsg = "Verificando paquetes necesarios..."
            YtDlpUpdaterState.packageState.value = PackageUpdateState.Checking(checkingMsg)
            if (notifyChecking) AuraDownloadService.notifyPackageChecking(appContext, checkingMsg)
            AuraDebugManager.logInfo(YTDLP_UPDATER_TAG, "Comprobando actualizaciones de paquetes (yt-dlp actual: $currentVersion)...")

            try {
                // Consultar metadatos de la última versión con timeout estricto de 16 segundos (evita bloqueos en redes 2G/EDGE)
                val releaseJsonStr = withTimeoutOrNull(16_000L) {
                    val request = Request.Builder()
                        .url(YTDLP_GITHUB_RELEASES_API)
                        .header("User-Agent", "AuraMusic-Android-Client")
                        .header("Accept", "application/vnd.github.v3+json")
                        .get()
                        .build()

                    YtDlpUpdaterState.httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("HTTP ${response.code}")
                        }
                        response.body?.string()
                    }
                }

                if (releaseJsonStr.isNullOrBlank()) {
                    val err = "Tiempo de espera agotado o respuesta vacía al verificar paquetes."
                    AuraDebugManager.logWarning(YTDLP_UPDATER_TAG, err)
                    YtDlpUpdaterState.resetToIdle(appContext, showNotifications)
                    return@withLock YtDlpAutoUpdater.UpdateResult.Error(err, currentVersion)
                }

                val json = JSONObject(releaseJsonStr)
                val latestTagName = json.optString("tag_name", "").trim()
                if (latestTagName.isBlank()) {
                    YtDlpUpdaterState.resetToIdle(appContext, showNotifications)
                    return@withLock YtDlpAutoUpdater.UpdateResult.Error("No se pudo obtener la versión de GitHub.", currentVersion)
                }

                // Comprobar si ya está al día
                if (!forceDownload && currentVersion.equals(latestTagName, ignoreCase = true) && resolveYtDlpFile(appContext).exists()) {
                    AuraDebugManager.logInfo(YTDLP_UPDATER_TAG, "Paquetes al día ($latestTagName).")
                    if (!AuraApplication.isAppInForeground) {
                        // Si el usuario ya salió de la app, quitar inmediatamente cualquier notificación de verificación
                        YtDlpUpdaterState.resetToIdle(appContext, true)
                    } else {
                        YtDlpUpdaterState.packageState.value = PackageUpdateState.UpToDate(
                            version = latestTagName,
                            message = "Paquetes necesarios al día ($latestTagName)"
                        )
                        if (showNotifications) AuraDownloadService.notifyPackageUpToDate(appContext, latestTagName)
                        YtDlpUpdaterState.updaterScope.launch {
                            delay(2400L)
                            if (YtDlpUpdaterState.packageState.value is PackageUpdateState.UpToDate) {
                                YtDlpUpdaterState.resetToIdle(appContext, true)
                            }
                        }
                    }
                    return@withLock YtDlpAutoUpdater.UpdateResult.AlreadyUpToDate(latestTagName)
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

                YtDlpUpdaterState.packageState.value = PackageUpdateState.Downloading(
                    progressPercent = 0,
                    version = latestTagName,
                    message = "Descargando actualización de paquetes ($latestTagName)..."
                )
                if (showNotifications) AuraDownloadService.notifyPackageDownloadProgress(appContext, 0, latestTagName)

                val expectedSha256 = withTimeoutOrNull(15_000L) {
                    fetchYtDlpExpectedSha256(checksumsUrl, "yt-dlp")
                }
                if (expectedSha256.isNullOrBlank()) {
                    val err = "No se pudo obtener el hash SHA-256 oficial para yt-dlp ($latestTagName). Cancelando."
                    AuraDebugManager.logError(YTDLP_UPDATER_TAG, err)
                    YtDlpUpdaterState.resetToIdle(appContext, showNotifications)
                    return@withLock YtDlpAutoUpdater.UpdateResult.Error(err, currentVersion)
                }

                AuraDebugManager.logInfo(YTDLP_UPDATER_TAG, "Descargando paquete yt-dlp ($latestTagName) con SHA-256 ($expectedSha256)...")
                val downloadSuccess = downloadYtDlpWithVerification(
                    url = downloadUrl,
                    expectedSha256 = expectedSha256,
                    versionTag = latestTagName,
                    context = appContext,
                    showNotifications = showNotifications
                )

                if (downloadSuccess) {
                    val applied = applyStagedYtDlpUpdate(appContext)
                    if (applied) {
                        AuraDebugManager.logInfo(YTDLP_UPDATER_TAG, "¡Paquete yt-dlp ($latestTagName) aplicado en caliente con éxito!")
                        YtDlpUpdaterState.packageState.value = PackageUpdateState.UpToDate(latestTagName, "Paquetes actualizados ($latestTagName)")
                        if (showNotifications) AuraDownloadService.notifyPackageUpToDate(appContext, latestTagName)
                        YtDlpUpdaterState.updaterScope.launch {
                            delay(2500L)
                            if (YtDlpUpdaterState.packageState.value is PackageUpdateState.UpToDate) YtDlpUpdaterState.resetToIdle(appContext, true)
                        }
                        YtDlpAutoUpdater.UpdateResult.Updated(currentVersion, latestTagName)
                    } else {
                        AuraDebugManager.logInfo(YTDLP_UPDATER_TAG, "Paquete yt-dlp ($latestTagName) preparado (.staged). Pendiente de reinicio.")
                        YtDlpUpdaterState.packageState.value = PackageUpdateState.RestartRequired(latestTagName, "Paquetes descargados ($latestTagName).")
                        if (showNotifications) AuraDownloadService.notifyPackageRestartRequired(appContext, latestTagName)
                        YtDlpAutoUpdater.UpdateResult.Updated(currentVersion, latestTagName)
                    }
                } else {
                    YtDlpUpdaterState.resetToIdle(appContext, showNotifications)
                    YtDlpAutoUpdater.UpdateResult.Error("Fallo de integridad o descarga del paquete yt-dlp.", currentVersion)
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                val msg = "Excepción al verificar/actualizar paquetes: ${t.message}"
                AuraDebugManager.logWarning(YTDLP_UPDATER_TAG, msg)
                YtDlpUpdaterState.resetToIdle(appContext, true)
                YtDlpAutoUpdater.UpdateResult.Error(msg, currentVersion)
            } finally {
                // Garantía absoluta: nunca dejar el estado ni la notificación atrapados en Checking
                if (YtDlpUpdaterState.packageState.value is PackageUpdateState.Checking) {
                    YtDlpUpdaterState.resetToIdle(appContext, true)
                }
            }
        }
    }
