package com.example.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Gestor centralizado del almacenamiento estructurado de la aplicación en:
 * Android/data/com.aistudio.musicplayer.aurasound/files/
 *
 * Mantiene 4 carpetas dedicadas:
 * - images/   -> Carátulas de álbumes convertidas a WebP con máxima compresión sin pérdida
 * - songs/    -> Canciones y pistas de audio locales
 * - lyrics/   -> Archivos de letras sincronizadas (.lrc) o texto plano
 * - metadata/ -> Archivos JSON con información técnica y descriptiva de las pistas
 */
class AppStorageManager(private val context: Context) {

    // Directorio base en almacenamiento externo privado del paquete
    private val baseDir: File = context.getExternalFilesDir(null) ?: context.filesDir

    val imagesDir: File = File(baseDir, "images").apply { if (!exists()) mkdirs() }
    val songsDir: File = File(baseDir, "songs").apply { if (!exists()) mkdirs() }
    val lyricsDir: File = File(baseDir, "lyrics").apply { if (!exists()) mkdirs() }
    val metadataDir: File = File(baseDir, "metadata").apply { if (!exists()) mkdirs() }

    /**
     * Guarda una carátula comprimiéndola a formato WebP sin pérdida de calidad (Lossless)
     * en un hilo secundario (Dispatchers.IO).
     */
    suspend fun saveCoverAsWebp(key: String, bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val file = File(imagesDir, "cover_$key.webp")
        FileOutputStream(file).use { outStream ->
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            bitmap.compress(format, 100, outStream)
        }
        file
    }

    /**
     * Convierte bytes de imagen extraídos (ID3) a formato WebP sin pérdida.
     */
    suspend fun saveCoverBytesAsWebp(key: String, bytes: ByteArray): File? = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            val file = saveCoverAsWebp(key, bitmap)
            bitmap.recycle()
            file
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Guarda una pista de audio en la carpeta songs/
     */
    suspend fun saveSongFile(fileName: String, inputStream: InputStream): File = withContext(Dispatchers.IO) {
        val file = File(songsDir, fileName)
        FileOutputStream(file).use { outStream ->
            inputStream.copyTo(outStream)
        }
        file
    }

    /**
     * Guarda la letra de una canción en lyrics/track_{id}.lrc
     */
    suspend fun saveLyrics(trackId: Long, lyricsContent: String): File = withContext(Dispatchers.IO) {
        val file = File(lyricsDir, "track_$trackId.lrc")
        file.writeText(lyricsContent)
        file
    }

    /**
     * Lee la letra de una canción si existe.
     */
    suspend fun getLyrics(trackId: Long): String? = withContext(Dispatchers.IO) {
        val file = File(lyricsDir, "track_$trackId.lrc")
        if (file.exists()) file.readText() else null
    }

    /**
     * Guarda un archivo JSON con los metadatos de la canción en metadata/track_{id}.json
     */
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

    /**
     * Lee los metadatos JSON de una canción.
     */
    suspend fun getMetadataJson(trackId: Long): String? = withContext(Dispatchers.IO) {
        val file = File(metadataDir, "track_$trackId.json")
        if (file.exists()) file.readText() else null
    }

    /**
     * Elimina los archivos asociados a una pista (carátula WebP, letra y metadatos JSON).
     */
    suspend fun deleteTrackFiles(trackId: Long, albumArtPath: String?) = withContext(Dispatchers.IO) {
        try {
            if (!albumArtPath.isNullOrEmpty()) {
                val artFile = File(albumArtPath)
                if (artFile.exists() && artFile.startsWith(imagesDir)) {
                    artFile.delete()
                }
            }
            File(lyricsDir, "track_$trackId.lrc").takeIf { it.exists() }?.delete()
            File(metadataDir, "track_$trackId.json").takeIf { it.exists() }?.delete()
        } catch (ignored: Exception) {}
    }
}
