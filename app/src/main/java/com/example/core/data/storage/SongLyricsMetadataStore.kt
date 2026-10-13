package com.example.core.data.storage

import android.content.Context
import com.example.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Canciones, letras .lrc, JSON de metadatos y borrado por pista.
 * Rol arquitectónico: pieza de AppStorageManager (fachada pública intacta).
 */
internal class SongLyricsMetadataStore(
    private val context: Context,
    private val songsDir: File,
    private val lyricsDir: File,
    private val metadataDir: File,
    private val imagesDir: File,
    private val videosDir: File
) {
    suspend fun saveSongFile(fileName: String, inputStream: InputStream): File = withContext(Dispatchers.IO) {
        val file = StoragePathGuards.resolveChild(songsDir, fileName)
        FileOutputStream(file).use { outStream ->
            inputStream.copyTo(outStream)
        }
        file
    }

    suspend fun saveLyrics(trackId: Long, lyricsContent: String): File = withContext(Dispatchers.IO) {
        val file = File(lyricsDir, "track_$trackId.lrc")
        file.writeText(lyricsContent)
        file
    }

    suspend fun getLyrics(trackId: Long): String? = withContext(Dispatchers.IO) {
        val file = File(lyricsDir, "track_$trackId.lrc")
        if (file.exists()) file.readText() else null
    }

    suspend fun saveMetadataJson(track: Track): File = withContext(Dispatchers.IO) {
        val file = File(metadataDir, "track_${track.id}.json")
        val json = JSONObject().apply {
            put("id", track.id)
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            put("durationMs", track.durationMs)
            put("uriString", track.uriString)
            put("albumArtPath", track.albumArtPath ?: "")
            put("videoUri", track.videoUri ?: "")
            put("isVideoLoop", track.isVideoLoop)
            put("mimeType", track.mimeType)
            put("dateAdded", track.dateAdded)
            put("isFavorite", track.isFavorite)
            put("playCount", track.playCount)
            put("folderName", track.folderName)
            put("fileSizeFormatted", track.fileSizeFormatted)
        }
        file.writeText(json.toString(2))
        file
    }

    suspend fun getMetadataJson(trackId: Long): String? = withContext(Dispatchers.IO) {
        val file = File(metadataDir, "track_$trackId.json")
        if (file.exists()) file.readText() else null
    }

    suspend fun deleteTrackFiles(trackId: Long, albumArtPath: String?, videoPath: String? = null) = withContext(Dispatchers.IO) {
        try {
            if (!albumArtPath.isNullOrEmpty()) {
                val artFile = File(albumArtPath)
                if (artFile.exists() && StoragePathGuards.isInside(artFile, imagesDir)) {
                    artFile.delete()
                }
            }
            if (!videoPath.isNullOrEmpty()) {
                val vidFile = File(videoPath)
                if (vidFile.exists() && StoragePathGuards.isInside(vidFile, videosDir)) {
                    vidFile.delete()
                }
            }
            StoragePathGuards.resolveChild(lyricsDir, "track_$trackId.lrc").takeIf { it.exists() }?.delete()
            StoragePathGuards.resolveChild(metadataDir, "track_$trackId.json").takeIf { it.exists() }?.delete()
        } catch (ignored: Exception) {}
    }
}
