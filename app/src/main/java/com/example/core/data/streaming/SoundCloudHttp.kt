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
 * client_id en memoria, HTTP GET y carátulas Hi-Res.
 * Rol arquitectónico: pieza de SoundCloudApiClient (fachada pública intacta).
 */
internal object SoundCloudHttp {
    private const val TAG = "SoundCloudApiClient"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    @Volatile
    private var clientIdCache: String? = null

    @Volatile
    private var lastFetchMs: Long = 0L

    private val fallbackIds = listOf(
        "iZIs9mchVcX5lhVRyQGGAYlNPVldzAoX",
        "a3e059563d7fd3372b49b37f00a00bcf",
        "2t9loNQH90kzJcsFCODdigxfp325aq4z"
    )

    @Volatile
    private var lastFailMs: Long = 0L

    fun invalidate() { clientIdCache = null }

    fun clientId(forceRefresh: Boolean = false): String? {
        val now = System.currentTimeMillis()
        if (!forceRefresh && !clientIdCache.isNullOrBlank() && (now - lastFetchMs) < 3_600_000L) {
            return clientIdCache
        }
        // Caché negativa: si falló hace menos de 5 min, no golpear la red otra vez.
        if (!forceRefresh && (now - lastFailMs) < 300_000L) {
            return clientIdCache ?: fallbackIds.firstOrNull()
        }

        try {
            val mainHtml = get("https://soundcloud.com")
            if (!mainHtml.isNullOrBlank()) {
                val scriptUrls = Regex("""https://a-v2\.sndcdn\.com/assets/[^\s"']+\.js""")
                    .findAll(mainHtml)
                    .map { it.value }
                    .toList()
                    .takeLast(4)
                    .reversed()

                val clientIdRegex = Regex("""client_id\s*[:=]\s*["']([a-zA-Z0-9]{32})["']""")
                for (jsUrl in scriptUrls) {
                    val jsContent = get(jsUrl) ?: continue
                    val match = clientIdRegex.find(jsContent)
                    if (match != null) {
                        val extracted = match.groupValues[1]
                        clientIdCache = extracted
                        lastFetchMs = now
                        return extracted
                    }
                }
            }
        } catch (_: Exception) {}

        // Verificar alguno de los client_id públicos de respaldo
        for (candidate in fallbackIds) {
            val testUrl = "https://api-v2.soundcloud.com/search/tracks?q=music&client_id=$candidate&limit=1"
            if (get(testUrl) != null) {
                clientIdCache = candidate
                lastFetchMs = now
                return candidate
            }
        }
        lastFailMs = now
        return clientIdCache ?: fallbackIds.firstOrNull()
    }


    fun hiResArtwork(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        return rawUrl
            .replace("-large.jpg", "-t500x500.jpg")
            .replace("-large.png", "-t500x500.png")
    }


    fun get(urlString: String): String? {
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
