package com.example.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
 * Paleta de colores dinámicos extraída de la carátula de una pista.
 * Permite ambientar la interfaz (Aura luminosa, barra de progreso, visualizador)
 * con los tonos predominantes de cada álbum sin perder la estética OLED profunda.
 */
data class ExtractedArtworkColors(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val ambientTopGlow: Color
)

/**
 * Extractor reactivo y optimizado de colores de carátula para Aura Music.
 * Utiliza AndroidX Palette en hilos de fondo ([Dispatchers.Default]) con decodificación
 * de bajo peso en memoria y caché LRU para transiciones fluidas a 60/120 FPS.
 */
object ArtworkColorExtractor {

    // Caché en memoria para almacenar las paletas de las últimas 50 canciones reproducidas
    private val memoryCache = LruCache<Long, ExtractedArtworkColors>(50)

    suspend fun extractColors(
        context: Context,
        track: Track?,
        fallbackPrimary: Color,
        fallbackSecondary: Color
    ): ExtractedArtworkColors = withContext(Dispatchers.Default) {
        if (track == null) {
            return@withContext createFallback(fallbackPrimary, fallbackSecondary)
        }

        // Consultar en caché rápida
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
    }
}
