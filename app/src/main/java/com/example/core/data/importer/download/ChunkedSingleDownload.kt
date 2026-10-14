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
 * Descarga lineal con sonda de tamaño y reintentos en 403.
 * Rol arquitectónico: pieza de ChunkedStreamDownloader (fachada pública intacta).
 */
internal object ChunkedSingleDownload {
    fun download(
        url: String,
        targetFile: File,
        phase: String,
        isYoutubeStream: Boolean,
        customHeaders: Map<String, String>,
        httpClient: OkHttpClient,
        onProgress: (DownloadProgress) -> Unit
    ): Boolean {
        val isGoogleVideoHost = try {
            val h = Uri.parse(url).host?.lowercase() ?: ""
            h == "googlevideo.com" || h.endsWith(".googlevideo.com")
        } catch (_: Exception) {
            false
        }

        val cleanUrl = if (isGoogleVideoHost) {
            url.replace(Regex("""&range=[^&]+"""), "")
                .replace(Regex("""\?range=[^&]+&"""), "?")
                .replace(Regex("""&rn=[^&]+"""), "")
                .replace(Regex("""&rbuf=[^&]+"""), "")
        } else url

        val alignedHeaders = com.example.core.data.streaming.StreamingHttpDataSourceConfig.resolveHeadersForStream(
            videoId = null,
            rawUrl = cleanUrl,
            explicitHeaders = customHeaders
        )

        // 1. Acelerador de descarga por bloques HTTP Range (evita el estrangulamiento de 63 KB/s de YouTube)
        if (isGoogleVideoHost) {
            val chunkedSuccess = ChunkedParallelDownload.download(cleanUrl, targetFile, phase, alignedHeaders, httpClient, onProgress)
            if (chunkedSuccess && targetFile.exists() && targetFile.length() > 0L) {
                return true
            }
        }

        // 2. Descarga lineal estándar de respaldo con cabeceras alineadas al cliente c=
        return try {
            val reqBuilder = Request.Builder().url(cleanUrl)
            for ((k, v) in alignedHeaders) {
                if (!k.equals("Range", ignoreCase = true)) {
                    reqBuilder.header(k, v)
                }
            }

            var response = executeWithBackoff(httpClient, reqBuilder.build())
                ?: return false

            // Si devuelve 403, reintentar con fallbacks de autenticación de cliente y bypass de IP-binding
            if (response.code == 403) {
                response.close()

                // Fallback 1: Si era URL de proxy de Invidious y devolvió 403, intentar URL directa
                val fallbackDirectUrl = customHeaders["X-Direct-Url"]
                if (!fallbackDirectUrl.isNullOrBlank() && fallbackDirectUrl != cleanUrl) {
                    val directReqBuilder = Request.Builder().url(fallbackDirectUrl)
                    for ((k, v) in alignedHeaders) {
                        if (!k.equals("Range", ignoreCase = true) && !k.startsWith("X-", ignoreCase = true)) {
                            directReqBuilder.header(k, v)
                        }
                    }
                    response = httpClient.newCall(directReqBuilder.build()).execute()
                }

                // Fallback 2: Si era URL directa con ip= y devolvió 403, intentar proxy Invidious
                if (response.code == 403) {
                    val invidiousBase = customHeaders["X-Invidious-Base"]
                    if (!invidiousBase.isNullOrBlank() && cleanUrl.contains("googlevideo.com")) {
                        val q = try { Uri.parse(cleanUrl).query } catch (_: Exception) { null }
                        if (!q.isNullOrBlank()) {
                            response.close()
                            val proxyUrl = "${invidiousBase.trimEnd('/')}/videoplayback?$q"
                            val proxyReqBuilder = Request.Builder()
                                .url(proxyUrl)
                                .header("User-Agent", customHeaders["User-Agent"] ?: "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                                .header("Referer", "$invidiousBase/")
                                .header("Origin", invidiousBase)
                            response = httpClient.newCall(proxyReqBuilder.build()).execute()
                        }
                    }
                }

                // Fallback 3: Reintentar exclusivamente con el User-Agent canónico puro del cliente `c=` sin cabeceras extra
                if (response.code == 403 && (isYoutubeStream || isGoogleVideoHost)) {
                    response.close()
                    val clientParam = try { Uri.parse(cleanUrl).getQueryParameter("c") } catch (_: Exception) { null }
                    val canonicalUa = com.example.core.data.streaming.StreamingHttpDataSourceConfig.inferCanonicalUserAgentForClient(clientParam)
                        ?: alignedHeaders["User-Agent"]
                        ?: "Mozilla/5.0 (Android; VR; Meta Quest 3) AppleWebKit/537.36"

                    val fallbackReqBuilder = Request.Builder()
                        .url(cleanUrl)
                        .header("User-Agent", canonicalUa)
                        .header("Accept", "*/*")
                        .header("Accept-Encoding", "identity")

                    if (clientParam == null || clientParam.startsWith("WEB", ignoreCase = true) || clientParam.startsWith("MWEB", ignoreCase = true)) {
                        fallbackReqBuilder
                            .header("Origin", "https://www.youtube.com")
                            .header("Referer", "https://www.youtube.com/")
                    }

                    response = httpClient.newCall(fallbackReqBuilder.build()).execute()
                }
            }

            // v0.2.1-beta.1f: blindaje anti-crash — crear carpeta padre y no dejar parciales corruptos.
            try { targetFile.parentFile?.mkdirs() } catch (_: Throwable) {}
            response.use { res ->
                if (!res.isSuccessful) {
                    val clientTag = try { Uri.parse(cleanUrl).getQueryParameter("c") ?: "N/A" } catch (_: Exception) { "N/A" }
                    AuraDebugManager.logWarning("Downloader", "HTTP ${res.code} (c=$clientTag) al descargar: ${cleanUrl.take(55)}...")
                    try { if (targetFile.exists() && targetFile.length() < 1024L) targetFile.delete() } catch (_: Throwable) {}
                    return false
                }
                val contentType = res.header("Content-Type", "") ?: ""
                if (contentType.contains("text/html", ignoreCase = true)) {
                    AuraDebugManager.logWarning("Downloader", "Respuesta rechazada: Servidor devolvió página HTML en lugar de contenido multimedia (posible captcha o bloqueo Cloudflare).")
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
            try { if (targetFile.exists() && targetFile.length() < 1024L) targetFile.delete() } catch (_: Throwable) {}
            false
        } catch (t: Throwable) {
            // OutOfMemoryError / StackOverflow en gamas bajas: nunca crashear la app.
            AuraDebugManager.logWarning("Downloader", "Descarga abortada por falta de memoria: ${t.message}")
            try { if (targetFile.exists() && targetFile.length() < 1024L) targetFile.delete() } catch (_: Throwable) {}
            try { System.gc() } catch (_: Throwable) {}
            false
        }
    }

    /**
     * Ejecuta una petición con reintentos y backoff (500ms, 1s) solo ante fallos
     * transitorios de E/S. Un 403 NO se reintenta aquí: lo gestiona la rotación
     * de URLs/cabeceras del llamante.
     */
    private fun executeWithBackoff(
        httpClient: OkHttpClient,
        request: okhttp3.Request
    ): okhttp3.Response? {
        var lastError: Exception? = null
        for (attempt in 0..2) {
            try {
                return httpClient.newCall(request).execute()
            } catch (e: java.io.IOException) {
                lastError = e
                if (attempt < 2) {
                    try { Thread.sleep(500L shl attempt) } catch (_: InterruptedException) { return null }
                }
            }
        }
        AuraDebugManager.logWarning("Downloader", "GET agotado tras 3 intentos: ${lastError?.message}")
        return null
    }
}
