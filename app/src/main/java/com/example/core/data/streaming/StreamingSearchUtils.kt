package com.example.core.data.streaming

import com.example.core.model.StreamingSearchItem
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Utilidades de búsqueda y JSON para el Modo Streaming.
 * Rol arquitectónico: capa pura de parseo y red usada por los motores de búsqueda.
 */
internal object StreamingSearchUtils {

    internal const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    internal fun interleaveResults(
        musicList: List<StreamingSearchItem>,
        videoList: List<StreamingSearchItem>,
        soundCloudList: List<StreamingSearchItem> = emptyList()
    ): List<StreamingSearchItem> {
        val combined = mutableListOf<StreamingSearchItem>()
        val seenIds = mutableSetOf<String>()
        val maxLen = maxOf(musicList.size, videoList.size, soundCloudList.size)
        for (i in 0 until maxLen) {
            if (i < musicList.size) {
                val m = musicList[i]
                if (seenIds.add(m.videoId)) combined.add(m)
            }
            if (i < videoList.size) {
                val v = videoList[i]
                if (seenIds.add(v.videoId)) combined.add(v)
            }
            if (i < soundCloudList.size) {
                val sc = soundCloudList[i]
                if (seenIds.add(sc.videoId)) combined.add(sc)
            }
        }
        return combined
    }

    internal fun collectJsonObjectsByKey(node: Any?, targetKey: String, out: MutableList<JSONObject>) {
        if (out.size >= 45) return
        when (node) {
            is JSONObject -> {
                if (node.has(targetKey)) {
                    val child = node.optJSONObject(targetKey)
                    if (child != null) out.add(child)
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    collectJsonObjectsByKey(node.opt(k), targetKey, out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectJsonObjectsByKey(node.opt(i), targetKey, out)
                }
            }
        }
    }

    internal fun extractTextFromRuns(textObj: JSONObject?): String {
        if (textObj == null) return ""
        val simple = textObj.optString("simpleText", "")
        if (simple.isNotBlank()) return simple
        val runs = textObj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text", "") ?: "")
        }
        return sb.toString().trim()
    }

    internal fun extractBestThumbnail(thumbnailObj: JSONObject?): String {
        val arr = thumbnailObj?.optJSONArray("thumbnails") ?: return ""
        if (arr.length() == 0) return ""
        return arr.optJSONObject(arr.length() - 1)?.optString("url", "") ?: ""
    }

    internal fun parseDurationTextToSeconds(text: String): Long {
        val clean = text.trim()
        if (!clean.contains(":")) return 0L
        val parts = clean.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60L + parts[1]
            3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
            else -> 0L
        }
    }

    internal fun cleanTrackTitle(raw: String): String {
        return raw
            .replace(Regex("\\s*\\(Official\\s*(Music\\s*)?Video\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\[Official\\s*(Music\\s*)?Video\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Official\\s*Audio\\)", RegexOption.IGNORE_CASE), "")
            .trim()
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
