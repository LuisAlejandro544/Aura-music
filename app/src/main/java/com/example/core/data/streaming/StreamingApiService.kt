package com.example.core.data.streaming

import android.content.Context
import com.example.core.data.importer.InnerTubeClient
import com.example.core.data.importer.OnlineVideoAudioImporter
import com.example.core.data.importer.WebStreamExtractor
import com.example.core.data.importer.YtDlpNativeEngine
import com.example.debug.AuraDebugManager
import com.example.core.model.StreamingSearchFilter
import com.example.core.model.StreamingSearchItem
import com.example.core.model.StreamingSourcePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente de API para el Modo Streaming Híbrido.
 *
 * Rol Arquitectónico:
 * 1. Búsqueda Unificada por API (sin usar yt-dlp para buscar):
 *    Consulta simultáneamente la API de YouTube Music (`WEB_REMIX` en `music.youtube.com`)
 *    y la API de YouTube (`WEB` / `ANDROID` en `youtube.com`), etiquetando cada resultado
 *    con su insignia correspondiente (`YT MUSIC` vs `YOUTUBE`).
 * 2. Recomendación de Canción Similar (Radio / Autoplay antes de terminar la canción):
 *    Consulta el endpoint `/youtubei/v1/next` (Automix / Watch Next) para obtener la siguiente
 *    pista similar recomendada antes de que finalice la pista actual.
 * 3. Resolución de Reproducción con `yt-dlp`:
 *    Una vez elegida la canción o la recomendación similar, normaliza enlaces de `music.youtube.com`
 *    y utiliza `YtDlpNativeEngine` (con respaldo `InnerTubeClient`) para obtener las URLs reales
 *    de audio y Video Canvas para reproducir y pre-cargar en caché.
 */
object StreamingApiService {

    private const val TAG = "StreamingApiService"

    suspend fun searchStreamingCatalog(
        query: String,
        filter: StreamingSearchFilter = StreamingSearchFilter.ALL,
        context: Context? = null
    ): List<StreamingSearchItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext getDefaultTrendingCatalog(filter, context)

        coroutineScope {
            val ytMusicDeferred = async {
                if (filter == StreamingSearchFilter.ALL || filter == StreamingSearchFilter.YT_MUSIC) {
                    YouTubeMusicSearchEngine.searchYouTubeMusicApi(cleanQuery)
                } else {
                    emptyList()
                }
            }
            val ytStandardDeferred = async {
                if (filter == StreamingSearchFilter.ALL || filter == StreamingSearchFilter.YOUTUBE) {
                    YouTubeSearchEngine.searchYouTubeStandardApi(cleanQuery)
                } else {
                    emptyList()
                }
            }
            val soundCloudDeferred = async {
                if (filter == StreamingSearchFilter.ALL || filter == StreamingSearchFilter.SOUNDCLOUD) {
                    SoundCloudApiClient.searchTracks(
                        context = context,
                        query = cleanQuery,
                        limit = if (filter == StreamingSearchFilter.SOUNDCLOUD) 25 else 14
                    )
                } else {
                    emptyList()
                }
            }

            val musicResults = ytMusicDeferred.await()
            val videoResults = ytStandardDeferred.await()
            val soundCloudResults = soundCloudDeferred.await()

            when (filter) {
                StreamingSearchFilter.YT_MUSIC -> musicResults.ifEmpty {
                    videoResults.map { it.copy(platform = StreamingSourcePlatform.YT_MUSIC) }
                }
                StreamingSearchFilter.YOUTUBE -> videoResults
                StreamingSearchFilter.SOUNDCLOUD -> soundCloudResults
                StreamingSearchFilter.ALL -> StreamingSearchUtils.interleaveResults(musicResults, videoResults, soundCloudResults)
            }
        }
    }

    suspend fun getDefaultTrendingCatalog(
        filter: StreamingSearchFilter = StreamingSearchFilter.ALL,
        context: Context? = null
    ): List<StreamingSearchItem> = withContext(Dispatchers.IO) {
        val defaultQuery = if (filter == StreamingSearchFilter.SOUNDCLOUD) {
            "Electronic Phonk Remix Hits"
        } else {
            "Top Hits Music Global"
        }
        searchStreamingCatalog(defaultQuery, filter, context)
    }

    suspend fun fetchSimilarNextTrack(
        currentVideoId: String,
        currentTitle: String,
        currentArtist: String,
        preferredPlatform: StreamingSourcePlatform = StreamingSourcePlatform.YT_MUSIC,
        excludedVideoIds: Set<String> = emptySet()
    ): StreamingSearchItem? = withContext(Dispatchers.IO) {
        val cleanVideoId = currentVideoId.trim()
        if (cleanVideoId.isBlank()) return@withContext null

        if (preferredPlatform == StreamingSourcePlatform.SOUNDCLOUD) {
            val scSimilar = SoundCloudApiClient.fetchSimilarTracks(
                currentTitle = currentTitle,
                currentArtist = currentArtist,
                excludedIds = excludedVideoIds + cleanVideoId
            )
            if (scSimilar.isNotEmpty()) {
                return@withContext scSimilar.first()
            }
        }

        // 1. Consultar endpoint Watch Next / Automix de YouTube Music o YouTube
        if (cleanVideoId.length == 11) {
            val relatedFromNext = YouTubeSearchEngine.fetchRelatedFromNextEndpoint(cleanVideoId, preferredPlatform)
                .filter { it.videoId != cleanVideoId && !excludedVideoIds.contains(it.videoId) }

            if (relatedFromNext.isNotEmpty()) {
                return@withContext relatedFromNext.first()
            }
        }

        // 2. Respaldo por búsqueda semántica de artista/canción similar en la API
        val fallbackQuery = if (currentArtist.isNotBlank() && !currentArtist.equals("Artista desconocido", ignoreCase = true)) {
            "$currentArtist mix canciones similares"
        } else {
            "$currentTitle mix"
        }
        val searchCandidates = searchStreamingCatalog(
            query = fallbackQuery,
            filter = when (preferredPlatform) {
                StreamingSourcePlatform.YT_MUSIC -> StreamingSearchFilter.YT_MUSIC
                StreamingSourcePlatform.SOUNDCLOUD -> StreamingSearchFilter.SOUNDCLOUD
                StreamingSourcePlatform.YOUTUBE -> StreamingSearchFilter.ALL
            }
        ).filter { it.videoId != cleanVideoId && !excludedVideoIds.contains(it.videoId) }

        searchCandidates.firstOrNull()
    }

    suspend fun resolvePlayableStreamWithYtDlp(
        context: Context,
        item: StreamingSearchItem,
        forceBypassYtDlp: Boolean = false
    ): Result<OnlineVideoAudioImporter.ResolvedMediaInfo> = withContext(Dispatchers.IO) {
        val watchUrl = item.canonicalWatchUrl

        fun enrichAndRegister(info: OnlineVideoAudioImporter.ResolvedMediaInfo): Result<OnlineVideoAudioImporter.ResolvedMediaInfo> {
            val enriched = info.copy(
                suggestedTitle = item.title.ifBlank { info.suggestedTitle },
                suggestedArtist = item.artist.ifBlank { info.suggestedArtist },
                coverUrl = item.thumbnailUrl.ifBlank { info.coverUrl },
                durationSeconds = if (item.durationSeconds > 0) item.durationSeconds else info.durationSeconds
            )
            StreamingHttpDataSourceConfig.registerStreamHeaders(
                videoId = item.videoId,
                audioUrl = enriched.audioUrl,
                videoUrl = enriched.videoUrl,
                headers = enriched.httpHeaders,
                audioHeaders = enriched.audioHeaders,
                videoHeaders = enriched.videoHeaders
            )
            return Result.success(enriched)
        }

        if (item.platform == StreamingSourcePlatform.SOUNDCLOUD) {
            val scResult = SoundCloudApiClient.resolvePlayableStream(context, item)
            val scInfo = scResult.getOrNull()
            if (scInfo != null) {
                return@withContext enrichAndRegister(scInfo)
            }
            return@withContext Result.failure(
                scResult.exceptionOrNull() ?: IllegalStateException("No se pudo resolver la pista de SoundCloud \"${item.title}\".")
            )
        }

        if (item.platform == StreamingSourcePlatform.YOUTUBE) {
            // === PLATAFORMA YOUTUBE: Usar yt-dlp en primer lugar ===
            if (!forceBypassYtDlp && !YtDlpAutoUpdaterIsBlockedSafe()) {
                val ytdlpResult = YtDlpNativeEngine.resolveStream(context, watchUrl)
                val info = ytdlpResult.getOrNull()
                if (info != null && (!info.audioUrl.isNullOrBlank() || info.videoUrl.isNotBlank())) {
                    return@withContext enrichAndRegister(info)
                }
            }

            // Respaldo automático si el video de YouTube tiene bloqueo en yt-dlp o recibió HTTP 403
            val innerTubeFallback = InnerTubeClient.resolve(
                videoId = item.videoId,
                originalUrl = watchUrl
            )
            if (innerTubeFallback != null && (!innerTubeFallback.audioUrl.isNullOrBlank() || innerTubeFallback.videoUrl.isNotBlank())) {
                return@withContext enrichAndRegister(innerTubeFallback)
            }

            val invidiousFallback = com.example.core.data.importer.InvidiousStreamResolver.resolve(
                videoId = item.videoId,
                originalUrl = watchUrl
            )
            if (invidiousFallback != null && (!invidiousFallback.audioUrl.isNullOrBlank() || invidiousFallback.videoUrl.isNotBlank())) {
                return@withContext enrichAndRegister(invidiousFallback)
            }

            val webFallback = com.example.core.data.importer.HeadlessWebViewExtractor.resolve(
                context = context,
                videoId = item.videoId,
                originalUrl = watchUrl
            )
            if (webFallback != null) {
                return@withContext enrichAndRegister(webFallback)
            }

            return@withContext Result.failure(
                IllegalStateException("No se pudo resolver la pista de YouTube \"${item.title}\".")
            )
        } else {
            // === PLATAFORMA YT MUSIC: Resolución directa sin usar yt-dlp ===
            val innerTubeInfo = InnerTubeClient.resolve(
                videoId = item.videoId,
                originalUrl = watchUrl
            )
            if (innerTubeInfo != null && (!innerTubeInfo.audioUrl.isNullOrBlank() || innerTubeInfo.videoUrl.isNotBlank())) {
                return@withContext enrichAndRegister(innerTubeInfo)
            }

            val invidiousInfo = com.example.core.data.importer.InvidiousStreamResolver.resolve(
                videoId = item.videoId,
                originalUrl = watchUrl
            )
            if (invidiousInfo != null && (!invidiousInfo.audioUrl.isNullOrBlank() || invidiousInfo.videoUrl.isNotBlank())) {
                return@withContext enrichAndRegister(invidiousInfo)
            }

            val webFallback = com.example.core.data.importer.HeadlessWebViewExtractor.resolve(
                context = context,
                videoId = item.videoId,
                originalUrl = watchUrl
            )
            if (webFallback != null) {
                return@withContext enrichAndRegister(webFallback)
            }

            return@withContext Result.failure(
                IllegalStateException("No se pudo resolver la pista de YT Music \"${item.title}\".")
            )
        }
    }

    private fun YtDlpAutoUpdaterIsBlockedSafe(): Boolean {
        return try {
            com.example.core.data.importer.YtDlpAutoUpdater.isYtDlpTemporarilyBlocked()
        } catch (_: Throwable) {
            false
        }
    }

}
