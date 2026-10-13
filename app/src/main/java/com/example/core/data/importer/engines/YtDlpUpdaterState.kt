package com.example.core.data.importer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.core.model.PackageUpdateState
import com.example.core.playback.AuraDownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Estado compartido del actualizador OTA de yt-dlp.
 * Rol arquitectónico: alcance, exclusión mutua, flujo reactivo y clientes HTTP.
 */
internal const val YTDLP_UPDATER_TAG = "YtDlpAutoUpdater"
internal const val YTDLP_GITHUB_RELEASES_API = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
/** Tamaño mínimo para considerar válido un binario/paquete yt-dlp. */
internal const val YTDLP_MIN_PACKAGE_BYTES = 50_000L
/** Tamaño mínimo unificado para considerar válido el binario QuickJS C99 real (450-700 KB, nunca < 250 KB). */
internal const val QUICKJS_MIN_PACKAGE_BYTES = 250_000L

internal object YtDlpUpdaterState {
    val updaterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val updateMutex = Mutex()

    val packageState = MutableStateFlow<PackageUpdateState>(PackageUpdateState.Idle)

    val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val downloadHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun hasActiveInternetConnection(context: Context): Boolean {
            return try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
                val net = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(net) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } catch (_: Throwable) {
                true
            }
    }

    fun resetToIdle(context: Context, showNotifications: Boolean) {
            if (showNotifications) {
                AuraDownloadService.dismissPackageNotification(context)
            }
            packageState.value = PackageUpdateState.Idle
    }
}
