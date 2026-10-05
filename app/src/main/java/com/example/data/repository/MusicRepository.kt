package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.importer.FFmpegNativeEngine
import com.example.data.local.AppDatabase
import com.example.data.local.entity.TrackEntity
import com.example.data.storage.AppStorageManager
import com.example.model.Playlist
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Aura Music - Repositorio Central de Datos de Música
 *
 * Arquitectura y Principio de Operación Modular:
 * - Orquestador desacoplado sobre Room Database y el almacenamiento privado estructurado.
 * - Delega la gestión reactiva de listas de reproducción a [PlaylistRepository].
 * - Delega la importación de archivos SAF, carpetas y flujos externos a [SafTrackImporter].
 * - Gestiona operaciones directas sobre canciones, favoritos, conteo de reproducciones,
 *   edición de metadatos, asignación de carátulas WebP y Video Canvas sincronizado.
 */
class MusicRepository(private val database: AppDatabase) {

    private val trackDao = database.trackDao()
    private val playlistRepository = PlaylistRepository(database.playlistDao(), database.trackDao())
    private val safTrackImporter = SafTrackImporter(database.trackDao())

    val allTracks: Flow<List<Track>> = trackDao.getAllTracks().map { list ->
        list.map { it.toDomain() }
    }

    val favoriteTracks: Flow<List<Track>> = trackDao.getFavoriteTracks().map { list ->
        list.map { it.toDomain() }
    }

    val topPlayedTracks: Flow<List<Track>> = trackDao.getTopPlayedTracks().map { list ->
        list.map { it.toDomain() }
    }

    val recentlyAddedTracks: Flow<List<Track>> = trackDao.getRecentlyAddedTracks().map { list ->
        list.map { it.toDomain() }
    }

    val playlists: Flow<List<Playlist>> = playlistRepository.playlists

    fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>> =
        playlistRepository.getTracksForPlaylist(playlistId)

    suspend fun toggleFavorite(trackId: Long, currentStatus: Boolean) = withContext(Dispatchers.IO) {
        trackDao.updateFavorite(trackId, !currentStatus)
    }

    suspend fun incrementPlayCount(trackId: Long) = withContext(Dispatchers.IO) {
        trackDao.incrementPlayCount(trackId)
    }

    suspend fun updateTrackInfo(context: Context, trackId: Long, title: String, artist: String, album: String) = withContext(Dispatchers.IO) {
        trackDao.updateTrackInfo(trackId, title.trim(), artist.trim(), album.trim())
        val updated = trackDao.getTrackById(trackId)?.toDomain()
        if (updated != null) {
            AppStorageManager(context).saveMetadataJson(updated)
        }
    }

    suspend fun updateTrackDetails(
        context: Context,
        trackId: Long,
        title: String,
        artist: String,
        album: String,
        customArtUri: Uri? = null,
        shouldRemoveArt: Boolean = false,
        customVideoUri: Uri? = null,
        shouldRemoveVideo: Boolean = false,
        forceVideoLoop: Boolean? = null,
        loopStyle: FFmpegNativeEngine.CanvasLoopStyle = FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE
    ): Track? = withContext(Dispatchers.IO) {
        val current = trackDao.getTrackById(trackId) ?: return@withContext null
        val storageManager = AppStorageManager(context)

        val finalArtPath = when {
            customArtUri != null -> {
                storageManager.saveCustomArtworkFromUri(trackId, customArtUri, current.albumArtPath)
            }
            shouldRemoveArt -> {
                storageManager.deleteArtworkFile(current.albumArtPath)
                null
            }
            else -> {
                current.albumArtPath
            }
        }

        val (finalVideoPath, finalIsLoop) = when {
            customVideoUri != null -> {
                val result = storageManager.saveCustomVideoFromUri(trackId, customVideoUri, current.videoUri, forceVideoLoop, loopStyle)
                if (result != null) result.first to result.second else null to false
            }
            shouldRemoveVideo -> {
                storageManager.deleteVideoFile(current.videoUri)
                null to false
            }
            else -> {
                val resolvedLoop = forceVideoLoop ?: current.isVideoLoop
                if (resolvedLoop && !current.videoUri.isNullOrEmpty() && loopStyle == FFmpegNativeEngine.CanvasLoopStyle.BOOMERANG) {
                    val existingVideoFile = File(current.videoUri)
                    if (existingVideoFile.exists()) {
                        val result = storageManager.saveCustomVideoFromUri(
                            trackId = trackId,
                            sourceUri = Uri.fromFile(existingVideoFile),
                            oldVideoPath = current.videoUri,
                            forceLoop = true,
                            loopStyle = loopStyle
                        )
                        if (result != null) result.first to result.second else current.videoUri to resolvedLoop
                    } else {
                        current.videoUri to resolvedLoop
                    }
                } else {
                    current.videoUri to resolvedLoop
                }
            }
        }

        trackDao.updateTrackDetailsWithVideo(
            trackId,
            title.trim(),
            artist.trim(),
            album.trim(),
            finalArtPath,
            finalVideoPath,
            finalIsLoop
        )
        val updated = trackDao.getTrackById(trackId)?.toDomain()
        if (updated != null) {
            storageManager.saveMetadataJson(updated)
        }
        updated
    }

    suspend fun deleteTrack(context: Context, trackId: Long) = withContext(Dispatchers.IO) {
        val track = trackDao.getTrackById(trackId)
        trackDao.deleteTrackById(trackId)
        if (track != null) {
            AppStorageManager(context).deleteTrackFiles(trackId, track.albumArtPath, track.videoUri)
        }
    }

    suspend fun clearAllTracks() = withContext(Dispatchers.IO) {
        trackDao.deleteAllTracks()
    }

    // --- Métodos delegados en PlaylistRepository ---

    suspend fun createPlaylist(
        context: Context,
        name: String,
        description: String = "",
        customArtUri: Uri? = null
    ): Long = playlistRepository.createPlaylist(context, name, description, customArtUri)

    suspend fun updatePlaylist(
        context: Context,
        playlistId: Long,
        name: String,
        description: String = "",
        customArtUri: Uri? = null,
        removeArtwork: Boolean = false
    ): String? = playlistRepository.updatePlaylist(context, playlistId, name, description, customArtUri, removeArtwork)

    suspend fun deletePlaylist(context: Context, playlistId: Long) =
        playlistRepository.deletePlaylist(context, playlistId)

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) =
        playlistRepository.addTrackToPlaylist(playlistId, trackId)

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) =
        playlistRepository.removeTrackFromPlaylist(playlistId, trackId)

    // --- Métodos delegados en SafTrackImporter ---

    suspend fun importUris(context: Context, uris: List<Uri>, trimSilence: Boolean = false): Int =
        safTrackImporter.importUris(context, uris, trimSilence)

    suspend fun importTreeUri(context: Context, treeUri: Uri, trimSilence: Boolean = false): Int =
        safTrackImporter.importTreeUri(context, treeUri, trimSilence)

    suspend fun seedDemoTracks(context: Context): Int =
        safTrackImporter.seedDemoTracks(context)

    suspend fun importVideoAsTrack(
        context: Context,
        videoUri: Uri,
        title: String,
        artist: String,
        album: String,
        attachAsCanvas: Boolean,
        forceLoop: Boolean?,
        trimSilence: Boolean = false,
        loopStyle: FFmpegNativeEngine.CanvasLoopStyle = FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE
    ): Track? = safTrackImporter.importVideoAsTrack(
        context = context,
        videoUri = videoUri,
        title = title,
        artist = artist,
        album = album,
        attachAsCanvas = attachAsCanvas,
        forceLoop = forceLoop,
        trimSilence = trimSilence,
        loopStyle = loopStyle
    )

    suspend fun insertCustomTrack(context: Context, track: Track): Track = withContext(Dispatchers.IO) {
        val storageManager = AppStorageManager(context)
        val entity = TrackEntity.fromDomain(track)
        val newId = trackDao.insertTrack(entity)
        val savedTrack = trackDao.getTrackById(newId)?.toDomain() ?: track.copy(id = newId)
        storageManager.saveMetadataJson(savedTrack)
        savedTrack
    }

    suspend fun importSingleAudioFromExternalUri(
        context: Context,
        uri: Uri,
        trimSilence: Boolean = false
    ): Track? = safTrackImporter.importSingleAudioFromExternalUri(context, uri, trimSilence)
}
