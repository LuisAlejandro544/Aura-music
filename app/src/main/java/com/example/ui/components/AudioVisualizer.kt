package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Visualizador interactivo de ondas de audio impulsado por el motor C++20 DSP de Aura Music.
 * Dibuja barras rítmicas de ecualización en tiempo real calculadas directamente a partir
 * de la intensidad acústica y la energía espectral de las muestras PCM.
 */
@Composable
fun AudioVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    realBands: FloatArray? = null,
    barCount: Int = 28,
    barHeight: Dp = 48.dp,
    customColor: Color? = null
) {
    val primaryColor = customColor ?: MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
    ) {
        val totalWidth = size.width
        val canvasHeight = size.height
        val effectiveBarCount = barCount.coerceAtLeast(8)
        val barSpacing = 3.5.dp.toPx()
        val barWidth = ((totalWidth - (barSpacing * (effectiveBarCount - 1))) / effectiveBarCount).coerceAtLeast(3f)

        val gradient = Brush.verticalGradient(
            colors = listOf(primaryColor, secondaryColor),
            startY = 0f,
            endY = canvasHeight
        )

        for (i in 0 until effectiveBarCount) {
            val wave = if (isPlaying) {
                if (realBands != null && realBands.isNotEmpty()) {
                    val bandIdx = (i * realBands.size / effectiveBarCount).coerceIn(0, realBands.size - 1)
                    realBands[bandIdx].coerceIn(0.08f, 1.0f)
                } else {
                    0.20f
                }
            } else {
                0.08f
            }

            val currentBarHeight = canvasHeight * wave
            val startX = i * (barWidth + barSpacing)
            val startY = canvasHeight - currentBarHeight

            drawRoundRect(
                brush = gradient,
                topLeft = Offset(startX, startY),
                size = Size(barWidth, currentBarHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}
