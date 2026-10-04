package com.example.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
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
 * Extractor reactivo y optimizado de colores de carátula y video para Aura Music.
 * Utiliza AndroidX Palette en hilos de fondo ([Dispatchers.Default]) con decodificación
 * de bajo peso en memoria y caché LRU para transiciones fluidas a 60/120 FPS.
 *
 * Si Video Canvas está activo, extrae la paleta cromática directamente de un fotograma
 * clave del video, garantizando que el color de la carátula estática NO interfiera
 * ni choque con la atmósfera visual del video.
 */
object ArtworkColorExtractor {

    // Caché en memoria para almacenar las paletas de carátulas y videos recientes
    private val memoryCache = LruCache<Long, ExtractedArtworkColors>(50)

    // Retriever persistente para reutilizar el descriptor de video y evitar sobrecarga I/O cada 250ms
    private val retrieverLock = Any()
    private var cachedVideoUri: String? = null
    private var cachedRetriever: MediaMetadataRetriever? = null

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

            return try {
                val retriever = MediaMetadataRetriever()
                if (videoUri.startsWith("content://")) {
                    retriever.setDataSource(context, Uri.parse(videoUri))
                } else {
                    retriever.setDataSource(videoUri)
                }
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
     * Extrae los colores armónicos para la reproducción activa en pantalla Now Playing.
     * Si Video Canvas está activo y la pista cuenta con video, extrae la paleta cromática
     * dinámicamente de los fotogramas del video en intervalos de ~2.5 segundos según la posición de reproducción,
     * permitiendo que la atmósfera lumínica evolucione con el ritmo y las escenas del video.
     * Si Video Canvas está desactivado o la pista no tiene video, extrae los colores de la carátula.
     * Si el usuario desactivó la opción en Ajustes, retorna la paleta predeterminada del tema.
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

        // Si el Video Canvas está en pantalla y hay video configurado: extraer color del fotograma del video
        val videoUri = track.videoUri
        if (isVideoActive && !videoUri.isNullOrBlank()) {
            val interval = (positionMs / 250L).coerceAtLeast(0L)
            val videoCacheKey = -(track.id.absoluteValue * 100_000L + interval)
            memoryCache.get(videoCacheKey)?.let { return@withContext it }

            var frameBitmap: Bitmap? = null
            try {
                val retriever = getOrCreateRetriever(context, videoUri)
                if (retriever != null) {
                    val timeUs = (positionMs * 1000L).coerceAtLeast(0L)
                    // OPTION_CLOSEST extrae el fotograma exacto del milisegundo actual (sin esperar al keyframe cada 2.5s)
                    frameBitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                        try {
                            retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, 32, 32)
                        } catch (_: Throwable) {
                            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        }
                    } else {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    } ?: retriever.frameAtTime

                    if (frameBitmap != null) {
                        // Si no se usó escalamiento nativo por hardware, reescalar a 32x32
                        val scaled = if (frameBitmap.width > 32 || frameBitmap.height > 32) {
                            Bitmap.createScaledBitmap(frameBitmap, 32, 32, false).also {
                                if (it != frameBitmap) frameBitmap.recycle()
                            }
                        } else {
                            frameBitmap
                        }

                        val palette = Palette.from(scaled).maximumColorCount(6).generate()
                        scaled.recycle()

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
                            ambientTopGlow = primaryColor.copy(alpha = 0.35f)
                        )
                        memoryCache.put(videoCacheKey, result)
                        return@withContext result
                    }
                }
            } catch (_: Throwable) {
                // Si falla la lectura del fotograma de video, continuar al fallback
            }
        }

        // Si no hay video o no está activo el Canvas, extraer de la carátula
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

    fun clearCache() {
        memoryCache.evictAll()
        synchronized(retrieverLock) {
            try {
                cachedRetriever?.release()
            } catch (_: Throwable) {}
            cachedRetriever = null
            cachedVideoUri = null
        }
    }
}
