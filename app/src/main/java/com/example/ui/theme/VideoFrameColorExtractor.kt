package com.example.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.absoluteValue

/**
 * Paleta de fotogramas, retriever persistente y arte remoto.
 * Rol arquitectónico: extensiones internas de ArtworkColorExtractor; la API pública no cambia.
 */
/**
 * Extrae la paleta armónica a partir de un micro-bitmap (ej. 24x24) capturado en vivo del TextureView.
 * Si el fotograma corresponde a un fundido en negro total (luminancia casi nula), conserva el último
 * color válido del video para evitar parpadeos grises/apagados entre escenas.
 */
internal suspend fun ArtworkColorExtractor.extractColorsFromVideoFrameInternal(
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

        val primaryColor = boostColorVibrancyInternal(Color(primaryInt), minSaturation = 0.35f, minLightness = 0.42f)
        val secondaryColor = boostColorVibrancyInternal(Color(secondaryInt), minSaturation = 0.30f, minLightness = 0.48f)
        val accentColor = boostColorVibrancyInternal(Color(accentInt), minSaturation = 0.40f, minLightness = 0.45f)

        val result = ExtractedArtworkColors(
            primary = primaryColor,
            secondary = secondaryColor,
            accent = accentColor,
            ambientTopGlow = primaryColor.copy(alpha = 0.38f)
        )

        publishLiveVideoColorsInternal(trackId, videoUri, result, sampleIntervalMs)
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
internal fun ArtworkColorExtractor.boostColorVibrancyInternal(color: Color, minSaturation: Float, minLightness: Float): Color {
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

internal fun ArtworkColorExtractor.getOrCreateRetrieverInternal(context: Context, videoUri: String): MediaMetadataRetriever? {
    // Prohibir MediaMetadataRetriever sobre flujos HTTP/HTTPS remotos para evitar
    // abrir una segunda conexión de red y decodificar video por software en CPU (recalentamiento).
    if (videoUri.startsWith("http://", ignoreCase = true) ||
        videoUri.startsWith("https://", ignoreCase = true)
    ) {
        return null
    }
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
 * Descarga o recupera de la caché temporal (`cacheDir/streaming_media/artwork/`)
 * un micro-bitmap de una portada online (`http://` o `https://`) de YouTube / YT Music
 * para extraer su paleta cromática real al instante.
 */
internal suspend fun ArtworkColorExtractor.decodeRemoteArtworkBitmapInternal(context: Context, urlString: String, trackId: Long): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            val artworkDir = File(File(context.cacheDir, "streaming_media"), "artwork").apply {
                if (!exists()) mkdirs()
            }
            val cacheKey = urlString.hashCode().absoluteValue
            val cachedFile = File(artworkDir, "art_${trackId.absoluteValue}_$cacheKey.jpg")

            val options = BitmapFactory.Options().apply {
                inSampleSize = 2
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            if (cachedFile.exists() && cachedFile.length() > 256L) {
                BitmapFactory.decodeFile(cachedFile.absolutePath, options)?.let { return@withContext it }
            }

            var conn: java.net.HttpURLConnection? = null
            try {
                conn = (java.net.URL(urlString).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                }
                if (conn.responseCode in 200..299) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (bytes.size > 256) {
                        try {
                            cachedFile.writeBytes(bytes)
                        } catch (_: Throwable) {}
                        return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    }
                }
            } finally {
                conn?.disconnect()
            }
            null
        } catch (_: Throwable) {
            null
        }
    }
