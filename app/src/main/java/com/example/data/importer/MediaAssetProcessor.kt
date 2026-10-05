package com.example.data.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import com.example.data.storage.AppStorageManager
import com.example.debug.AuraDebugManager
import com.example.model.DownloadProgress
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Aura Music - Procesador Modular de Carátulas WebP y Video Canvas
 *
 * Responsabilidades:
 * - Descarga oficial en cascada de carátulas (maxresdefault, hqdefault, mqdefault, etc.) con compresión WebP sin pérdida.
 * - Extracción alternativa de fotogramas clave en alta resolución desde el video como carátula fallback.
 * - Validación y adquisición de flujo de video dedicado a 480p para Video Canvas.
 * - Procesamiento mediante FFmpeg (Seamless Loop con crossfade xfade o Boomerang Ping-Pong con reverse+concat).
 */
object MediaAssetProcessor {

    /**
     * Valida que el archivo exista y contenga un flujo de video real decodificable.
     */
    fun isValidVideoFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4096L) return false
        return try {
            val vRetriever = MediaMetadataRetriever()
            vRetriever.setDataSource(file.absolutePath)
            val hasVid = vRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            val width = vRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            vRetriever.release()
            hasVid == "yes" || width > 0
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Procesa la carátula oficial en WebP sin pérdida a partir de URLs candidatas o fotograma del video.
     */
    fun processArtwork(
        storageManager: AppStorageManager,
        resolvedInfo: OnlineVideoAudioImporter.ResolvedMediaInfo,
        tempVideoFile: File,
        timestamp: Long,
        httpClient: OkHttpClient
    ): String? {
        var artworkPath: String? = null

        // 1. Intentar descargar portada oficial con soporte en cascada para YouTube
        val candidateCoverUrls = mutableListOf<String>()
        if (!resolvedInfo.coverUrl.isNullOrBlank()) {
            candidateCoverUrls.add(resolvedInfo.coverUrl)
        }
        val ytVideoId = WebStreamExtractor.extractVideoId(resolvedInfo.originalUrl)
        if (ytVideoId != null) {
            candidateCoverUrls.add("https://img.youtube.com/vi/$ytVideoId/maxresdefault.jpg")
            candidateCoverUrls.add("https://img.youtube.com/vi/$ytVideoId/hqdefault.jpg")
            candidateCoverUrls.add("https://img.youtube.com/vi/$ytVideoId/mqdefault.jpg")
            candidateCoverUrls.add("https://i.ytimg.com/vi/$ytVideoId/hqdefault.jpg")
        }

        val artFile = File(storageManager.imagesDir, "art_online_${timestamp}.webp")
        for (coverCandidate in candidateCoverUrls.distinct()) {
            if (artworkPath != null) break
            try {
                val normalizedUrl = if (coverCandidate.startsWith("//")) "https:$coverCandidate" else coverCandidate
                val request = Request.Builder()
                    .url(normalizedUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "image/*,*/*")
                    .build()
                httpClient.newCall(request).execute().use { res ->
                    if (res.isSuccessful) {
                        val stream = res.body?.byteStream()
                        if (stream != null) {
                            val bitmap = BitmapFactory.decodeStream(stream)
                            if (bitmap != null && bitmap.width > 30 && bitmap.height > 30) {
                                FileOutputStream(artFile).use { out ->
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out)
                                    } else {
                                        bitmap.compress(Bitmap.CompressFormat.WEBP, 95, out)
                                    }
                                }
                                artworkPath = artFile.absolutePath
                                bitmap.recycle()
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // Continuar al siguiente candidato
            }
        }

        // 2. Si no hubo portada o falló y tenemos video, extraer fotograma clave del video descargado
        if (artworkPath == null && tempVideoFile.exists() && tempVideoFile.length() > 0L) {
            val frameRetriever = MediaMetadataRetriever()
            try {
                frameRetriever.setDataSource(tempVideoFile.absolutePath)
                val frame = frameRetriever.getFrameAtTime(
                    1_000_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: frameRetriever.frameAtTime
                if (frame != null) {
                    val fallbackArtFile = File(storageManager.imagesDir, "art_online_${timestamp}.webp")
                    FileOutputStream(fallbackArtFile).use { out ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            frame.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out)
                        } else {
                            frame.compress(Bitmap.CompressFormat.WEBP, 95, out)
                        }
                    }
                    artworkPath = fallbackArtFile.absolutePath
                    frame.recycle()
                }
            } catch (_: Throwable) {
            } finally {
                try { frameRetriever.release() } catch (_: Throwable) {}
            }
        }

        return artworkPath
    }

    /**
     * Procesa y optimiza el flujo de Video Canvas vinculando loops o videos sincronizados.
     */
    suspend fun processVideoCanvas(
        context: Context,
        storageManager: AppStorageManager,
        resolvedInfo: OnlineVideoAudioImporter.ResolvedMediaInfo,
        tempVideoFile: File,
        audioFile: File,
        timestamp: Long,
        isLoop: Boolean,
        loopStyle: FFmpegNativeEngine.CanvasLoopStyle,
        onProgressUpdate: (DownloadProgress) -> Unit,
        downloadFunction: (url: String, target: File, phase: String, headers: Map<String, String>) -> Boolean
    ): String? {
        // 1. Si aún no tenemos tempVideoFile y videoUrl es distinto de audioUrl, descargarlo directamente
        if (!isValidVideoFile(tempVideoFile) &&
            !resolvedInfo.videoUrl.isNullOrBlank() &&
            resolvedInfo.videoUrl != resolvedInfo.audioUrl
        ) {
            downloadFunction(
                resolvedInfo.videoUrl,
                tempVideoFile,
                "Descargando Video Canvas de fondo (480p)...",
                resolvedInfo.httpHeaders
            )
        }

        // 2. Si videoUrl == audioUrl, verificar si audioFile era un contenedor combinado
        if (!isValidVideoFile(tempVideoFile) && resolvedInfo.videoUrl == resolvedInfo.audioUrl) {
            if (isValidVideoFile(audioFile)) {
                audioFile.copyTo(tempVideoFile, overwrite = true)
            }
        }

        // 3. Si aún no es válido, resolver activamente un flujo de video MP4 480p dedicado
        if (!isValidVideoFile(tempVideoFile) && WebStreamExtractor.isWebVideoUrl(resolvedInfo.originalUrl)) {
            val ytId = WebStreamExtractor.extractVideoId(resolvedInfo.originalUrl)
            if (ytId != null) {
                AuraDebugManager.logInfo(
                    "OnlineImporter",
                    "Resolviendo flujo de Video Canvas 480p dedicado para $ytId..."
                )
                val videoCandidates = mutableListOf<Pair<String, Map<String, String>>>()

                val innerTubeFallback = InnerTubeClient.resolve(ytId, resolvedInfo.originalUrl)
                if (innerTubeFallback != null &&
                    innerTubeFallback.videoUrl.isNotBlank() &&
                    innerTubeFallback.videoUrl != innerTubeFallback.audioUrl
                ) {
                    videoCandidates.add(innerTubeFallback.videoUrl to innerTubeFallback.httpHeaders)
                }

                val invidiousFallback = InvidiousStreamResolver.resolve(ytId, resolvedInfo.originalUrl)
                if (invidiousFallback != null &&
                    invidiousFallback.videoUrl.isNotBlank() &&
                    invidiousFallback.videoUrl != invidiousFallback.audioUrl
                ) {
                    videoCandidates.add(invidiousFallback.videoUrl to invidiousFallback.httpHeaders)
                }

                if (YtDlpNativeEngine.isAvailable(context)) {
                    val ytdlpFallback = YtDlpNativeEngine.resolveStream(context, resolvedInfo.originalUrl).getOrNull()
                    if (ytdlpFallback != null &&
                        ytdlpFallback.videoUrl.isNotBlank() &&
                        ytdlpFallback.videoUrl != ytdlpFallback.audioUrl
                    ) {
                        videoCandidates.add(ytdlpFallback.videoUrl to ytdlpFallback.httpHeaders)
                    }
                }

                for ((candidateVideoUrl, candidateHeaders) in videoCandidates) {
                    if (tempVideoFile.exists()) tempVideoFile.delete()
                    val downloaded = downloadFunction(
                        candidateVideoUrl,
                        tempVideoFile,
                        "Descargando Video Canvas de fondo (480p)...",
                        candidateHeaders
                    )
                    if (downloaded && isValidVideoFile(tempVideoFile)) {
                        break
                    }
                }
            }
        }

        if (tempVideoFile.exists() && tempVideoFile.length() > 0L) {
            onProgressUpdate(
                DownloadProgress(
                    isDownloading = true,
                    phase = when {
                        isLoop && loopStyle == FFmpegNativeEngine.CanvasLoopStyle.BOOMERANG ->
                            "Generando bucle infinito Boomerang / Ping-Pong (reverse + concat)..."
                        isLoop ->
                            "Perfeccionando bucle infinito continuo (Seamless Loop)..."
                        else ->
                            "Optimizando fluidez de video y fotogramas clave (480p)..."
                    },
                    bytesDownloaded = tempVideoFile.length(),
                    totalBytes = tempVideoFile.length(),
                    progressFraction = 0.94f
                )
            )
            val optimizedVideoFile = File(storageManager.videosDir, "canvas_opt_${timestamp}.mp4")
            val canvasResult = FFmpegNativeEngine.processVideoForCanvas(
                context = context,
                inputFile = tempVideoFile,
                outputFile = optimizedVideoFile,
                isLoop = isLoop,
                loopStyle = loopStyle
            )
            return if (canvasResult.success && isValidVideoFile(optimizedVideoFile)) {
                tempVideoFile.delete()
                optimizedVideoFile.absolutePath
            } else {
                if (optimizedVideoFile.exists()) optimizedVideoFile.delete()
                tempVideoFile.absolutePath
            }
        }
        return null
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024 * 1024)
        return String.format(java.util.Locale.US, "%.1f MB", mb)
    }
}
