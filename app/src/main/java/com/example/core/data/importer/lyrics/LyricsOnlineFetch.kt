package com.example.core.data.importer

import com.example.core.data.storage.AppStorageManager
import com.example.core.model.LyricsState
import com.example.core.model.Track
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

internal object LyricsOnlineFetch {
/**
 * Descarga automática desde LRCLIB: intento directo + búsqueda flexible.
 * Rol arquitectónico: submódulo de LyricsManager (fachada pública intacta).
 */
    /**
     * Descarga letras automáticamente desde la base de datos libre LRCLIB.
     */
    suspend fun fetch(
        track: Track,
        storageManager: AppStorageManager
    ): Result<LyricsState> = withContext(Dispatchers.IO) {
        val cleanTitle = LyricsTextParsing.cleanSearchTerm(track.title)
        val cleanArtist = LyricsTextParsing.cleanSearchTerm(track.artist)
        val durationSec = (track.durationMs / 1000L).coerceAtLeast(0L)

        // Intento 1: Consulta directa por metadatos
        val directResult = fetchDirect(track, cleanTitle, cleanArtist, durationSec, storageManager)
        if (directResult != null) {
            return@withContext Result.success(directResult)
        }

        // Intento 2: Búsqueda flexible por texto
        val searchResult = searchFallback(track, "$cleanTitle $cleanArtist", storageManager)
        if (searchResult != null) {
            return@withContext Result.success(searchResult)
        }

        Result.failure(Exception("No se encontraron letras sincronizadas en línea para esta canción."))
    }

    private fun fetchDirect(
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
                .header("User-Agent", LyricsHttp.APP_USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .build()

            LyricsHttp.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val json = JSONObject(body)
                    return LyricsLocalStore.fromJson(track, json, storageManager)
                }
            }
        } catch (t: Throwable) {
            AuraDebugManager.logWarning("LyricsManager", "Fallo al consultar LRCLIB get: ${t.message}")
        }
        return null
    }

    private fun searchFallback(
        track: Track,
        query: String,
        storageManager: AppStorageManager
    ): LyricsState? {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://lrclib.net/api/search?q=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", LyricsHttp.APP_USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .build()

            LyricsHttp.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val array = JSONArray(body)
                    if (array.length() > 0) {
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            val synced = item.optString("syncedLyrics", "")
                            if (synced.isNotBlank()) {
                                return LyricsLocalStore.fromJson(track, item, storageManager)
                            }
                        }
                        return LyricsLocalStore.fromJson(track, array.getJSONObject(0), storageManager)
                    }
                }
            }
        } catch (t: Throwable) {
            AuraDebugManager.logWarning("LyricsManager", "Fallo al buscar en LRCLIB: ${t.message}")
        }
        return null
    }
}
