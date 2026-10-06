package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.data.importer.AudioMetadataParser
import com.example.data.importer.AudioSilenceTrimmer
import com.example.data.importer.FFmpegNativeEngine
import com.example.data.importer.LyricsManager
import com.example.data.importer.SampleMusicGenerator
import com.example.data.importer.VideoAudioExtractor
import com.example.data.local.dao.TrackDao
import com.example.data.local.entity.TrackEntity
import com.example.data.storage.AppStorageManager
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Aura Music - Importador Modular de Archivos y Flujos SAF (Storage Access Framework)
 *
 * Responsabilidades:
 * - Importación selectiva de archivos de audio mediante SAF OpenDocument y persistencia de permisos.
 * - Importación recursiva de árboles de carpetas completos mediante SAF OpenDocumentTree.
 * - Soporte opcional de recorte y eliminación inteligente de silencios al inicio y al final.
 * - Extracción y conversión de video local a música con Video Canvas y carátula WebP.
 * - Recepción de Intents externos ("Abrir con..." / "Compartir con...") con copia a songs/ y metadata/.
 * - Generación de canciones demostrativas iniciales.
 */
class SafTrackImporter(private val trackDao: TrackDao) {

    /**
     * Importa una lista de URIs seleccionadas por el usuario a través del selector de archivos SAF.
     */
    suspend fun importUris(context: Context, uris: List<Uri>, trimSilence: Boolean = false): Int = withContext(Dispatchers.IO) {
        var importedCount = 0
        val entities = mutableListOf<TrackEntity>()

        for (uri in uris) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (ignored: SecurityException) {}

                var track = AudioMetadataParser.parseUri(context, uri)
                if (track != null) {
                    if (trimSilence) {
                        val trimRes = AudioSilenceTrimmer.processUriForSilenceTrim(
                            context = context,
                            uri = uri,
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
            } catch (ignored: Exception) {}
        }

        if (entities.isNotEmpty()) {
            val insertedIds = trackDao.insertTracks(entities)
            val storageManager = AppStorageManager(context)
            insertedIds.forEach { id ->
                trackDao.getTrackById(id)?.let {
                    val domainTrack = it.toDomain()
                    storageManager.saveMetadataJson(domainTrack)
                    LyricsManager.autoDetectAndAssociateLyrics(context, domainTrack, storageManager)
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
                            val trimRes = AudioSilenceTrimmer.processUriForSilenceTrim(
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
                    val storageManager = AppStorageManager(context)
                    insertedIds.forEach { id ->
                        trackDao.getTrackById(id)?.let {
                            val domainTrack = it.toDomain()
                            storageManager.saveMetadataJson(domainTrack)
                            LyricsManager.autoDetectAndAssociateLyrics(context, domainTrack, storageManager)
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
        val storageManager = AppStorageManager(context)
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
        loopStyle: FFmpegNativeEngine.CanvasLoopStyle = FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE
    ): Track? = withContext(Dispatchers.IO) {
        val storageManager = AppStorageManager(context)
        val extractedTrack = VideoAudioExtractor.convertVideoToTrack(
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
     * Importa un archivo de audio proveniente de un Intent externo ("Abrir con..." o "Compartir con...").
     */
    suspend fun importSingleAudioFromExternalUri(
        context: Context,
        uri: Uri,
        trimSilence: Boolean = false
    ): Track? = withContext(Dispatchers.IO) {
        try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}

            val existing = trackDao.getTrackByUri(uri.toString())
            if (existing != null) {
                return@withContext existing.toDomain()
            }

            val storageManager = AppStorageManager(context)
            val parsed = AudioMetadataParser.parseUri(context, uri, folderName = "Externo")
            val rawName = AudioMetadataParser.getFileName(context, uri) ?: "Audio_${System.currentTimeMillis()}"
            val title = parsed?.title ?: rawName.substringBeforeLast(".")
            val artist = parsed?.artist ?: "Desconocido"
            val album = parsed?.album ?: "Música Compartida"

            var finalUriString = uri.toString()
            var fileSizeFormatted = parsed?.fileSizeFormatted ?: ""
            var durationMs = parsed?.durationMs ?: 0L
            var copiedLocalFile: File? = null

            try {
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    val mime = context.contentResolver.getType(uri)?.lowercase()
                    val rawExt = AudioMetadataParser.getFileExtension(context, uri)?.lowercase()
                    val mimeMappedExt = when {
                        mime?.contains("flac") == true -> "flac"
                        mime?.contains("wav") == true -> "wav"
                        mime?.contains("ogg") == true -> "ogg"
                        mime?.contains("opus") == true -> "opus"
                        mime?.contains("aac") == true -> "aac"
                        mime?.contains("mp4") == true || mime?.contains("m4a") == true -> "m4a"
                        mime?.contains("webm") == true -> "webm"
                        else -> null
                    }
                    val allowedExtensions = setOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac", "webm")
                    val safeExt = when {
                        rawExt != null && rawExt in allowedExtensions -> rawExt
                        mimeMappedExt != null -> mimeMappedExt
                        else -> "mp3"
                    }
                    val safeTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(25).ifBlank { "track" }
                    val uniqueToken = java.util.UUID.randomUUID().toString().replace("-", "").take(8)
                    var fileName = "ext_${System.currentTimeMillis()}_${uniqueToken}_$safeTitle.$safeExt"
                    
                    // Verificación de no-colisión en disco
                    val songsDir = storageManager.songsDir
                    var counter = 1
                    while (File(songsDir, fileName).exists()) {
                        fileName = "ext_${System.currentTimeMillis()}_${uniqueToken}_${safeTitle}_$counter.$safeExt"
                        counter++
                    }

                    val copiedFile = storageManager.saveSongFile(fileName, inStream)
                    copiedLocalFile = copiedFile
                    finalUriString = Uri.fromFile(copiedFile).toString()
                    val mb = copiedFile.length() / (1024f * 1024f)
                    fileSizeFormatted = String.format("%.1f MB", mb)
                }
            } catch (_: Exception) {}

            if (trimSilence) {
                val localFile = copiedLocalFile
                if (localFile != null && localFile.exists()) {
                    val trimRes = AudioSilenceTrimmer.processLocalAudioFile(
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
                    val trimRes = AudioSilenceTrimmer.processUriForSilenceTrim(
                        context = context,
                        uri = uri,
                        originalDurationMs = durationMs
                    )
                    if (trimRes.wasTrimmed && trimRes.newDurationMs > 0L) {
                        durationMs = trimRes.newDurationMs
                    }
                }
            }

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
            LyricsManager.autoDetectAndAssociateLyrics(context, saved, storageManager)
            saved
        } catch (_: Exception) {
            null
        }
    }
}
