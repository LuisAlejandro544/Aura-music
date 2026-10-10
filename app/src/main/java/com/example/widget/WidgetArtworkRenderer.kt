package com.example.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import com.example.model.WidgetConfig
import com.example.model.WidgetPersistedState
import com.example.model.WidgetQuickItem
import java.io.File
import kotlin.math.abs

/**
 * Motor gráfico de renderizado de Bitmaps para los Widgets de Aura Music.
 *
 * Responsabilidades:
 * - Generar fondos dinámicos Neo-Glass OLED con degradados suaves, halo superior e iluminación
 *   de borde basados en los colores extraídos de la imagen (carátula) de la canción actual.
 * - Renderizar el botón principal Play/Pausa y la barra de progreso sincronizada con el acento cromático.
 * - Renderizar carátulas redondeadas WebP, collages 2x2 para Playlists y arte procedural matemático.
 */
object WidgetArtworkRenderer {

    private const val BASE_OLED_DARK = 0xFF0A0E17.toInt()
    private const val BASE_OLED_SURFACE = 0xFF131B2E.toInt()

    /**
     * Genera el Bitmap de fondo redondeado del Widget reaccionando a los colores de la imagen
     * cuando [config.isDynamicColorEnabled] está activo, o usando la paleta oscura Neo-Glass base.
     */
    fun createDynamicBackgroundBitmap(
        widthPx: Int,
        heightPx: Int,
        state: WidgetPersistedState,
        config: WidgetConfig,
        cornerRadiusPx: Float = 44f
    ): Bitmap {
        val w = widthPx.coerceIn(240, 1400)
        val h = heightPx.coerceIn(100, 1000)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val intensity = (config.colorIntensityPercent / 100f).coerceIn(0.3f, 1.0f)
        val primary = if (config.isDynamicColorEnabled) state.primaryColorInt else 0xFF8B5CF6.toInt()
        val secondary = if (config.isDynamicColorEnabled) state.secondaryColorInt else 0xFF06B6D4.toInt()

        val topLeftColor = ColorUtils.blendARGB(BASE_OLED_SURFACE, primary, if (config.isDynamicColorEnabled) 0.42f * intensity else 0.18f)
        val midColor = ColorUtils.blendARGB(BASE_OLED_DARK, primary, if (config.isDynamicColorEnabled) 0.22f * intensity else 0.08f)
        val bottomRightColor = ColorUtils.blendARGB(BASE_OLED_DARK, secondary, if (config.isDynamicColorEnabled) 0.34f * intensity else 0.14f)

        val rect = RectF(2f, 2f, w - 2f, h - 2f)

        // 1. Degradado diagonal principal Neo-Glass
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(topLeftColor, midColor, bottomRightColor),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, bgPaint)

        // 2. Halo radial ambiental en la esquina superior izquierda (donde se ubica la carátula)
        if (config.isDynamicColorEnabled) {
            val glowColor = ColorUtils.setAlphaComponent(primary, (95 * intensity).toInt().coerceIn(20, 130))
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * 0.18f,
                    h * 0.30f,
                    (w * 0.55f).coerceAtLeast(120f),
                    glowColor,
                    0x00000000,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, glowPaint)
        }

        // 3. Borde luminoso sutil alrededor del Widget
        val borderColor = ColorUtils.blendARGB(
            0x33FFFFFF,
            primary,
            if (config.isDynamicColorEnabled) 0.55f * intensity else 0.25f
        )
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(borderColor, 115)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, borderPaint)

        return bitmap
    }

    /**
     * Genera el botón circular de Play/Pausa teñido con el color vibrante de la carátula actual.
     */
    fun createPlayButtonBackground(
        sizePx: Int,
        state: WidgetPersistedState,
        config: WidgetConfig
    ): Bitmap {
        val s = sizePx.coerceIn(72, 220)
        val bitmap = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val primary = if (config.isDynamicColorEnabled) state.primaryColorInt else 0xFF8B5CF6.toInt()
        val secondary = if (config.isDynamicColorEnabled) state.secondaryColorInt else 0xFF06B6D4.toInt()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, s.toFloat(), s.toFloat(),
                primary,
                ColorUtils.blendARGB(primary, secondary, 0.45f),
                Shader.TileMode.CLAMP
            )
        }
        val radius = (s / 2f) - 2f
        canvas.drawCircle(s / 2f, s / 2f, radius, paint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawCircle(s / 2f, s / 2f, radius - 1f, ringPaint)

        return bitmap
    }

    /**
     * Genera la barra de progreso horizontal con bolita clásica luminosa y color dinámico de la imagen.
     */
    fun createProgressBarBitmap(
        widthPx: Int,
        heightPx: Int,
        progressPercent: Int,
        state: WidgetPersistedState,
        config: WidgetConfig
    ): Bitmap {
        val w = widthPx.coerceIn(200, 1200)
        val h = heightPx.coerceIn(18, 48)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val primary = if (config.isDynamicColorEnabled) state.primaryColorInt else 0xFF8B5CF6.toInt()
        val secondary = if (config.isDynamicColorEnabled) state.secondaryColorInt else 0xFF06B6D4.toInt()

        val trackHeight = (h * 0.34f).coerceAtLeast(6f)
        val centerY = h / 2f
        val thumbRadius = (h * 0.42f).coerceAtLeast(8f)
        val horizontalPad = thumbRadius + 2f
        val usableWidth = (w - horizontalPad * 2f).coerceAtLeast(10f)
        val fraction = (progressPercent.coerceIn(0, 100) / 100f)
        val activeX = horizontalPad + usableWidth * fraction

        // Pista de fondo inactiva
        val bgTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x38FFFFFF
            style = Paint.Style.FILL
        }
        val bgRect = RectF(
            horizontalPad,
            centerY - trackHeight / 2f,
            w - horizontalPad,
            centerY + trackHeight / 2f
        )
        canvas.drawRoundRect(bgRect, trackHeight / 2f, trackHeight / 2f, bgTrackPaint)

        // Pista activa con degradado del color de la imagen
        val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                horizontalPad, 0f, activeX.coerceAtLeast(horizontalPad + 8f), 0f,
                primary,
                secondary,
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        val activeRect = RectF(
            horizontalPad,
            centerY - trackHeight / 2f,
            activeX.coerceAtLeast(horizontalPad + trackHeight),
            centerY + trackHeight / 2f
        )
        canvas.drawRoundRect(activeRect, trackHeight / 2f, trackHeight / 2f, activePaint)

        // Halo y bolita clásica blanca en la posición actual
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(primary, 110)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(activeX, centerY, thumbRadius, haloPaint)

        val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawCircle(activeX, centerY, thumbRadius * 0.68f, thumbPaint)

        return bitmap
    }

    /**
     * Carga la carátula WebP de la canción o dibuja una carátula procedural redondeada.
     */
    fun loadOrGenerateTrackArt(
        artPath: String?,
        title: String,
        artist: String,
        sizePx: Int = 220
    ): Bitmap {
        if (!artPath.isNullOrBlank()) {
            try {
                val file = File(artPath)
                if (file.exists() && file.length() > 0L) {
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = if (sizePx > 240) 1 else 2
                    }
                    val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
                    if (decoded != null) {
                        return createRoundedBitmap(decoded, sizePx)
                    }
                }
            } catch (_: Throwable) {}
        }
        return generateProceduralBitmap(title, artist, sizePx)
    }

    /**
     * Renderiza la portada de un elemento del segundo Widget (Canción o Playlist con collage 2x2).
     */
    fun loadQuickItemArt(item: WidgetQuickItem, sizePx: Int = 160): Bitmap {
        if (item.isPlaylist && item.collageArtPaths.size >= 2 && item.primaryArtPath.isNullOrBlank()) {
            return createCollageBitmap(item.collageArtPaths, item.title, sizePx)
        }
        if (item.isPlaylist && item.collageArtPaths.size >= 4) {
            // Si no tiene portada personalizada pero sí 4 canciones con portada, armar collage 2x2
            val firstIsSameAsCollage = item.primaryArtPath == item.collageArtPaths.firstOrNull()
            if (firstIsSameAsCollage) {
                return createCollageBitmap(item.collageArtPaths, item.title, sizePx)
            }
        }
        return loadOrGenerateTrackArt(item.primaryArtPath, item.title, item.subtitle, sizePx)
    }

    private fun createCollageBitmap(paths: List<String>, fallbackTitle: String, sizePx: Int): Bitmap {
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val half = sizePx / 2
        val gap = 2

        val bitmaps = paths.take(4).mapNotNull { path ->
            try {
                val f = File(path)
                if (f.exists()) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeFile(f.absolutePath, opts)
                } else null
            } catch (_: Throwable) {
                null
            }
        }

        if (bitmaps.isEmpty()) {
            return generateProceduralBitmap(fallbackTitle, "Playlist", sizePx)
        }

        val tempCanvasBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(tempCanvasBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val positions = arrayOf(
            RectF(0f, 0f, (half - gap).toFloat(), (half - gap).toFloat()),
            RectF((half + gap).toFloat(), 0f, sizePx.toFloat(), (half - gap).toFloat()),
            RectF(0f, (half + gap).toFloat(), (half - gap).toFloat(), sizePx.toFloat()),
            RectF((half + gap).toFloat(), (half + gap).toFloat(), sizePx.toFloat(), sizePx.toFloat())
        )

        for (i in 0 until 4) {
            val src = bitmaps[i % bitmaps.size]
            val scaled = Bitmap.createScaledBitmap(src, half, half, true)
            tempCanvas.drawBitmap(scaled, null, positions[i], paint)
        }

        val rounded = createRoundedBitmap(tempCanvasBitmap, sizePx)
        tempCanvasBitmap.recycle()
        return rounded
    }

    fun createRoundedBitmap(source: Bitmap, targetSize: Int): Bitmap {
        val scaled = Bitmap.createScaledBitmap(source, targetSize, targetSize, true)
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = targetSize * 0.18f
        canvas.drawRoundRect(RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat()), radius, radius, paint)
        if (scaled != source) {
            scaled.recycle()
        }
        return output
    }

    fun generateProceduralBitmap(title: String, artist: String, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val hash = abs((title + artist).hashCode())

        val palettes = arrayOf(
            intArrayOf(0xFF4C1D95.toInt(), 0xFF7C3AED.toInt(), 0xFFEC4899.toInt()),
            intArrayOf(0xFF0F172A.toInt(), 0xFF1D4ED8.toInt(), 0xFF06B6D4.toInt()),
            intArrayOf(0xFF064E3B.toInt(), 0xFF059669.toInt(), 0xFF10B981.toInt()),
            intArrayOf(0xFF7F1D1D.toInt(), 0xFFDC2626.toInt(), 0xFFF59E0B.toInt())
        )
        val palette = palettes[hash % palettes.size]

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                palette[0], palette[2],
                Shader.TileMode.CLAMP
            )
        }
        val radius = size * 0.18f
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, bgPaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = (size * 0.02f).coerceAtLeast(2f)
        }
        canvas.drawCircle(size * 0.5f, size * 0.5f, size * 0.28f, ringPaint)
        canvas.drawCircle(size * 0.5f, size * 0.5f, size * 0.10f, ringPaint)

        return bitmap
    }
}
