package com.example.core.data.importer.playlist

import android.content.Context
import com.example.core.data.importer.YtDlpNativeEngine
import com.example.debug.AuraDebugManager
import com.example.core.model.ExternalPlaylistPreview
import com.example.core.model.ExternalPlaylistSource
import com.example.core.model.ExternalPlaylistTrackItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Extractor de playlists públicas de YouTube Music / YouTube sin login.
 * Rol arquitectónico: fuente YouTube; la fachada ExternalPlaylistImporter delega aquí.
 */
internal object YouTubePlaylistExtractor {

    private const val TAG = "ExternalPlaylistImporter"

    private const val USER_AGENT_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    internal suspend fun fetchYouTubePlaylist(
        context: Context,
        url: String
    ): Result<ExternalPlaylistPreview> {
        val playlistId = extractYouTubePlaylistId(url)
            ?: return Result.failure(
                IllegalArgumentException("No se encontró el parámetro 'list=' en el enlace de YouTube Music / YouTube.")
            )

        AuraDebugManager.logInfo(TAG, "Analizando Playlist de YouTube Music / YouTube ID=$playlistId")

        // Paso 1: Consultar endpoint 'browse' de InnerTube (WEB_REMIX y WEB)
        val innerTubePreview = fetchYouTubePlaylistViaInnerTube(url, playlistId)
        if (innerTubePreview != null && innerTubePreview.tracks.isNotEmpty()) {
            AuraDebugManager.logInfo(TAG, "InnerTube browse extrajo ${innerTubePreview.tracks.size} canciones para '${innerTubePreview.title}'")
            return Result.success(innerTubePreview)
        }

        // Paso 2: Respaldo con yt-dlp --flat-playlist
        val canonicalPlaylistUrl = "https://www.youtube.com/playlist?list=$playlistId"
        val flatDump = YtDlpNativeEngine.resolveFlatPlaylist(context, canonicalPlaylistUrl).getOrNull()
        if (flatDump != null && flatDump.entries.isNotEmpty()) {
            val tracks = flatDump.entries.mapIndexed { idx, entry ->
                ExternalPlaylistTrackItem(
                    index = idx + 1,
                    title = entry.title,
                    artist = entry.uploader,
                    album = flatDump.title,
                    durationSeconds = entry.durationSeconds,
                    thumbnailUrl = entry.thumbnailUrl,
                    videoIdOrUrl = entry.id,
                    sourcePlatform = ExternalPlaylistSource.YOUTUBE_MUSIC
                )
            }
            return Result.success(
                ExternalPlaylistPreview(
                    sourceUrl = url,
                    playlistId = playlistId,
                    title = flatDump.title.ifBlank { "Playlist de YouTube Music" },
                    author = flatDump.uploader.ifBlank { "YouTube Music" },
                    coverUrl = flatDump.thumbnailUrl.ifBlank { tracks.firstOrNull()?.thumbnailUrl.orEmpty() },
                    source = ExternalPlaylistSource.YOUTUBE_MUSIC,
                    tracks = tracks
                )
            )
        }

        return Result.failure(
            IllegalStateException("No se pudo cargar la Playlist de YouTube Music / YouTube. Verifica que sea pública o no listada.")
        )
    }
    internal fun extractYouTubePlaylistId(url: String): String? {
        val regex = Regex("[?&]list=([a-zA-Z0-9_-]+)")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }
    internal fun fetchYouTubePlaylistViaInnerTube(
        sourceUrl: String,
        playlistId: String
    ): ExternalPlaylistPreview? {
        val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"

        // Intentar primero con el cliente WEB de YouTube estándar (soporta tanto playlists de YT como de YT Music)
        val webBody = JSONObject().apply {
            put("browseId", browseId)
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20240724.00.00")
                    put("hl", "es")
                    put("gl", "US")
                })
            })
        }

        val responseStr = PlaylistHttpClient.postJson(
            endpoint = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false",
            body = webBody.toString(),
            headers = mapOf(
                "User-Agent" to USER_AGENT_DESKTOP,
                "Origin" to "https://www.youtube.com"
            )
        )

        if (!responseStr.isNullOrBlank()) {
            val parsed = parseYouTubeBrowseResponse(JSONObject(responseStr), sourceUrl, playlistId)
            if (parsed != null && parsed.tracks.isNotEmpty()) {
                return parsed
            }
        }

        // Intentar también con WEB_REMIX (YouTube Music)
        val remixBody = JSONObject().apply {
            put("browseId", browseId)
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20240724.01.00")
                    put("hl", "es")
                    put("gl", "US")
                })
            })
        }

        val remixStr = PlaylistHttpClient.postJson(
            endpoint = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false",
            body = remixBody.toString(),
            headers = mapOf(
                "User-Agent" to USER_AGENT_DESKTOP,
                "Origin" to "https://music.youtube.com"
            )
        )

        if (!remixStr.isNullOrBlank()) {
            val parsedRemix = parseYouTubeBrowseResponse(JSONObject(remixStr), sourceUrl, playlistId)
            if (parsedRemix != null && parsedRemix.tracks.isNotEmpty()) {
                return parsedRemix
            }
        }

        return null
    }
    internal fun parseYouTubeBrowseResponse(
        root: JSONObject,
        sourceUrl: String,
        playlistId: String
    ): ExternalPlaylistPreview? {
        try {
            var playlistTitle = root.optJSONObject("metadata")
                ?.optJSONObject("playlistMetadataRenderer")
                ?.optString("title", "")
                .orEmpty()

            var author = ""
            var coverUrl = ""

            val headerRenderer = root.optJSONObject("header")?.optJSONObject("playlistHeaderRenderer")
                ?: root.optJSONObject("header")?.optJSONObject("musicDetailHeaderRenderer")
                ?: root.optJSONObject("header")?.optJSONObject("musicResponsiveHeaderRenderer")

            if (headerRenderer != null) {
                if (playlistTitle.isBlank()) {
                    playlistTitle = extractRunsText(headerRenderer.optJSONObject("title"))
                }
                author = extractRunsText(headerRenderer.optJSONObject("ownerText"))
                    .ifBlank { extractRunsText(headerRenderer.optJSONObject("straplineTextOne")) }
                coverUrl = extractBestThumbnail(
                    headerRenderer.optJSONObject("playlistHeaderBanner")
                        ?.optJSONObject("heroPlaylistThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?: headerRenderer.optJSONObject("thumbnail")
                            ?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")
                )
            }

            val tracks = mutableListOf<ExternalPlaylistTrackItem>()
            val seenIds = mutableSetOf<String>()

            // 1. Recolectar playlistVideoRenderer (YouTube Web)
            val videoRenderers = mutableListOf<JSONObject>()
            collectObjectsByKey(root, "playlistVideoRenderer", videoRenderers)
            for (vr in videoRenderers) {
                val videoId = vr.optString("videoId", "")
                if (videoId.length != 11 || !seenIds.add(videoId)) continue
                val title = extractRunsText(vr.optJSONObject("title"))
                if (title.isBlank() || title.equals("[Deleted video]", ignoreCase = true) || title.equals("[Private video]", ignoreCase = true)) {
                    continue
                }
                val artist = extractRunsText(vr.optJSONObject("shortBylineText"))
                    .replace(" - Topic", "")
                    .trim()
                    .ifBlank { "YouTube Music" }
                val durationSecs = vr.optString("lengthSeconds", "0").toLongOrNull()
                    ?: parseColonDuration(extractRunsText(vr.optJSONObject("lengthText")))
                val thumb = extractBestThumbnail(vr.optJSONObject("thumbnail"))
                    .ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }

                tracks.add(
                    ExternalPlaylistTrackItem(
                        index = tracks.size + 1,
                        title = title,
                        artist = artist,
                        album = playlistTitle.ifBlank { "YouTube Music" },
                        durationSeconds = durationSecs,
                        thumbnailUrl = thumb,
                        videoIdOrUrl = videoId,
                        sourcePlatform = ExternalPlaylistSource.YOUTUBE_MUSIC
                    )
                )
            }

            // 2. Recolectar musicResponsiveListItemRenderer (YouTube Music WEB_REMIX)
            if (tracks.isEmpty()) {
                val musicRenderers = mutableListOf<JSONObject>()
                collectObjectsByKey(root, "musicResponsiveListItemRenderer", musicRenderers)
                for (mr in musicRenderers) {
                    val videoId = mr.optJSONObject("playlistItemData")?.optString("videoId", "")
                        ?: mr.optJSONObject("overlay")?.optJSONObject("musicItemThumbnailOverlayRenderer")
                            ?.optJSONObject("content")?.optJSONObject("musicPlayButtonRenderer")
                            ?.optJSONObject("playNavigationEndpoint")?.optJSONObject("watchEndpoint")
                            ?.optString("videoId", "")
                        ?: ""
                    if (videoId.length != 11 || !seenIds.add(videoId)) continue

                    val flexCols = mr.optJSONArray("flexColumns") ?: continue
                    val title = if (flexCols.length() > 0) {
                        extractRunsText(
                            flexCols.optJSONObject(0)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                        )
                    } else ""
                    if (title.isBlank()) continue

                    val artist = if (flexCols.length() > 1) {
                        extractRunsText(
                            flexCols.optJSONObject(1)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                        ).split("•").firstOrNull()?.trim().orEmpty()
                    } else "YouTube Music"

                    val fixedCols = mr.optJSONArray("fixedColumns")
                    val durationText = if (fixedCols != null && fixedCols.length() > 0) {
                        extractRunsText(
                            fixedCols.optJSONObject(0)
                                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                                ?.optJSONObject("text")
                        )
                    } else ""
                    val durationSecs = parseColonDuration(durationText)
                    val thumb = extractBestThumbnail(
                        mr.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")
                    ).ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }

                    tracks.add(
                        ExternalPlaylistTrackItem(
                            index = tracks.size + 1,
                            title = title,
                            artist = artist.replace(" - Topic", "").ifBlank { "YouTube Music" },
                            album = playlistTitle.ifBlank { "YouTube Music" },
                            durationSeconds = durationSecs,
                            thumbnailUrl = thumb,
                            videoIdOrUrl = videoId,
                            sourcePlatform = ExternalPlaylistSource.YOUTUBE_MUSIC
                        )
                    )
                }
            }

            if (tracks.isEmpty()) return null

            val finalCover = coverUrl.ifBlank { tracks.firstOrNull()?.thumbnailUrl.orEmpty() }
            return ExternalPlaylistPreview(
                sourceUrl = sourceUrl,
                playlistId = playlistId,
                title = playlistTitle.ifBlank { "Playlist de YouTube Music" },
                author = author.ifBlank { "YouTube Music" },
                coverUrl = finalCover,
                source = ExternalPlaylistSource.YOUTUBE_MUSIC,
                tracks = tracks
            )
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Error parseando browse de YouTube Playlist: ${e.message}")
            return null
        }
    }

// =========================================================================
// UTILIDADES HTTP Y JSON
// =========================================================================
    internal fun collectObjectsByKey(node: Any?, targetKey: String, out: MutableList<JSONObject>) {
        if (out.size >= 250) return
        when (node) {
            is JSONObject -> {
                if (node.has(targetKey)) {
                    val child = node.optJSONObject(targetKey)
                    if (child != null) out.add(child)
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    collectObjectsByKey(node.opt(k), targetKey, out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectObjectsByKey(node.opt(i), targetKey, out)
                }
            }
        }
    }
    internal fun extractRunsText(textObj: JSONObject?): String {
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
    internal fun parseColonDuration(text: String): Long {
        val clean = text.trim()
        if (!clean.contains(":")) return 0L
        val parts = clean.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60L + parts[1]
            3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
            else -> 0L
        }
    }
}
