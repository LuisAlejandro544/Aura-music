package com.example.core.data.streaming

import com.example.debug.AuraDebugManager
import com.example.core.model.StreamingSearchItem
import com.example.core.model.StreamingSourcePlatform
import org.json.JSONObject

/**
 * Motor de búsqueda de YouTube Music (cliente WEB_REMIX).
 * Rol arquitectónico: fuente YT MUSIC; la fachada StreamingApiService delega aquí.
 */
internal object YouTubeMusicSearchEngine {

    private const val TAG = "StreamingApiService"
    internal const val YT_MUSIC_SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"

    internal fun searchYouTubeMusicApi(query: String): List<StreamingSearchItem> {
        return try {
            val payload = JSONObject().apply {
                put("query", query)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240724.01.00")
                        put("hl", "es")
                        put("gl", "US")
                    })
                })
            }

            val responseStr = StreamingSearchUtils.postJson(
                endpoint = YT_MUSIC_SEARCH_URL,
                body = payload.toString(),
                headers = mapOf(
                    "User-Agent" to StreamingSearchUtils.DESKTOP_USER_AGENT,
                    "Origin" to "https://music.youtube.com",
                    "Referer" to "https://music.youtube.com/"
                )
            ) ?: return emptyList()

            parseYouTubeMusicSearchResults(JSONObject(responseStr))
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Aviso en búsqueda YT Music API: ${e.message}")
            emptyList()
        }
    }

    internal fun parseYouTubeMusicSearchResults(root: JSONObject): List<StreamingSearchItem> {
        val items = mutableListOf<StreamingSearchItem>()
        val seenIds = mutableSetOf<String>()
        val renderers = mutableListOf<JSONObject>()
        StreamingSearchUtils.collectJsonObjectsByKey(root, "musicResponsiveListItemRenderer", renderers)

        for (renderer in renderers) {
            val playlistItemData = renderer.optJSONObject("playlistItemData")
            val videoId = playlistItemData?.optString("videoId", "") ?: ""
            if (videoId.length != 11 || !seenIds.add(videoId)) continue

            val flexColumns = renderer.optJSONArray("flexColumns") ?: continue
            var title = ""
            var artist = ""
            var album = "YT Music"
            var durationSecs = 0L

            if (flexColumns.length() > 0) {
                val col0 = flexColumns.optJSONObject(0)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                title = StreamingSearchUtils.extractTextFromRuns(col0)
            }
            if (flexColumns.length() > 1) {
                val col1Runs = flexColumns.optJSONObject(1)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs")
                if (col1Runs != null) {
                    val parts = mutableListOf<String>()
                    for (i in 0 until col1Runs.length()) {
                        val t = col1Runs.optJSONObject(i)?.optString("text", "")?.trim() ?: ""
                        if (t.isNotBlank() && t != "•" && t != "·" && t != "&") {
                            parts.add(t)
                        }
                    }
                    for (part in parts) {
                        val maybeDur = StreamingSearchUtils.parseDurationTextToSeconds(part)
                        if (maybeDur > 0L) {
                            durationSecs = maybeDur
                        } else if (artist.isBlank() && !part.equals("Canción", ignoreCase = true) && !part.equals("Song", ignoreCase = true) && !part.equals("Video", ignoreCase = true)) {
                            artist = part
                        } else if (album == "YT Music" && part != artist && !part.equals("Canción", ignoreCase = true)) {
                            album = part
                        }
                    }
                }
            }

            if (title.isBlank()) continue
            val thumb = StreamingSearchUtils.extractBestThumbnail(renderer.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail"))
                .ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }

            items.add(
                StreamingSearchItem(
                    videoId = videoId,
                    title = title,
                    artist = artist.replace(" - Topic", "").ifBlank { "YouTube Music" },
                    album = album,
                    durationSeconds = durationSecs,
                    thumbnailUrl = thumb,
                    platform = StreamingSourcePlatform.YT_MUSIC
                )
            )
            if (items.size >= 25) break
        }
        return items
    }

}
