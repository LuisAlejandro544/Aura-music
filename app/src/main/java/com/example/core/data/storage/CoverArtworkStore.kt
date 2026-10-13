package com.example.core.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Carátulas WebP en images/ (caché, custom y playlists).
 * Rol arquitectónico: pieza de AppStorageManager (fachada pública intacta).
 */
internal class CoverArtworkStore(
    private val context: Context,
    private val imagesDir: File
) {
    suspend fun saveCoverAsWebp(key: String, bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val safeKey = key.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(80).ifBlank { "${System.currentTimeMillis()}" }
        val file = StoragePathGuards.resolveChild(imagesDir, "cover_$safeKey.webp")
        val cleanBitmap = com.example.core.data.importer.MediaAssetProcessor.removeHorizontalLetterboxBars(bitmap)
        FileOutputStream(file).use { outStream ->
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            cleanBitmap.compress(format, 100, outStream)
        }
        if (cleanBitmap !== bitmap && !cleanBitmap.isRecycled) {
            cleanBitmap.recycle()
        }
        file
    }

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
                     if (oldFile.exists() && StoragePathGuards.isInside(oldFile, imagesDir)) {
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

    suspend fun savePlaylistArtworkFromUri(
        playlistId: Long,
        sourceUri: Uri,
        oldArtworkPath: String?
    ): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) return@withContext null

            if (!oldArtworkPath.isNullOrEmpty()) {
                try {
                    val oldFile = File(oldArtworkPath)
                    if (oldFile.exists() && StoragePathGuards.isInside(oldFile, imagesDir)) {
                        oldFile.delete()
                    }
                } catch (_: Exception) {}
            }

            val newFile = File(imagesDir, "playlist_cover_${playlistId}_${System.currentTimeMillis()}.webp")
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
        } catch (_: Exception) {
            null
        }
    }

     suspend fun deleteArtworkFile(artworkPath: String?): Boolean = withContext(Dispatchers.IO) {
         if (artworkPath.isNullOrEmpty()) return@withContext false
         try {
             val file = File(artworkPath)
             if (file.exists() && StoragePathGuards.isInside(file, imagesDir)) {
                 file.delete()
             } else {
                 false
             }
         } catch (e: Exception) {
             false
         }
     }
}
