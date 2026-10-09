package com.example.ui.components

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/**
 * Visualizador interactivo de ondas de audio impulsado por el motor C++20 DSP de Aura Music.
 * Dibuja barras rítmicas de ecualización en tiempo real calculadas directamente a partir
 * de la intensidad acústica y la energía espectral de las muestras PCM.
 *
 * Aísla la recolección de [bandsFlow] dentro de este único componente pequeño y de su fase
 * de dibujo (DrawScope), evitando que [AuraMusicAppContent] o [NowPlayingScreen] se recompongan
 * decenas de veces por segundo.
 */
@Composable
fun AudioVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    realBands: FloatArray? = null,
    bandsFlow: StateFlow<FloatArray>? = null,
    barCount: Int = 28,
    barHeight: Dp = 48.dp,
    customColor: Color? = null,
    customPrimaryColor: Color? = null,
    customSecondaryColor: Color? = null
) {
    val primaryColor = customPrimaryColor ?: customColor ?: MaterialTheme.colorScheme.primary
    val secondaryColor = customSecondaryColor ?: MaterialTheme.colorScheme.secondary
    val collectedBands = bandsFlow?.collectAsStateWithLifecycle()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
    ) {
        val activeBands = collectedBands?.value ?: realBands
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
                if (activeBands != null && activeBands.isNotEmpty()) {
                    val bandIdx = (i * activeBands.size / effectiveBarCount).coerceIn(0, activeBands.size - 1)
                    activeBands[bandIdx].coerceIn(0.08f, 1.0f)
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
