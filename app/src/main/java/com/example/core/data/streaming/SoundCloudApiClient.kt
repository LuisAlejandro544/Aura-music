package com.example.core.data.streaming

import android.content.Context
import com.example.core.data.importer.OnlineVideoAudioImporter
import com.example.core.data.importer.YtDlpNativeEngine
import com.example.debug.AuraDebugManager
import com.example.core.model.StreamingSearchItem
import com.example.core.model.StreamingSourcePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Cliente modular de búsqueda y resolución de audio para SoundCloud en Aura Music.
 *
 * Rol arquitectónico: fachada pública intacta que delega en 3 piezas internas.
 */
object SoundCloudApiClient {

    suspend fun searchTracks(
        context: Context? = null,
        query: String,
        limit: Int = 20
    ): List<StreamingSearchItem> = SoundCloudSearch.search(context, query, limit)

    suspend fun fetchSimilarTracks(
        currentTitle: String,
        currentArtist: String,
        excludedIds: Set<String> = emptySet()
    ): List<StreamingSearchItem> = SoundCloudSearch.similar(currentTitle, currentArtist, excludedIds)

    suspend fun resolvePlayableStream(
        context: Context,
        item: StreamingSearchItem
    ): Result<OnlineVideoAudioImporter.ResolvedMediaInfo> =
        SoundCloudStreamResolver.resolve(context, item)
}
