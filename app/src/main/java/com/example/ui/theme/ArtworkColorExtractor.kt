package com.example.ui.theme

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.BatteryManager
import android.os.PowerManager
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.example.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.absoluteValue

/**
 * Paleta de colores dinámicos extraída de la carátula o fotograma de video de una pista.
 * Permite ambientar la interfaz (Aura luminosa, barra de progreso, visualizador)
 * con los tonos predominantes sin perder la estética OLED profunda.
 */
data class ExtractedArtworkColors(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val ambientTopGlow: Color
)

/**
 * Estado en vivo de los colores extraídos directamente del fotograma actual del Video Canvas.
 */
data class LiveVideoColorSnapshot(
    val videoUri: String,
    val colors: ExtractedArtworkColors,
    val sampleIntervalMs: Long
)

/**
 * Extractor reactivo y optimizado de colores de carátula y video para Aura Music.
 * Utiliza AndroidX Palette en hilos de fondo ([Dispatchers.Default]) con decodificación
 * de bajo peso en memoria y caché LRU para transiciones fluidas a 60/120 FPS.
 *
 * Cuando el Video Canvas está activo:
 * - Extrae la paleta cromática en tiempo real a partir de un micro-bitmap (24x24) del
 *   [android.view.TextureView] activo (sin instanciar un segundo MediaCodec).
 * - Adapta automáticamente la frecuencia de muestreo cada **1 segundo (1000 ms)** para brindar
 *   transiciones cromáticas orgánicas y descansadas sin marear al usuario:
 *   • **1000 ms (1s)** cuando el dispositivo tiene **> 15% de batería** y **no está en modo ahorro de energía**.
 *   • **1500 ms (1.5s)** cuando el dispositivo tiene **<= 15% de batería** o **tiene activado el ahorro de batería**.
 * - Si el TextureView aún no ha renderizado su primer cuadro, extrae directamente del fotograma real
 *   del video con [MediaMetadataRetriever.OPTION_CLOSEST] sin dejar que la carátula estática interfiera.
 */
object ArtworkColorExtractor {

    const val INTERVAL_NORMAL_MS = 1000L
    const val INTERVAL_BATTERY_SAVER_MS = 1500L

    // Caché en memoria para almacenar las paletas de carátulas y buckets temporales de video
    internal val memoryCache = LruCache<Long, ExtractedArtworkColors>(80)

    // Flujo compartido en tiempo real para sincronizar NowPlayingScreen y MiniPlayer al instante
    internal val _liveVideoColorsFlow = MutableStateFlow<LiveVideoColorSnapshot?>(null)
    val liveVideoColorsFlow: StateFlow<LiveVideoColorSnapshot?> = _liveVideoColorsFlow.asStateFlow()

    // Retriever persistente para reutilizar el descriptor de video y evitar sobrecarga I/O
    internal val retrieverLock = Any()
    internal var cachedVideoUri: String? = null
    internal var cachedRetriever: MediaMetadataRetriever? = null
    internal var cachedVideoDurationMs: Long = 0L

    // Memoria persistente del último color válido extraído de video para evitar apagones en fundidos negros
    @Volatile
    internal var lastVideoTrackId: Long? = null
    @Volatile
    internal var lastVideoUri: String? = null
    @Volatile
    internal var lastVideoColors: ExtractedArtworkColors? = null

    fun getAdaptiveSampleIntervalMs(context: Context): Long = getAdaptiveSampleIntervalMsInternal(context)


    fun publishLiveVideoColors(trackId: Long?, videoUri: String, colors: ExtractedArtworkColors, sampleIntervalMs: Long) = publishLiveVideoColorsInternal(trackId, videoUri, colors, sampleIntervalMs)


    suspend fun extractColorsFromVideoFrame(frameBitmap: Bitmap, trackId: Long?, videoUri: String, fallbackPrimary: Color, fallbackSecondary: Color, sampleIntervalMs: Long = INTERVAL_NORMAL_MS): ExtractedArtworkColors? = extractColorsFromVideoFrameInternal(frameBitmap, trackId, videoUri, fallbackPrimary, fallbackSecondary, sampleIntervalMs)





    suspend fun extractColors(context: Context, track: Track?, fallbackPrimary: Color, fallbackSecondary: Color): ExtractedArtworkColors = extractColorsInternal(context, track, fallbackPrimary, fallbackSecondary)


    suspend fun extractPlaybackColors(context: Context, track: Track?, isVideoActive: Boolean, isDynamicEnabled: Boolean, fallbackPrimary: Color, fallbackSecondary: Color, positionMs: Long = 1000L): ExtractedArtworkColors = extractPlaybackColorsInternal(context, track, isVideoActive, isDynamicEnabled, fallbackPrimary, fallbackSecondary, positionMs)




    fun releaseRetriever() {
        synchronized(retrieverLock) {
            try {
                cachedRetriever?.release()
            } catch (_: Throwable) {}
            cachedRetriever = null
            cachedVideoUri = null
            cachedVideoDurationMs = 0L
        }
    }

    /**
     * Libera recursos pesados de extracción de video y poda la caché de memoria cuando
     * la aplicación pasa a segundo plano mientras el usuario juega, conservando el último
     * color válido de la pista actual para que al regresar no haya parpadeos.
     */
    fun trimForBackgroundGaming() {
        releaseRetriever()
        memoryCache.trimToSize(10)
    }

    fun clearCache() {
        memoryCache.evictAll()
        lastVideoTrackId = null
        lastVideoUri = null
        lastVideoColors = null
        _liveVideoColorsFlow.value = null
        releaseRetriever()
    }
}
