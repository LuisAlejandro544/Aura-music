package com.example.core.data.streaming

import android.content.Context
import com.example.core.data.importer.OnlineVideoAudioImporter
import com.example.core.data.importer.YtDlpNativeEngine
import com.example.debug.AuraDebugManager
import com.example.core.model.StreamingSearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Resolución de streams progresivos MP3/Opus para ExoPlayer.
 * Rol arquitectónico: pieza de SoundCloudApiClient (fachada pública intacta).
 */
internal object SoundCloudStreamResolver {
    private const val TAG = "SoundCloudApiClient"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    suspend fun resolve(
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
            val directResolved = viaApiV2(permalinkUrl, item)
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

    private suspend fun viaApiV2(
        permalinkUrl: String,
        item: StreamingSearchItem
    ): OnlineVideoAudioImporter.ResolvedMediaInfo? {
        val clientId = SoundCloudHttp.clientId() ?: return null
        val encodedUrl = URLEncoder.encode(permalinkUrl, "UTF-8")
        val resolveEndpoint = "https://api-v2.soundcloud.com/resolve?url=$encodedUrl&client_id=$clientId"
        val jsonStr = SoundCloudHttp.get(resolveEndpoint) ?: return null
        val trackObj = JSONObject(jsonStr)

        val title = trackObj.optString("title", item.title).ifBlank { item.title }
        val userObj = trackObj.optJSONObject("user")
        val artist = userObj?.optString("username", item.artist)?.ifBlank { item.artist } ?: item.artist
        val durationMs = trackObj.optLong("full_duration", trackObj.optLong("duration", item.durationSeconds * 1000L))
        val artworkUrl = SoundCloudHttp.hiResArtwork(
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
        val streamJsonStr = SoundCloudHttp.get(streamEndpoint) ?: return null
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
}
