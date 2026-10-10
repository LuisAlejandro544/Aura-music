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
import com.example.model.Track
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
 * - Adapta automáticamente la frecuencia de muestreo según el estado de batería del teléfono:
 *   • **180 ms** cuando el dispositivo tiene **> 15% de batería** y **no está en modo ahorro de energía**.
 *   • **800 ms** cuando el dispositivo tiene **<= 15% de batería** o **tiene activado el ahorro de batería**.
 * - Si el TextureView aún no ha renderizado su primer cuadro, extrae directamente del fotograma real
 *   del video con [MediaMetadataRetriever.OPTION_CLOSEST] sin dejar que la carátula estática interfiera.
 */
object ArtworkColorExtractor {

    const val INTERVAL_NORMAL_MS = 180L
    const val INTERVAL_BATTERY_SAVER_MS = 800L

    // Caché en memoria para almacenar las paletas de carátulas y buckets temporales de video
    private val memoryCache = LruCache<Long, ExtractedArtworkColors>(80)

    // Flujo compartido en tiempo real para sincronizar NowPlayingScreen y MiniPlayer al instante
    private val _liveVideoColorsFlow = MutableStateFlow<LiveVideoColorSnapshot?>(null)
    val liveVideoColorsFlow: StateFlow<LiveVideoColorSnapshot?> = _liveVideoColorsFlow.asStateFlow()

    // Retriever persistente para reutilizar el descriptor de video y evitar sobrecarga I/O
    private val retrieverLock = Any()
    private var cachedVideoUri: String? = null
    private var cachedRetriever: MediaMetadataRetriever? = null
    private var cachedVideoDurationMs: Long = 0L

    // Memoria persistente del último color válido extraído de video para evitar apagones en fundidos negros
    @Volatile
    private var lastVideoTrackId: Long? = null
    @Volatile
    private var lastVideoUri: String? = null
    @Volatile
    private var lastVideoColors: ExtractedArtworkColors? = null

    /**
     * Determina el intervalo de muestreo cromático en tiempo real según el estado de batería y ahorro de energía:
     * - 180 ms si la batería es > 15% y el Modo Ahorro de Batería está desactivado.
     * - 800 ms si la batería es <= 15% o el Modo Ahorro de Batería está activado.
     */
    fun getAdaptiveSampleIntervalMs(context: Context): Long {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isPowerSave = powerManager?.isPowerSaveMode == true
            if (isPowerSave) {
                return INTERVAL_BATTERY_SAVER_MS
            }

            val batteryStatus: Intent? = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) {
                (level * 100) / scale
            } else {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            }

            if (batteryPct in 0..15) {
                INTERVAL_BATTERY_SAVER_MS
            } else {
                INTERVAL_NORMAL_MS
            }
        } catch (_: Throwable) {
            INTERVAL_NORMAL_MS
        }
    }

    /**
     * Publica una nueva instantánea de colores extraída en vivo desde el reproductor de video.
     */
    fun publishLiveVideoColors(
        trackId: Long?,
        videoUri: String,
        colors: ExtractedArtworkColors,
        sampleIntervalMs: Long
    ) {
        if (trackId != null) {
            lastVideoTrackId = trackId
        }
        lastVideoUri = videoUri
        lastVideoColors = colors
        _liveVideoColorsFlow.value = LiveVideoColorSnapshot(
            videoUri = videoUri,
            colors = colors,
            sampleIntervalMs = sampleIntervalMs
        )
    }

    /**
     * Extrae la paleta armónica a partir de un micro-bitmap (ej. 24x24) capturado en vivo del TextureView.
     * Si el fotograma corresponde a un fundido en negro total (luminancia casi nula), conserva el último
     * color válido del video para evitar parpadeos grises/apagados entre escenas.
     */
    suspend fun extractColorsFromVideoFrame(
        frameBitmap: Bitmap,
        trackId: Long?,
        videoUri: String,
        fallbackPrimary: Color,
        fallbackSecondary: Color,
        sampleIntervalMs: Long = INTERVAL_NORMAL_MS
    ): ExtractedArtworkColors? = withContext(Dispatchers.Default) {
        try {
            if (frameBitmap.isRecycled || frameBitmap.width <= 0 || frameBitmap.height <= 0) {
                return@withContext null
            }

            // Verificar que el cuadro no sea completamente transparente o negro puro (fade-out)
            val w = frameBitmap.width
            val h = frameBitmap.height
            val pixels = IntArray(w * h)
            frameBitmap.getPixels(pixels, 0, w, 0, 0, w, h)

            var nonBlackPixels = 0
            var totalBrightness = 0L
            for (px in pixels) {
                val a = (px ushr 24) and 0xFF
                if (a > 32) {
                    val r = (px ushr 16) and 0xFF
                    val g = (px ushr 8) and 0xFF
                    val b = px and 0xFF
                    val luma = (r * 3 + g * 4 + b) shr 3
                    totalBrightness += luma
                    if (luma > 14) {
                        nonBlackPixels++
                    }
                }
            }

            // Si el cuadro está casi totalmente en negro (ej. inicio/fin o corte), mantener el último color vivo
            if (nonBlackPixels < (pixels.size / 12) || (totalBrightness / pixels.size.coerceAtLeast(1)) < 8L) {
                return@withContext lastVideoColors
            }

            val palette = Palette.from(frameBitmap)
                .maximumColorCount(8)
                .clearFilters()
                .generate()

            val vibrant = palette.vibrantSwatch
            val lightVibrant = palette.lightVibrantSwatch
            val dominant = palette.dominantSwatch
            val darkVibrant = palette.darkVibrantSwatch
            val muted = palette.mutedSwatch
            val lightMuted = palette.lightMutedSwatch

            val primaryInt = vibrant?.rgb
                ?: lightVibrant?.rgb
                ?: dominant?.rgb
                ?: muted?.rgb
                ?: darkVibrant?.rgb
                ?: return@withContext lastVideoColors

            val secondaryInt = lightVibrant?.rgb
                ?: vibrant?.rgb
                ?: lightMuted?.rgb
                ?: muted?.rgb
                ?: dominant?.rgb
                ?: primaryInt

            val accentInt = vibrant?.rgb
                ?: darkVibrant?.rgb
                ?: dominant?.rgb
                ?: primaryInt

            val primaryColor = boostColorVibrancy(Color(primaryInt), minSaturation = 0.35f, minLightness = 0.42f)
            val secondaryColor = boostColorVibrancy(Color(secondaryInt), minSaturation = 0.30f, minLightness = 0.48f)
            val accentColor = boostColorVibrancy(Color(accentInt), minSaturation = 0.40f, minLightness = 0.45f)

            val result = ExtractedArtworkColors(
                primary = primaryColor,
                secondary = secondaryColor,
                accent = accentColor,
                ambientTopGlow = primaryColor.copy(alpha = 0.38f)
            )

            publishLiveVideoColors(trackId, videoUri, result, sampleIntervalMs)
            return@withContext result
        } catch (_: Throwable) {
            return@withContext lastVideoColors
        } finally {
            try {
                if (!frameBitmap.isRecycled) {
                    frameBitmap.recycle()
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Garantiza que los colores extraídos de escenas oscuras de video mantengan suficiente
     * saturación y brillo neón sobre el fondo OLED de Aura Music.
     */
    private fun boostColorVibrancy(color: Color, minSaturation: Float, minLightness: Float): Color {
        val hsl = FloatArray(3)
        val argb = android.graphics.Color.argb(
            255,
            (color.red * 255f).toInt().coerceIn(0, 255),
            (color.green * 255f).toInt().coerceIn(0, 255),
            (color.blue * 255f).toInt().coerceIn(0, 255)
        )
        androidx.core.graphics.ColorUtils.colorToHSL(argb, hsl)
        if (hsl[1] > 0.06f && hsl[1] < minSaturation) {
            hsl[1] = minSaturation
        }
        if (hsl[2] < minLightness) {
            hsl[2] = minLightness
        } else if (hsl[2] > 0.82f) {
            hsl[2] = 0.82f
        }
        return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
    }

    private fun getOrCreateRetriever(context: Context, videoUri: String): MediaMetadataRetriever? {
        synchronized(retrieverLock) {
            if (cachedVideoUri == videoUri && cachedRetriever != null) {
                return cachedRetriever
            }
            try {
                cachedRetriever?.release()
            } catch (_: Throwable) {}
            cachedRetriever = null
            cachedVideoUri = null
            cachedVideoDurationMs = 0L

            return try {
                val retriever = MediaMetadataRetriever()
                if (videoUri.startsWith("content://")) {
                    retriever.setDataSource(context, Uri.parse(videoUri))
                } else {
                    retriever.setDataSource(videoUri)
                }
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                cachedVideoDurationMs = durStr?.toLongOrNull() ?: 0L
                cachedVideoUri = videoUri
                cachedRetriever = retriever
                retriever
            } catch (_: Throwable) {
                null
            }
        }
    }

    /**
     * Extrae los colores de la carátula estática o genera una paleta armónica si no existe.
     */
    suspend fun extractColors(
        context: Context,
        track: Track?,
        fallbackPrimary: Color,
        fallbackSecondary: Color
    ): ExtractedArtworkColors = withContext(Dispatchers.Default) {
        if (track == null) {
            return@withContext createFallback(fallbackPrimary, fallbackSecondary)
        }

        // Consultar en caché rápida de carátula
        memoryCache.get(track.id)?.let { return@withContext it }

        var bitmap: Bitmap? = null
        try {
            val artPath = track.albumArtPath
            if (!artPath.isNullOrBlank()) {
                val file = File(artPath)
                if (file.exists() && file.length() > 0) {
                    val options = BitmapFactory.Options().apply {
                        // Submuestreo rápido para decodificar a baja resolución (ej: ~96x96)
                        inSampleSize = 4
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                }
            }

            // Si no se obtuvo de archivo privado, intentar leer de SAF si es una URI de imagen
            if (bitmap == null && track.uriString.isNotBlank()) {
                // Generar color a partir de metadatos si no hay portada física
                val hash = (track.title + track.artist).hashCode().absoluteValue
                return@withContext generateHarmonicPaletteFromHash(hash, fallbackPrimary, fallbackSecondary).also {
                    memoryCache.put(track.id, it)
                }
            }

            if (bitmap != null) {
                val palette = Palette.from(bitmap)
                    .maximumColorCount(16)
                    .generate()

                val vibrant = palette.vibrantSwatch
                val dominant = palette.dominantSwatch
                val lightVibrant = palette.lightVibrantSwatch
                val darkVibrant = palette.darkVibrantSwatch
                val muted = palette.mutedSwatch

                val primaryInt = vibrant?.rgb ?: dominant?.rgb ?: lightVibrant?.rgb ?: fallbackPrimary.hashCode()
                val secondaryInt = lightVibrant?.rgb ?: muted?.rgb ?: vibrant?.rgb ?: fallbackSecondary.hashCode()
                val accentInt = darkVibrant?.rgb ?: dominant?.rgb ?: primaryInt

                val primaryColor = Color(primaryInt)
                val secondaryColor = Color(secondaryInt)
                val accentColor = Color(accentInt)

                val result = ExtractedArtworkColors(
                    primary = primaryColor,
                    secondary = secondaryColor,
                    accent = accentColor,
                    ambientTopGlow = primaryColor.copy(alpha = 0.28f)
                )

                memoryCache.put(track.id, result)
                return@withContext result
            }
        } catch (_: Throwable) {
            // Ignorar y caer en fallback en caso de cualquier error
        } finally {
            try {
                bitmap?.recycle()
            } catch (_: Throwable) {}
        }

        val fallback = createFallback(fallbackPrimary, fallbackSecondary)
        memoryCache.put(track.id, fallback)
        return@withContext fallback
    }

    /**
     * Extrae los colores armónicos para la reproducción activa en Now Playing y Mini Reproductor.
     *
     * - Si el reproductor de video ya está emitiendo fotogramas en vivo por [liveVideoColorsFlow],
     *   retorna inmediatamente el color real de la escena actual.
     * - Si el video acaba de iniciar o está en un modo donde aún no hay muestra de TextureView,
     *   extrae del fotograma correspondiente a [positionMs] con [MediaMetadataRetriever.OPTION_CLOSEST]
     *   sin ser bloqueado jamás por la carátula estática WebP.
     */
    suspend fun extractPlaybackColors(
        context: Context,
        track: Track?,
        isVideoActive: Boolean,
        isDynamicEnabled: Boolean,
        fallbackPrimary: Color,
        fallbackSecondary: Color,
        positionMs: Long = 1000L
    ): ExtractedArtworkColors = withContext(Dispatchers.Default) {
        if (!isDynamicEnabled || track == null) {
            return@withContext createFallback(fallbackPrimary, fallbackSecondary)
        }

        val videoUri = track.videoUri
        if (isVideoActive && !videoUri.isNullOrBlank()) {
            // Si ya tenemos una muestra en tiempo real del TextureView para este mismo video, usarla al instante
            val liveSnapshot = _liveVideoColorsFlow.value
            if (liveSnapshot != null && liveSnapshot.videoUri == videoUri) {
                return@withContext liveSnapshot.colors
            }

            val intervalMs = getAdaptiveSampleIntervalMs(context)
            val timeBucket = (positionMs.coerceAtLeast(0L) / intervalMs)
            val videoCacheKey = -(
                track.id.absoluteValue * 1_000_000L +
                    ((videoUri.hashCode().absoluteValue % 999L) * 1_000L) +
                    (timeBucket % 1_000L)
            )
            memoryCache.get(videoCacheKey)?.let { cachedResult ->
                lastVideoTrackId = track.id
                lastVideoUri = videoUri
                lastVideoColors = cachedResult
                return@withContext cachedResult
            }

            var frameBitmap: Bitmap? = null
            try {
                val retriever = getOrCreateRetriever(context, videoUri)
                val durationMs = cachedVideoDurationMs
                if (retriever != null) {
                    val effectivePosMs = when {
                        durationMs > 0L && track.isVideoLoop -> (positionMs % durationMs).coerceAtLeast(0L)
                        durationMs > 0L -> positionMs.coerceIn(0L, durationMs)
                        else -> positionMs.coerceAtLeast(0L)
                    }
                    val sampleTimeUs = effectivePosMs * 1000L
                    frameBitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                        try {
                            retriever.getScaledFrameAtTime(sampleTimeUs, MediaMetadataRetriever.OPTION_CLOSEST, 28, 28)
                        } catch (_: Throwable) {
                            retriever.getScaledFrameAtTime(sampleTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 28, 28)
                        }
                    } else {
                        try {
                            retriever.getFrameAtTime(sampleTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        } catch (_: Throwable) {
                            retriever.getFrameAtTime(sampleTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        }
                    } ?: retriever.frameAtTime

                    if (frameBitmap != null) {
                        val scaled = if (frameBitmap.width > 28 || frameBitmap.height > 28) {
                            Bitmap.createScaledBitmap(frameBitmap, 28, 28, false).also {
                                if (it != frameBitmap) frameBitmap.recycle()
                            }
                        } else {
                            frameBitmap
                        }

                        val extracted = extractColorsFromVideoFrame(
                            frameBitmap = scaled,
                            trackId = track.id,
                            videoUri = videoUri,
                            fallbackPrimary = fallbackPrimary,
                            fallbackSecondary = fallbackSecondary,
                            sampleIntervalMs = intervalMs
                        )
                        if (extracted != null) {
                            memoryCache.put(videoCacheKey, extracted)
                            return@withContext extracted
                        }
                    }
                }
            } catch (_: Throwable) {
                releaseRetriever()
            }

            if (lastVideoUri == videoUri && lastVideoColors != null) {
                return@withContext lastVideoColors!!
            }
        }

        // Si no hay video o no está activo el Canvas, extraer de la carátula estática
        return@withContext extractColors(context, track, fallbackPrimary, fallbackSecondary)
    }

    private fun createFallback(primary: Color, secondary: Color): ExtractedArtworkColors {
        return ExtractedArtworkColors(
            primary = primary,
            secondary = secondary,
            accent = primary,
            ambientTopGlow = primary.copy(alpha = 0.22f)
        )
    }

    private fun generateHarmonicPaletteFromHash(
        hash: Int,
        fallbackPrimary: Color,
        fallbackSecondary: Color
    ): ExtractedArtworkColors {
        // Paletas armónicas procedurales para canciones sin carátula
        val hues = listOf(
            Color(0xFF8B5CF6) to Color(0xFF06B6D4), // Violeta a Cyan
            Color(0xFFEC4899) to Color(0xFF8B5CF6), // Magenta a Violeta
            Color(0xFF10B981) to Color(0xFF06B6D4), // Esmeralda a Menta
            Color(0xFFF59E0B) to Color(0xFFEF4444), // Ámbar a Rojo
            Color(0xFF3B82F6) to Color(0xFF6366F1), // Azul a Índigo
            Color(0xFF14B8A6) to Color(0xFF3B82F6)  // Turquesa a Zafiro
        )
        val selected = hues[hash % hues.size]
        return ExtractedArtworkColors(
            primary = selected.first,
            secondary = selected.second,
            accent = selected.first,
            ambientTopGlow = selected.first.copy(alpha = 0.25f)
        )
    }

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

    fun clearCache() {
        memoryCache.evictAll()
        lastVideoTrackId = null
        lastVideoUri = null
        lastVideoColors = null
        _liveVideoColorsFlow.value = null
        releaseRetriever()
    }
}
