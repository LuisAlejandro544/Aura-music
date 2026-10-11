package com.example.data.importer

import android.content.Context
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Motores de extracción seleccionables por el usuario para enlaces de YouTube y Web.
 */
enum class YoutubeExtractionEngine(val label: String, val description: String) {
    YTDLP(
        "yt-dlp + Cookies",
        "Motor nativo de escritorio (Python + QuickJS + cookies.txt del WebView)."
    ),
    PIPED(
        "Piped API",
        "Red abierta sin anuncios con proxy anti-403 para YouTube y YouTube Music."
    ),
    INNERTUBE(
        "InnerTube + Invidious",
        "API directa de YouTube Music (ANDROID_VR / IOS / TV) + Proxy Invidious."
    ),
    INVIDIOUS(
        "Invidious Proxy",
        "Red de instancias abiertas con túnel proxy local=true (/latest_version)."
    ),
    WEBVIEW(
        "WebView Real",
        "Navegador móvil en segundo plano que captura flujos y refresca cookies.txt."
    );

    val badgeText: String
        get() = when (this) {
            YTDLP -> "YT-DLP"
            PIPED -> "PIPED API"
            INNERTUBE -> "INNERTUBE"
            INVIDIOUS -> "INVIDIOUS"
            WEBVIEW -> "WEBVIEW"
        }

    companion object {
        const val PREF_ONLINE_EXTRACTION_ENGINE = "pref_online_extraction_engine"

        fun fromPreferenceName(name: String?): YoutubeExtractionEngine {
            if (name.isNullOrBlank()) return YTDLP
            return entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: YTDLP
        }
    }
}

/**
 * Orquestador de Extracción de Video/Audio Web con Arquitectura Resiliente de 5 Niveles:
 * - Nivel 0 (yt-dlp + cookies.txt): Motor nativo ejecutado localmente con Python + QuickJS y cookies reales del WebView.
 * - Nivel 1 (Piped API): Red de servidores abiertos con proxy anti-403 (`pipedproxy`) para YouTube y YT Music.
 * - Nivel 2 (InnerTube): Clientes directos (`ANDROID_VR` Oculus, `IOS`, `TVHTML5_SIMPLY_EMBEDDED_PLAYER`) con `visitorData`.
 * - Nivel 3 (Invidious API v1): Espejos públicos con auto-descubrimiento y proxy `local=true` (`/latest_version`).
 * - Nivel 4 (Headless WebView): Navegador real sobre `m.youtube.com` que intercepta flujos y exporta `cookies.txt`.
 */
object WebStreamExtractor {

    private val YOUTUBE_ID_REGEX = Regex(
        "(?:youtube\\.com/(?:[^/]+/.+/|(?:v|e(?:mbed)?|shorts|live)/|.*[?&]v=)|youtu\\.be/)([^\"&?/\\s]{11})",
        RegexOption.IGNORE_CASE
    )

    fun extractVideoId(url: String): String? {
        return YOUTUBE_ID_REGEX.find(url)?.groupValues?.getOrNull(1)
    }

    fun isWebVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("youtube.com") ||
                lower.contains("youtu.be") ||
                lower.contains("music.youtube.com") ||
                lower.contains("piped.video") ||
                lower.contains("vimeo.com") ||
                lower.contains("dailymotion.com") ||
                lower.contains("soundcloud.com") ||
                lower.contains("facebook.com/watch") ||
                lower.contains("fb.watch") ||
                lower.contains("instagram.com/reel") ||
                lower.contains("x.com/") ||
                lower.contains("twitter.com/")
    }

    /**
     * Obtiene el motor de extracción online seleccionado por el usuario en Ajustes (por defecto [YoutubeExtractionEngine.YTDLP]).
     */
    fun getSelectedEngine(context: Context): YoutubeExtractionEngine {
        return try {
            val prefs = context.applicationContext.getSharedPreferences("aura_music_ui_prefs", Context.MODE_PRIVATE)
            YoutubeExtractionEngine.fromPreferenceName(
                prefs.getString(YoutubeExtractionEngine.PREF_ONLINE_EXTRACTION_ENGINE, YoutubeExtractionEngine.YTDLP.name)
            )
        } catch (_: Throwable) {
            YoutubeExtractionEngine.YTDLP
        }
    }

    /**
     * Guarda el motor de extracción online seleccionado por el usuario en Ajustes.
     */
    fun setSelectedEngine(context: Context, engine: YoutubeExtractionEngine) {
        try {
            val prefs = context.applicationContext.getSharedPreferences("aura_music_ui_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString(YoutubeExtractionEngine.PREF_ONLINE_EXTRACTION_ENGINE, engine.name).apply()
        } catch (_: Throwable) {}
    }

    /**
     * Resuelve los flujos multimedia usando el motor preferido y aplicando fallback automático en cascada
     * entre `yt-dlp + cookies.txt`, `Piped API`, `InnerTube`, `Invidious Proxy` y `Headless WebView`.
     */
    suspend fun resolveStream(
        context: Context,
        url: String,
        preferredEngine: YoutubeExtractionEngine = getSelectedEngine(context)
    ): Result<OnlineVideoAudioImporter.ResolvedMediaInfo> = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        val videoId = extractVideoId(cleanUrl)

        when (preferredEngine) {
            YoutubeExtractionEngine.YTDLP -> {
                // 1º Intentar con yt-dlp nativo + cookies.txt del WebView
                val ytdlpResult = YtDlpNativeEngine.resolveStream(context, cleanUrl)
                if (ytdlpResult.isSuccess) return@withContext ytdlpResult

                AuraDebugManager.logInfo("WebStreamExtractor", "yt-dlp no resolvió ($cleanUrl), activando cascada Piped -> InnerTube -> Invidious -> WebView...")
                if (videoId != null) {
                    PipedStreamResolver.resolve(videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                    InnerTubeClient.resolve(videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                    InvidiousStreamResolver.resolve(videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                    HeadlessWebViewExtractor.resolve(context, videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                }
                return@withContext ytdlpResult
            }

            YoutubeExtractionEngine.PIPED -> {
                if (videoId != null) {
                    PipedStreamResolver.resolve(videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                    InvidiousStreamResolver.resolve(videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                    InnerTubeClient.resolve(videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                }
                val ytdlpFallback = YtDlpNativeEngine.resolveStream(context, cleanUrl)
                if (ytdlpFallback.isSuccess) return@withContext ytdlpFallback
                if (videoId != null) {
                    HeadlessWebViewExtractor.resolve(context, videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                }
            }

            YoutubeExtractionEngine.INNERTUBE -> {
                if (videoId != null) {
                    InnerTubeClient.resolve(videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                    PipedStreamResolver.resolve(videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                    InvidiousStreamResolver.resolve(videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                }
                val ytdlpFallback = YtDlpNativeEngine.resolveStream(context, cleanUrl)
                if (ytdlpFallback.isSuccess) return@withContext ytdlpFallback
                if (videoId != null) {
                    HeadlessWebViewExtractor.resolve(context, videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                }
            }

            YoutubeExtractionEngine.INVIDIOUS -> {
                if (videoId != null) {
                    InvidiousStreamResolver.resolve(videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                    PipedStreamResolver.resolve(videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                    InnerTubeClient.resolve(videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                }
                val ytdlpFallback = YtDlpNativeEngine.resolveStream(context, cleanUrl)
                if (ytdlpFallback.isSuccess) return@withContext ytdlpFallback
                if (videoId != null) {
                    HeadlessWebViewExtractor.resolve(context, videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                }
            }

            YoutubeExtractionEngine.WEBVIEW -> {
                if (videoId != null) {
                    HeadlessWebViewExtractor.resolve(context, videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                    PipedStreamResolver.resolve(videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                    InnerTubeClient.resolve(videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                    InvidiousStreamResolver.resolve(videoId, cleanUrl)?.let { return@withContext Result.success(it) }
                }
                val ytdlpFallback = YtDlpNativeEngine.resolveStream(context, cleanUrl)
                if (ytdlpFallback.isSuccess) return@withContext ytdlpFallback
            }
        }

        Result.failure(
            IllegalStateException("No se pudo extraer la pista multimedia de este enlace. Verifica tu conexión o intenta con otro enlace.")
        )
    }
}

