package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.example.data.importer.AudioMetadataParser
import com.example.data.importer.SampleMusicGenerator
import com.example.data.local.AppDatabase
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.PlaylistTrackCrossRef
import com.example.data.local.entity.TrackEntity
import com.example.model.Playlist
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repositorio central de datos de música.
 * Gestiona el acceso desacoplado a Room Database y la importación de archivos de audio
 * mediante el Storage Access Framework (SAF), respetando la privacidad del usuario.
 */
class MusicRepository(private val database: AppDatabase) {

    private val trackDao = database.trackDao()
    private val playlistDao = database.playlistDao()

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

    val playlists: Flow<List<Playlist>> = playlistDao.getAllPlaylists().map { list ->
        list.map { it.toDomain() }
    }

    fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>> =
        playlistDao.getTracksForPlaylist(playlistId).map { list ->
            list.map { it.toDomain() }
        }

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
            com.example.data.storage.AppStorageManager(context).saveMetadataJson(updated)
        }
    }

    suspend fun updateTrackDetails(
        context: Context,
        trackId: Long,
        title: String,
        artist: String,
        album: String,
        customArtUri: Uri? = null,
        shouldRemoveArt: Boolean = false
    ): Track? = withContext(Dispatchers.IO) {
        val current = trackDao.getTrackById(trackId) ?: return@withContext null
        val storageManager = com.example.data.storage.AppStorageManager(context)

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

        trackDao.updateTrackDetails(trackId, title.trim(), artist.trim(), album.trim(), finalArtPath)
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
            com.example.data.storage.AppStorageManager(context).deleteTrackFiles(trackId, track.albumArtPath)
        }
    }

    suspend fun clearAllTracks() = withContext(Dispatchers.IO) {
        trackDao.deleteAllTracks()
    }

    suspend fun createPlaylist(name: String, description: String = ""): Long = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(
            PlaylistEntity(
                name = name.trim(),
                description = description.trim()
            )
        )
    }

    suspend fun updatePlaylist(playlistId: Long, name: String, description: String = "") = withContext(Dispatchers.IO) {
        playlistDao.updatePlaylist(playlistId, name.trim(), description.trim())
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
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

    /**
     * Importa una lista de URIs seleccionadas por el usuario a través del selector de archivos SAF.
     */
    suspend fun importUris(context: Context, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var importedCount = 0
        val entities = mutableListOf<TrackEntity>()

        for (uri in uris) {
            try {
                // Solicitar persistencia de permisos de lectura para poder reproducir en sesiones futuras
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (ignored: SecurityException) {}

                val track = AudioMetadataParser.parseUri(context, uri)
                if (track != null) {
                    // Verificar si ya existe por uriString
                    val existing = trackDao.getTrackByUri(track.uriString)
                    if (existing == null) {
                        entities.add(TrackEntity.fromDomain(track))
                        importedCount++
                    }
                }
            } catch (ignored: Exception) {}
        }

        if (entities.isNotEmpty()) {
            val insertedIds = trackDao.insertTracks(entities)
            val storageManager = com.example.data.storage.AppStorageManager(context)
            insertedIds.forEach { id ->
                trackDao.getTrackById(id)?.let { storageManager.saveMetadataJson(it.toDomain()) }
            }
        }
        importedCount
    }

    /**
     * Importa una carpeta completa seleccionada por el usuario (SAF OpenDocumentTree).
     */
    suspend fun importTreeUri(context: Context, treeUri: Uri): Int = withContext(Dispatchers.IO) {
        var importedCount = 0
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            } catch (ignored: SecurityException) {}

            val documentFile = DocumentFile.fromTreeUri(context, treeUri)
            if (documentFile != null && documentFile.isDirectory) {
                val folderName = documentFile.name ?: "Carpeta Importada"
                val audioFiles = mutableListOf<DocumentFile>()
                findAudioFilesRecursive(documentFile, audioFiles)

                val entities = mutableListOf<TrackEntity>()
                for (doc in audioFiles) {
                    val track = AudioMetadataParser.parseUri(context, doc.uri, folderName = folderName)
                    if (track != null) {
                        val existing = trackDao.getTrackByUri(track.uriString)
                        if (existing == null) {
                            entities.add(TrackEntity.fromDomain(track))
                            importedCount++
                        }
                    }
                }

                if (entities.isNotEmpty()) {
                    val insertedIds = trackDao.insertTracks(entities)
                    val storageManager = com.example.data.storage.AppStorageManager(context)
                    insertedIds.forEach { id ->
                        trackDao.getTrackById(id)?.let { storageManager.saveMetadataJson(it.toDomain()) }
                    }
                }
            }
        } catch (ignored: Exception) {}
        importedCount
    }

    private fun findAudioFilesRecursive(folder: DocumentFile, results: MutableList<DocumentFile>) {
        val files = folder.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                findAudioFilesRecursive(file, results)
            } else if (isAudioFile(file)) {
                results.add(file)
            }
        }
    }

    private fun isAudioFile(file: DocumentFile): Boolean {
        val type = file.type
        if (type != null && type.startsWith("audio/")) return true
        val name = file.name?.lowercase() ?: ""
        return name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac") ||
                name.endsWith(".ogg") || name.endsWith(".m4a") || name.endsWith(".aac")
    }

    /**
     * Genera e importa canciones demostrativas listas para reproducir.
     */
    suspend fun seedDemoTracks(context: Context): Int = withContext(Dispatchers.IO) {
        val demos = SampleMusicGenerator.generateDemoTracks(context)
        val entities = demos.map { TrackEntity.fromDomain(it) }
        val ids = trackDao.insertTracks(entities)
        val storageManager = com.example.data.storage.AppStorageManager(context)
        ids.forEach { id ->
            trackDao.getTrackById(id)?.let { storageManager.saveMetadataJson(it.toDomain()) }
        }
        ids.size
    }
}
