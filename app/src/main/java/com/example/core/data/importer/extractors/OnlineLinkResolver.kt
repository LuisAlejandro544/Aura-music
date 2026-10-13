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
 * Resolución de enlaces web a metadatos y URLs directas (YouTube, TikTok, web).
 * Rol arquitectónico: pieza de OnlineVideoAudioImporter (fachada pública intacta).
 */
internal object OnlineLinkResolver {
    private val httpClient = com.example.core.data.importer.download.ChunkedStreamDownloader.defaultHttpClient

    suspend fun resolve(
        linkUrl: String,
        context: Context? = null,
        engine: YoutubeExtractionEngine = YoutubeExtractionEngine.YTDLP
    ): Result<OnlineVideoAudioImporter.ResolvedMediaInfo> = withContext(Dispatchers.IO) {
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
                    OnlineVideoAudioImporter.ResolvedMediaInfo(
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
}
