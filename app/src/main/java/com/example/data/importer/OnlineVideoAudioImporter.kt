package com.example.data.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.data.storage.AppStorageManager
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
    suspend fun resolveMediaLink(linkUrl: String, context: Context? = null): Result<ResolvedMediaInfo> = withContext(Dispatchers.IO) {
        val cleanUrl = linkUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("El enlace no puede estar vacío"))
        }

        try {
            // Caso 1: Enlace de YouTube o video web compatible
            if (WebStreamExtractor.isWebVideoUrl(cleanUrl)) {
                if (context != null) {
                    return@withContext WebStreamExtractor.resolveStream(context, cleanUrl)
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
                val streamRes = WebStreamExtractor.resolveStream(context, cleanUrl)
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
                            return Result.success(
                                ResolvedMediaInfo(
                                    originalUrl = tikTokUrl,
                                    suggestedTitle = title,
                                    suggestedArtist = artist,
                                    videoUrl = if (videoPlayUrl.startsWith("//")) "https:$videoPlayUrl" else videoPlayUrl,
                                    audioUrl = if (musicUrl.startsWith("//")) "https:$musicUrl" else musicUrl.ifBlank { null },
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
        onProgressUpdate: (String) -> Unit = {}
    ): Result<Track> = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val finalTitle = customTitle?.trim()?.ifBlank { resolvedInfo.suggestedTitle } ?: resolvedInfo.suggestedTitle
        val finalArtist = customArtist?.trim()?.ifBlank { resolvedInfo.suggestedArtist } ?: resolvedInfo.suggestedArtist

        try {
            onProgressUpdate("Descargando video en alta definición...")
            val tempVideoFile = File(storageManager.videosDir, "tiktok_video_${timestamp}.mp4")
            val downloadVideoSuccess = downloadUrlToFile(resolvedInfo.videoUrl, tempVideoFile)
            if (!downloadVideoSuccess || !tempVideoFile.exists() || tempVideoFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Error al descargar el archivo de video."))
            }

            // Extraer duración real del video descargado
            var durationMs = resolvedInfo.durationSeconds * 1000L
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(tempVideoFile.absolutePath)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durStr?.toLongOrNull()?.takeIf { it > 0 } ?: durationMs
            } catch (_: Throwable) {
            } finally {
                try { retriever.release() } catch (_: Throwable) {}
            }

            onProgressUpdate("Procesando audio de alta fidelidad...")
            val audioFile = File(storageManager.songsDir, "track_online_${timestamp}.m4a")
            var audioReady = false

            // Intentar demuxing sin recodificación a través de VideoAudioExtractor
            try {
                val videoUri = Uri.fromFile(tempVideoFile)
                val dummyTrack = VideoAudioExtractor.convertVideoToTrack(
                    context = context,
                    storageManager = storageManager,
                    videoUri = videoUri,
                    title = finalTitle,
                    artist = finalArtist,
                    album = "TikTok Music & Videos",
                    attachAsCanvas = false,
                    forceLoop = forceLoop
                )
                if (dummyTrack != null) {
                    val extractedFile = File(Uri.parse(dummyTrack.uriString).path ?: "")
                    if (extractedFile.exists() && extractedFile.length() > 0L) {
                        audioReady = true
                        extractedFile.copyTo(audioFile, overwrite = true)
                    }
                }
            } catch (_: Throwable) {}

            // Si el demuxing falló, descargar el audio directo si está disponible o usar el archivo de video como fuente de audio
            if (!audioReady) {
                if (!resolvedInfo.audioUrl.isNullOrBlank()) {
                    val directAudioFile = File(storageManager.songsDir, "track_online_${timestamp}.mp3")
                    if (downloadUrlToFile(resolvedInfo.audioUrl, directAudioFile)) {
                        directAudioFile.copyTo(audioFile, overwrite = true)
                        directAudioFile.delete()
                        audioReady = true
                    }
                }
                if (!audioReady) {
                    // Fallback directo: el contenedor mp4 se reproduce nativamente en ExoPlayer
                    tempVideoFile.copyTo(audioFile, overwrite = true)
                }
            }

            onProgressUpdate("Generando carátula en WebP sin pérdida...")
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

            // 2. Si no hubo portada o falló, extraer fotograma clave del video descargado
            if (artworkPath == null) {
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

            onProgressUpdate("Configurando Video Canvas...")
            var videoCanvasPath: String? = null
            val isLoop = forceLoop ?: (durationMs in 1..20500L)

            if (attachAsCanvas) {
                videoCanvasPath = tempVideoFile.absolutePath
            } else {
                // Si el usuario no quería video de fondo, eliminamos el archivo de video para no gastar espacio
                if (tempVideoFile.exists() && audioFile.absolutePath != tempVideoFile.absolutePath) {
                    tempVideoFile.delete()
                }
            }

            val fileSizeFormatted = formatFileSize(audioFile.length())

            val createdTrack = Track(
                id = 0L,
                title = finalTitle,
                artist = finalArtist,
                album = "TikTok Music & Canvas",
                durationMs = durationMs,
                uriString = Uri.fromFile(audioFile).toString(),
                albumArtPath = artworkPath,
                videoUri = videoCanvasPath,
                isVideoLoop = isLoop,
                mimeType = "audio/mp4",
                dateAdded = System.currentTimeMillis(),
                isFavorite = false,
                playCount = 0,
                folderName = "TikTok & Web",
                fileSizeFormatted = fileSizeFormatted
            )

            onProgressUpdate("¡Canción lista!")
            Result.success(createdTrack)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun downloadUrlToFile(url: String, targetFile: File): Boolean {
        return try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
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
