package com.example.data.importer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generador procedural de carátulas de audio.
 * Genera obras de arte digitales únicas, geométricas y con gradientes de neón
 * basadas en el título y artista de la pista, eliminando el uso de imágenes fijas
 * o artificiales genéricas.
 */
object ProceduralArtworkGenerator {

    // Paletas de degradados de estilo Dark Luxury & Neón
    private val COLOR_PALETTES = listOf(
        // Nebula Violet
        Pair(Color.parseColor("#7928CA"), Color.parseColor("#00DFD8")),
        // Cyber Mint
        Pair(Color.parseColor("#059669"), Color.parseColor("#10B981")),
        // Sunset Ember
        Pair(Color.parseColor("#E11D48"), Color.parseColor("#F59E0B")),
        // Ocean Abyss
        Pair(Color.parseColor("#1E40AF"), Color.parseColor("#06B6D4")),
        // Synthwave Dream
        Pair(Color.parseColor("#9333EA"), Color.parseColor("#EC4899")),
        // Deep Space Gold
        Pair(Color.parseColor("#D97706"), Color.parseColor("#FBBF24")),
        // Electric Azure
        Pair(Color.parseColor("#2563EB"), Color.parseColor("#38BDF8")),
        // Acid Lime Neon
        Pair(Color.parseColor("#4D7C0F"), Color.parseColor("#A3E635"))
    )

    fun getPaletteFor(title: String, artist: String): Pair<Int, Int> {
        val hash = abs((title + artist).hashCode())
        return COLOR_PALETTES[hash % COLOR_PALETTES.size]
    }

    /**
     * Genera un Bitmap procedural de 512x512 con alta fidelidad gráfica.
     */
    fun generateBitmap(title: String, artist: String, size: Int = 512): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (primaryColor, secondaryColor) = getPaletteFor(title, artist)
        val hash = abs((title + artist).hashCode())

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Fondo degradado profundo
        val bgShader = LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            intArrayOf(Color.parseColor("#0B0F19"), primaryColor, Color.parseColor("#050811")),
            floatArrayOf(0.0f, 0.55f, 1.0f),
            Shader.TileMode.CLAMP
        )
        paint.shader = bgShader
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        // Resplandor radial ambiental
        val radialShader = RadialGradient(
            size * 0.5f, size * 0.5f, size * 0.45f,
            intArrayOf(secondaryColor, Color.TRANSPARENT),
            floatArrayOf(0.1f, 1.0f),
            Shader.TileMode.CLAMP
        )
        paint.shader = radialShader
        paint.alpha = 110
        canvas.drawCircle(size * 0.5f, size * 0.5f, size * 0.45f, paint)

        // Círculos concéntricos de vinilo / radar de audio
        paint.shader = null
        paint.style = Paint.Style.STROKE
        val ringCount = 6 + (hash % 4)
        for (i in 1..ringCount) {
            val radius = (size * 0.15f) + (i * (size * 0.28f / ringCount))
            paint.color = if (i % 2 == 0) secondaryColor else primaryColor
            paint.strokeWidth = 2f + (i % 3)
            paint.alpha = 60 + (i * 20).coerceAtMost(160)
            canvas.drawCircle(size * 0.5f, size * 0.5f, radius, paint)
        }

        // Rayos geométricos de frecuencia
        val rayCount = 8 + (hash % 8)
        val center = size * 0.5f
        for (i in 0 until rayCount) {
            val angle = (i * 2.0 * Math.PI / rayCount)
            val startR = size * 0.22f
            val endR = size * 0.40f
            val x1 = center + (startR * cos(angle)).toFloat()
            val y1 = center + (startR * sin(angle)).toFloat()
            val x2 = center + (endR * cos(angle)).toFloat()
            val y2 = center + (endR * sin(angle)).toFloat()

            paint.color = primaryColor
            paint.strokeWidth = 3f
            paint.alpha = 90
            canvas.drawLine(x1, y1, x2, y2, paint)
        }

        // Disco central de vinilo
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#121826")
        paint.alpha = 240
        canvas.drawCircle(center, center, size * 0.16f, paint)

        // Borde luminoso del disco central
        paint.style = Paint.Style.STROKE
        paint.color = secondaryColor
        paint.strokeWidth = 4f
        paint.alpha = 220
        canvas.drawCircle(center, center, size * 0.16f, paint)

        // Inicial o símbolo musical centrado
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = size * 0.13f
        paint.textAlign = Paint.Align.CENTER
        val initialLetter = title.firstOrNull()?.uppercase() ?: "A"
        val yPos = center - ((paint.descent() + paint.ascent()) / 2)
        canvas.drawText(initialLetter, center, yPos, paint)

        return bitmap
    }
}
