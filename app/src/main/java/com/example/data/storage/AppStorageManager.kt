package com.example.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
 * Mantiene 5 carpetas dedicadas:
 * - images/   -> Carátulas de álbumes convertidas a WebP con máxima compresión sin pérdida
 * - songs/    -> Canciones y pistas de audio locales
 * - lyrics/   -> Archivos de letras sincronizadas (.lrc) o texto plano
 * - metadata/ -> Archivos JSON con información técnica y descriptiva de las pistas
 * - videos/   -> Videos de fondo y loops de Canvas sincronizados o continuos
 */
class AppStorageManager(private val context: Context) {

    // Directorio base en almacenamiento externo privado del paquete
    private val baseDir: File = context.getExternalFilesDir(null) ?: context.filesDir

    val imagesDir: File = File(baseDir, "images").apply { if (!exists()) mkdirs() }
    val songsDir: File = File(baseDir, "songs").apply { if (!exists()) mkdirs() }
    val lyricsDir: File = File(baseDir, "lyrics").apply { if (!exists()) mkdirs() }
    val metadataDir: File = File(baseDir, "metadata").apply { if (!exists()) mkdirs() }
    val videosDir: File = File(baseDir, "videos").apply { if (!exists()) mkdirs() }

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

    /**
     * Lee los metadatos JSON de una canción.
     */
    suspend fun getMetadataJson(trackId: Long): String? = withContext(Dispatchers.IO) {
        val file = File(metadataDir, "track_$trackId.json")
        if (file.exists()) file.readText() else null
    }

    /**
     * Guarda una carátula personalizada seleccionada por el usuario desde la galería (SAF/Photo Picker),
     * comprimiéndola a WebP sin pérdida en images/ y eliminando físicamente la carátula anterior
     * asociada a la canción para evitar acumulación de archivos huérfanos.
     */
     suspend fun saveCustomArtworkFromUri(
         trackId: Long,
         sourceUri: Uri,
         oldArtworkPath: String?
     ): String? = withContext(Dispatchers.IO) {
         try {
             // 1. Decodificar la imagen desde el Uri
             val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext null
             val bitmap = BitmapFactory.decodeStream(inputStream)
             inputStream.close()
             if (bitmap == null) return@withContext null

             // 2. Eliminar la carátula previa si existía en images/
             if (!oldArtworkPath.isNullOrEmpty()) {
                 try {
                     val oldFile = File(oldArtworkPath)
                     if (oldFile.exists() && oldFile.canonicalPath.startsWith(imagesDir.canonicalPath)) {
                         oldFile.delete()
                     }
                 } catch (ignored: Exception) {}
             }

             // 3. Guardar el nuevo archivo WebP
             val newFile = File(imagesDir, "cover_custom_${trackId}_${System.currentTimeMillis()}.webp")
             FileOutputStream(newFile).use { outStream ->
                 val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                     Bitmap.CompressFormat.WEBP_LOSSLESS
                 } else {
                     @Suppress("DEPRECATION")
                     Bitmap.CompressFormat.WEBP
                 }
                 bitmap.compress(format, 100, outStream)
             }
             bitmap.recycle()
             newFile.absolutePath
         } catch (e: Exception) {
             null
         }
     }

    /**
     * Elimina físicamente un archivo de carátula si existe en el directorio de imágenes.
     */
     suspend fun deleteArtworkFile(artworkPath: String?): Boolean = withContext(Dispatchers.IO) {
         if (artworkPath.isNullOrEmpty()) return@withContext false
         try {
             val file = File(artworkPath)
             if (file.exists() && file.canonicalPath.startsWith(imagesDir.canonicalPath)) {
                 file.delete()
             } else {
                 false
             }
         } catch (e: Exception) {
             false
         }
     }

     /**
      * Guarda un video personalizado o Canvas seleccionado por el usuario desde la galería (SAF/Photo Picker).
      * Analiza la duración del video con MediaMetadataRetriever para determinar si es un loop corto (<= 20s)
      * o un video largo sincronizado con la canción, y elimina el video previo si existía.
      */
     suspend fun saveCustomVideoFromUri(
         trackId: Long,
         sourceUri: Uri,
         oldVideoPath: String?,
         forceLoop: Boolean? = null
     ): Pair<String, Boolean>? = withContext(Dispatchers.IO) {
         try {
             // 1. Determinar duración con MediaMetadataRetriever o respetar forzado manual
             var isLoop = false
             if (forceLoop != null) {
                 isLoop = forceLoop
             } else {
                 try {
                     val retriever = android.media.MediaMetadataRetriever()
                     retriever.setDataSource(context, sourceUri)
                     val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                     val durationMs = durStr?.toLongOrNull() ?: 0L
                     // Si dura 20 segundos o menos, se cataloga automáticamente como bucle continuo de Canvas
                     isLoop = durationMs in 1..20500L
                     retriever.release()
                 } catch (e: Exception) {
                     isLoop = false
                 }
             }

             // 2. Eliminar video previo si existía en videosDir
             if (!oldVideoPath.isNullOrEmpty()) {
                 try {
                     val oldFile = File(oldVideoPath)
                     if (oldFile.exists() && oldFile.canonicalPath.startsWith(videosDir.canonicalPath)) {
                         oldFile.delete()
                     }
                 } catch (ignored: Exception) {}
             }

             // 3. Copiar archivo al directorio videos/
             val newFile = File(videosDir, "canvas_${trackId}_${System.currentTimeMillis()}.mp4")
             context.contentResolver.openInputStream(sourceUri)?.use { input ->
                 FileOutputStream(newFile).use { output ->
                     input.copyTo(output)
                 }
             } ?: return@withContext null

             Pair(newFile.absolutePath, isLoop)
         } catch (e: Exception) {
             null
         }
     }

     /**
      * Elimina físicamente un archivo de video si existe en el directorio de videos.
      */
     suspend fun deleteVideoFile(videoPath: String?): Boolean = withContext(Dispatchers.IO) {
         if (videoPath.isNullOrEmpty()) return@withContext false
         try {
             val file = File(videoPath)
             if (file.exists() && file.canonicalPath.startsWith(videosDir.canonicalPath)) {
                 file.delete()
             } else {
                 false
             }
         } catch (e: Exception) {
             false
         }
     }

    /**
     * Elimina los archivos asociados a una pista (carátula WebP, video de fondo, letra y metadatos JSON).
     */
    suspend fun deleteTrackFiles(trackId: Long, albumArtPath: String?, videoPath: String? = null) = withContext(Dispatchers.IO) {
        try {
            if (!albumArtPath.isNullOrEmpty()) {
                val artFile = File(albumArtPath)
                if (artFile.exists() && artFile.startsWith(imagesDir)) {
                    artFile.delete()
                }
            }
            if (!videoPath.isNullOrEmpty()) {
                val vidFile = File(videoPath)
                if (vidFile.exists() && vidFile.startsWith(videosDir)) {
                    vidFile.delete()
                }
            }
            File(lyricsDir, "track_$trackId.lrc").takeIf { it.exists() }?.delete()
            File(metadataDir, "track_$trackId.json").takeIf { it.exists() }?.delete()
        } catch (ignored: Exception) {}
    }
}
