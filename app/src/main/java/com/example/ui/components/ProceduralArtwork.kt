package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.data.importer.ProceduralArtworkGenerator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Componente gráfico procedural que renderiza carátulas vectoriales en tiempo real.
 * Reemplaza la imagen por defecto con un diseño dinámico generado matemáticamente.
 */
@Composable
fun ProceduralArtwork(
    title: String,
    artist: String,
    modifier: Modifier = Modifier
) {
    val (primaryRaw, secondaryRaw) = remember(title, artist) {
        ProceduralArtworkGenerator.getPaletteFor(title, artist)
    }
    val primary = remember(primaryRaw) { Color(primaryRaw) }
    val secondary = remember(secondaryRaw) { Color(secondaryRaw) }
    val hash = remember(title, artist) { abs((title + artist).hashCode()) }
    val initial = remember(title) { title.firstOrNull()?.uppercase() ?: "A" }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val minDim = size.minDimension

            // Fondo con degradado
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0B0F19),
                        primary.copy(alpha = 0.85f),
                        Color(0xFF04060C)
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )

            // Resplandor radial
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondary.copy(alpha = 0.5f), Color.Transparent),
                    center = center,
                    radius = minDim * 0.45f
                ),
                radius = minDim * 0.45f,
                center = center
            )

            // Surcos concéntricos de vinilo
            val ringCount = 5 + (hash % 4)
            for (i in 1..ringCount) {
                val radius = (minDim * 0.16f) + (i * (minDim * 0.28f / ringCount))
                drawCircle(
                    color = if (i % 2 == 0) secondary.copy(alpha = 0.5f) else primary.copy(alpha = 0.35f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2f)
                )
            }

            // Rayos geométricos
            val rayCount = 8 + (hash % 8)
            for (i in 0 until rayCount) {
                val angle = (i * 2.0 * Math.PI / rayCount)
                val startR = minDim * 0.22f
                val endR = minDim * 0.42f
                val startOffset = Offset(
                    center.x + (startR * cos(angle)).toFloat(),
                    center.y + (startR * sin(angle)).toFloat()
                )
                val endOffset = Offset(
                    center.x + (endR * cos(angle)).toFloat(),
                    center.y + (endR * sin(angle)).toFloat()
                )
                drawLine(
                    color = primary.copy(alpha = 0.35f),
                    start = startOffset,
                    end = endOffset,
                    strokeWidth = 2.5f
                )
            }

            // Núcleo del disco
            drawCircle(
                color = Color(0xFF111827),
                radius = minDim * 0.16f,
                center = center
            )
            drawCircle(
                color = secondary,
                radius = minDim * 0.16f,
                center = center,
                style = Stroke(width = 3.5f)
            )
        }

        // Inicial centrada
        Text(
            text = initial,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontSize = 22.sp
            )
        )
    }
}
