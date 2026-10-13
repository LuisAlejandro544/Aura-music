package com.example.core.data.importer.mixtape

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.example.core.data.storage.AppStorageManager
import com.example.debug.AuraDebugManager
import com.example.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Collage WebP sin pérdida desde carátulas de pistas.
 * Rol arquitectónico: pieza de MixtapeEngine (fachada pública intacta).
 */
internal object MixtapeArtwork {
    private const val TAG = "MixtapeEngine"

    suspend fun collageArtwork(
        context: Context,
        tracks: List<Track>,
        timestamp: Long,
        storageManager: AppStorageManager
    ): File? {
        return try {
            val distinctArtTracks = tracks.filter { !it.albumArtPath.isNullOrBlank() && File(it.albumArtPath).exists() }
                .take(4)

            val targetSize = 512
            val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            if (distinctArtTracks.isEmpty()) {
                // Fondo oscuro con gradiente
                paint.color = android.graphics.Color.DKGRAY
                canvas.drawRect(0f, 0f, targetSize.toFloat(), targetSize.toFloat(), paint)
            } else {
                val decodedBitmaps = distinctArtTracks.mapNotNull { track ->
                    try {
                        BitmapFactory.decodeFile(track.albumArtPath)
                    } catch (_: Throwable) { null }
                }

                when (decodedBitmaps.size) {
                    1 -> {
                        canvas.drawBitmap(decodedBitmaps[0], null, Rect(0, 0, targetSize, targetSize), paint)
                    }
                    2 -> {
                        canvas.drawBitmap(decodedBitmaps[0], null, Rect(0, 0, targetSize / 2, targetSize), paint)
                        canvas.drawBitmap(decodedBitmaps[1], null, Rect(targetSize / 2, 0, targetSize, targetSize), paint)
                    }
                    3 -> {
                        canvas.drawBitmap(decodedBitmaps[0], null, Rect(0, 0, targetSize / 2, targetSize / 2), paint)
                        canvas.drawBitmap(decodedBitmaps[1], null, Rect(targetSize / 2, 0, targetSize, targetSize / 2), paint)
                        canvas.drawBitmap(decodedBitmaps[2], null, Rect(0, targetSize / 2, targetSize, targetSize), paint)
                    }
                    else -> {
                        // 4 fotos en cuadrícula 2x2
                        canvas.drawBitmap(decodedBitmaps[0], null, Rect(0, 0, targetSize / 2, targetSize / 2), paint)
                        canvas.drawBitmap(decodedBitmaps[1], null, Rect(targetSize / 2, 0, targetSize, targetSize / 2), paint)
                        canvas.drawBitmap(decodedBitmaps[2], null, Rect(0, targetSize / 2, targetSize / 2, targetSize), paint)
                        canvas.drawBitmap(decodedBitmaps[3], null, Rect(targetSize / 2, targetSize / 2, targetSize, targetSize), paint)
                    }
                }

                decodedBitmaps.forEach { it.recycle() }
            }

            val collageFile = storageManager.saveCoverAsWebp("mixtape_$timestamp", bitmap)
            bitmap.recycle()
            collageFile
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Error generando carátula de collage para mixtape: ${e.message}")
            null
        }
    }
}
