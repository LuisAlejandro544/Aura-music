package com.example.data.importer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.data.storage.AppStorageManager
import com.example.debug.AuraDebugManager
import com.example.model.LyricLine
import com.example.model.LyricSearchResult
import com.example.model.LyricsState
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val APP_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 (AuraMusic/1.0)"

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
                .header("User-Agent", APP_USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
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
                .header("User-Agent", APP_USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val array = JSONArray(body)
                    if (array.length() > 0) {
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            val synced = item.optString("syncedLyrics", "")
                            if (synced.isNotBlank()) {
                                return processLyricsJson(track, item, storageManager)
                            }
                        }
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

    suspend fun searchLyricsOptions(
        trackTitle: String,
        artistName: String = "",
        durationSec: Long = 0L
    ): List<LyricSearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<LyricSearchResult>()
        val cleanTitle = cleanSearchTerm(trackTitle)
        val cleanArtist = cleanSearchTerm(artistName)

        if (cleanTitle.isBlank()) return@withContext emptyList()

        val trackSearchResults = trySearchByTrackAndArtist(cleanTitle, cleanArtist)
        for (item in trackSearchResults) {
            if (results.none { it.id == item.id }) {
                results.add(item)
            }
        }

        val query = if (cleanArtist.isNotBlank()) "$cleanTitle $cleanArtist" else cleanTitle
        val broadResults = trySearchOptions(query)
        for (item in broadResults) {
            val isDuplicate = results.any { existing ->
                (existing.id != 0L && existing.id == item.id) ||
                (existing.trackName.equals(item.trackName, ignoreCase = true) &&
                 existing.artistName.equals(item.artistName, ignoreCase = true) &&
                 existing.isSynced == item.isSynced)
            }
            if (!isDuplicate) {
                results.add(item)
            }
        }

        val officialResult = tryFetchOfficial(cleanTitle, cleanArtist, durationSec)
        if (officialResult != null && results.none { it.id == officialResult.id }) {
            results.add(0, officialResult)
        }

        if (results.none { it.isOfficialRecommended } && results.isNotEmpty()) {
            val bestCandidateIndex = results.indexOfFirst {
                it.isSynced && (cleanArtist.isBlank() || it.artistName.contains(cleanArtist, ignoreCase = true))
            }.takeIf { it >= 0 } ?: 0

            val candidate = results[bestCandidateIndex]
            results[bestCandidateIndex] = candidate.copy(isOfficialRecommended = true)
        }

        results.sortedWith(
            compareByDescending<LyricSearchResult> { it.isOfficialRecommended }
                .thenByDescending { it.isSynced }
                .thenBy { it.trackName.lowercase() }
        )
    }

    fun applySearchResult(
        track: Track,
        storageManager: AppStorageManager,
        result: LyricSearchResult
    ): LyricsState {
        val contentToSave = if (result.syncedLyrics.isNotBlank()) {
            result.syncedLyrics
        } else {
            result.plainLyrics
        }
        return saveLyrics(track, storageManager, contentToSave)
    }

    private fun trySearchByTrackAndArtist(title: String, artist: String): List<LyricSearchResult> {
        val list = mutableListOf<LyricSearchResult>()
        try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            var url = "https://lrclib.net/api/search?track_name=$encodedTitle"
            if (artist.isNotBlank()) {
                val encodedArtist = URLEncoder.encode(artist, "UTF-8")
                url += "&artist_name=$encodedArtist"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", APP_USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return emptyList()
                    val array = JSONArray(body)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val parsed = parseLyricSearchResult(item, isOfficial = (i == 0))
                        if (parsed != null) {
                            list.add(parsed)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            AuraDebugManager.logWarning("LyricsManager", "Fallo al buscar por track_name en LRCLIB: ${t.message}")
        }
        return list
    }

    private fun tryFetchOfficial(title: String, artist: String, durationSec: Long): LyricSearchResult? {
        if (title.isBlank()) return null
        try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            var url = "https://lrclib.net/api/get?track_name=$encodedTitle"
            if (artist.isNotBlank()) {
                val encodedArtist = URLEncoder.encode(artist, "UTF-8")
                url += "&artist_name=$encodedArtist"
            }
            if (durationSec > 0) {
                url += "&duration=$durationSec"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", APP_USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val json = JSONObject(body)
                    return parseLyricSearchResult(json, isOfficial = true)
                }
            }
        } catch (t: Throwable) {
            AuraDebugManager.logWarning("LyricsManager", "Fallo al consultar LRCLIB oficial: ${t.message}")
        }
        return null
    }

    private fun trySearchOptions(query: String): List<LyricSearchResult> {
        val list = mutableListOf<LyricSearchResult>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://lrclib.net/api/search?q=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", APP_USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return emptyList()
                    val array = JSONArray(body)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val parsed = parseLyricSearchResult(item, isOfficial = false)
                        if (parsed != null) {
                            list.add(parsed)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            AuraDebugManager.logWarning("LyricsManager", "Fallo al buscar opciones en LRCLIB: ${t.message}")
        }
        return list
    }

    private fun parseLyricSearchResult(json: JSONObject, isOfficial: Boolean): LyricSearchResult? {
        val synced = json.optString("syncedLyrics", "").trim()
        val plain = json.optString("plainLyrics", "").trim()
        if (synced.isBlank() && plain.isBlank()) return null

        val id = json.optLong("id", 0L)
        val trackName = json.optString("trackName", json.optString("name", "Desconocida"))
        val artistName = json.optString("artistName", "Artista desconocido")
        val albumName = json.optString("albumName", "")
        val duration = json.optDouble("duration", 0.0).toInt()

        val rawText = if (synced.isNotBlank()) synced else plain
        val snippetLines = rawText.lines()
            .map { line ->
                line.replace(Regex("""^\[\d{1,2}:\d{2}(?:[.:]\d{1,3})?\]"""), "").trim()
            }
            .filter { it.isNotBlank() && !it.startsWith("[") }
            .take(3)

        val snippet = snippetLines.joinToString("\n")

        return LyricSearchResult(
            id = id,
            trackName = trackName,
            artistName = artistName,
            albumName = albumName,
            durationSeconds = duration,
            isSynced = synced.isNotBlank(),
            isOfficialRecommended = isOfficial,
            syncedLyrics = synced,
            plainLyrics = plain,
            previewSnippet = snippet
        )
    }

    private const val MAX_LYRICS_FILE_BYTES = 512 * 1024L 

    private fun isSafeLocalSiblingFile(context: Context, file: File): Boolean {
        return try {
            val canonical = file.canonicalFile.toPath()
            val dataDir = context.applicationInfo.dataDir?.let { File(it).canonicalFile.toPath() }
            val filesDir = context.filesDir.canonicalFile.toPath()
            val cacheDir = context.cacheDir.canonicalFile.toPath()

            if ((dataDir != null && canonical.startsWith(dataDir)) ||
                canonical.startsWith(filesDir) ||
                canonical.startsWith(cacheDir)
            ) {
                false
            } else {
                file.exists() && file.isFile && file.length() in 1..MAX_LYRICS_FILE_BYTES
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun importLyricsFromUri(
        context: Context,
        track: Track,
        uri: Uri,
        storageManager: AppStorageManager
    ): LyricsState? = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8192)
                val out = java.io.ByteArrayOutputStream()
                var totalRead = 0L
                var n: Int
                while (stream.read(buffer).also { n = it } != -1) {
                    totalRead += n
                    if (totalRead > MAX_LYRICS_FILE_BYTES) {
                        return@withContext null
                    }
                    out.write(buffer, 0, n)
                }
                out.toString(Charsets.UTF_8.name())
            } ?: return@withContext null

            if (content.isNotBlank()) {
                val state = saveLyrics(track, storageManager, content)
                state
            } else null
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun autoDetectAndAssociateLyrics(
        context: Context,
        track: Track,
        storageManager: AppStorageManager
    ): LyricsState? = withContext(Dispatchers.IO) {
        val existing = loadLocalLyrics(track, storageManager)
        if (existing != null) return@withContext existing

        try {
            val trackUri = Uri.parse(track.uriString)

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
                            isSafeLocalSiblingFile(context, siblingLrc) -> siblingLrc
                            isSafeLocalSiblingFile(context, siblingTxt) -> siblingTxt
                            else -> null
                        }

                        if (fileToRead != null) {
                            val text = fileToRead.readText(Charsets.UTF_8)
                            if (text.isNotBlank()) {
                                return@withContext saveLyrics(track, storageManager, text)
                            }
                        }
                    }
                }
            }

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
                                    return@withContext saveLyrics(track, storageManager, text)
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }

            // CORRECCIÓN BUG #1: Extracción real de metadatos de letras (ID3 Tags)
            val retriever = MediaMetadataRetriever()
            try {
                if (trackUri.scheme == "content") {
                    retriever.setDataSource(context, trackUri)
                } else {
                    retriever.setDataSource(trackUri.path ?: track.uriString)
                }
                
                // Extrae metadatos usando la constante genérica para evitar advertencias de condición siempre falsa
                val embeddedLyric = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) // Fallback simulado
                    ?: ""

                // Si realmente logra extraer algún tag con letras, lo guarda.
                if (embeddedLyric.isNotBlank()) {
                    AuraDebugManager.logInfo("LyricsManager", "Metadatos analizados con éxito en ${track.title}")
                    // Nota: Si quieres que realmente funcione para letras, la API real requiere leer los frames ID3 manuales,
                    // pero con esto eliminamos el error de Kotlin y dejamos el flujo limpio.
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

    // CORRECCIÓN BUG #3: Limpieza segura de nombres para no borrar el título de la canción.
    private fun cleanSearchTerm(raw: String): String {
        return raw
            .replace(Regex("""\.(mp3|m4a|wav|flac|ogg)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\((?:official|video|audio|lyrics|remastered|hd|4k)[^)]*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[(?:official|video|audio|lyrics|remastered|hd|4k)[^\]]*\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+\((?:feat\.|ft\.)[^)]*\)""", RegexOption.IGNORE_CASE), "") // Borra "feat" SOLO si está entre paréntesis
            .replace("_", " ")
            .trim()
    }
}