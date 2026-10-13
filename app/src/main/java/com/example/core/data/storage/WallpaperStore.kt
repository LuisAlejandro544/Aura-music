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
 * Fondo de pantalla personalizado (imagen WebP o video MP4).
 * Rol arquitectónico: pieza de AppStorageManager (fachada pública intacta).
 */
internal class WallpaperStore(
    private val context: Context,
    private val imagesDir: File,
    private val videosDir: File
) {
    suspend fun saveAppWallpaperImageFromUri(
        sourceUri: Uri,
        oldMediaPath: String?
    ): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) return@withContext null

            deleteAppWallpaperFile(oldMediaPath)

            val newFile = File(imagesDir, "app_wallpaper_${System.currentTimeMillis()}.webp")
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

    suspend fun saveAppWallpaperVideoFromUri(
        sourceUri: Uri,
        oldMediaPath: String?
    ): String? = withContext(Dispatchers.IO) {
        try {
            deleteAppWallpaperFile(oldMediaPath)

            val rawTempFile = File(videosDir, "wallpaper_raw_${System.currentTimeMillis()}.tmp")
            val newFile = File(videosDir, "app_wallpaper_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(rawTempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            try {
                val processResult = com.example.core.data.importer.FFmpegNativeEngine.processVideoForCanvas(
                    context = context,
                    inputFile = rawTempFile,
                    outputFile = newFile,
                    isLoop = true,
                    loopStyle = com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE
                )
                if (!processResult.success || !newFile.exists() || newFile.length() == 0L) {
                    rawTempFile.renameTo(newFile)
                } else {
                    rawTempFile.delete()
                }
            } catch (_: Exception) {
                rawTempFile.renameTo(newFile)
            }

            newFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deleteAppWallpaperFile(mediaPath: String?): Boolean = withContext(Dispatchers.IO) {
        if (mediaPath.isNullOrBlank()) return@withContext false
        try {
            val file = File(mediaPath)
            if (file.exists() && (StoragePathGuards.isInside(file, imagesDir) || StoragePathGuards.isInside(file, videosDir))) {
                file.delete()
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
