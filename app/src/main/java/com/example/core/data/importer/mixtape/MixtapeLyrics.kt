package com.example.core.data.importer.mixtape

import com.example.core.data.storage.AppStorageManager
import com.example.debug.AuraDebugManager
import com.example.core.model.MixtapeChapter
import com.example.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Concatenación .LRC con offset temporal por capítulo.
 * Rol arquitectónico: pieza de MixtapeEngine (fachada pública intacta).
 */
internal object MixtapeLyrics {
    private const val TAG = "MixtapeEngine"

    private val LRC_TIMESTAMP_PATTERN = java.util.regex.Pattern.compile("^\\[(\\d{1,3}):(\\d{2})[.:](\\d{2,3})\\](.*)")

    suspend fun combineLyrics(
        storageManager: AppStorageManager,
        mixtapeTrackId: Long,
        tracks: List<Track>,
        chapters: List<MixtapeChapter>
    ) {
        val masterLyricsBuilder = StringBuilder()
        masterLyricsBuilder.append("[ti:Aura Continuous Mix]\n")
        masterLyricsBuilder.append("[ar:Varios Artistas]\n")
        masterLyricsBuilder.append("[by:Aura Music Mixtape Maker]\n\n")

        var hasAnyLyrics = false

        for ((index, chapter) in chapters.withIndex()) {
            val originalLyrics = storageManager.getLyrics(chapter.trackId)
            masterLyricsBuilder.append("[${lrcTime(chapter.startMs)}] --- ${chapter.title} • ${chapter.artist} ---\n")

            if (!originalLyrics.isNullOrBlank()) {
                hasAnyLyrics = true
                val lines = originalLyrics.lines()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isBlank() || trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") || trimmed.startsWith("[al:")) continue

                    val matcher = LRC_TIMESTAMP_PATTERN.matcher(trimmed)
                    if (matcher.find()) {
                        val min = matcher.group(1)?.toLongOrNull() ?: 0L
                        val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                        val centisStr = matcher.group(3) ?: "00"
                        val centis = if (centisStr.length == 3) (centisStr.toLongOrNull() ?: 0L) / 10L else (centisStr.toLongOrNull() ?: 0L)
                        val text = matcher.group(4) ?: ""

                        val lineMs = (min * 60_000L) + (sec * 1000L) + (centis * 10L)
                        val adjustedMs = chapter.startMs + lineMs
                        masterLyricsBuilder.append("[${lrcTime(adjustedMs)}]$text\n")
                    }
                }
            }
            masterLyricsBuilder.append("\n")
        }

        if (hasAnyLyrics) {
            storageManager.saveLyrics(mixtapeTrackId, masterLyricsBuilder.toString())
        }
    }

    private fun lrcTime(ms: Long): String {
        val totalSec = ms / 1000L
        val min = totalSec / 60L
        val sec = totalSec % 60L
        val centis = (ms % 1000L) / 10L
        return String.format(java.util.Locale.US, "%02d:%02d.%02d", min, sec, centis)
    }
}
