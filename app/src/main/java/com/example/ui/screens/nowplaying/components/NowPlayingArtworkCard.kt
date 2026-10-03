package com.example.ui.screens.nowplaying.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Track
import com.example.model.VideoDisplayMode
import com.example.ui.components.ArtworkImage
import com.example.ui.components.BackgroundVideoPlayer

/**
 * Cuadro central de Carátula o Video Canvas con aura lumínica, elevación y sombra dinámica.
 */
@Composable
fun NowPlayingArtworkCard(
    currentTrack: Track,
    isPlaying: Boolean,
    currentPositionMs: Long,
    videoDisplayMode: VideoDisplayMode,
    animatedPrimary: Color,
    animatedSecondary: Color,
    onCycleVideoDisplayMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasVideo = !currentTrack.videoUri.isNullOrEmpty()
    val isFullscreenVideo = hasVideo && (videoDisplayMode == VideoDisplayMode.FULLSCREEN_BACKGROUND)
    val isCardVideo = hasVideo && (videoDisplayMode == VideoDisplayMode.CARD_CANVAS)

    Box(
        modifier = modifier
            .fillMaxWidth(0.85f)
            .aspectRatio(1f)
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(24.dp),
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
                cornerRadius = 24.dp
            )
        } else {
            ArtworkImage(
                track = currentTrack,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 24.dp
            )

            // Si está en modo Fondo Completo, mostrar badge interactivo para cambiar de modo al tocar
            if (isFullscreenVideo) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.8.dp, animatedPrimary.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCycleVideoDisplayMode() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wallpaper,
                            contentDescription = null,
                            tint = animatedPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (currentTrack.isVideoLoop) "FONDO LOOP" else "FONDO SYNC",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}
