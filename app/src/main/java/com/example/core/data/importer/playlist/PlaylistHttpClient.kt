package com.example.core.data.importer.playlist

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cliente HTTP y utilidades JSON para importar playlists externas.
 * Rol arquitectónico: capa de red pura usada por SpotifyPlaylistExtractor y YouTubePlaylistExtractor.
 */
internal object PlaylistHttpClient {

    internal const val USER_AGENT_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    internal fun unescapeJsonText(raw: String): String {
        return try {
            JSONObject("{\"v\":\"$raw\"}").optString("v", raw)
        } catch (_: Exception) {
            raw.replace("\\\"", "\"").replace("\\\\", "\\")
        }
    }
    internal fun unescapeJsEvaluation(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        return try {
            JSONArray("[$raw]").optString(0, "")
        } catch (_: Exception) {
            raw.trim('"')
        }
    }
    internal fun resolveRedirectUrl(shortUrl: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(shortUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", USER_AGENT_DESKTOP)
            }
            conn.connect()
            conn.url?.toString()
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
    internal fun httpGetText(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 9000
                readTimeout = 10000
                setRequestProperty("User-Agent", USER_AGENT_DESKTOP)
                setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            }
            if (conn.responseCode !in 200..299) return null
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
    internal fun postJson(
        endpoint: String,
        body: String,
        headers: Map<String, String>
    ): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 9000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
                writer.flush()
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
