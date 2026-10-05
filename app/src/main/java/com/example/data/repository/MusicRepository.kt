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

    val playlists: Flow<List<Playlist>> = kotlinx.coroutines.flow.combine(
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
        shouldRemoveArt: Boolean = false,
        customVideoUri: Uri? = null,
        shouldRemoveVideo: Boolean = false,
        forceVideoLoop: Boolean? = null,
        loopStyle: com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle = com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE
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
                if (resolvedLoop && !current.videoUri.isNullOrEmpty() && loopStyle == com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle.BOOMERANG) {
                    val existingVideoFile = java.io.File(current.videoUri)
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
            com.example.data.storage.AppStorageManager(context).deleteTrackFiles(trackId, track.albumArtPath, track.videoUri)
        }
    }

    suspend fun clearAllTracks() = withContext(Dispatchers.IO) {
        trackDao.deleteAllTracks()
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
            val storageManager = com.example.data.storage.AppStorageManager(context)
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
        val storageManager = com.example.data.storage.AppStorageManager(context)
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
            com.example.data.storage.AppStorageManager(context).deleteArtworkFile(existing?.customArtPath)
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

    /**
     * Importa una lista de URIs seleccionadas por el usuario a través del selector de archivos SAF.
     */
    suspend fun importUris(context: Context, uris: List<Uri>, trimSilence: Boolean = false): Int = withContext(Dispatchers.IO) {
        var importedCount = 0
        val entities = mutableListOf<TrackEntity>()

        for (uri in uris) {
            try {
                // Solicitar persistencia de permisos de lectura para poder reproducir en sesiones futuras
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (ignored: SecurityException) {}

                var track = AudioMetadataParser.parseUri(context, uri)
                if (track != null) {
                    if (trimSilence) {
                        val trimRes = com.example.data.importer.AudioSilenceTrimmer.processUriForSilenceTrim(
                            context = context,
                            uri = uri,
                            originalDurationMs = track.durationMs
                        )
                        if (trimRes.wasTrimmed && trimRes.newDurationMs > 0L) {
                            track = track.copy(durationMs = trimRes.newDurationMs)
                        }
                    }
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
                trackDao.getTrackById(id)?.let {
                    val domainTrack = it.toDomain()
                    storageManager.saveMetadataJson(domainTrack)
                    com.example.data.importer.LyricsManager.autoDetectAndAssociateLyrics(context, domainTrack, storageManager)
                }
            }
        }
        importedCount
    }

    /**
     * Importa una carpeta completa seleccionada por el usuario (SAF OpenDocumentTree).
     */
    suspend fun importTreeUri(context: Context, treeUri: Uri, trimSilence: Boolean = false): Int = withContext(Dispatchers.IO) {
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
                    var track = AudioMetadataParser.parseUri(context, doc.uri, folderName = folderName)
                    if (track != null) {
                        if (trimSilence) {
                            val trimRes = com.example.data.importer.AudioSilenceTrimmer.processUriForSilenceTrim(
                                context = context,
                                uri = doc.uri,
                                originalDurationMs = track.durationMs
                            )
                            if (trimRes.wasTrimmed && trimRes.newDurationMs > 0L) {
                                track = track.copy(durationMs = trimRes.newDurationMs)
                            }
                        }
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
                        trackDao.getTrackById(id)?.let {
                            val domainTrack = it.toDomain()
                            storageManager.saveMetadataJson(domainTrack)
                            com.example.data.importer.LyricsManager.autoDetectAndAssociateLyrics(context, domainTrack, storageManager)
                        }
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

    /**
     * Importa y convierte un video de la galería a canción con audio extraído,
     * carátula generada en WebP y Video Canvas opcional sincronizado.
     */
    suspend fun importVideoAsTrack(
        context: Context,
        videoUri: Uri,
        title: String,
        artist: String,
        album: String,
        attachAsCanvas: Boolean,
        forceLoop: Boolean?,
        trimSilence: Boolean = false,
        loopStyle: com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle = com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE
    ): Track? = withContext(Dispatchers.IO) {
        val storageManager = com.example.data.storage.AppStorageManager(context)
        val extractedTrack = com.example.data.importer.VideoAudioExtractor.convertVideoToTrack(
            context = context,
            storageManager = storageManager,
            videoUri = videoUri,
            title = title,
            artist = artist,
            album = album,
            attachAsCanvas = attachAsCanvas,
            forceLoop = forceLoop,
            trimSilence = trimSilence,
            loopStyle = loopStyle
        ) ?: return@withContext null

        val entity = TrackEntity.fromDomain(extractedTrack)
        val newId = trackDao.insertTrack(entity)
        val savedTrack = trackDao.getTrackById(newId)?.toDomain()
        if (savedTrack != null) {
            storageManager.saveMetadataJson(savedTrack)
        }
        savedTrack
    }

    /**
     * Inserta directamente una pista creada (ej: descarga desde enlace web/TikTok)
     * y genera su respaldo estructurado en metadata/.
     */
    suspend fun insertCustomTrack(context: Context, track: Track): Track = withContext(Dispatchers.IO) {
        val storageManager = com.example.data.storage.AppStorageManager(context)
        val entity = TrackEntity.fromDomain(track)
        val newId = trackDao.insertTrack(entity)
        val savedTrack = trackDao.getTrackById(newId)?.toDomain() ?: track.copy(id = newId)
        storageManager.saveMetadataJson(savedTrack)
        savedTrack
    }

    /**
     * Importa un archivo de audio proveniente de un Intent externo ("Abrir con..." o "Compartir con..."),
     * por ejemplo desde SnapTube, navegadores, gestores de archivos o mensajería.
     *
     * Para asegurar que la pista no se pierda si la aplicación externa revoca permisos o borra su caché,
     * copia el flujo a `songs/` en el almacenamiento privado estructurado de Aura Music,
     * extrae portada WebP y genera persistencia completa en Room y `metadata/`.
     */
    suspend fun importSingleAudioFromExternalUri(
        context: Context,
        uri: Uri,
        trimSilence: Boolean = false
    ): Track? = withContext(Dispatchers.IO) {
        try {
            // Intentar persistir permiso si es una URI de tipo SAF
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}

            // Si ya existe registrada con esa misma URI en Room
            val existing = trackDao.getTrackByUri(uri.toString())
            if (existing != null) {
                return@withContext existing.toDomain()
            }

            val storageManager = com.example.data.storage.AppStorageManager(context)
            val parsed = AudioMetadataParser.parseUri(context, uri, folderName = "Externo")
            val rawName = AudioMetadataParser.getFileName(context, uri) ?: "Audio_${System.currentTimeMillis()}"
            val title = parsed?.title ?: rawName.substringBeforeLast(".")
            val artist = parsed?.artist ?: "Desconocido"
            val album = parsed?.album ?: "Música Compartida"

            var finalUriString = uri.toString()
            var fileSizeFormatted = parsed?.fileSizeFormatted ?: ""
            var durationMs = parsed?.durationMs ?: 0L
            var copiedLocalFile: java.io.File? = null

            // Copia segura a la carpeta estructurada songs/ para disponibilidad sin conexión
            try {
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    val ext = AudioMetadataParser.getFileExtension(context, uri) ?: "mp3"
                    val safeTitle = title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(25)
                    val fileName = "ext_${System.currentTimeMillis()}_$safeTitle.$ext"
                    val copiedFile = storageManager.saveSongFile(fileName, inStream)
                    copiedLocalFile = copiedFile
                    finalUriString = Uri.fromFile(copiedFile).toString()
                    val mb = copiedFile.length() / (1024f * 1024f)
                    fileSizeFormatted = String.format("%.1f MB", mb)
                }
            } catch (_: Exception) {}

            // Si el usuario activó la eliminación inteligente de silencios al inicio y final
            if (trimSilence) {
                val localFile = copiedLocalFile
                if (localFile != null && localFile.exists()) {
                    val trimRes = com.example.data.importer.AudioSilenceTrimmer.processLocalAudioFile(
                        context = context,
                        audioFile = localFile,
                        originalDurationMs = durationMs
                    )
                    if (trimRes.wasTrimmed && trimRes.newDurationMs > 0L) {
                        durationMs = trimRes.newDurationMs
                    }
                    val mb = localFile.length() / (1024f * 1024f)
                    fileSizeFormatted = String.format("%.1f MB", mb)
                } else {
                    val trimRes = com.example.data.importer.AudioSilenceTrimmer.processUriForSilenceTrim(
                        context = context,
                        uri = uri,
                        originalDurationMs = durationMs
                    )
                    if (trimRes.wasTrimmed && trimRes.newDurationMs > 0L) {
                        durationMs = trimRes.newDurationMs
                    }
                }
            }

            // Verificar si ya existe con la URI de destino
            val existingWithCopiedUri = trackDao.getTrackByUri(finalUriString)
            if (existingWithCopiedUri != null) {
                return@withContext existingWithCopiedUri.toDomain()
            }

            val trackToSave = (parsed ?: Track(
                id = 0,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                uriString = finalUriString,
                albumArtPath = null,
                mimeType = "audio/mpeg",
                dateAdded = System.currentTimeMillis(),
                isFavorite = false,
                playCount = 0,
                folderName = "Externo",
                fileSizeFormatted = fileSizeFormatted
            )).copy(
                uriString = finalUriString,
                durationMs = durationMs,
                fileSizeFormatted = fileSizeFormatted,
                folderName = "Externo"
            )

            val entity = TrackEntity.fromDomain(trackToSave)
            val newId = trackDao.insertTrack(entity)
            val saved = trackDao.getTrackById(newId)?.toDomain() ?: trackToSave.copy(id = newId)
            storageManager.saveMetadataJson(saved)
            com.example.data.importer.LyricsManager.autoDetectAndAssociateLyrics(context, saved, storageManager)
            saved
        } catch (_: Exception) {
            null
        }
    }
}
