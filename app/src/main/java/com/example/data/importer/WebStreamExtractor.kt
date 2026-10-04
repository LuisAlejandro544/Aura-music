package com.example.data.importer

import android.content.Context
import com.example.debug.AuraDebugManager
import java.util.regex.Pattern

/**
 * Motores de extracción disponibles para YouTube y video web:
 * - INNERTUBE: API nativa directa de YouTube (sin navegador, ultrarrápida, con respaldo multi-cliente e Invidious).
 * - WEBVIEW: Navegador efímero que ejecuta scripts y el reproductor móvil en memoria para capturar el stream.
 */
enum class YoutubeExtractionEngine(val label: String, val description: String) {
    INNERTUBE("InnerTube (Rápido)", "API nativa directa de alta velocidad con bypass inteligente"),
    WEBVIEW("Motor WebView", "Navegador efímero móvil con ejecución de scripts en segundo plano")
}

/**
 * Orquestador principal para la extracción de flujos multimedia de alta fidelidad desde YouTube y video web.
 *
 * Arquitectura de 3 Niveles de Resiliencia:
 * 1. InnerTube Nativo (InnerTubeClient): Consulta endpoints de YouTube con clientes sin fricción
 *    (ANDROID_VR y VISIONOS), que entregan enlaces directos de alta fidelidad sin 'LOGIN_REQUIRED' ni n-sig.
 * 2. Bypass de Respaldo Invidious (InvidiousStreamResolver): Para canciones con restricciones de derechos
 *    estrictas (VEVO, discográficas), consulta instancias públicas que resuelven los enlaces de googlevideo.
 * 3. Motor Headless WebView (HeadlessWebViewExtractor): Navegador efímero sobre 'm.youtube.com'
 *    que intercepta el tráfico de red en memoria y evalúa 'ytInitialPlayerResponse'.
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
     * Permite especificar el motor preferido (InnerTube o WebView) con fallback automático transparente.
     */
    suspend fun resolveStream(
        context: Context,
        url: String,
        preferredEngine: YoutubeExtractionEngine = YoutubeExtractionEngine.INNERTUBE
    ): Result<OnlineVideoAudioImporter.ResolvedMediaInfo> {
        val videoId = extractVideoId(url)
            ?: return Result.failure(IllegalArgumentException("No se pudo identificar el ID del video de YouTube"))

        return if (preferredEngine == YoutubeExtractionEngine.INNERTUBE) {
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

            // Nivel 3: Fallback a WebView móvil
            AuraDebugManager.logWarning("WebStreamExtractor", "InnerTube e Invidious no devolvieron streams. Intentando con Motor WebView...")
            val webViewResult = HeadlessWebViewExtractor.resolve(context, videoId, url)
            if (webViewResult != null) {
                Result.success(webViewResult)
            } else {
                Result.failure(Exception("No se pudo extraer el audio de YouTube con ninguno de los motores disponibles. Verifica el enlace."))
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
                Result.success(invidiousResult)
            } else {
                Result.failure(Exception("No se pudo extraer el audio con WebView, InnerTube ni Invidious."))
            }
        }
    }
}
