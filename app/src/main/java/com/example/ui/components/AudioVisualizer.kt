package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Visualizador interactivo de ondas de audio animadas.
 * Dibuja barras rítmicas de ecualización en tiempo real durante la reproducción.
 */
@Composable
fun AudioVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 28,
    barHeight: Dp = 48.dp,
    customColor: Color? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_visualizer")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_anim"
    )

    val primaryColor = customColor ?: MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
    ) {
        val totalWidth = size.width
        val canvasHeight = size.height
        val barSpacing = 4.dp.toPx()
        val barWidth = ((totalWidth - (barSpacing * (barCount - 1))) / barCount).coerceAtLeast(3f)

        val gradient = Brush.verticalGradient(
            colors = listOf(primaryColor, secondaryColor),
            startY = 0f,
            endY = canvasHeight
        )

        for (i in 0 until barCount) {
            val normalizedIdx = i.toFloat() / barCount
            val wave = if (isPlaying) {
                val s1 = sin(phase + (normalizedIdx * 5f))
                val s2 = sin((phase * 1.5f) + (normalizedIdx * 9f))
                val combined = ((s1 + s2) / 2f + 1f) / 2f
                (0.15f + combined * 0.85f).coerceIn(0.1f, 1f)
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
