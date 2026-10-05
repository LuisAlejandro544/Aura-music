package com.example.data.importer.download

import com.example.debug.AuraDebugManager
import com.example.model.DownloadProgress
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Aura Music - Acelerador y Descargador de Flujos Multimedia por Rangos HTTP
 *
 * Responsabilidades:
 * - Aceleración mediante fragmentación por bloques HTTP Range (Chunked Range Download),
 *   eliminando por completo la limitación artificial de ~63 KB/s de YouTube.
 * - Descarga lineal continua de respaldo con reintentos limpios en HTTP 403.
 * - Sonda de tamaño total y emisión fluida de métricas de progreso (bytes/s, porcentaje, fase).
 */
object ChunkedStreamDownloader {

    val defaultHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun downloadUrlToFile(
        url: String,
        targetFile: File,
        phase: String,
        customHeaders: Map<String, String> = emptyMap(),
        httpClient: OkHttpClient = defaultHttpClient,
        onProgress: (DownloadProgress) -> Unit
    ): Boolean {
        val isYoutubeStream = url.contains("googlevideo.com") || url.contains("youtube.com")

        // 1. Si la URL contiene rangos parciales (&range=0-...), probar primero sin rango para descargar el archivo completo
        val cleanUrl = if (isYoutubeStream && url.contains("&range=")) {
            url.replace(Regex("&range=[^&]+"), "")
                .replace(Regex("&rn=[^&]+"), "")
                .replace(Regex("&rbuf=[^&]+"), "")
        } else {
            url
        }

        val success = executeDownload(cleanUrl, targetFile, phase, isYoutubeStream, customHeaders, httpClient, onProgress)
        if (success && targetFile.exists() && targetFile.length() > 0L) {
            return true
        }

        // 2. Si la URL limpia falló, reintentar con la URL original
        if (cleanUrl != url) {
            val retrySuccess = executeDownload(url, targetFile, phase, isYoutubeStream, customHeaders, httpClient, onProgress)
            if (retrySuccess && targetFile.exists() && targetFile.length() > 0L) {
                return true
            }
        }
        return false
    }

    private fun executeDownload(
        url: String,
        targetFile: File,
        phase: String,
        isYoutubeStream: Boolean,
        customHeaders: Map<String, String>,
        httpClient: OkHttpClient,
        onProgress: (DownloadProgress) -> Unit
    ): Boolean {
        val cleanUrl = if (url.contains("googlevideo.com")) {
            url.replace(Regex("""&range=\d+-\d+"""), "")
                .replace(Regex("""\?range=\d+-\d+&"""), "?")
        } else url

        // 1. Acelerador de descarga por bloques HTTP Range (evita el estrangulamiento de 63 KB/s de YouTube)
        if (cleanUrl.contains("googlevideo.com")) {
            val chunkedSuccess = executeChunkedDownload(cleanUrl, targetFile, phase, customHeaders, httpClient, onProgress)
            if (chunkedSuccess && targetFile.exists() && targetFile.length() > 0L) {
                return true
            }
        }

        // 2. Descarga lineal estándar de respaldo
        return try {
            val defaultUa = customHeaders["User-Agent"]
                ?: customHeaders["user-agent"]
                ?: "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

            val reqBuilder = Request.Builder().url(cleanUrl)
                .header("User-Agent", defaultUa)
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")

            for ((k, v) in customHeaders) {
                if (!k.equals("Range", ignoreCase = true) && !k.equals("Accept-Encoding", ignoreCase = true)) {
                    reqBuilder.header(k, v)
                }
            }

            if (isYoutubeStream || cleanUrl.contains("googlevideo.com")) {
                if (customHeaders.isEmpty()) {
                    reqBuilder.header("Referer", "https://m.youtube.com/")
                }
                try {
                    val cookies = android.webkit.CookieManager.getInstance().getCookie("https://m.youtube.com")
                    if (!cookies.isNullOrBlank() && !customHeaders.containsKey("Cookie")) {
                        reqBuilder.header("Cookie", cookies)
                    }
                } catch (_: Throwable) {}
            }

            var response = httpClient.newCall(reqBuilder.build()).execute()

            if (response.code == 403 && (isYoutubeStream || cleanUrl.contains("googlevideo.com"))) {
                response.close()
                val fallbackReq = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", defaultUa)
                    .header("Accept", "*/*")
                    .build()
                response = httpClient.newCall(fallbackReq).execute()
            }

            response.use { res ->
                if (!res.isSuccessful) {
                    AuraDebugManager.logWarning("Downloader", "HTTP ${res.code} al descargar: ${cleanUrl.take(50)}...")
                    return false
                }
                val body = res.body ?: return false
                val totalBytes = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L
                        var lastReportTime = System.currentTimeMillis()
                        var bytesSinceLastReport = 0L
                        var currentSpeed = 0L

                        onProgress(
                            DownloadProgress(
                                isDownloading = true,
                                phase = phase,
                                bytesDownloaded = 0L,
                                totalBytes = totalBytes,
                                bytesPerSecond = 0L,
                                progressFraction = 0f
                            )
                        )

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            bytesSinceLastReport += bytesRead

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastReportTime
                            if (elapsed >= 150) {
                                currentSpeed = (bytesSinceLastReport * 1000L) / elapsed
                                val fraction = if (totalBytes > 0) {
                                    (totalRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                } else 0f

                                onProgress(
                                    DownloadProgress(
                                        isDownloading = true,
                                        phase = phase,
                                        bytesDownloaded = totalRead,
                                        totalBytes = totalBytes,
                                        bytesPerSecond = currentSpeed,
                                        progressFraction = fraction
                                    )
                                )
                                lastReportTime = now
                                bytesSinceLastReport = 0L
                            }
                        }

                        val finalFraction = if (totalBytes > 0) 1f else 0f
                        onProgress(
                            DownloadProgress(
                                isDownloading = true,
                                phase = phase,
                                bytesDownloaded = totalRead,
                                totalBytes = if (totalBytes > 0) totalBytes else totalRead,
                                bytesPerSecond = currentSpeed,
                                progressFraction = finalFraction
                            )
                        )
                    }
                }
            }
            targetFile.exists() && targetFile.length() > 0L
        } catch (e: Exception) {
            AuraDebugManager.logWarning("Downloader", "Fallo en descarga: ${e.message}")
            false
        }
    }

    private fun executeChunkedDownload(
        url: String,
        targetFile: File,
        phase: String,
        customHeaders: Map<String, String>,
        httpClient: OkHttpClient,
        onProgress: (DownloadProgress) -> Unit
    ): Boolean {
        return try {
            val signedUa = customHeaders["User-Agent"]
                ?: customHeaders["user-agent"]
                ?: "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

            val probeBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", signedUa)
                .header("Accept", "*/*")

            for ((k, v) in customHeaders) {
                if (!k.equals("Range", ignoreCase = true) && !k.equals("Accept-Encoding", ignoreCase = true)) {
                    probeBuilder.header(k, v)
                }
            }
            probeBuilder.header("Range", "bytes=0-0")

            var totalBytes = -1L
            httpClient.newCall(probeBuilder.build()).execute().use { probeRes ->
                if (probeRes.code == 206) {
                    val crHeader = probeRes.header("Content-Range")
                    totalBytes = crHeader?.substringAfterLast("/")?.trim()?.toLongOrNull() ?: -1L
                } else if (probeRes.isSuccessful) {
                    totalBytes = probeRes.body?.contentLength() ?: -1L
                }
            }

            if (totalBytes <= 0L) {
                return false
            }

            val chunkSize = 2_621_440L // 2.5 MB por fragmento
            var currentByte = 0L
            val buffer = ByteArray(64 * 1024)
            var lastReportTime = System.currentTimeMillis()
            var bytesSinceLastReport = 0L
            var currentSpeed = 0L

            if (targetFile.exists()) {
                targetFile.delete()
            }

            FileOutputStream(targetFile, true).use { fileOut ->
                while (currentByte < totalBytes) {
                    val endByte = minOf(currentByte + chunkSize - 1, totalBytes - 1)
                    val chunkBuilder = Request.Builder()
                        .url(url)
                        .header("User-Agent", signedUa)
                        .header("Accept", "*/*")

                    for ((k, v) in customHeaders) {
                        if (!k.equals("Range", ignoreCase = true) && !k.equals("Accept-Encoding", ignoreCase = true)) {
                            chunkBuilder.header(k, v)
                        }
                    }
                    chunkBuilder.header("Range", "bytes=$currentByte-$endByte")

                    val chunkRes = httpClient.newCall(chunkBuilder.build()).execute()
                    if (!chunkRes.isSuccessful && chunkRes.code != 206) {
                        chunkRes.close()
                        AuraDebugManager.logWarning("Downloader", "Bloque rechazado con código ${chunkRes.code}. Recurriendo a descarga lineal.")
                        return false
                    }

                    chunkRes.body?.byteStream()?.use { chunkStream ->
                        var r: Int
                        while (chunkStream.read(buffer).also { r = it } != -1) {
                            fileOut.write(buffer, 0, r)
                            currentByte += r
                            bytesSinceLastReport += r

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastReportTime
                            if (elapsed >= 150) {
                                currentSpeed = (bytesSinceLastReport * 1000L) / elapsed
                                val fraction = (currentByte.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                onProgress(
                                    DownloadProgress(
                                        isDownloading = true,
                                        phase = phase,
                                        bytesDownloaded = currentByte,
                                        totalBytes = totalBytes,
                                        bytesPerSecond = currentSpeed,
                                        progressFraction = fraction
                                    )
                                )
                                lastReportTime = now
                                bytesSinceLastReport = 0L
                            }
                        }
                    }
                    chunkRes.close()
                }
            }

            onProgress(
                DownloadProgress(
                    isDownloading = true,
                    phase = phase,
                    bytesDownloaded = currentByte,
                    totalBytes = totalBytes,
                    bytesPerSecond = currentSpeed,
                    progressFraction = 1f
                )
            )

            targetFile.exists() && targetFile.length() > 0L
        } catch (e: Exception) {
            AuraDebugManager.logWarning("Downloader", "Descarga por bloques omitida por excepción: ${e.message}")
            false
        }
    }
}
