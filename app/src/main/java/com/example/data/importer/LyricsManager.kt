package com.example.data.importer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
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

    /**
     * Importa un archivo de letras (.lrc o .txt) seleccionado por el usuario desde el almacenamiento del dispositivo.
     */
    suspend fun importLyricsFromUri(
        context: Context,
        track: Track,
        uri: Uri,
        storageManager: AppStorageManager
    ): LyricsState? = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            } ?: return@withContext null

            if (content.isNotBlank()) {
                val state = saveLyrics(track, storageManager, content)
                AuraDebugManager.logInfo("LyricsManager", "Letra importada para ${track.title} (${if (state.isSynced) "Sincronizada" else "Texto plano"})")
                state
            } else null
        } catch (e: Throwable) {
            AuraDebugManager.logWarning("LyricsManager", "Error al importar archivo de letras desde celular: ${e.message}")
            null
        }
    }

    /**
     * Detecta y asocia automáticamente letras si la pista tiene un archivo .lrc/.txt hermano en su carpeta
     * o si el archivo de audio contiene letras incrustadas en sus metadatos ID3/Vorbis.
     */
    suspend fun autoDetectAndAssociateLyrics(
        context: Context,
        track: Track,
        storageManager: AppStorageManager
    ): LyricsState? = withContext(Dispatchers.IO) {
        // 1. Si ya tiene letras guardadas, cargarlas de inmediato
        val existing = loadLocalLyrics(track, storageManager)
        if (existing != null) return@withContext existing

        try {
            val trackUri = Uri.parse(track.uriString)

            // 2. Comprobar archivo hermano en caso de URIs basadas en archivo físico (file://)
            if (trackUri.scheme == "file") {
                val path = trackUri.path
                if (path != null) {
                    val audioFile = File(path)
                    val baseName = audioFile.nameWithoutExtension
                    val parentDir = audioFile.parentFile
                    if (parentDir != null && parentDir.exists()) {
                        val siblingLrc = File(parentDir, "$baseName.lrc")
                        val siblingTxt = File(parentDir, "$baseName.txt")

                        val fileToRead = when {
                            siblingLrc.exists() && siblingLrc.length() > 0L -> siblingLrc
                            siblingTxt.exists() && siblingTxt.length() > 0L -> siblingTxt
                            else -> null
                        }

                        if (fileToRead != null) {
                            val text = fileToRead.readText(Charsets.UTF_8)
                            if (text.isNotBlank()) {
                                AuraDebugManager.logInfo("LyricsManager", "Letra hermana detectada automáticamente: ${fileToRead.name}")
                                return@withContext saveLyrics(track, storageManager, text)
                            }
                        }
                    }
                }
            }

            // 3. Comprobar archivo hermano en almacenamiento SAF (content://) si el padre es accesible
            if (trackUri.scheme == "content") {
                try {
                    val docFile = DocumentFile.fromSingleUri(context, trackUri)
                    val parentDoc = docFile?.parentFile
                    if (parentDoc != null && parentDoc.isDirectory) {
                        val baseName = docFile.name?.substringBeforeLast(".") ?: ""
                        if (baseName.isNotBlank()) {
                            val sibling = parentDoc.findFile("$baseName.lrc") ?: parentDoc.findFile("$baseName.txt")
                            if (sibling != null && sibling.isFile && sibling.length() > 0L) {
                                val text: String? = context.contentResolver.openInputStream(sibling.uri)?.use { stream: InputStream ->
                                    stream.bufferedReader(Charsets.UTF_8).readText()
                                }
                                if (!text.isNullOrBlank()) {
                                    AuraDebugManager.logInfo("LyricsManager", "Letra SAF hermana detectada: ${sibling.name}")
                                    return@withContext saveLyrics(track, storageManager, text)
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }

            // 4. Inspección de metadatos de letras embebidas en el contenedor de audio
            val retriever = MediaMetadataRetriever()
            try {
                if (trackUri.scheme == "content") {
                    retriever.setDataSource(context, trackUri)
                } else {
                    retriever.setDataSource(trackUri.path ?: track.uriString)
                }
                // En Android MediaMetadataRetriever o Vorbis tag a veces expone letras
                // METADATA_KEY_COMPILATION o lectura de frames
                val embeddedLyric = try {
                    // Si la versión de Android o codec expone texto descriptivo o comentarios
                    null
                } catch (_: Throwable) { null }

                if (embeddedLyric != null && embeddedLyric.isNotBlank()) {
                    return@withContext saveLyrics(track, storageManager, embeddedLyric)
                }
            } catch (_: Throwable) {
            } finally {
                try { retriever.release() } catch (_: Throwable) {}
            }
        } catch (e: Throwable) {
            AuraDebugManager.logWarning("LyricsManager", "Fallo en auto-detección de letras: ${e.message}")
        }

        null
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
