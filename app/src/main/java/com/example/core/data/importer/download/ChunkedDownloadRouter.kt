package com.example.core.data.importer.download

import android.net.Uri
import com.example.debug.AuraDebugManager
import com.example.core.model.DownloadProgress
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit


/**
 * Puerta de entrada: elige descarga lineal o fragmentada.
 * Rol arquitectónico: pieza de ChunkedStreamDownloader (fachada pública intacta).
 */
internal object ChunkedDownloadRouter {
    fun downloadUrlToFile(
        url: String,
        targetFile: File,
        phase: String,
        customHeaders: Map<String, String> = emptyMap(),
        httpClient: OkHttpClient = ChunkedStreamDownloader.defaultHttpClient,
        onProgress: (DownloadProgress) -> Unit
    ): Boolean {
        if (!ChunkedUrlGuards.isSafePublicUrl(url)) {
            AuraDebugManager.logWarning("Downloader", "Rechazada URL insegura o local: $url")
            return false
        }

        val isYoutubeStream = ChunkedUrlGuards.isTrustedYoutubeHost(url)

        // 1. Si la URL contiene rangos parciales (&range=0-...), probar primero sin rango para descargar el archivo completo
        val cleanUrl = if (isYoutubeStream && url.contains("&range=")) {
            url.replace(Regex("&range=[^&]+"), "")
                .replace(Regex("&rn=[^&]+"), "")
                .replace(Regex("&rbuf=[^&]+"), "")
        } else {
            url
        }

        val success = ChunkedSingleDownload.download(cleanUrl, targetFile, phase, isYoutubeStream, customHeaders, httpClient, onProgress)
        if (success && targetFile.exists() && targetFile.length() > 0L) {
            return true
        }

        // 2. Si la URL limpia falló, reintentar con la URL original
        if (cleanUrl != url) {
            val retrySuccess = ChunkedSingleDownload.download(url, targetFile, phase, isYoutubeStream, customHeaders, httpClient, onProgress)
            if (retrySuccess && targetFile.exists() && targetFile.length() > 0L) {
                return true
            }
        }
        return false
    }
}
