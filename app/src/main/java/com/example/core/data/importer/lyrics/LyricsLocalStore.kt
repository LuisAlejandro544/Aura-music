package com.example.core.data.importer

import com.example.core.data.storage.AppStorageManager
import com.example.core.model.LyricsState
import com.example.core.model.Track
import com.example.debug.AuraDebugManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal object LyricsLocalStore {
/**
 * Carga y guardado local en lyrics/track_{id}.lrc.
 * Rol arquitectónico: submódulo de LyricsManager (fachada pública intacta).
 */
    /**
     * Carga las letras almacenadas localmente para la pista en `lyrics/track_{id}.lrc`.
     */
    fun load(track: Track, storageManager: AppStorageManager): LyricsState? {
        val lyricFile = File(storageManager.lyricsDir, "track_${track.id}.lrc")
        if (!lyricFile.exists() || lyricFile.length() == 0L) return null

        return try {
            val text = lyricFile.readText()
            val parsedLines = LyricsTextParsing.parse(text)
            if (parsedLines.isNotEmpty()) {
                LyricsState(
                    trackId = track.id,
                    isSynced = true,
                    lines = parsedLines,
                    plainLyrics = text
                )
            } else {
                LyricsState(
                    trackId = track.id,
                    isSynced = false,
                    lines = emptyList(),
                    plainLyrics = text
                )
            }
        } catch (e: Throwable) {
            AuraDebugManager.logWarning("LyricsManager", "Error al leer letra local: ${e.message}")
            null
        }
    }

    /**
     * Guarda letras en formato .LRC en el almacenamiento local.
     */
    fun save(track: Track, storageManager: AppStorageManager, lrcContent: String): LyricsState {
        val lyricFile = File(storageManager.lyricsDir, "track_${track.id}.lrc")
        lyricFile.writeText(lrcContent)

        val parsedLines = LyricsTextParsing.parse(lrcContent)
        return LyricsState(
            trackId = track.id,
            isSynced = parsedLines.isNotEmpty(),
            lines = parsedLines,
            plainLyrics = lrcContent
        )
    }

    fun fromJson(track: Track, json: JSONObject, storageManager: AppStorageManager): LyricsState? {
        val synced = json.optString("syncedLyrics", "").trim()
        val plain = json.optString("plainLyrics", "").trim()

        if (synced.isNotBlank()) {
            return save(track, storageManager, synced)
        } else if (plain.isNotBlank()) {
            return save(track, storageManager, plain)
        }
        return null
    }
}
