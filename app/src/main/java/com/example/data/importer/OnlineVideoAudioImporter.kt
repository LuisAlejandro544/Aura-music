package com.example.data.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.data.storage.AppStorageManager
import com.example.model.DownloadProgress
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Importador y descargador de audio y video desde enlaces web (compatible con TikTok y videos online).
 *
 * Arquitectura y Principio de Operación:
 * 1. Resuelve enlaces de video mediante servicios de extracción directa sin marcas de agua.
 * 2. Descarga el flujo de video en alta definición y lo almacena en `videos/` para el Video Canvas.
 * 3. Extrae o descarga el audio de alta fidelidad guardándolo en `songs/` (.m4a/.mp3).
 * 4. Obtiene la carátula oficial o extrae un fotograma clave en alta resolución, comprimiéndola a WebP
 *    sin pérdida en `images/`.
 * 5. Soporta videos de cualquier duración (tanto loops cortos de Canvas como videos largos sincronizados).
 */
object OnlineVideoAudioImporter {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class ResolvedMediaInfo(
        val originalUrl: String,
        val suggestedTitle: String,
        val suggestedArtist: String,
        val videoUrl: String,
        val audioUrl: String?,
        val coverUrl: String?,
        val durationSeconds: Long
    )

    /**
     * Resuelve el enlace de video para obtener los metadatos y las URLs directas de descarga.
     */
    suspend fun resolveMediaLink(
        linkUrl: String,
        context: Context? = null,
        engine: YoutubeExtractionEngine = YoutubeExtractionEngine.INNERTUBE
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
                return@withContext resolveTikTokMedia(cleanUrl)
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
            return@withContext resolveTikTokMedia(cleanUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resuelve videos de TikTok utilizando el endpoint público de alta fidelidad TikWM sin marca de agua.
     */
    private fun resolveTikTokMedia(tikTokUrl: String): Result<ResolvedMediaInfo> {
        val encodedUrl = URLEncoder.encode(tikTokUrl, "UTF-8")
        val apiEndpoints = listOf(
            "https://www.tikwm.com/api/?url=$encodedUrl&hd=1",
            "https://api.tiklydown.eu.org/api/download?url=$encodedUrl"
        )

        for (endpoint in apiEndpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bodyString = response.body?.string() ?: return@use
                    val json = JSONObject(bodyString)

                    // Formato TikWM
                    if (json.has("code") && json.getInt("code") == 0 && json.has("data")) {
                        val data = json.getJSONObject("data")
                        val videoPlayUrl = data.optString("play").ifBlank { data.optString("wmplay") }
                        val rawTitle = data.optString("title").trim()
                        val duration = data.optLong("duration", 0L)
                        val coverUrl = data.optString("cover").ifBlank { data.optString("origin_cover") }

                        // Información de audio y artista
                        val musicInfo = data.optJSONObject("music_info")
                        val musicUrl = musicInfo?.optString("play") ?: data.optString("music")
                        val musicTitle = musicInfo?.optString("title")
                        val musicAuthor = musicInfo?.optString("author")
                        val authorObj = data.optJSONObject("author")
                        val authorNickname = authorObj?.optString("nickname") ?: authorObj?.optString("unique_id")

                        val title = when {
                            !musicTitle.isNullOrBlank() && !musicTitle.contains("original sound", ignoreCase = true) -> musicTitle
                            rawTitle.isNotBlank() -> cleanCaptionAsTitle(rawTitle)
                            else -> "TikTok Music ${System.currentTimeMillis() % 1000}"
                        }

                        val artist = when {
                            !musicAuthor.isNullOrBlank() -> musicAuthor
                            !authorNickname.isNullOrBlank() -> "@$authorNickname"
                            else -> "TikTok Creator"
                        }

                        if (videoPlayUrl.isNotBlank()) {
                            // En TikTok, los enlaces en music_info.play están limitados a 60 segundos por su biblioteca de sonidos.
                            // Para admitir videos de cualquier duración (5 min, 10 min, 30 min o hasta 1 hora),
                            // dejamos audioUrl = null para forzar la extracción directa y sin recodificación
                            // desde el flujo de video original mediante MediaExtractor/MediaMuxer.
                            val resolvedVideo = if (videoPlayUrl.startsWith("//")) "https:$videoPlayUrl" else videoPlayUrl
                            return Result.success(
                                ResolvedMediaInfo(
                                    originalUrl = tikTokUrl,
                                    suggestedTitle = title,
                                    suggestedArtist = artist,
                                    videoUrl = resolvedVideo,
                                    audioUrl = null,
                                    coverUrl = if (coverUrl.startsWith("//")) "https:$coverUrl" else coverUrl.ifBlank { null },
                                    durationSeconds = duration
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Continuar al siguiente endpoint si hubo algún error transitorio
            }
        }

        return Result.failure(IllegalStateException("No se pudo obtener el video desde el enlace provisto. Verifica que el enlace sea válido."))
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

            // 1. Descarga prioritaria del audio de alta fidelidad si está disponible por separado (p. ej. InnerTube)
            if (!resolvedInfo.audioUrl.isNullOrBlank()) {
                val downloadedAudio = downloadUrlToFile(
                    url = resolvedInfo.audioUrl,
                    targetFile = audioFile,
                    phase = "Descargando audio de alta fidelidad...",
                    onProgress = onProgressUpdate
                )
                if (downloadedAudio && audioFile.exists() && audioFile.length() > 0L) {
                    audioReady = true
                }
            }

            // 2. Si no había audio directo o falló, descargar flujo de video y demuxear
            if (!audioReady) {
                val downloadVideoSuccess = downloadUrlToFile(
                    url = resolvedInfo.videoUrl,
                    targetFile = tempVideoFile,
                    phase = "Descargando flujo multimedia...",
                    onProgress = onProgressUpdate
                )
                if (downloadVideoSuccess && tempVideoFile.exists() && tempVideoFile.length() > 0L) {
                    // Intentar demuxing sin recodificación a través de VideoAudioExtractor
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

                    if (!audioReady) {
                        tempVideoFile.copyTo(audioFile, overwrite = true)
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

            // Si el usuario activó el interruptor para eliminar silencios al inicio y al final
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

            onProgressUpdate(
                DownloadProgress(
                    isDownloading = true,
                    phase = "Generando carátula en WebP sin pérdida...",
                    bytesDownloaded = audioFile.length(),
                    totalBytes = audioFile.length(),
                    progressFraction = 0.90f
                )
            )
            var artworkPath: String? = null

            // 1. Intentar descargar portada oficial
            if (!resolvedInfo.coverUrl.isNullOrBlank()) {
                val artFile = File(storageManager.imagesDir, "art_online_${timestamp}.webp")
                try {
                    val request = Request.Builder().url(resolvedInfo.coverUrl).build()
                    httpClient.newCall(request).execute().use { res ->
                        if (res.isSuccessful) {
                            val stream = res.body?.byteStream()
                            if (stream != null) {
                                val bitmap = BitmapFactory.decodeStream(stream)
                                if (bitmap != null) {
                                    FileOutputStream(artFile).use { out ->
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
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
                    artworkPath = null
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
                        val artFile = File(storageManager.imagesDir, "art_online_${timestamp}.webp")
                        FileOutputStream(artFile).use { out ->
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                frame.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out)
                            } else {
                                frame.compress(Bitmap.CompressFormat.WEBP, 95, out)
                            }
                        }
                        artworkPath = artFile.absolutePath
                        frame.recycle()
                    }
                } catch (_: Throwable) {
                } finally {
                    try { frameRetriever.release() } catch (_: Throwable) {}
                }
            }

            var videoCanvasPath: String? = null
            val isLoop = forceLoop ?: (durationMs in 1..20500L)

            if (attachAsCanvas) {
                // Si attachAsCanvas está marcado pero aún no descargamos el video porque el audio vino directo
                if ((!tempVideoFile.exists() || tempVideoFile.length() == 0L) &&
                    !resolvedInfo.videoUrl.isNullOrBlank() &&
                    resolvedInfo.videoUrl != resolvedInfo.audioUrl
                ) {
                    downloadUrlToFile(
                        url = resolvedInfo.videoUrl,
                        targetFile = tempVideoFile,
                        phase = "Descargando Video Canvas de fondo...",
                        onProgress = onProgressUpdate
                    )
                }
                if (tempVideoFile.exists() && tempVideoFile.length() > 0L) {
                    videoCanvasPath = tempVideoFile.absolutePath
                }
            } else {
                // Si el usuario no quería video de fondo, eliminamos el archivo de video para no gastar espacio
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
            val fileSizeFormatted = formatFileSize(audioFile.length())

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

    private fun downloadUrlToFile(
        url: String,
        targetFile: File,
        phase: String,
        onProgress: (DownloadProgress) -> Unit
    ): Boolean {
        return try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
                val totalBytes = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
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
                            if (elapsed >= 200) {
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
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun cleanCaptionAsTitle(rawCaption: String): String {
        return rawCaption
            .replace(Regex("#\\S+"), "") // Eliminar hashtags (#fyp, #music, etc.)
            .replace(Regex("@\\S+"), "") // Eliminar menciones (@usuario)
            .replace(Regex("\n+"), " ")
            .trim()
            .take(60)
            .ifBlank { "TikTok Music" }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024 * 1024)
        return String.format(java.util.Locale.US, "%.1f MB", mb)
    }
}
