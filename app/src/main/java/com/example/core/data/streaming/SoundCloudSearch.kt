package com.example.core.data.streaming

import android.content.Context
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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Búsqueda API v2, hidratación web y respaldo yt-dlp.
 * Rol arquitectónico: pieza de SoundCloudApiClient (fachada pública intacta).
 */
internal object SoundCloudSearch {
    private const val TAG = "SoundCloudApiClient"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    suspend fun search(
        context: Context? = null,
        query: String,
        limit: Int = 20
    ): List<StreamingSearchItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        // 1. Intentar búsqueda ultrarrápida mediante API v2 de SoundCloud
        val apiResults = viaApiV2(cleanQuery, limit)
        if (apiResults.isNotEmpty()) {
            return@withContext apiResults
        }

        // 2. Respaldo mediante hidratación HTML de soundcloud.com/search
        val webResults = viaWebHydration(cleanQuery, limit)
        if (webResults.isNotEmpty()) {
            return@withContext webResults
        }

        // 3. Respaldo mediante yt-dlp scsearch si el contexto está disponible
        if (context != null) {
            val ytdlpResults = viaYtDlp(context, cleanQuery, limit.coerceAtMost(12))
            if (ytdlpResults.isNotEmpty()) {
                return@withContext ytdlpResults
            }
        }

        emptyList()
    }

    suspend fun similar(
        currentTitle: String,
        currentArtist: String,
        excludedIds: Set<String> = emptySet()
    ): List<StreamingSearchItem> = withContext(Dispatchers.IO) {
        val query = if (currentArtist.isNotBlank() && !currentArtist.equals("SoundCloud", ignoreCase = true)) {
            "$currentArtist $currentTitle"
        } else {
            "$currentTitle remix"
        }
        viaApiV2(query, 12).filter { !excludedIds.contains(it.videoId) }
    }

    private suspend fun viaApiV2(query: String, limit: Int): List<StreamingSearchItem> {
        val clientId = SoundCloudHttp.clientId() ?: return emptyList()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val endpoint =
            "https://api-v2.soundcloud.com/search/tracks?q=$encodedQuery&client_id=$clientId&limit=$limit&linked_partitioning=1"

        var response = SoundCloudHttp.get(endpoint)
        if (response == null) {
            // Si el client_id expiró, forzar renovación
            SoundCloudHttp.invalidate()
            val refreshedId = SoundCloudHttp.clientId(forceRefresh = true) ?: return emptyList()
            val retryEndpoint =
                "https://api-v2.soundcloud.com/search/tracks?q=$encodedQuery&client_id=$refreshedId&limit=$limit&linked_partitioning=1"
            response = SoundCloudHttp.get(retryEndpoint) ?: return emptyList()
        }

        return try {
            val root = JSONObject(response)
            val collection = root.optJSONArray("collection") ?: return emptyList()
            parseTracks(collection)
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Error parseando respuesta SoundCloud API v2: ${e.message}")
            emptyList()
        }
    }

    private fun parseTracks(collection: JSONArray): List<StreamingSearchItem> {
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
            val hiResArt = SoundCloudHttp.hiResArtwork(rawArt)

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

    private fun viaWebHydration(query: String, limit: Int): List<StreamingSearchItem> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val html = SoundCloudHttp.get("https://soundcloud.com/search/sounds?q=$encoded") ?: return emptyList()
            val hydrationMatch = Regex("""window\.__sc_hydration\s*=\s*(\[\{.*?\}\]);""", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.groupValues?.getOrNull(1) ?: return emptyList()

            val arr = JSONArray(hydrationMatch)
            for (i in 0 until arr.length()) {
                val entry = arr.optJSONObject(i) ?: continue
                if (entry.optString("hydratable") == "search") {
                    val collection = entry.optJSONObject("data")?.optJSONArray("collection")
                    if (collection != null) {
                        return parseTracks(collection).take(limit)
                    }
                }
            }
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun viaYtDlp(
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
                        thumbnailUrl = SoundCloudHttp.hiResArtwork(thumb),
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
}
