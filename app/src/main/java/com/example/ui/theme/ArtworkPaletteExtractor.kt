package com.example.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import com.example.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.absoluteValue

/**
 * Extracción principal (carátula/video), fallback y paleta armónica.
 * Rol arquitectónico: extensiones internas de ArtworkColorExtractor; la API pública no cambia.
 */
/**
 * Extrae los colores de la carátula estática (local u online HTTP/HTTPS) o genera una paleta armónica si no existe.
 */
internal suspend fun ArtworkColorExtractor.extractColorsInternal(
    context: Context,
    track: Track?,
    fallbackPrimary: Color,
    fallbackSecondary: Color
): ExtractedArtworkColors = withContext(Dispatchers.Default) {
    if (track == null) {
        return@withContext createFallbackInternal(fallbackPrimary, fallbackSecondary)
    }

    // Consultar en caché rápida de carátula
    memoryCache.get(track.id)?.let { return@withContext it }

    var bitmap: Bitmap? = null
    try {
        val artPath = track.albumArtPath
        if (!artPath.isNullOrBlank()) {
            if (artPath.startsWith("http://", ignoreCase = true) ||
                artPath.startsWith("https://", ignoreCase = true)
            ) {
                bitmap = decodeRemoteArtworkBitmapInternal(context, artPath, track.id)
            } else {
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
        }

        // Si no se obtuvo de archivo privado ni de URL online, generar color armónico a partir de metadatos
        if (bitmap == null && track.uriString.isNotBlank()) {
            val hash = (track.title + track.artist).hashCode().absoluteValue
            return@withContext generateHarmonicPaletteFromHashInternal(hash, fallbackPrimary, fallbackSecondary).also {
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

            val primaryInt = vibrant?.rgb ?: lightVibrant?.rgb ?: dominant?.rgb ?: fallbackPrimary.toArgb()
            val secondaryInt = lightVibrant?.rgb ?: muted?.rgb ?: vibrant?.rgb ?: fallbackSecondary.toArgb()
            val accentInt = darkVibrant?.rgb ?: dominant?.rgb ?: primaryInt

            val primaryColor = boostColorVibrancyInternal(Color(primaryInt), minSaturation = 0.35f, minLightness = 0.42f)
            val secondaryColor = boostColorVibrancyInternal(Color(secondaryInt), minSaturation = 0.30f, minLightness = 0.48f)
            val accentColor = boostColorVibrancyInternal(Color(accentInt), minSaturation = 0.40f, minLightness = 0.45f)

            val result = ExtractedArtworkColors(
                primary = primaryColor,
                secondary = secondaryColor,
                accent = accentColor,
                ambientTopGlow = primaryColor.copy(alpha = 0.32f)
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

    val fallback = createFallbackInternal(fallbackPrimary, fallbackSecondary)
    memoryCache.put(track.id, fallback)
    return@withContext fallback
}

/**
 * Extrae los colores armónicos para la reproducción activa en Now Playing y Mini Reproductor.
 *
 * - Si el reproductor de video ya está emitiendo fotogramas en vivo por [liveVideoColorsFlow],
 *   retorna inmediatamente el color real de la escena actual.
 * - Si el video es online (`http://` o `https://`) y aún está cargando su primer fotograma,
 *   usa los colores extraídos de la miniatura oficial online sin abrir conexiones extra con MediaMetadataRetriever,
 *   y en cuanto el TextureView arranca, cambia cada 1 segundo siguiendo el video.
 * - Si el video es un archivo local y aún no hay muestra de TextureView, extrae del fotograma
 *   correspondiente a [positionMs] con [MediaMetadataRetriever.OPTION_CLOSEST].
 */
internal suspend fun ArtworkColorExtractor.extractPlaybackColorsInternal(
    context: Context,
    track: Track?,
    isVideoActive: Boolean,
    isDynamicEnabled: Boolean,
    fallbackPrimary: Color,
    fallbackSecondary: Color,
    positionMs: Long = 1000L
): ExtractedArtworkColors = withContext(Dispatchers.Default) {
    if (!isDynamicEnabled || track == null) {
        return@withContext createFallbackInternal(fallbackPrimary, fallbackSecondary)
    }

    val videoUri = track.videoUri
    if (isVideoActive && !videoUri.isNullOrBlank()) {
        // Si ya tenemos una muestra en tiempo real del TextureView para este mismo video (o para la misma pista en streaming), usarla al instante
        val liveSnapshot = _liveVideoColorsFlow.value
        if (liveSnapshot != null && (liveSnapshot.videoUri == videoUri || lastVideoTrackId == track.id)) {
            return@withContext liveSnapshot.colors
        }

        val isRemoteStreamVideo = videoUri.startsWith("http://", ignoreCase = true) ||
            videoUri.startsWith("https://", ignoreCase = true)

        if (!isRemoteStreamVideo) {
            val intervalMs = getAdaptiveSampleIntervalMsInternal(context)
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
                val retriever = getOrCreateRetrieverInternal(context, videoUri)
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

                        val extracted = extractColorsFromVideoFrameInternal(
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
        }

        if ((lastVideoUri == videoUri || lastVideoTrackId == track.id)) {
            val snapshot = lastVideoColors
            if (snapshot != null) return@withContext snapshot
        }
    }

    // Si no hay video o mientras el video online prepara su primer fotograma, extraer de la carátula oficial (local u online)
    return@withContext extractColorsInternal(context, track, fallbackPrimary, fallbackSecondary)
}

internal fun ArtworkColorExtractor.createFallbackInternal(primary: Color, secondary: Color): ExtractedArtworkColors {
    return ExtractedArtworkColors(
        primary = primary,
        secondary = secondary,
        accent = primary,
        ambientTopGlow = primary.copy(alpha = 0.22f)
    )
}

internal fun ArtworkColorExtractor.generateHarmonicPaletteFromHashInternal(
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
