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
 * Descarga fragmentada por bloques HTTP Range en paralelo.
 * Rol arquitectónico: pieza de ChunkedStreamDownloader (fachada pública intacta).
 */
internal object ChunkedParallelDownload {
    fun download(
        url: String,
        targetFile: File,
        phase: String,
        alignedHeaders: Map<String, String>,
        httpClient: OkHttpClient,
        onProgress: (DownloadProgress) -> Unit
    ): Boolean {
        return try {
            val probeBuilder = Request.Builder().url(url)
            for ((k, v) in alignedHeaders) {
                if (!k.equals("Range", ignoreCase = true)) {
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
                // Algunos formatos progresivos aceptan &range=start-end por query param en lugar de cabecera Range
                val clenParam = try { Uri.parse(url).getQueryParameter("clen")?.toLongOrNull() } catch (_: Exception) { null }
                if (clenParam != null && clenParam > 0L) {
                    totalBytes = clenParam
                } else {
                    return false
                }
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
                    val chunkBuilder = Request.Builder().url(url)

                    for ((k, v) in alignedHeaders) {
                        if (!k.equals("Range", ignoreCase = true)) {
                            chunkBuilder.header(k, v)
                        }
                    }
                    chunkBuilder.header("Range", "bytes=$currentByte-$endByte")

                    var chunkRes = httpClient.newCall(chunkBuilder.build()).execute()
                    if (!chunkRes.isSuccessful && chunkRes.code != 206) {
                        chunkRes.close()
                        // Si el servidor CDN prefiere parámetro query &range=start-end en lugar de cabecera HTTP Range
                        val separator = if (url.contains("?")) "&" else "?"
                        val queryRangeUrl = "${url}${separator}range=$currentByte-$endByte"
                        val queryRangeBuilder = Request.Builder().url(queryRangeUrl)
                        for ((k, v) in alignedHeaders) {
                            if (!k.equals("Range", ignoreCase = true)) {
                                queryRangeBuilder.header(k, v)
                            }
                        }
                        chunkRes = httpClient.newCall(queryRangeBuilder.build()).execute()
                        if (!chunkRes.isSuccessful && chunkRes.code != 206) {
                            val code = chunkRes.code
                            chunkRes.close()
                            AuraDebugManager.logWarning("Downloader", "Bloque rechazado con código $code. Recurriendo a descarga lineal.")
                            return false
                        }
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
