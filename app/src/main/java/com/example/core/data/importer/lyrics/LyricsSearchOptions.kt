package com.example.core.data.importer

import com.example.core.model.LyricSearchResult
import com.example.core.model.LyricsState
import com.example.core.model.Track
import com.example.core.data.storage.AppStorageManager
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

internal object LyricsSearchOptions {
/**
 * Búsqueda multidimensional de opciones y recomendación oficial canónica.
 * Rol arquitectónico: submódulo de LyricsManager (fachada pública intacta).
 */
    suspend fun search(
        trackTitle: String,
        artistName: String = "",
        durationSec: Long = 0L
    ): List<LyricSearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<LyricSearchResult>()
        val cleanTitle = LyricsTextParsing.cleanSearchTerm(trackTitle)
        val cleanArtist = LyricsTextParsing.cleanSearchTerm(artistName)

        if (cleanTitle.isBlank()) return@withContext emptyList()

        val trackSearchResults = byTrackAndArtist(cleanTitle, cleanArtist)
        for (item in trackSearchResults) {
            if (results.none { it.id == item.id }) {
                results.add(item)
            }
        }

        val query = if (cleanArtist.isNotBlank()) "$cleanTitle $cleanArtist" else cleanTitle
        val broadResults = broadSearch(query)
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

        val officialResult = fetchOfficial(cleanTitle, cleanArtist, durationSec)
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

    fun apply(
        track: Track,
        storageManager: AppStorageManager,
        result: LyricSearchResult
    ): LyricsState {
        val contentToSave = if (result.syncedLyrics.isNotBlank()) {
            result.syncedLyrics
        } else {
            result.plainLyrics
        }
        return LyricsLocalStore.save(track, storageManager, contentToSave)
    }

    private fun byTrackAndArtist(title: String, artist: String): List<LyricSearchResult> {
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
                .header("User-Agent", LyricsHttp.APP_USER_AGENT)
                .build()

            LyricsHttp.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return emptyList()
                    val array = JSONArray(body)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val parsed = parseResult(item, isOfficial = (i == 0))
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

    private fun fetchOfficial(title: String, artist: String, durationSec: Long): LyricSearchResult? {
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
                .header("User-Agent", LyricsHttp.APP_USER_AGENT)
                .build()

            LyricsHttp.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val json = JSONObject(body)
                    return parseResult(json, isOfficial = true)
                }
            }
        } catch (t: Throwable) {
            AuraDebugManager.logWarning("LyricsManager", "Fallo al consultar LRCLIB oficial: ${t.message}")
        }
        return null
    }

    private fun broadSearch(query: String): List<LyricSearchResult> {
        val list = mutableListOf<LyricSearchResult>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://lrclib.net/api/search?q=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", LyricsHttp.APP_USER_AGENT)
                .build()

            LyricsHttp.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return emptyList()
                    val array = JSONArray(body)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val parsed = parseResult(item, isOfficial = false)
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

    private fun parseResult(json: JSONObject, isOfficial: Boolean): LyricSearchResult? {
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
}
