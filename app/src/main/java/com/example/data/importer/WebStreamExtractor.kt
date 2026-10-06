package com.example.data.importer

import android.content.Context
import com.example.debug.AuraDebugManager
import java.util.regex.Pattern

/**
 * Motores de extracción disponibles para YouTube y video web:
 * - YTDLP: Extractor nativo local basado en yt-dlp y FFmpeg con descifrado de firmas en el dispositivo.
 * - INNERTUBE: API nativa directa de YouTube (sin navegador, ultrarrápida, con respaldo multi-cliente e Invidious).
 * - WEBVIEW: Navegador efímero que ejecuta scripts y el reproductor móvil en memoria para capturar el stream.
 */
enum class YoutubeExtractionEngine(val label: String, val description: String) {
    YTDLP("yt-dlp + FFmpeg (Local)", "Extractor local sin restricciones con soporte de firmas y parches"),
    INNERTUBE("InnerTube (Rápido)", "API nativa directa de alta velocidad con bypass inteligente"),
    WEBVIEW("Motor WebView", "Navegador efímero móvil con ejecución de scripts en segundo plano")
}

/**
 * Orquestador principal para la extracción de flujos multimedia de alta fidelidad desde YouTube y video web.
 *
 * Arquitectura de 4 Niveles de Resiliencia:
 * 1. Motor yt-dlp Local (YtDlpNativeEngine): Extracción en el propio teléfono con capacidad de auto-actualización OTA.
 * 2. InnerTube Nativo (InnerTubeClient): Consulta endpoints de YouTube con clientes sin fricción (ANDROID_VR y VISIONOS).
 * 3. Bypass de Respaldo Invidious (InvidiousStreamResolver): Para canciones con restricciones de derechos estrictas.
 * 4. Motor Headless WebView (HeadlessWebViewExtractor): Navegador efímero sobre 'm.youtube.com' con descarga en cascada de carátulas.
 */
object WebStreamExtractor {

    private val YOUTUBE_ID_PATTERN = Pattern.compile(
        "(?:youtu\\.be/|youtube\\.com/(?:embed/|v/|shorts/|watch\\?v=|watch\\?.+&v=))([a-zA-Z0-9_-]{11})"
    )

    fun isWebVideoUrl(url: String): Boolean {
        val clean = url.trim()
        return clean.contains("youtube.com", ignoreCase = true) ||
                clean.contains("youtu.be", ignoreCase = true)
    }

    fun extractVideoId(url: String): String? {
        val matcher = YOUTUBE_ID_PATTERN.matcher(url.trim())
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * Resuelve el enlace web y retorna la información de audio, video y carátula.
     * Permite especificar el motor preferido con fallback automático transparente.
     */
    suspend fun resolveStream(
        context: Context,
        url: String,
        preferredEngine: YoutubeExtractionEngine = YoutubeExtractionEngine.INNERTUBE
    ): Result<OnlineVideoAudioImporter.ResolvedMediaInfo> {
        val videoId = extractVideoId(url)
            ?: return Result.failure(IllegalArgumentException("No se pudo identificar el ID del video de YouTube"))

        // Opción 1: Solicitado yt-dlp prioritario
        if (preferredEngine == YoutubeExtractionEngine.YTDLP) {
            if (YtDlpAutoUpdater.isYtDlpTemporarilyBlocked()) {
                AuraDebugManager.logInfo("WebStreamExtractor", "yt-dlp en actualización. Fallback automático transparente a InnerTube...")
            } else {
                val ytdlpResult = YtDlpNativeEngine.resolveStream(context, url)
                if (ytdlpResult.isSuccess) {
                    return ytdlpResult
                }
                AuraDebugManager.logWarning("WebStreamExtractor", "yt-dlp no pudo resolver el stream. Continuando con InnerTube...")
            }
        }

        return if (preferredEngine == YoutubeExtractionEngine.INNERTUBE || preferredEngine == YoutubeExtractionEngine.YTDLP) {
            // Nivel 1: InnerTube directo (Ultra-rápido <300ms)
            val innerTubeResult = InnerTubeClient.resolve(videoId, url)
            if (innerTubeResult != null) {
                return Result.success(innerTubeResult)
            }

            // Nivel 2: Bypass Invidious para canciones con protección estricta / LOGIN_REQUIRED
            AuraDebugManager.logInfo("WebStreamExtractor", "InnerTube requirió bypass de sesión. Consultando motor de respaldo...")
            val invidiousResult = InvidiousStreamResolver.resolve(videoId, url)
            if (invidiousResult != null) {
                return Result.success(invidiousResult)
            }

            // Nivel 3: Motor yt-dlp local si aún no se intentó
            if (preferredEngine != YoutubeExtractionEngine.YTDLP && YtDlpNativeEngine.isAvailable(context)) {
                AuraDebugManager.logInfo("WebStreamExtractor", "Probando motor local yt-dlp...")
                val ytdlpResult = YtDlpNativeEngine.resolveStream(context, url)
                if (ytdlpResult.isSuccess) {
                    return ytdlpResult
                }
            }

            // Nivel 4: Fallback a WebView móvil
            AuraDebugManager.logWarning("WebStreamExtractor", "InnerTube e Invidious no devolvieron streams. Intentando con Motor WebView...")
            val webViewResult = HeadlessWebViewExtractor.resolve(context, videoId, url)
            if (webViewResult != null) {
                Result.success(webViewResult)
            } else {
                // Último intento: yt-dlp
                val finalYtdlp = YtDlpNativeEngine.resolveStream(context, url)
                if (finalYtdlp.isSuccess) {
                    finalYtdlp
                } else {
                    Result.failure(Exception("No se pudo extraer el audio de YouTube con ninguno de los motores disponibles. Verifica el enlace."))
                }
            }
        } else {
            // Solicitado expresamente por el usuario: Motor WebView primero
            val webViewResult = HeadlessWebViewExtractor.resolve(context, videoId, url)
            if (webViewResult != null) {
                return Result.success(webViewResult)
            }

            // Fallback 1: InnerTube
            AuraDebugManager.logWarning("WebStreamExtractor", "Motor WebView falló. Intentando con InnerTube...")
            val innerTubeResult = InnerTubeClient.resolve(videoId, url)
            if (innerTubeResult != null) {
                return Result.success(innerTubeResult)
            }

            // Fallback 2: Invidious
            val invidiousResult = InvidiousStreamResolver.resolve(videoId, url)
            if (invidiousResult != null) {
                return Result.success(invidiousResult)
            }

            // Fallback 3: yt-dlp
            val ytdlpResult = YtDlpNativeEngine.resolveStream(context, url)
            if (ytdlpResult.isSuccess) {
                ytdlpResult
            } else {
                Result.failure(Exception("No se pudo extraer el audio con WebView, InnerTube, Invidious ni yt-dlp."))
            }
        }
    }
}
