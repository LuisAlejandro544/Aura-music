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
    ): Result<ResolvedMediaInfo> =
        OnlineLinkResolver.resolve(linkUrl, context, engine)

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
    ): Result<Track> = OnlineTrackImporter.import(
        context, storageManager, resolvedInfo, customTitle, customArtist,
        attachAsCanvas, forceLoop, trimSilence, loopStyle, onProgressUpdate
    )
}

