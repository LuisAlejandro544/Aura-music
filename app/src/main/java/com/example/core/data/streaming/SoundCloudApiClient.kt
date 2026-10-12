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
 * Rol Arquitectónico:
 * 1. Hidratación automática y en memoria del `client_id` público de SoundCloud (`api-v2.soundcloud.com`)
 *    sin requerir claves `.env`, inicio de sesión ni configuración manual del usuario.
 * 2. Búsqueda rápida de pistas, remixes, sets de DJ y música independiente en SoundCloud (`☁️ SOUNDCLOUD`).
 * 3. Resolución de flujos progresivos HTTP (MP3 / Opus) listos para ExoPlayer, `StreamingCacheManager`
 *    y `AuraDownloadService`, con respaldo automático en `YtDlpNativeEngine` (`scsearch:` / URL directa).
 */
object SoundCloudApiClient {

    private const val TAG = "SoundCloudApiClient"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    @Volatile
    private var cachedClientId: String? = null

    @Volatile
    private var lastClientIdFetchMs: Long = 0L

    private val fallbackKnownClientIds = listOf(
        "iZIs9mchVcX5lhVRyQGGAYlNPVldzAoX",
        "a3e059563d7fd3372b49b37f00a00bcf",
        "2t9loNQH90kzJcsFCODdigxfp325aq4z"
    )

    /**
     * Busca canciones en el catálogo de SoundCloud retornando elementos listos para `StreamingExploreScreen`.
     */
    suspend fun searchTracks(
        context: Context? = null,
        query: String,
        limit: Int = 20
    ): List<StreamingSearchItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        // 1. Intentar búsqueda ultrarrápida mediante API v2 de SoundCloud
        val apiResults = searchViaApiV2(cleanQuery, limit)
        if (apiResults.isNotEmpty()) {
            return@withContext apiResults
        }

        // 2. Respaldo mediante hidratación HTML de soundcloud.com/search
        val webResults = searchViaWebHydration(cleanQuery, limit)
        if (webResults.isNotEmpty()) {
            return@withContext webResults
        }

        // 3. Respaldo mediante yt-dlp scsearch si el contexto está disponible
        if (context != null) {
            val ytdlpResults = searchViaYtDlpFallback(context, cleanQuery, limit.coerceAtMost(12))
            if (ytdlpResults.isNotEmpty()) {
                return@withContext ytdlpResults
            }
        }

        emptyList()
    }

    /**
     * Obtiene pistas similares / recomendadas en SoundCloud a partir de la canción actual.
     */
    suspend fun fetchSimilarTracks(
        currentTitle: String,
        currentArtist: String,
        excludedIds: Set<String> = emptySet()
    ): List<StreamingSearchItem> = withContext(Dispatchers.IO) {
        val query = if (currentArtist.isNotBlank() && !currentArtist.equals("SoundCloud", ignoreCase = true)) {
            "$currentArtist $currentTitle"
        } else {
            "$currentTitle remix"
        }
        searchViaApiV2(query, 12).filter { !excludedIds.contains(it.videoId) }
    }

    /**
     * Resuelve una pista de SoundCloud a un flujo HTTP directo (MP3/Opus) reproducible por ExoPlayer.
     * Utiliza primero la API v2 de transcodificación progresiva de SoundCloud (<350ms) y, como respaldo,
     * el motor nativo `YtDlpNativeEngine`.
     */
    suspend fun resolvePlayableStream(
        context: Context,
        item: StreamingSearchItem
    ): Result<OnlineVideoAudioImporter.ResolvedMediaInfo> = withContext(Dispatchers.IO) {
        val permalinkUrl = if (item.canonicalWatchUrl.startsWith("http", ignoreCase = true)) {
            item.canonicalWatchUrl
        } else if (item.videoId.startsWith("http", ignoreCase = true)) {
            item.videoId
        } else {
            "https://soundcloud.com/${item.videoId.removePrefix("sc_")}"
        }

        // 1. Intentar resolución rápida con API v2 (stream progressive MP3)
        try {
            val directResolved = resolveViaApiV2(permalinkUrl, item)
            if (directResolved != null && !directResolved.audioUrl.isNullOrBlank()) {
                return@withContext Result.success(directResolved)
            }
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Aviso en resolución directa API v2 SoundCloud: ${e.message}")
        }

        // 2. Respaldo con yt-dlp nativo (soporta SoundCloud nativamente)
        val ytdlpRes = YtDlpNativeEngine.resolveStream(context, permalinkUrl)
        val info = ytdlpRes.getOrNull()
        if (info != null && (!info.audioUrl.isNullOrBlank() || info.videoUrl.isNotBlank())) {
            val enriched = info.copy(
                suggestedTitle = item.title.ifBlank { info.suggestedTitle },
                suggestedArtist = item.artist.ifBlank { info.suggestedArtist },
                coverUrl = item.thumbnailUrl.ifBlank { info.coverUrl },
                durationSeconds = if (item.durationSeconds > 0L) item.durationSeconds else info.durationSeconds
            )
            return@withContext Result.success(enriched)
        }

        Result.failure(IllegalStateException("No se pudo resolver el stream de SoundCloud para \"${item.title}\"."))
    }

    private fun searchViaApiV2(query: String, limit: Int): List<StreamingSearchItem> {
        val clientId = getOrFetchClientId() ?: return emptyList()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val endpoint =
            "https://api-v2.soundcloud.com/search/tracks?q=$encodedQuery&client_id=$clientId&limit=$limit&linked_partitioning=1"

        var response = httpGet(endpoint)
        if (response == null) {
            // Si el client_id expiró, forzar renovación
            cachedClientId = null
            val refreshedId = getOrFetchClientId(forceRefresh = true) ?: return emptyList()
            val retryEndpoint =
                "https://api-v2.soundcloud.com/search/tracks?q=$encodedQuery&client_id=$refreshedId&limit=$limit&linked_partitioning=1"
            response = httpGet(retryEndpoint) ?: return emptyList()
        }

        return try {
            val root = JSONObject(response)
            val collection = root.optJSONArray("collection") ?: return emptyList()
            parseSoundCloudTrackArray(collection)
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Error parseando respuesta SoundCloud API v2: ${e.message}")
            emptyList()
        }
    }

    private fun resolveViaApiV2(
        permalinkUrl: String,
        item: StreamingSearchItem
    ): OnlineVideoAudioImporter.ResolvedMediaInfo? {
        val clientId = getOrFetchClientId() ?: return null
        val encodedUrl = URLEncoder.encode(permalinkUrl, "UTF-8")
        val resolveEndpoint = "https://api-v2.soundcloud.com/resolve?url=$encodedUrl&client_id=$clientId"
        val jsonStr = httpGet(resolveEndpoint) ?: return null
        val trackObj = JSONObject(jsonStr)

        val title = trackObj.optString("title", item.title).ifBlank { item.title }
        val userObj = trackObj.optJSONObject("user")
        val artist = userObj?.optString("username", item.artist)?.ifBlank { item.artist } ?: item.artist
        val durationMs = trackObj.optLong("full_duration", trackObj.optLong("duration", item.durationSeconds * 1000L))
        val artworkUrl = normalizeArtworkHiRes(
            trackObj.optString("artwork_url", "").ifBlank {
                userObj?.optString("avatar_url", "") ?: item.thumbnailUrl
            }
        )

        val transcodings = trackObj.optJSONObject("media")?.optJSONArray("transcodings") ?: return null
        var progressiveLocatorUrl: String? = null
        var fallbackLocatorUrl: String? = null

        for (i in 0 until transcodings.length()) {
            val t = transcodings.optJSONObject(i) ?: continue
            val url = t.optString("url", "")
            if (url.isBlank()) continue
            val protocol = t.optJSONObject("format")?.optString("protocol", "") ?: ""
            val mimeType = t.optJSONObject("format")?.optString("mime_type", "") ?: ""
            if (protocol.equals("progressive", ignoreCase = true)) {
                progressiveLocatorUrl = url
                if (mimeType.contains("mpeg", ignoreCase = true)) break
            } else if (fallbackLocatorUrl == null) {
                fallbackLocatorUrl = url
            }
        }

        val chosenLocator = progressiveLocatorUrl ?: fallbackLocatorUrl ?: return null
        val separator = if (chosenLocator.contains("?")) "&" else "?"
        val streamEndpoint = "${chosenLocator}${separator}client_id=$clientId"
        val streamJsonStr = httpGet(streamEndpoint) ?: return null
        val finalStreamUrl = JSONObject(streamJsonStr).optString("url", "")
        if (finalStreamUrl.isBlank()) return null

        return OnlineVideoAudioImporter.ResolvedMediaInfo(
            originalUrl = permalinkUrl,
            suggestedTitle = title,
            suggestedArtist = artist,
            videoUrl = finalStreamUrl,
            audioUrl = finalStreamUrl,
            coverUrl = artworkUrl.ifBlank { item.thumbnailUrl },
            durationSeconds = (durationMs / 1000L).coerceAtLeast(item.durationSeconds),
            httpHeaders = mapOf("User-Agent" to USER_AGENT)
        )
    }

    private fun parseSoundCloudTrackArray(collection: JSONArray): List<StreamingSearchItem> {
        val results = mutableListOf<StreamingSearchItem>()
        val seenUrls = mutableSetOf<String>()

        for (i in 0 until collection.length()) {
            val obj = collection.optJSONObject(i) ?: continue
            val kind = obj.optString("kind", "track")
            if (kind != "track") continue

            val permalinkUrl = obj.optString("permalink_url", "").trim()
            if (!permalinkUrl.startsWith("http", ignoreCase = true) || !seenUrls.add(permalinkUrl)) continue

            val title = obj.optString("title", "").trim()
            if (title.isBlank()) continue

            val userObj = obj.optJSONObject("user")
            val artist = userObj?.optString("username", "SoundCloud")?.trim().orEmpty().ifBlank { "SoundCloud" }
            val genre = obj.optString("genre", "").trim().ifBlank { "SoundCloud" }
            val durationMs = obj.optLong("full_duration", obj.optLong("duration", 0L))
            val rawArt = obj.optString("artwork_url", "").ifBlank {
                userObj?.optString("avatar_url", "").orEmpty()
            }
            val hiResArt = normalizeArtworkHiRes(rawArt)

            results.add(
                StreamingSearchItem(
                    videoId = permalinkUrl,
                    title = title,
                    artist = artist,
                    album = genre,
                    durationSeconds = (durationMs / 1000L).coerceAtLeast(0L),
                    thumbnailUrl = hiResArt,
                    platform = StreamingSourcePlatform.SOUNDCLOUD,
                    canonicalWatchUrl = permalinkUrl,
                    musicWatchUrl = permalinkUrl
                )
            )
        }
        return results
    }

    private fun searchViaWebHydration(query: String, limit: Int): List<StreamingSearchItem> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val html = httpGet("https://soundcloud.com/search/sounds?q=$encoded") ?: return emptyList()
            val hydrationMatch = Regex("""window\.__sc_hydration\s*=\s*(\[\{.*?\}\]);""", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.groupValues?.getOrNull(1) ?: return emptyList()

            val arr = JSONArray(hydrationMatch)
            for (i in 0 until arr.length()) {
                val entry = arr.optJSONObject(i) ?: continue
                if (entry.optString("hydratable") == "search") {
                    val collection = entry.optJSONObject("data")?.optJSONArray("collection")
                    if (collection != null) {
                        return parseSoundCloudTrackArray(collection).take(limit)
                    }
                }
            }
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun searchViaYtDlpFallback(
        context: Context,
        query: String,
        limit: Int
    ): List<StreamingSearchItem> {
        return try {
            YtDlpNativeEngine.init(context)
            val pythonBin = YtDlpNativeEngine.getPythonExecutable(context) ?: return emptyList()
            val ytdlpFile = com.example.core.data.importer.YtDlpAutoUpdater.getYtDlpFile(context)
            if (!ytdlpFile.exists()) return emptyList()

            val nativeDir = context.applicationInfo.nativeLibraryDir
            val pythonEnvDir = File(context.filesDir, "env/python")
            val directPythonZipSo = File(nativeDir, "libpython.zip.so")
            val modulesDir = File(pythonEnvDir, "modules")
            val localCert = File(pythonEnvDir, "usr/etc/tls/cert.pem")

            val cmd = listOf(
                pythonBin.absolutePath,
                ytdlpFile.absolutePath,
                "--flat-playlist",
                "--dump-single-json",
                "--no-warnings",
                "--",
                "scsearch$limit:$query"
            )
            val pb = ProcessBuilder(cmd)
                .directory(File(nativeDir))
                .redirectErrorStream(false)
            pb.environment().apply {
                this["PYTHONHOME"] = pythonEnvDir.absolutePath
                this["PYTHONPATH"] = "${directPythonZipSo.absolutePath}:${modulesDir.absolutePath}:${pythonEnvDir.absolutePath}"
                this["LD_LIBRARY_PATH"] = "$nativeDir:${modulesDir.absolutePath}"
                if (localCert.exists()) {
                    this["SSL_CERT_FILE"] = localCert.absolutePath
                }
            }
            val process = pb.start()
            val out = StringBuilder()
            val readerThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { r ->
                        var line: String?
                        while (r.readLine().also { line = it } != null) {
                            out.append(line)
                        }
                    }
                } catch (_: Exception) {}
            }
            readerThread.start()
            process.waitFor(10, TimeUnit.SECONDS)
            readerThread.join(2000)

            if (out.isBlank()) return emptyList()
            val root = JSONObject(out.toString())
            val entries = root.optJSONArray("entries") ?: return emptyList()
            val items = mutableListOf<StreamingSearchItem>()
            for (i in 0 until entries.length()) {
                val e = entries.optJSONObject(i) ?: continue
                val url = e.optString("url", e.optString("webpage_url", "")).trim()
                val title = e.optString("title", "").trim()
                if (url.isBlank() || title.isBlank()) continue
                val uploader = e.optString("uploader", "SoundCloud").trim()
                val duration = e.optLong("duration", 0L)
                val thumb = e.optString("thumbnail", "")
                items.add(
                    StreamingSearchItem(
                        videoId = url,
                        title = title,
                        artist = uploader,
                        album = "SoundCloud",
                        durationSeconds = duration,
                        thumbnailUrl = normalizeArtworkHiRes(thumb),
                        platform = StreamingSourcePlatform.SOUNDCLOUD,
                        canonicalWatchUrl = url,
                        musicWatchUrl = url
                    )
                )
            }
            items
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun getOrFetchClientId(forceRefresh: Boolean = false): String? {
        val now = System.currentTimeMillis()
        if (!forceRefresh && !cachedClientId.isNullOrBlank() && (now - lastClientIdFetchMs) < 3_600_000L) {
            return cachedClientId
        }

        try {
            val mainHtml = httpGet("https://soundcloud.com")
            if (!mainHtml.isNullOrBlank()) {
                val scriptUrls = Regex("""https://a-v2\.sndcdn\.com/assets/[^\s"']+\.js""")
                    .findAll(mainHtml)
                    .map { it.value }
                    .toList()
                    .takeLast(4)
                    .reversed()

                val clientIdRegex = Regex("""client_id\s*[:=]\s*["']([a-zA-Z0-9]{32})["']""")
                for (jsUrl in scriptUrls) {
                    val jsContent = httpGet(jsUrl) ?: continue
                    val match = clientIdRegex.find(jsContent)
                    if (match != null) {
                        val extracted = match.groupValues[1]
                        cachedClientId = extracted
                        lastClientIdFetchMs = now
                        return extracted
                    }
                }
            }
        } catch (_: Exception) {}

        // Verificar alguno de los client_id públicos de respaldo
        for (candidate in fallbackKnownClientIds) {
            val testUrl = "https://api-v2.soundcloud.com/search/tracks?q=music&client_id=$candidate&limit=1"
            if (httpGet(testUrl) != null) {
                cachedClientId = candidate
                lastClientIdFetchMs = now
                return candidate
            }
        }
        return cachedClientId ?: fallbackKnownClientIds.firstOrNull()
    }

    private fun normalizeArtworkHiRes(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        return rawUrl
            .replace("-large.jpg", "-t500x500.jpg")
            .replace("-large.png", "-t500x500.png")
    }

    private fun httpGet(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 8000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json, text/html, */*")
                setRequestProperty("Origin", "https://soundcloud.com")
                setRequestProperty("Referer", "https://soundcloud.com/")
            }
            if (conn.responseCode !in 200..299) return null
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
