package com.example.core.data.importer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.core.data.importer.download.ChunkedStreamDownloader
import com.example.core.data.importer.tiktok.TikTokMediaResolver
import com.example.core.data.storage.AppStorageManager
import com.example.debug.AuraDebugManager
import com.example.core.model.DownloadProgress
import com.example.core.model.Track
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
        val httpHeaders: Map<String, String> = emptyMap(),
        val audioHeaders: Map<String, String> = emptyMap(),
        val videoHeaders: Map<String, String> = emptyMap()
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
                if (!ChunkedStreamDownloader.isSafePublicUrl(cleanUrl)) {
                    return@withContext Result.failure(IllegalArgumentException("La URL apunta a una dirección local o no permitida."))
                }
                val fileName = cleanUrl.substringAfterLast("/").substringBefore("?")
                return@withContext Result.success(
                    ResolvedMediaInfo(
                        originalUrl = cleanUrl,
                        suggestedTitle = fileName.substringBeforeLast("."),
                        suggestedArtist = "Descarga Web",
                        videoUrl = cleanUrl,
                        audioUrl = if (cleanUrl.endsWith(".mp3", ignoreCase = true) || cleanUrl.endsWith(".m4a", ignoreCase = true) ||
                    cleanUrl.endsWith(".opus", ignoreCase = true) || cleanUrl.endsWith(".ogg", ignoreCase = true) ||
                    cleanUrl.endsWith(".webm", ignoreCase = true)) cleanUrl else null,
                        coverUrl = null,
                        durationSeconds = 0L
                    )
                )
            }

            // Caso 4: Intentar con WebStreamExtractor si hay contexto disponible
            if (context != null && ChunkedStreamDownloader.isSafePublicUrl(cleanUrl)) {
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
            // Formato local según calidad elegida en Ajustes (Opus .opus vs M4A .m4a); vale para local y streaming.
            val preferredQuality = readPreferredAudioQuality(context)
            val localExt = if (preferredQuality.prefersOpus && preferredQuality != com.example.core.model.StreamingAudioQuality.AUTO_HIFI) {
                "opus"
            } else {
                // AUTO_HIFI y M4A estándar guardan .m4a para máxima compatibilidad con reproductores externos.
                "m4a"
            }
            val audioFile = File(storageManager.songsDir, "track_online_${timestamp}.$localExt")
            val tempVideoFile = File(storageManager.videosDir, "media_video_${timestamp}.mp4")
            var audioReady = false
            var durationMs = resolvedInfo.durationSeconds * 1000L

            val effectiveAudioHeaders = resolvedInfo.audioHeaders.ifEmpty { resolvedInfo.httpHeaders }
            val effectiveVideoHeaders = resolvedInfo.videoHeaders.ifEmpty { resolvedInfo.httpHeaders }

            suspend fun extractAudioFromDownloadedVideo(sourceVidFile: File): Boolean {
                if (!sourceVidFile.exists() || sourceVidFile.length() <= 0L) return false
                try {
                    val videoUri = Uri.fromFile(sourceVidFile)
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
                            extractedFile.copyTo(audioFile, overwrite = true)
                            if (extractedFile.absolutePath != audioFile.absolutePath) {
                                extractedFile.delete()
                            }
                            return audioFile.exists() && audioFile.length() > 0L
                        }
                    }
                } catch (_: Throwable) {}

                if (FFmpegNativeEngine.isAvailable(context)) {
                    val ffmpegResult = FFmpegNativeEngine.extractAudio(
                        context = context,
                        inputFile = sourceVidFile,
                        outputFile = audioFile,
                        audioBitrate = "256k",
                        targetFormat = if (audioFile.extension.equals("opus", ignoreCase = true)) "opus" else "m4a",
                        totalDurationMs = durationMs
                    )
                    if (ffmpegResult.success && audioFile.exists() && audioFile.length() > 0L) {
                        return true
                    }
                }

                sourceVidFile.copyTo(audioFile, overwrite = true)
                return audioFile.exists() && audioFile.length() > 0L
            }

            // 1. Descarga directa prioritaria con motor nativo yt-dlp para enlaces de YouTube/Web
            val isWebVideo = WebStreamExtractor.isWebVideoUrl(resolvedInfo.originalUrl)
            if (isWebVideo && YtDlpNativeEngine.isAvailable(context)) {
                AuraDebugManager.logInfo("OnlineImporter", "Intentando descarga directa nativa con yt-dlp para '${resolvedInfo.originalUrl}'...")
                val directAudioSuccess = YtDlpNativeEngine.downloadStreamDirectToFile(
                    context = context,
                    url = resolvedInfo.originalUrl,
                    targetFile = audioFile,
                    isAudioOnly = true,
                    onProgressUpdate = onProgressUpdate,
                    audioQuality = preferredQuality
                )
                if (directAudioSuccess && audioFile.exists() && audioFile.length() > 0L) {
                    audioReady = true
                    AuraDebugManager.logInfo("OnlineImporter", "¡Audio descargado directamente con éxito mediante yt-dlp (${audioFile.length()} bytes)!")

                    // Descarga directa opcional de Video Canvas en 480p si fue solicitado
                    if (attachAsCanvas) {
                        AuraDebugManager.logInfo("OnlineImporter", "Descargando Video Canvas 480p directamente con yt-dlp...")
                        val directVideoSuccess = YtDlpNativeEngine.downloadStreamDirectToFile(
                            context = context,
                            url = resolvedInfo.originalUrl,
                            targetFile = tempVideoFile,
                            isAudioOnly = false,
                            onProgressUpdate = onProgressUpdate
                        )
                        if (directVideoSuccess && tempVideoFile.exists() && tempVideoFile.length() > 0L) {
                            AuraDebugManager.logInfo("OnlineImporter", "¡Video Canvas 480p descargado directamente con éxito mediante yt-dlp!")
                        }
                    }
                } else {
                    AuraDebugManager.logWarning("OnlineImporter", "Descarga directa de yt-dlp no completó el archivo. Recurriendo a respaldo con OkHttp...")
                }
            }

            // 2. Respaldo: Descarga de audio de alta fidelidad con OkHttp (o copia instantánea si ya estaba en caché)
            if (!audioReady && !resolvedInfo.audioUrl.isNullOrBlank()) {
                val localCachedAudio = if (resolvedInfo.audioUrl.startsWith("/")) File(resolvedInfo.audioUrl) else null
                if (localCachedAudio != null && localCachedAudio.exists() && localCachedAudio.length() > 0L) {
                    localCachedAudio.copyTo(audioFile, overwrite = true)
                    audioReady = audioFile.exists() && audioFile.length() > 0L
                } else {
                    val downloadedAudio = ChunkedStreamDownloader.downloadUrlToFile(
                        url = resolvedInfo.audioUrl,
                        targetFile = audioFile,
                        phase = "Descargando audio de alta fidelidad...",
                        customHeaders = effectiveAudioHeaders,
                        httpClient = httpClient,
                        onProgress = onProgressUpdate
                    )
                    if (downloadedAudio && audioFile.exists() && audioFile.length() > 0L) {
                        audioReady = true
                    }
                }
            }

            // 2. Si no había audio directo o falló, descargar flujo de video (con sus videoHeaders propios) y demuxear
            if (!audioReady) {
                val localCachedVideo = if (resolvedInfo.videoUrl.startsWith("/")) File(resolvedInfo.videoUrl) else null
                val downloadVideoSuccess = if (localCachedVideo != null && localCachedVideo.exists() && localCachedVideo.length() > 0L) {
                    localCachedVideo.copyTo(tempVideoFile, overwrite = true)
                    tempVideoFile.exists() && tempVideoFile.length() > 0L
                } else if (resolvedInfo.videoUrl.isNotBlank() && resolvedInfo.videoUrl != resolvedInfo.audioUrl) {
                    ChunkedStreamDownloader.downloadUrlToFile(
                        url = resolvedInfo.videoUrl,
                        targetFile = tempVideoFile,
                        phase = "Descargando flujo multimedia...",
                        customHeaders = effectiveVideoHeaders,
                        httpClient = httpClient,
                        onProgress = onProgressUpdate
                    )
                } else {
                    false
                }
                if (downloadVideoSuccess && tempVideoFile.exists() && tempVideoFile.length() > 0L) {
                    audioReady = extractAudioFromDownloadedVideo(tempVideoFile)
                }
            }

            // 3. Cascada completa de autoreparación en 5 niveles si googlevideo.com devolvió HTTP 403 (VEVO / CDN / colisión de sesión)
            if (!audioReady && WebStreamExtractor.isWebVideoUrl(resolvedInfo.originalUrl)) {
                val ytId = WebStreamExtractor.extractVideoId(resolvedInfo.originalUrl)
                AuraDebugManager.logInfo(
                    "OnlineImporter",
                    "Activando cascada de autoreparación anti-403 para '${resolvedInfo.originalUrl}' (ID: ${ytId ?: "N/A"})..."
                )

                suspend fun tryDownloadFromCandidate(candidateName: String, candidate: ResolvedMediaInfo?): Boolean {
                    if (candidate == null) return false
                    // Evitar reintentar exactamente la misma URL que ya falló con 403
                    val candAudioUrl = candidate.audioUrl
                    if (!candAudioUrl.isNullOrBlank() && candAudioUrl != resolvedInfo.audioUrl) {
                        val candAudioHeaders = candidate.audioHeaders.ifEmpty { candidate.httpHeaders }
                        val okAudio = ChunkedStreamDownloader.downloadUrlToFile(
                            url = candAudioUrl,
                            targetFile = audioFile,
                            phase = "Descargando audio ($candidateName)...",
                            customHeaders = candAudioHeaders,
                            httpClient = httpClient,
                            onProgress = onProgressUpdate
                        )
                        if (okAudio && audioFile.exists() && audioFile.length() > 0L) {
                            AuraDebugManager.logInfo("OnlineImporter", "¡Audio recuperado con éxito mediante $candidateName!")
                            return true
                        }
                    }

                    val candVideoUrl = candidate.videoUrl
                    if (candVideoUrl.isNotBlank() && candVideoUrl != resolvedInfo.videoUrl && candVideoUrl != resolvedInfo.audioUrl) {
                        val candVideoHeaders = candidate.videoHeaders.ifEmpty { candidate.httpHeaders }
                        val okVid = ChunkedStreamDownloader.downloadUrlToFile(
                            url = candVideoUrl,
                            targetFile = tempVideoFile,
                            phase = "Descargando flujo multimedia ($candidateName)...",
                            customHeaders = candVideoHeaders,
                            httpClient = httpClient,
                            onProgress = onProgressUpdate
                        )
                        if (okVid && tempVideoFile.exists() && tempVideoFile.length() > 0L) {
                            if (extractAudioFromDownloadedVideo(tempVideoFile)) {
                                AuraDebugManager.logInfo("OnlineImporter", "¡Audio extraído con éxito mediante video de $candidateName!")
                                return true
                            }
                        }
                    }
                    return false
                }

                // Nivel Fallback 1: InnerTube Multi-Cliente (ANDROID_VR -> VISIONOS -> ANDROID_MUSIC -> IOS)
                if (!audioReady && ytId != null) {
                    val innerTubeCandidate = InnerTubeClient.resolve(ytId, resolvedInfo.originalUrl, preferredQuality)
                    audioReady = tryDownloadFromCandidate("InnerTube", innerTubeCandidate)
                }

                // Nivel Fallback 2: Bypass Invidious API
                if (!audioReady && ytId != null) {
                    val invidiousCandidate = InvidiousStreamResolver.resolve(ytId, resolvedInfo.originalUrl, preferredQuality)
                    audioReady = tryDownloadFromCandidate("Invidious", invidiousCandidate)
                }

                // Nivel Fallback 3: Headless WebView (m.youtube.com)
                if (!audioReady && ytId != null) {
                    val webViewCandidate = HeadlessWebViewExtractor.resolve(context, ytId, resolvedInfo.originalUrl)
                    audioReady = tryDownloadFromCandidate("WebView", webViewCandidate)
                }

                // Nivel Fallback 4: Descarga nativa directa con el binario yt-dlp CLI (misma sesión TLS/OpenSSL que resolvió el token)
                if (!audioReady && YtDlpNativeEngine.isAvailable(context)) {
                    onProgressUpdate(
                        DownloadProgress(
                            isDownloading = true,
                            phase = "Descargando directamente con motor nativo yt-dlp...",
                            bytesDownloaded = 0L,
                            totalBytes = 0L,
                            progressFraction = 0.35f
                        )
                    )
                    val directOk = YtDlpNativeEngine.downloadStreamDirectToFile(
                        context = context,
                        url = resolvedInfo.originalUrl,
                        targetFile = audioFile,
                        isAudioOnly = true,
                        audioQuality = preferredQuality
                    )
                    if (directOk && audioFile.exists() && audioFile.length() > 0L) {
                        audioReady = true
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
                mimeType = if (audioFile.extension.equals("opus", ignoreCase = true)) "audio/opus" else "audio/mp4",
                dateAdded = System.currentTimeMillis(),
                isFavorite = false,
                playCount = 0,
                folderName = finalFolder,
                fileSizeFormatted = fileSizeFormatted
            )

            onProgressUpdate(DownloadProgress(isDownloading = false))
            Result.success(createdTrack)
        } catch (t: Throwable) {
            onProgressUpdate(DownloadProgress(isDownloading = false))
            Result.failure(if (t is Exception) t else IllegalStateException(t.localizedMessage ?: "Error procesando descarga", t))
        }
    }

    /**
     * Lee la calidad de audio elegida en Ajustes (misma preferencia que usa el streaming).
     * Vale tanto para canciones locales como para streaming/descargas con yt-dlp.
     */
    private fun readPreferredAudioQuality(context: Context): com.example.core.model.StreamingAudioQuality {
        return try {
            val prefs = context.getSharedPreferences("aura_streaming_cache_prefs", Context.MODE_PRIVATE)
            com.example.core.model.StreamingAudioQuality.fromId(
                prefs.getInt(
                    "streaming_audio_quality",
                    com.example.core.model.StreamingAudioQuality.AUTO_HIFI.id
                )
            )
        } catch (_: Throwable) {
            com.example.core.model.StreamingAudioQuality.AUTO_HIFI
        }
    }
}
