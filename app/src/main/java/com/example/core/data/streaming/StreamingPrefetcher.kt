package com.example.core.data.streaming

import com.example.core.data.importer.download.ChunkedStreamDownloader
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pre-descarga no lineal por ventanas HTTP Range con telemetría.
 * Rol arquitectónico: pieza de StreamingCacheManager (fachada pública intacta).
 */
internal class StreamingPrefetcher(
    private val s: StreamingCacheState,
    private val lookup: StreamingCacheLookup,
    private val requestEvict: suspend () -> Unit,
    private val isVideoAllowed: () -> Boolean
) {
    fun prefetchAudioAheadAsync(
        videoId: String,
        streamUrl: String,
        onReadyForLocalPlayback: ((File) -> Unit)? = null
    ) {
        val safeId = s.sanitizeId(videoId)
        if (safeId.isBlank() || streamUrl.isBlank()) return

        val existing = lookup.getCachedAudioFile(safeId)
        if (existing != null) {
            onReadyForLocalPlayback?.invoke(existing)
            return
        }

        val jobKey = "audio_$safeId"
        if (s.jobs[jobKey]?.isActive == true) return

        val job = s.scope.launch {
            try {
                requestEvict()
                val targetFile = File(s.audioDir, "stream_audio_$safeId.m4a")
                val tempFile = File(s.audioDir, "stream_audio_${safeId}.part")

                // Descarga acelerada por bloques HTTP Range (estilo YouTube Read-Ahead)
                val success = downloadWindow(
                    safeId = safeId,
                    streamUrl = streamUrl,
                    outputFile = tempFile,
                    maxPreloadBytes = StreamingCacheState.MAX_SINGLE_AUDIO_CACHE_BYTES
                )

                if (success && tempFile.exists() && tempFile.length() > StreamingCacheState.MIN_VALID_AUDIO_BYTES) {
                    s.mutex.withLock {
                        if (targetFile.exists()) targetFile.delete()
                        tempFile.renameTo(targetFile)
                        s.touchFile(targetFile)
                    }
                    requestEvict()
                    onReadyForLocalPlayback?.invoke(targetFile)
                } else {
                    try { tempFile.delete() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                AuraDebugManager.logWarning(
                    tag = "StreamingCache",
                    message = "Aviso en pre-carga de audio ($safeId): ${e.localizedMessage ?: "Error en pre-carga por bloques"}",
                    throwable = e
                )
            } finally {
                s.jobs.remove(jobKey)
            }
        }
        s.jobs[jobKey] = job
    }

    fun prefetchVideoCanvasAsync(
        videoId: String,
        videoStreamUrl: String,
        onVideoCached: ((File) -> Unit)? = null
    ) {
        if (!isVideoAllowed()) return
        val safeId = s.sanitizeId(videoId)
        if (safeId.isBlank() || videoStreamUrl.isBlank()) return

        val existing = lookup.getCachedVideoFile(safeId)
        if (existing != null) {
            onVideoCached?.invoke(existing)
            return
        }

        val jobKey = "video_$safeId"
        if (s.jobs[jobKey]?.isActive == true) return

        val job = s.scope.launch {
            try {
                requestEvict()
                val targetFile = File(s.videoDir, "stream_video_$safeId.mp4")
                val tempFile = File(s.videoDir, "stream_video_${safeId}.part")

                val success = downloadWindow(
                    safeId = safeId,
                    streamUrl = videoStreamUrl,
                    outputFile = tempFile,
                    maxPreloadBytes = StreamingCacheState.MAX_SINGLE_VIDEO_CACHE_BYTES
                )

                if (success && tempFile.exists() && tempFile.length() > StreamingCacheState.MIN_VALID_VIDEO_BYTES) {
                    s.mutex.withLock {
                        if (targetFile.exists()) targetFile.delete()
                        tempFile.renameTo(targetFile)
                        s.touchFile(targetFile)
                    }
                    requestEvict()
                    onVideoCached?.invoke(targetFile)
                } else {
                    try { tempFile.delete() } catch (_: Exception) {}
                }
            } catch (_: Exception) {
            } finally {
                s.jobs.remove(jobKey)
            }
        }
        s.jobs[jobKey] = job
    }

    private suspend fun downloadWindow(
        safeId: String,
        streamUrl: String,
        outputFile: File,
        maxPreloadBytes: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolvedHeaders = StreamingHttpDataSourceConfig.resolveHeadersForStream(
                videoId = safeId,
                rawUrl = streamUrl
            )

            // 1. Intentar primero con el acelerador por bloques HTTP Range y cabeceras alineadas al cliente c=
            val chunkedOk = ChunkedStreamDownloader.downloadUrlToFile(
                url = streamUrl,
                targetFile = outputFile,
                phase = "Pre-cargando caché en segundo plano...",
                customHeaders = resolvedHeaders,
                onProgress = {}
            )
            if (chunkedOk && outputFile.exists() && outputFile.length() > StreamingCacheState.MIN_VALID_AUDIO_BYTES) {
                return@withContext true
            }

            // 2. Si el stream inicial devolvió 403 y es un ID de YouTube (11 caracteres), resolver stream alternativo con InnerTube / Invidious
            if (safeId.length == 11) {
                val isVideoTarget = outputFile.name.endsWith(".mp4", ignoreCase = true)
                val watchUrl = "https://www.youtube.com/watch?v=$safeId"
                val altInfo = com.example.core.data.importer.InnerTubeClient.resolve(safeId, watchUrl)
                    ?: com.example.core.data.importer.InvidiousStreamResolver.resolve(safeId, watchUrl)

                if (altInfo != null) {
                    val altUrl = if (isVideoTarget) altInfo.videoUrl else (altInfo.audioUrl ?: altInfo.videoUrl)
                    val altHeaders = if (isVideoTarget) {
                        altInfo.videoHeaders.ifEmpty { altInfo.httpHeaders }
                    } else {
                        altInfo.audioHeaders.ifEmpty { altInfo.httpHeaders }
                    }
                    if (!altUrl.isNullOrBlank() && altUrl != streamUrl) {
                        val altOk = ChunkedStreamDownloader.downloadUrlToFile(
                            url = altUrl,
                            targetFile = outputFile,
                            phase = "Pre-cargando caché en segundo plano (Fallback)...",
                            customHeaders = altHeaders,
                            onProgress = {}
                        )
                        if (altOk && outputFile.exists() && outputFile.length() > StreamingCacheState.MIN_VALID_AUDIO_BYTES) {
                            return@withContext true
                        }
                    }
                }
            }

            // 3. Respaldo por ventanas de rango secuenciales rápidas de 512 KB
            var downloaded = 0L
            val chunkSize = 512 * 1024L
            RandomAccessFile(outputFile, "rw").use { raf ->
                while (downloaded < maxPreloadBytes) {
                    val endByte = downloaded + chunkSize - 1
                    val conn = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 10000
                        resolvedHeaders.forEach { (k, v) ->
                            if (!k.equals("Range", ignoreCase = true)) {
                                setRequestProperty(k, v)
                            }
                        }
                        setRequestProperty("Range", "bytes=$downloaded-$endByte")
                    }
                    val code = conn.responseCode
                    if (code != HttpURLConnection.HTTP_PARTIAL && code != HttpURLConnection.HTTP_OK) {
                        conn.disconnect()
                        break
                    }
                    val stream = conn.inputStream
                    val buffer = ByteArray(32 * 1024)
                    var readInChunk = 0L
                    var n: Int
                    while (stream.read(buffer).also { n = it } != -1) {
                        raf.write(buffer, 0, n)
                        downloaded += n
                        readInChunk += n
                        if (downloaded >= maxPreloadBytes) break
                    }
                    stream.close()
                    conn.disconnect()

                    if (code == HttpURLConnection.HTTP_OK || readInChunk < chunkSize / 2) {
                        break
                    }
                }
            }
            outputFile.exists() && outputFile.length() > StreamingCacheState.MIN_VALID_AUDIO_BYTES
        } catch (_: Exception) {
            false
        }
    }
}
