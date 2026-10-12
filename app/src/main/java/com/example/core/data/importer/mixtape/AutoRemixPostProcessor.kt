package com.example.core.data.importer.mixtape

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.example.core.data.storage.AppStorageManager
import com.example.core.model.MixtapeChapter
import com.example.core.model.MixtapeMetadata
import com.example.core.model.Track
import java.io.File
import java.util.Locale
import java.util.regex.Pattern

/**
 * Post-procesado de Auto-Remix (carátula, letras y metadatos).
 * Rol arquitectónico: collage WebP, LRC continuo con offsets y JSON de capítulos.
 */
internal object AutoRemixPostProcessor {

    private val LRC_TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?](.*)")

    internal suspend fun generateSplitRemixArtwork(
        trackA: Track,
        trackB: Track,
        timestamp: Long,
        storageManager: AppStorageManager
    ): File? {
        return try {
            val targetSize = 512
            val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            val bmpA = trackA.albumArtPath?.takeIf { File(it).exists() }?.let { BitmapFactory.decodeFile(it) }
            val bmpB = trackB.albumArtPath?.takeIf { File(it).exists() }?.let { BitmapFactory.decodeFile(it) }

            when {
                bmpA != null && bmpB != null -> {
                    canvas.drawBitmap(bmpA, null, Rect(0, 0, targetSize / 2, targetSize), paint)
                    canvas.drawBitmap(bmpB, null, Rect(targetSize / 2, 0, targetSize, targetSize), paint)
                }
                bmpA != null -> canvas.drawBitmap(bmpA, null, Rect(0, 0, targetSize, targetSize), paint)
                bmpB != null -> canvas.drawBitmap(bmpB, null, Rect(0, 0, targetSize, targetSize), paint)
                else -> {
                    paint.color = android.graphics.Color.rgb(30, 27, 75)
                    canvas.drawRect(0f, 0f, targetSize.toFloat(), targetSize.toFloat(), paint)
                }
            }

            bmpA?.recycle()
            bmpB?.recycle()

            val outFile = storageManager.saveCoverAsWebp("autoremix_$timestamp", bitmap)
            bitmap.recycle()
            outFile
        } catch (_: Exception) {
            null
        }
    }

    internal suspend fun combineLyricsForRemix(
        storageManager: AppStorageManager,
        remixTrackId: Long,
        chapters: List<MixtapeChapter>
    ) {
        val builder = StringBuilder()
        builder.append("[ti:Aura Auto-Remix]\n[ar:Aura Smart Remix]\n\n")
        var hasAny = false

        for (chapter in chapters) {
            val raw = storageManager.getLyrics(chapter.trackId)
            builder.append("[${formatLrcTime(chapter.startMs)}] ⚡ ${chapter.title} • ${chapter.artist}\n")
            if (!raw.isNullOrBlank()) {
                hasAny = true
                for (line in raw.lines()) {
                    val trimmed = line.trim()
                    val matcher = LRC_TIMESTAMP_PATTERN.matcher(trimmed)
                    if (matcher.find()) {
                        val min = matcher.group(1)?.toLongOrNull() ?: 0L
                        val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                        val centisStr = matcher.group(3) ?: "00"
                        val centis = if (centisStr.length == 3) (centisStr.toLongOrNull() ?: 0L) / 10L else (centisStr.toLongOrNull() ?: 0L)
                        val text = matcher.group(4) ?: ""
                        val lineMs = (min * 60_000L) + (sec * 1000L) + (centis * 10L)
                        if (lineMs <= chapter.durationMs) {
                            builder.append("[${formatLrcTime(chapter.startMs + lineMs)}]$text\n")
                        }
                    }
                }
            }
        }
        if (hasAny) {
            storageManager.saveLyrics(remixTrackId, builder.toString())
        }
    }

    internal fun formatLrcTime(ms: Long): String {
        val totalSec = ms / 1000L
        val min = totalSec / 60L
        val sec = totalSec % 60L
        val centis = (ms % 1000L) / 10L
        return String.format(Locale.US, "%02d:%02d.%02d", min, sec, centis)
    }

    internal fun saveRemixMetadata(storageManager: AppStorageManager, metadata: MixtapeMetadata) {
        try {
            val file = File(storageManager.metadataDir, "mixtape_${metadata.mixtapeId}.json")
            file.writeText(metadata.toJsonObject().toString(2))
        } catch (_: Exception) {}
    }

}
