package com.example.data.importer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.data.importer.download.ChunkedStreamDownloader
import com.example.data.importer.tiktok.TikTokMediaResolver
import com.example.data.storage.AppStorageManager
import com.example.debug.AuraDebugManager
import com.example.model.DownloadProgress
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Aura Music - Importador y Descargador de Audio y Video desde Enlaces Web (TikTok, YouTube y Web)
 *
 * Arquitectura y Principio de Operación Modular:
 * 1. Resuelve enlaces de video mediante servicios especializados ([TikTokMediaResolver], [WebStreamExtractor]).
 * 2. Acelera descargas mediante bloques HTTP Range con [ChunkedStreamDownloader] (eliminando el límite de 63 KB/s).
 * 3. Procesa carátulas en WebP sin pérdida y Video Canvas optimizado (480p, Seamless Loop / Boomerang) con [MediaAssetProcessor].
 * 4. Extrae o demuxea el audio de alta fidelidad sin recodificación innecesaria guardándolo en `songs/` (.m4a/.mp3).
 * 5. Garantiza compatibilidad sin cortes para videos de cualquier duración (desde loops cortos de 5s hasta mixes de 1 hora).
 */
object OnlineVideoAudioImporter {

    private val httpClient = ChunkedStreamDownloader.defaultHttpClient

    data class ResolvedMediaInfo(
        val originalUrl: String,
        val suggestedTitle: String,
        val suggestedArtist: String,
        val videoUrl: String,
        val audioUrl: String?,
        val coverUrl: String?,
        val durationSeconds: Long,
        val httpHeaders: Map<String, String> = emptyMap()
    )

    /**
     * Resuelve el enlace de video para obtener los metadatos y las URLs directas de descarga.
     */
    suspend fun resolveMediaLink(
        linkUrl: String,
        context: Context? = null,
        engine: YoutubeExtractionEngine = YoutubeExtractionEngine.YTDLP
    ): Result<ResolvedMediaInfo> = withContext(Dispatchers.IO) {
        val cleanUrl = linkUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("El enlace no puede estar vacío"))
        }

        try {
            // Caso 1: Enlace de YouTube o video web compatible
            if (WebStreamExtractor.isWebVideoUrl(cleanUrl)) {
                if (context != null) {
                    return@withContext WebStreamExtractor.resolveStream(context, cleanUrl, engine)
                }
            }

            // Caso 2: Enlace de TikTok (tiktok.com, vt.tiktok.com, vm.tiktok.com)
            if (cleanUrl.contains("tiktok.com", ignoreCase = true)) {
                return@withContext TikTokMediaResolver.resolve(cleanUrl, httpClient)
            }

            // Caso 3: Enlace directo a archivo multimedia (.mp4, .m4a, .mp3, .webm)
            if (cleanUrl.endsWith(".mp4", ignoreCase = true) ||
                cleanUrl.endsWith(".m4a", ignoreCase = true) ||
                cleanUrl.endsWith(".mp3", ignoreCase = true) ||
                cleanUrl.endsWith(".webm", ignoreCase = true)
            ) {
                val fileName = cleanUrl.substringAfterLast("/").substringBefore("?")
                return@withContext Result.success(
                    ResolvedMediaInfo(
                        originalUrl = cleanUrl,
                        suggestedTitle = fileName.substringBeforeLast("."),
                        suggestedArtist = "Descarga Web",
                        videoUrl = cleanUrl,
                        audioUrl = if (cleanUrl.endsWith(".mp3") || cleanUrl.endsWith(".m4a")) cleanUrl else null,
                        coverUrl = null,
                        durationSeconds = 0L
                    )
                )
            }

            // Caso 4: Intentar con WebStreamExtractor si hay contexto disponible
            if (context != null && (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://"))) {
                val streamRes = WebStreamExtractor.resolveStream(context, cleanUrl, engine)
                if (streamRes.isSuccess) {
                    return@withContext streamRes
                }
            }

            // Intento por defecto con el resolver de TikTok
            return@withContext TikTokMediaResolver.resolve(cleanUrl, httpClient)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Descarga y procesa el video, extrayendo el audio, la carátula en WebP y vinculando el Video Canvas.
     */
    suspend fun downloadAndImport(
        context: Context,
        storageManager: AppStorageManager,
        resolvedInfo: ResolvedMediaInfo,
        customTitle: String? = null,
        customArtist: String? = null,
        attachAsCanvas: Boolean = true,
        forceLoop: Boolean? = null,
        trimSilence: Boolean = false,
        loopStyle: FFmpegNativeEngine.CanvasLoopStyle = FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE,
        onProgressUpdate: (DownloadProgress) -> Unit = {}
    ): Result<Track> = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val finalTitle = customTitle?.trim()?.ifBlank { resolvedInfo.suggestedTitle } ?: resolvedInfo.suggestedTitle
        val finalArtist = customArtist?.trim()?.ifBlank { resolvedInfo.suggestedArtist } ?: resolvedInfo.suggestedArtist

        try {
            val audioFile = File(storageManager.songsDir, "track_online_${timestamp}.m4a")
            val tempVideoFile = File(storageManager.videosDir, "media_video_${timestamp}.mp4")
            var audioReady = false
            var durationMs = resolvedInfo.durationSeconds * 1000L

            // 1. Descarga prioritaria del audio de alta fidelidad si está disponible por separado
            if (!resolvedInfo.audioUrl.isNullOrBlank()) {
                val downloadedAudio = ChunkedStreamDownloader.downloadUrlToFile(
                    url = resolvedInfo.audioUrl,
                    targetFile = audioFile,
                    phase = "Descargando audio de alta fidelidad...",
                    customHeaders = resolvedInfo.httpHeaders,
                    httpClient = httpClient,
                    onProgress = onProgressUpdate
                )
                if (downloadedAudio && audioFile.exists() && audioFile.length() > 0L) {
                    audioReady = true
                }
            }

            // 2. Si no había audio directo o falló, descargar flujo de video y demuxear
            if (!audioReady) {
                val downloadVideoSuccess = ChunkedStreamDownloader.downloadUrlToFile(
                    url = resolvedInfo.videoUrl,
                    targetFile = tempVideoFile,
                    phase = "Descargando flujo multimedia...",
                    customHeaders = resolvedInfo.httpHeaders,
                    httpClient = httpClient,
                    onProgress = onProgressUpdate
                )
                if (downloadVideoSuccess && tempVideoFile.exists() && tempVideoFile.length() > 0L) {
                    try {
                        val videoUri = Uri.fromFile(tempVideoFile)
                        val isYoutube = resolvedInfo.originalUrl.contains("youtu", ignoreCase = true)
                        val dummyTrack = VideoAudioExtractor.convertVideoToTrack(
                            context = context,
                            storageManager = storageManager,
                            videoUri = videoUri,
                            title = finalTitle,
                            artist = finalArtist,
                            album = if (isYoutube) "YouTube Music" else "TikTok Music",
                            attachAsCanvas = false,
                            forceLoop = forceLoop,
                            trimSilence = false
                        )
                        if (dummyTrack != null) {
                            val extractedFile = File(Uri.parse(dummyTrack.uriString).path ?: "")
                            if (extractedFile.exists() && extractedFile.length() > 0L) {
                                audioReady = true
                                extractedFile.copyTo(audioFile, overwrite = true)
                            }
                        }
                    } catch (_: Throwable) {}

                    if (!audioReady && FFmpegNativeEngine.isAvailable(context)) {
                        val ffmpegResult = FFmpegNativeEngine.extractAudio(
                            context = context,
                            inputFile = tempVideoFile,
                            outputFile = audioFile,
                            audioBitrate = "256k",
                            targetFormat = "m4a",
                            totalDurationMs = durationMs
                        )
                        if (ffmpegResult.success && audioFile.exists() && audioFile.length() > 0L) {
                            audioReady = true
                        }
                    }

                    if (!audioReady) {
                        tempVideoFile.copyTo(audioFile, overwrite = true)
                        audioReady = true
                    }
                }
            }

            // 3. Fallback inteligente de autoreparación: si el stream devolvió 403 (ej. pistas VEVO protegidas) o falló
            if (!audioReady && WebStreamExtractor.isWebVideoUrl(resolvedInfo.originalUrl)) {
                val ytId = WebStreamExtractor.extractVideoId(resolvedInfo.originalUrl)
                if (ytId != null) {
                    AuraDebugManager.logInfo(
                        "OnlineImporter",
                        "El stream inicial requirió autoreparación (posible bloqueo VEVO/403). Resolviendo stream firmado con yt-dlp / InnerTube / Invidious..."
                    )
                    val freshInfo = (if (YtDlpNativeEngine.isAvailable(context)) {
                        YtDlpNativeEngine.resolveStream(context, resolvedInfo.originalUrl).getOrNull()
                    } else null)
                        ?: InnerTubeClient.resolve(ytId, resolvedInfo.originalUrl)
                        ?: InvidiousStreamResolver.resolve(ytId, resolvedInfo.originalUrl)

                    if (freshInfo != null) {
                        if (!freshInfo.audioUrl.isNullOrBlank()) {
                            val backupAudioSuccess = ChunkedStreamDownloader.downloadUrlToFile(
                                url = freshInfo.audioUrl,
                                targetFile = audioFile,
                                phase = "Descargando audio de alta fidelidad...",
                                customHeaders = freshInfo.httpHeaders,
                                httpClient = httpClient,
                                onProgress = onProgressUpdate
                            )
                            if (backupAudioSuccess && audioFile.exists() && audioFile.length() > 0L) {
                                audioReady = true
                            }
                        }

                        if (!audioReady && !freshInfo.videoUrl.isNullOrBlank()) {
                            val backupVideoSuccess = ChunkedStreamDownloader.downloadUrlToFile(
                                url = freshInfo.videoUrl,
                                targetFile = tempVideoFile,
                                phase = "Descargando flujo multimedia...",
                                customHeaders = freshInfo.httpHeaders,
                                httpClient = httpClient,
                                onProgress = onProgressUpdate
                            )
                            if (backupVideoSuccess && tempVideoFile.exists() && tempVideoFile.length() > 0L) {
                                try {
                                    val videoUri = Uri.fromFile(tempVideoFile)
                                    val dummyTrack = VideoAudioExtractor.convertVideoToTrack(
                                        context = context,
                                        storageManager = storageManager,
                                        videoUri = videoUri,
                                        title = finalTitle,
                                        artist = finalArtist,
                                        album = "YouTube Music",
                                        attachAsCanvas = false,
                                        forceLoop = forceLoop,
                                        trimSilence = false
                                    )
                                    if (dummyTrack != null) {
                                        val extractedFile = File(Uri.parse(dummyTrack.uriString).path ?: "")
                                        if (extractedFile.exists() && extractedFile.length() > 0L) {
                                            audioReady = true
                                            extractedFile.copyTo(audioFile, overwrite = true)
                                        }
                                    }
                                } catch (_: Throwable) {}

                                if (!audioReady) {
                                    tempVideoFile.copyTo(audioFile, overwrite = true)
                                    audioReady = true
                                }
                            }
                        }
                    }
                }
            }

            if (!audioReady || !audioFile.exists() || audioFile.length() == 0L) {
                onProgressUpdate(DownloadProgress(isDownloading = false))
                return@withContext Result.failure(IllegalStateException("Error al descargar el archivo de audio. Verifica tu conexión."))
            }

            // Extraer duración real del archivo
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(audioFile.absolutePath)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durStr?.toLongOrNull()?.takeIf { it > 0 } ?: durationMs
            } catch (_: Throwable) {
            } finally {
                try { retriever.release() } catch (_: Throwable) {}
            }

            // Si el usuario activó la eliminación inteligente de silencios al inicio y al final
            if (trimSilence) {
                onProgressUpdate(
                    DownloadProgress(
                        isDownloading = true,
                        phase = "Eliminando silencios al inicio y final...",
                        bytesDownloaded = audioFile.length(),
                        totalBytes = audioFile.length(),
                        progressFraction = 0.86f
                    )
                )
                val trimResult = AudioSilenceTrimmer.processLocalAudioFile(
                    context = context,
                    audioFile = audioFile,
                    originalDurationMs = durationMs
                )
                if (trimResult.wasTrimmed && trimResult.newDurationMs > 0L) {
                    durationMs = trimResult.newDurationMs
                }
            }

            // Generación de carátula WebP
            onProgressUpdate(
                DownloadProgress(
                    isDownloading = true,
                    phase = "Generando carátula en WebP sin pérdida...",
                    bytesDownloaded = audioFile.length(),
                    totalBytes = audioFile.length(),
                    progressFraction = 0.90f
                )
            )
            val artworkPath = MediaAssetProcessor.processArtwork(
                storageManager = storageManager,
                resolvedInfo = resolvedInfo,
                tempVideoFile = tempVideoFile,
                timestamp = timestamp,
                httpClient = httpClient
            )

            // Procesamiento de Video Canvas de fondo
            var videoCanvasPath: String? = null
            val isLoop = forceLoop ?: (loopStyle == FFmpegNativeEngine.CanvasLoopStyle.BOOMERANG || durationMs in 1..20500L)

            if (attachAsCanvas) {
                videoCanvasPath = MediaAssetProcessor.processVideoCanvas(
                    context = context,
                    storageManager = storageManager,
                    resolvedInfo = resolvedInfo,
                    tempVideoFile = tempVideoFile,
                    audioFile = audioFile,
                    timestamp = timestamp,
                    isLoop = isLoop,
                    loopStyle = loopStyle,
                    onProgressUpdate = onProgressUpdate,
                    downloadFunction = { url, target, phase, headers ->
                        ChunkedStreamDownloader.downloadUrlToFile(
                            url = url,
                            targetFile = target,
                            phase = phase,
                            customHeaders = headers,
                            httpClient = httpClient,
                            onProgress = onProgressUpdate
                        )
                    }
                )
            } else {
                if (tempVideoFile.exists() && audioFile.absolutePath != tempVideoFile.absolutePath) {
                    tempVideoFile.delete()
                }
            }

            onProgressUpdate(
                DownloadProgress(
                    isDownloading = true,
                    phase = "¡Canción preparada exitosamente!",
                    bytesDownloaded = audioFile.length(),
                    totalBytes = audioFile.length(),
                    progressFraction = 1.0f
                )
            )

            val isYoutubeSource = resolvedInfo.originalUrl.contains("youtu", ignoreCase = true)
            val finalAlbum = if (isYoutubeSource) "YouTube Music & Canvas" else "TikTok Music & Canvas"
            val finalFolder = if (isYoutubeSource) "YouTube & Web" else "TikTok & Web"
            val fileSizeFormatted = MediaAssetProcessor.formatFileSize(audioFile.length())

            val createdTrack = Track(
                id = 0L,
                title = finalTitle,
                artist = finalArtist,
                album = finalAlbum,
                durationMs = durationMs,
                uriString = Uri.fromFile(audioFile).toString(),
                albumArtPath = artworkPath,
                videoUri = videoCanvasPath,
                isVideoLoop = isLoop,
                mimeType = "audio/mp4",
                dateAdded = System.currentTimeMillis(),
                isFavorite = false,
                playCount = 0,
                folderName = finalFolder,
                fileSizeFormatted = fileSizeFormatted
            )

            onProgressUpdate(DownloadProgress(isDownloading = false))
            Result.success(createdTrack)
        } catch (e: Exception) {
            onProgressUpdate(DownloadProgress(isDownloading = false))
            Result.failure(e)
        }
    }
}
