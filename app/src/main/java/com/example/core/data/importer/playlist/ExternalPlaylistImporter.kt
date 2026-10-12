package com.example.core.data.importer.playlist

import android.content.Context
import com.example.core.data.streaming.StreamingApiService
import com.example.core.model.ExternalPlaylistPreview
import com.example.core.model.ExternalPlaylistSource
import com.example.core.model.ExternalPlaylistTrackItem
import com.example.core.model.StreamingSearchFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Importador Universal de Playlists Públicas desde Spotify y YouTube Music / YouTube.
 *
 * Rol Arquitectónico:
 * - Fachada pública estable; delega en SpotifyPlaylistExtractor, YouTubePlaylistExtractor,
 *   PlaylistHttpClient y StreamingApiService sin claves API ni archivos `.env`.
 */
object ExternalPlaylistImporter {

    private const val TAG = "ExternalPlaylistImporter"

    fun isPlaylistUrl(rawUrl: String): Boolean {
        val clean = rawUrl.trim()
        if (!clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
            return false
        }
        val lower = clean.lowercase()
        if (lower.contains("spotify.com/playlist/") || lower.contains("spotify.com/embed/playlist/")) {
            return true
        }
        if ((lower.contains("youtube.com") || lower.contains("youtu.be")) && lower.contains("list=")) {
            // Si es un enlace directo a /playlist?list= o tiene list=PL / list=OLAK / list=UU
            if (lower.contains("/playlist") ||
                lower.contains("list=pl") ||
                lower.contains("list=olak") ||
                lower.contains("list=fl") ||
                lower.contains("list=uu")
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Detecta la plataforma de origen a partir de la URL ingresada por el usuario.
     */

    fun detectSourcePlatform(rawUrl: String): ExternalPlaylistSource? {
        val lower = rawUrl.trim().lowercase()
        return when {
            lower.contains("spotify.com") || lower.contains("spotify.link") -> ExternalPlaylistSource.SPOTIFY
            lower.contains("youtube.com") || lower.contains("youtu.be") -> ExternalPlaylistSource.YOUTUBE_MUSIC
            else -> null
        }
    }

    /**
     * Analiza una URL de Playlist pública de Spotify o YouTube Music y devuelve su previsualización
     * completa (título, autor, portada y lista de canciones).
     */

    suspend fun fetchPlaylistPreview(
        context: Context,
        rawUrl: String
    ): Result<ExternalPlaylistPreview> = withContext(Dispatchers.IO) {
        val cleanUrl = rawUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Ingresa un enlace válido de Playlist de Spotify o YouTube Music."))
        }

        val resolvedUrl = if (cleanUrl.contains("spotify.link", ignoreCase = true)) {
            PlaylistHttpClient.resolveRedirectUrl(cleanUrl) ?: cleanUrl
        } else {
            cleanUrl
        }

        val platform = detectSourcePlatform(resolvedUrl)
            ?: return@withContext Result.failure(
                IllegalArgumentException("El enlace no pertenece a Spotify ni a YouTube Music / YouTube.")
            )

        return@withContext when (platform) {
            ExternalPlaylistSource.SPOTIFY -> SpotifyPlaylistExtractor.fetchSpotifyPlaylist(context, resolvedUrl)
            ExternalPlaylistSource.YOUTUBE_MUSIC -> YouTubePlaylistExtractor.fetchYouTubePlaylist(context, resolvedUrl)
        }
    }

    /**
     * Empareja una pista de Spotify (`Título + Artista`) con el catálogo de YouTube Music
     * para obtener su `videoId`, miniatura HD y flujo de audio/video reproducible.
     */

    suspend fun matchSpotifyTrackToStreaming(
        item: ExternalPlaylistTrackItem
    ): ExternalPlaylistTrackItem = withContext(Dispatchers.IO) {
        if (item.videoIdOrUrl.length == 11 && !item.videoIdOrUrl.contains("/")) {
            return@withContext item
        }
        val query = "${item.title} ${item.artist}".trim()
        val results = StreamingApiService.searchStreamingCatalog(
            query = query,
            filter = StreamingSearchFilter.YT_MUSIC
        )
        val bestMatch = results.firstOrNull() ?: StreamingApiService.searchStreamingCatalog(
            query = query,
            filter = StreamingSearchFilter.YOUTUBE
        ).firstOrNull()

        if (bestMatch != null) {
            item.copy(
                videoIdOrUrl = bestMatch.videoId,
                thumbnailUrl = item.thumbnailUrl.ifBlank { bestMatch.thumbnailUrl },
                durationSeconds = if (item.durationSeconds > 0L) item.durationSeconds else bestMatch.durationSeconds
            )
        } else {
            item
        }
    }

    /**
     * Empareja un lote de pistas de Spotify en paralelo controlado (bloques de 5)
     * reportando el progreso al coordinador.
     */

    suspend fun matchSpotifyTracksBatch(
        tracks: List<ExternalPlaylistTrackItem>,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): List<ExternalPlaylistTrackItem> = withContext(Dispatchers.IO) {
        val total = tracks.size
        if (total == 0) return@withContext emptyList()
        val matched = MutableList(total) { tracks[it] }
        var completedCount = 0

        tracks.chunked(5).forEachIndexed { chunkIdx, chunk ->
            val chunkResults = coroutineScope {
                chunk.map { trackItem ->
                    async {
                        matchSpotifyTrackToStreaming(trackItem)
                    }
                }.awaitAll()
            }
            chunkResults.forEachIndexed { innerIdx, resolvedItem ->
                val globalIdx = chunkIdx * 5 + innerIdx
                if (globalIdx < matched.size) {
                    matched[globalIdx] = resolvedItem
                }
            }
            completedCount = (completedCount + chunk.size).coerceAtMost(total)
            onProgress(completedCount, total)
        }
        matched
    }

    // =========================================================================
    // 1. EXTRACTOR DE PLAYLISTS DE SPOTIFY (SIN API KEY NI LOGIN)
    // =========================================================================

}
