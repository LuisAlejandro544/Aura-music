package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Icono animado de descarga en curso con una flecha que desciende sucesivamente
 * hacia la bandeja inferior con estela luminosa y pulso de recepción.
 *
 * Se muestra mientras una canción o video se está descargando en segundo plano,
 * antes de pasar al icono de verificación (✓) cuando la descarga termina realmente.
 */
@Composable
fun AnimatedDownloadingArrowIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "DownloadingArrowTransition")

    // Progreso cíclico de 0f a 1f para que la flecha baje sucesivamente una y otra vez
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 920, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ArrowFallProgress"
    )

    val trayPulse by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 460, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TrayGlowPulse"
    )

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeWidth = (w * 0.105f).coerceAtLeast(2f)

        // Aparición suave arriba y desvanecimiento justo al tocar la bandeja inferior
        val arrowAlpha = when {
            progress < 0.18f -> (progress / 0.18f).coerceIn(0.2f, 1f)
            progress > 0.82f -> ((1f - progress) / 0.18f).coerceIn(0.15f, 1f)
            else -> 1f
        }

        // Recorrido vertical descendente de la flecha principal
        val travelRange = h * 0.34f
        val yOffset = (progress * travelRange) - (travelRange * 0.45f)

        val centerX = w * 0.5f
        val arrowTopY = h * 0.18f + yOffset
        val arrowTipY = h * 0.58f + yOffset
        val wingSpan = w * 0.22f
        val wingY = arrowTipY - (w * 0.19f)

        // Estela secundaria superior que sigue el movimiento descendente
        val trailAlpha = (arrowAlpha * 0.38f).coerceIn(0f, 0.5f)
        val trailOffset = h * 0.16f
        if (arrowTopY - trailOffset > h * 0.06f) {
            drawLine(
                color = tint.copy(alpha = trailAlpha),
                start = Offset(centerX, (arrowTopY - trailOffset).coerceAtLeast(h * 0.06f)),
                end = Offset(centerX, arrowTopY - h * 0.04f),
                strokeWidth = strokeWidth * 0.82f,
                cap = StrokeCap.Round
            )
        }

        // Tallo vertical de la flecha
        drawLine(
            color = tint.copy(alpha = arrowAlpha),
            start = Offset(centerX, arrowTopY),
            end = Offset(centerX, arrowTipY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Punta en V de la flecha bajando
        val headPath = Path().apply {
            moveTo(centerX - wingSpan, wingY)
            lineTo(centerX, arrowTipY)
            lineTo(centerX + wingSpan, wingY)
        }
        drawPath(
            path = headPath,
            color = tint.copy(alpha = arrowAlpha),
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Bandeja / base receptora inferior con pulso luminoso
        val trayY = h * 0.84f
        val traySideTopY = h * 0.68f
        val trayLeftX = w * 0.18f
        val trayRightX = w * 0.82f

        val trayPath = Path().apply {
            moveTo(trayLeftX, traySideTopY)
            lineTo(trayLeftX, trayY)
            lineTo(trayRightX, trayY)
            lineTo(trayRightX, traySideTopY)
        }
        drawPath(
            path = trayPath,
            color = tint.copy(alpha = (0.55f + 0.45f * trayPulse).coerceIn(0.4f, 1f)),
            style = Stroke(
                width = strokeWidth * 0.95f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Pequeño destello horizontal en el fondo de la bandeja cuando la flecha llega abajo
        if (progress > 0.62f) {
            val impactFactor = ((progress - 0.62f) / 0.38f).coerceIn(0f, 1f)
            val barWidth = (w * 0.44f) * impactFactor
            drawRoundRect(
                color = tint.copy(alpha = (1f - impactFactor * 0.6f) * 0.75f),
                topLeft = Offset(centerX - barWidth / 2f, trayY - strokeWidth * 1.4f),
                size = Size(barWidth, strokeWidth * 0.9f),
                cornerRadius = CornerRadius(strokeWidth, strokeWidth)
            )
        }
    }
}
