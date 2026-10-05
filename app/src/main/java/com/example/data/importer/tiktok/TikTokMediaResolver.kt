package com.example.data.importer.tiktok

import com.example.data.importer.OnlineVideoAudioImporter
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Aura Music - Extractor y Resolutor Modular de Medios de TikTok
 *
 * Responsabilidades:
 * - Resolución de enlaces de videos de TikTok sin marcas de agua mediante endpoints públicos de alta fidelidad (TikWM, Tiklydown).
 * - Extracción y limpieza heurística de títulos, autores y portadas originales.
 * - Desbloqueo de videos de cualquier duración para demuxing y generación de Video Canvas sin recortes.
 */
object TikTokMediaResolver {

    /**
     * Resuelve videos de TikTok utilizando el endpoint público de alta fidelidad TikWM sin marca de agua.
     */
    fun resolve(tikTokUrl: String, httpClient: OkHttpClient): Result<OnlineVideoAudioImporter.ResolvedMediaInfo> {
        val encodedUrl = URLEncoder.encode(tikTokUrl, "UTF-8")
        val apiEndpoints = listOf(
            "https://www.tikwm.com/api/?url=$encodedUrl&hd=1",
            "https://api.tiklydown.eu.org/api/download?url=$encodedUrl"
        )

        for (endpoint in apiEndpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bodyString = response.body?.string() ?: return@use
                    val json = JSONObject(bodyString)

                    // Formato TikWM
                    if (json.has("code") && json.getInt("code") == 0 && json.has("data")) {
                        val data = json.getJSONObject("data")
                        val videoPlayUrl = data.optString("play").ifBlank { data.optString("wmplay") }
                        val rawTitle = data.optString("title").trim()
                        val duration = data.optLong("duration", 0L)
                        val coverUrl = data.optString("cover").ifBlank { data.optString("origin_cover") }

                        // Información de audio y artista
                        val musicInfo = data.optJSONObject("music_info")
                        val musicUrl = musicInfo?.optString("play") ?: data.optString("music")
                        val musicTitle = musicInfo?.optString("title")
                        val musicAuthor = musicInfo?.optString("author")
                        val authorObj = data.optJSONObject("author")
                        val authorNickname = authorObj?.optString("nickname") ?: authorObj?.optString("unique_id")

                        val title = when {
                            !musicTitle.isNullOrBlank() && !musicTitle.contains("original sound", ignoreCase = true) -> musicTitle
                            rawTitle.isNotBlank() -> cleanCaptionAsTitle(rawTitle)
                            else -> "TikTok Music ${System.currentTimeMillis() % 1000}"
                        }

                        val artist = when {
                            !musicAuthor.isNullOrBlank() -> musicAuthor
                            !authorNickname.isNullOrBlank() -> "@$authorNickname"
                            else -> "TikTok Creator"
                        }

                        if (videoPlayUrl.isNotBlank()) {
                            val resolvedVideo = if (videoPlayUrl.startsWith("//")) "https:$videoPlayUrl" else videoPlayUrl
                            return Result.success(
                                OnlineVideoAudioImporter.ResolvedMediaInfo(
                                    originalUrl = tikTokUrl,
                                    suggestedTitle = title,
                                    suggestedArtist = artist,
                                    videoUrl = resolvedVideo,
                                    audioUrl = null,
                                    coverUrl = if (coverUrl.startsWith("//")) "https:$coverUrl" else coverUrl.ifBlank { null },
                                    durationSeconds = duration
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Continuar al siguiente endpoint si hubo algún error transitorio
            }
        }

        return Result.failure(IllegalStateException("No se pudo obtener el video desde el enlace provisto. Verifica que el enlace sea válido."))
    }

    private fun cleanCaptionAsTitle(rawCaption: String): String {
        return rawCaption
            .replace(Regex("#\\S+"), "") // Eliminar hashtags (#fyp, #music, etc.)
            .replace(Regex("@\\S+"), "") // Eliminar menciones (@usuario)
            .replace(Regex("\n+"), " ")
            .trim()
            .take(60)
            .ifBlank { "TikTok Music" }
    }
}
