package com.example.data.importer

import com.example.data.storage.AppStorageManager
import com.example.debug.AuraDebugManager
import com.example.model.LyricLine
import com.example.model.LyricsState
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Gestor y analizador de Letras Sincronizadas (.LRC) estilo Karaoke.
 *
 * Responsabilidades:
 * 1. Parser de archivos y formatos estándar .LRC con marcas de tiempo [mm:ss.xx].
 * 2. Carga y persistencia local en la carpeta estructurada `lyrics/` de Aura Music.
 * 3. Descarga automática 100% gratuita y sin API key desde el servicio público LRCLIB.
 * 4. Edición y guardado de letras manuales por el usuario.
 */
object LyricsManager {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val LRC_LINE_PATTERN = Pattern.compile(
        """\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\](.*)"""
    )

    /**
     * Parsea un texto con formato .LRC y retorna las líneas ordenadas cronológicamente.
     */
    fun parseLrc(content: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val rawLines = content.lines()

        for (raw in rawLines) {
            val trimmed = raw.trim()
            if (trimmed.isBlank() || trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") ||
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:") || trimmed.startsWith("[length:")
            ) {
                continue
            }

            val matcher = LRC_LINE_PATTERN.matcher(trimmed)
            if (matcher.find()) {
                val minStr = matcher.group(1) ?: "0"
                val secStr = matcher.group(2) ?: "0"
                val msStr = matcher.group(3) ?: "0"
                val text = matcher.group(4)?.trim() ?: ""

                val minutes = minStr.toLongOrNull() ?: 0L
                val seconds = secStr.toLongOrNull() ?: 0L
                val millis = when (msStr.length) {
                    1 -> (msStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (msStr.toLongOrNull() ?: 0L) * 10L
                    else -> msStr.take(3).toLongOrNull() ?: 0L
                }

                val totalMs = (minutes * 60L * 1000L) + (seconds * 1000L) + millis
                lines.add(LyricLine(timeMs = totalMs, text = text))
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    /**
     * Carga las letras almacenadas localmente para la pista en `lyrics/track_{id}.lrc`.
     */
    fun loadLocalLyrics(track: Track, storageManager: AppStorageManager): LyricsState? {
        val lyricFile = File(storageManager.lyricsDir, "track_${track.id}.lrc")
        if (!lyricFile.exists() || lyricFile.length() == 0L) return null

        return try {
            val text = lyricFile.readText()
            val parsedLines = parseLrc(text)
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
    fun saveLyrics(track: Track, storageManager: AppStorageManager, lrcContent: String): LyricsState {
        val lyricFile = File(storageManager.lyricsDir, "track_${track.id}.lrc")
        lyricFile.writeText(lrcContent)

        val parsedLines = parseLrc(lrcContent)
        return LyricsState(
            trackId = track.id,
            isSynced = parsedLines.isNotEmpty(),
            lines = parsedLines,
            plainLyrics = lrcContent
        )
    }

    /**
     * Descarga letras automáticamente desde la base de datos libre LRCLIB.
     */
    suspend fun fetchLyricsOnline(
        track: Track,
        storageManager: AppStorageManager
    ): Result<LyricsState> = withContext(Dispatchers.IO) {
        val cleanTitle = cleanSearchTerm(track.title)
        val cleanArtist = cleanSearchTerm(track.artist)
        val durationSec = (track.durationMs / 1000L).coerceAtLeast(0L)

        // Intento 1: Consulta directa por metadatos
        val directResult = tryFetchDirect(track, cleanTitle, cleanArtist, durationSec, storageManager)
        if (directResult != null) {
            return@withContext Result.success(directResult)
        }

        // Intento 2: Búsqueda flexible por texto
        val searchResult = trySearchLyrics(track, "$cleanTitle $cleanArtist", storageManager)
        if (searchResult != null) {
            return@withContext Result.success(searchResult)
        }

        Result.failure(Exception("No se encontraron letras sincronizadas en línea para esta canción."))
    }

    private fun tryFetchDirect(
        track: Track,
        title: String,
        artist: String,
        durationSec: Long,
        storageManager: AppStorageManager
    ): LyricsState? {
        try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            var url = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist"
            if (durationSec > 0) {
                url += "&duration=$durationSec"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AuraMusic/1.0 (Android; Offline HiFi Player)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val json = JSONObject(body)
                    return processLyricsJson(track, json, storageManager)
                }
            }
        } catch (t: Throwable) {
            AuraDebugManager.logWarning("LyricsManager", "Fallo al consultar LRCLIB get: ${t.message}")
        }
        return null
    }

    private fun trySearchLyrics(
        track: Track,
        query: String,
        storageManager: AppStorageManager
    ): LyricsState? {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://lrclib.net/api/search?q=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AuraMusic/1.0 (Android; Offline HiFi Player)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val array = JSONArray(body)
                    if (array.length() > 0) {
                        // Buscar la primera coincidencia que tenga letras sincronizadas
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            val synced = item.optString("syncedLyrics", "")
                            if (synced.isNotBlank()) {
                                return processLyricsJson(track, item, storageManager)
                            }
                        }
                        // Si ninguna tenía sincronizada, tomar el primer resultado
                        return processLyricsJson(track, array.getJSONObject(0), storageManager)
                    }
                }
            }
        } catch (t: Throwable) {
            AuraDebugManager.logWarning("LyricsManager", "Fallo al buscar en LRCLIB: ${t.message}")
        }
        return null
    }

    private fun processLyricsJson(track: Track, json: JSONObject, storageManager: AppStorageManager): LyricsState? {
        val synced = json.optString("syncedLyrics", "").trim()
        val plain = json.optString("plainLyrics", "").trim()

        if (synced.isNotBlank()) {
            return saveLyrics(track, storageManager, synced)
        } else if (plain.isNotBlank()) {
            return saveLyrics(track, storageManager, plain)
        }
        return null
    }

    private fun cleanSearchTerm(raw: String): String {
        return raw
            .replace(Regex("""\.(mp3|m4a|wav|flac|ogg)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\((?:official|video|audio|lyrics|remastered|hd|4k)[^)]*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[(?:official|video|audio|lyrics|remastered|hd|4k)[^\]]*\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""(?i)\b(feat\.|ft\.).*"""), "")
            .replace("_", " ")
            .trim()
    }
}
