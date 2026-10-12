package com.example.core.data.streaming

import com.example.debug.AuraDebugManager
import com.example.core.model.StreamingSearchItem
import com.example.core.model.StreamingSourcePlatform
import org.json.JSONArray
import org.json.JSONObject

/**
 * Motor de búsqueda de YouTube estándar y endpoint Watch Next.
 * Rol arquitectónico: fuente YOUTUBE y recomendaciones; la fachada delega aquí.
 */
internal object YouTubeSearchEngine {

    private const val TAG = "StreamingApiService"
    internal const val YT_STANDARD_SEARCH_URL = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
    internal const val YT_MUSIC_NEXT_URL = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false"
    internal const val YT_STANDARD_NEXT_URL = "https://www.youtube.com/youtubei/v1/next?prettyPrint=false"

    internal fun searchYouTubeStandardApi(query: String): List<StreamingSearchItem> {
        return try {
            val payload = JSONObject().apply {
                put("query", query)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20240726.00.00")
                        put("hl", "es")
                        put("gl", "US")
                    })
                })
            }

            val responseStr = StreamingSearchUtils.postJson(
                endpoint = YT_STANDARD_SEARCH_URL,
                body = payload.toString(),
                headers = mapOf(
                    "User-Agent" to StreamingSearchUtils.DESKTOP_USER_AGENT,
                    "Origin" to "https://www.youtube.com",
                    "Referer" to "https://www.youtube.com/"
                )
            ) ?: return emptyList()

            parseYouTubeStandardSearchResults(JSONObject(responseStr))
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Aviso en búsqueda YouTube API: ${e.message}")
            emptyList()
        }
    }

    internal fun parseYouTubeStandardSearchResults(root: JSONObject): List<StreamingSearchItem> {
        val items = mutableListOf<StreamingSearchItem>()
        val seenIds = mutableSetOf<String>()
        val renderers = mutableListOf<JSONObject>()
        StreamingSearchUtils.collectJsonObjectsByKey(root, "videoRenderer", renderers)

        for (renderer in renderers) {
            val videoId = renderer.optString("videoId", "")
            if (videoId.length != 11 || !seenIds.add(videoId)) continue

            val title = StreamingSearchUtils.extractTextFromRuns(renderer.optJSONObject("title"))
            if (title.isBlank()) continue

            val rawArtist = StreamingSearchUtils.extractTextFromRuns(renderer.optJSONObject("ownerText"))
                .ifBlank { StreamingSearchUtils.extractTextFromRuns(renderer.optJSONObject("longBylineText")) }
                .ifBlank { "YouTube" }

            val lengthText = StreamingSearchUtils.extractTextFromRuns(renderer.optJSONObject("lengthText"))
                .ifBlank { renderer.optJSONObject("lengthText")?.optString("simpleText", "") ?: "" }
            val durationSecs = StreamingSearchUtils.parseDurationTextToSeconds(lengthText)

            val isTopicMusic = rawArtist.endsWith(" - Topic", ignoreCase = true)
            val platform = if (isTopicMusic) StreamingSourcePlatform.YT_MUSIC else StreamingSourcePlatform.YOUTUBE
            val cleanArtist = rawArtist.replace(" - Topic", "").trim()

            val thumb = StreamingSearchUtils.extractBestThumbnail(renderer.optJSONObject("thumbnail"))
                .ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }

            items.add(
                StreamingSearchItem(
                    videoId = videoId,
                    title = title,
                    artist = cleanArtist,
                    album = if (platform == StreamingSourcePlatform.YT_MUSIC) "YT Music" else "YouTube Video",
                    durationSeconds = durationSecs,
                    thumbnailUrl = thumb,
                    platform = platform
                )
            )
            if (items.size >= 25) break
        }
        return items
    }

    internal fun fetchRelatedFromNextEndpoint(
        videoId: String,
        preferredPlatform: StreamingSourcePlatform
    ): List<StreamingSearchItem> {
        val results = mutableListOf<StreamingSearchItem>()
        try {
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("enablePersistentPlaylistPanel", true)
                put("isAudioOnly", true)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20240726.00.00")
                        put("hl", "es")
                        put("gl", "US")
                    })
                })
            }

            val responseStr = StreamingSearchUtils.postJson(
                endpoint = YT_STANDARD_NEXT_URL,
                body = payload.toString(),
                headers = mapOf(
                    "User-Agent" to StreamingSearchUtils.DESKTOP_USER_AGENT
                )
            ) ?: return emptyList()

            val root = JSONObject(responseStr)
            val collected = mutableListOf<JSONObject>()
            StreamingSearchUtils.collectJsonObjectsByKey(root, "compactVideoRenderer", collected)
            StreamingSearchUtils.collectJsonObjectsByKey(root, "playlistPanelVideoRenderer", collected)
            StreamingSearchUtils.collectJsonObjectsByKey(root, "lockupViewModel", collected)

            val seenIds = mutableSetOf<String>()
            for (node in collected) {
                val id = node.optString("videoId", "").ifBlank {
                    node.optString("contentId", "")
                }
                if (id.length != 11 || !seenIds.add(id)) continue

                val title = StreamingSearchUtils.extractTextFromRuns(node.optJSONObject("title"))
                    .ifBlank { "Recomendación Aura" }
                val artist = StreamingSearchUtils.extractTextFromRuns(node.optJSONObject("longBylineText"))
                    .ifBlank { StreamingSearchUtils.extractTextFromRuns(node.optJSONObject("shortBylineText")) }
                    .ifBlank { "YouTube Music" }
                val lengthText = StreamingSearchUtils.extractTextFromRuns(node.optJSONObject("lengthText"))
                val durationSecs = StreamingSearchUtils.parseDurationTextToSeconds(lengthText)

                val platform = if (artist.contains(" - Topic", ignoreCase = true) ||
                    preferredPlatform == StreamingSourcePlatform.YT_MUSIC
                ) {
                    StreamingSourcePlatform.YT_MUSIC
                } else {
                    StreamingSourcePlatform.YOUTUBE
                }

                results.add(
                    StreamingSearchItem(
                        videoId = id,
                        title = StreamingSearchUtils.cleanTrackTitle(title),
                        artist = artist.replace(" - Topic", "").trim(),
                        album = if (platform == StreamingSourcePlatform.YT_MUSIC) "YT Music Radio" else "YouTube Radio",
                        durationSeconds = durationSecs,
                        thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                        platform = platform
                    )
                )
                if (results.size >= 15) break
            }
        } catch (_: Exception) {}
        return results
    }

}
