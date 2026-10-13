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
    companion object {
        /** Lado máximo del fondo en px: evita OOM y reduce peso sin perder nitidez en pantalla. */
        const val MAX_WALLPAPER_SIDE_PX = 1920
        /** Calidad WebP de máxima compresión manteniendo buena nitidez de fondo. */
        const val WALLPAPER_WEBP_QUALITY = 80
        /** Lado corto mínimo aceptado para video de fondo: por debajo no se reescala. */
        const val MIN_WALLPAPER_VIDEO_SHORT_SIDE_PX = 720
    }

    suspend fun saveAppWallpaperImageFromUri(
        sourceUri: Uri,
        oldMediaPath: String?
    ): String? = withContext(Dispatchers.IO) {
        try {
            // 1ª pasada: solo dimensiones para calcular inSampleSize y no agotar memoria.
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            }
            val srcWidth = boundsOptions.outWidth
            val srcHeight = boundsOptions.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return@withContext null

            var sampleSize = 1
            val maxSide = maxOf(srcWidth, srcHeight)
            while ((maxSide / sampleSize) > MAX_WALLPAPER_SIDE_PX) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return@withContext null

            // Reescala fina si aún supera el máximo tras el inSampleSize.
            val bitmap = run {
                val w = rawBitmap.width
                val h = rawBitmap.height
                val longest = maxOf(w, h)
                if (longest <= MAX_WALLPAPER_SIDE_PX) {
                    rawBitmap
                } else {
                    val ratio = MAX_WALLPAPER_SIDE_PX.toFloat() / longest.toFloat()
                    val targetW = (w * ratio).toInt().coerceAtLeast(1)
                    val targetH = (h * ratio).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(rawBitmap, targetW, targetH, true)
                    if (scaled != rawBitmap) rawBitmap.recycle()
                    scaled
                }
            }

            deleteAppWallpaperFile(oldMediaPath)

            val newFile = File(imagesDir, "app_wallpaper_${System.currentTimeMillis()}.webp")
            FileOutputStream(newFile).use { outStream ->
                // Máxima compresión: WebP con pérdida ligera (calidad 80) conserva
                // nitidez como fondo (lleva velo + blur) y pesa mucho menos que lossless 100.
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                bitmap.compress(format, WALLPAPER_WEBP_QUALITY, outStream)
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
                // Formato comprimido (MP4 sin audio/metadata) + bajada de resolución
                // solo si supera el mínimo de 720px en el lado corto; si el video ya
                // es pequeño se deja como está en resolución y solo se convierte.
                val processResult = com.example.core.data.importer.FFmpegNativeEngine.optimizeWallpaperVideo(
                    context = context,
                    inputFile = rawTempFile,
                    outputFile = newFile,
                    minShortSidePx = MIN_WALLPAPER_VIDEO_SHORT_SIDE_PX,
                    targetFps = 30
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
