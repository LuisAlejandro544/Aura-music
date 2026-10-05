package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.dao.PlaylistDao
import com.example.data.local.dao.TrackDao
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.PlaylistTrackCrossRef
import com.example.data.storage.AppStorageManager
import com.example.model.Playlist
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Aura Music - Repositorio Modular de Listas de Reproducción (Playlists)
 *
 * Responsabilidades:
 * - Operaciones reactivas sobre entidades de playlists y referencias cruzadas (PlaylistTrackCrossRef).
 * - Creación y edición de listas con soporte de carátula personalizada WebP o generación automática
 *   de collage dinámico (1, 2, 3 o 4 canciones).
 * - Eliminación segura de listas y limpieza asociada en el almacenamiento privado.
 */
class PlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao
) {

    val playlists: Flow<List<Playlist>> = combine(
        playlistDao.getAllPlaylists(),
        playlistDao.getAllPlaylistCrossRefs(),
        trackDao.getAllTracks()
    ) { playlistEntities, crossRefs, trackEntities ->
        val tracksById = trackEntities.associate { it.id to it.toDomain() }
        val crossRefsByPlaylist = crossRefs.groupBy { it.playlistId }
        playlistEntities.map { entity ->
            val refs = crossRefsByPlaylist[entity.id].orEmpty()
            val playlistTracks = refs.mapNotNull { tracksById[it.trackId] }
            entity.toDomain(
                trackCount = playlistTracks.size,
                previewTracks = playlistTracks.take(4)
            )
        }
    }

    fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>> =
        playlistDao.getTracksForPlaylist(playlistId).map { list ->
            list.map { it.toDomain() }
        }

    suspend fun createPlaylist(
        context: Context,
        name: String,
        description: String = "",
        customArtUri: Uri? = null
    ): Long = withContext(Dispatchers.IO) {
        val newId = playlistDao.insertPlaylist(
            PlaylistEntity(
                name = name.trim(),
                description = description.trim()
            )
        )
        if (customArtUri != null) {
            val storageManager = AppStorageManager(context)
            val savedPath = storageManager.savePlaylistArtworkFromUri(newId, customArtUri, null)
            if (savedPath != null) {
                playlistDao.updatePlaylistWithArt(newId, name.trim(), description.trim(), savedPath)
            }
        }
        newId
    }

    suspend fun updatePlaylist(
        context: Context,
        playlistId: Long,
        name: String,
        description: String = "",
        customArtUri: Uri? = null,
        removeArtwork: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        val existing = playlistDao.getPlaylistById(playlistId)
        val storageManager = AppStorageManager(context)
        val finalArtPath = when {
            customArtUri != null -> {
                storageManager.savePlaylistArtworkFromUri(playlistId, customArtUri, existing?.customArtPath)
            }
            removeArtwork -> {
                storageManager.deleteArtworkFile(existing?.customArtPath)
                null
            }
            else -> {
                existing?.customArtPath
            }
        }
        playlistDao.updatePlaylistWithArt(playlistId, name.trim(), description.trim(), finalArtPath)
        finalArtPath
    }

    suspend fun deletePlaylist(context: Context, playlistId: Long) = withContext(Dispatchers.IO) {
        val existing = playlistDao.getPlaylistById(playlistId)
        if (!existing?.customArtPath.isNullOrEmpty()) {
            AppStorageManager(context).deleteArtworkFile(existing?.customArtPath)
        }
        playlistDao.deleteCrossRefsForPlaylist(playlistId)
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) = withContext(Dispatchers.IO) {
        playlistDao.insertCrossRef(
            PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackId)
        )
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) = withContext(Dispatchers.IO) {
        playlistDao.removeTrackFromPlaylist(playlistId, trackId)
    }
}
