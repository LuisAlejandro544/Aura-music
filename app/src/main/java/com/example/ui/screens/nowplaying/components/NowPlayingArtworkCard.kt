package com.example.ui.screens.nowplaying.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Track
import com.example.model.VideoDisplayMode
import com.example.ui.components.ArtworkImage
import com.example.ui.components.BackgroundVideoPlayer

/**
 * Cuadro central de Carátula o Video Canvas con aura lumínica, elevación y respiración dinámica
 * sincronizada en tiempo real con la intensidad acústica procesada en el motor nativo C++20.
 *
 * Incluye cápsula flotante ergonómica de alto contraste para alternar entre los modos de Video Canvas
 * con área táctil superior a 48.dp y feedback visual claro.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingArtworkCard(
    currentTrack: Track,
    isPlaying: Boolean,
    currentPositionMs: Long,
    videoDisplayMode: VideoDisplayMode,
    animatedPrimary: Color,
    animatedSecondary: Color,
    onCycleVideoDisplayMode: () -> Unit,
    audioIntensity: Float = 0.15f,
    onOpenVideoMode: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hasVideo = !currentTrack.videoUri.isNullOrEmpty()
    val isFullscreenVideo = hasVideo && (videoDisplayMode == VideoDisplayMode.FULLSCREEN_BACKGROUND)
    val isCardVideo = hasVideo && (videoDisplayMode == VideoDisplayMode.CARD_CANVAS)

    // Pulsación y respiración acústica directamente ligada a la intensidad calculada en C++20
    val targetScale = if (isPlaying) {
        1.0f + (audioIntensity.coerceIn(0.0f, 1.0f) * 0.040f)
    } else {
        1.0f
    }
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 60),
        label = "NativeDspScale"
    )

    val dynamicElevation = if (isPlaying) {
        18.dp + (16.dp * audioIntensity.coerceIn(0.0f, 1.0f))
    } else {
        20.dp
    }

    Box(
        modifier = modifier
            .fillMaxWidth(0.86f)
            .aspectRatio(1f)
            .scale(animatedScale)
            .shadow(
                elevation = dynamicElevation,
                shape = RoundedCornerShape(26.dp),
                ambientColor = animatedPrimary,
                spotColor = animatedSecondary
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isCardVideo && currentTrack.videoUri != null) {
            BackgroundVideoPlayer(
                videoUriString = currentTrack.videoUri,
                isVideoLoop = currentTrack.isVideoLoop,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 26.dp
            )
        } else {
            ArtworkImage(
                track = currentTrack,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 26.dp
            )
        }

        // Cápsula ergonómica flotante de alto contraste para alternar el modo de Video Canvas
        if (hasVideo) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.88f),
                border = BorderStroke(1.5.dp, animatedPrimary.copy(alpha = 0.85f)),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .combinedClickable(
                        onClick = onCycleVideoDisplayMode,
                        onLongClick = { onOpenVideoMode?.invoke() }
                    )
                    .testTag("now_playing_video_canvas_capsule")
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .defaultMinSize(minHeight = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when (videoDisplayMode) {
                            VideoDisplayMode.FULLSCREEN_BACKGROUND -> Icons.Default.Fullscreen
                            VideoDisplayMode.CARD_CANVAS -> Icons.Default.CropSquare
                            VideoDisplayMode.OFF -> Icons.Default.VideocamOff
                        },
                        contentDescription = "Modo Video Canvas",
                        tint = if (videoDisplayMode != VideoDisplayMode.OFF) animatedPrimary else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(19.dp)
                    )

                    Text(
                        text = when (videoDisplayMode) {
                            VideoDisplayMode.FULLSCREEN_BACKGROUND -> if (currentTrack.isVideoLoop) "CANVAS: FONDO LOOP" else "CANVAS: FONDO SYNC"
                            VideoDisplayMode.CARD_CANVAS -> "CANVAS: EN CARÁTULA"
                            VideoDisplayMode.OFF -> "CANVAS: DESACTIVADO"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.8.sp,
                            color = Color.White
                        )
                    )

                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Tocar para alternar modo",
                        tint = animatedPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
