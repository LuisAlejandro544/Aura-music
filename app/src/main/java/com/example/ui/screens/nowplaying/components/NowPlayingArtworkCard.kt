package com.example.ui.screens.nowplaying.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.core.model.Track
import com.example.core.model.VideoDisplayMode
import com.example.ui.components.ArtworkImage
import com.example.ui.components.BackgroundVideoPlayer

/**
 * Cuadro central de Carátula o Video Canvas con aura lumínica y elevación elegante.
 *
 * Diseño limpio y nítido:
 * - Se mantiene estático y fijo (sin movimiento o pulsación por la música) según la preferencia del usuario.
 * - Sin elementos o cápsulas superpuestas que tapen la ilustración o el video.
 * - Toda la carátula y el Video Canvas se aprecian al 100% de visibilidad.
 */
@Composable
fun NowPlayingArtworkCard(
    currentTrack: Track,
    isPlaying: Boolean,
    currentPositionMs: Long = 0L,
    currentPositionFlow: kotlinx.coroutines.flow.StateFlow<Long>? = null,
    videoDisplayMode: VideoDisplayMode,
    animatedPrimary: Color,
    animatedSecondary: Color,
    playbackSpeed: Float = 1.0f,
    onCycleVideoDisplayMode: () -> Unit = {},
    audioIntensity: Float = 0.15f,
    onOpenVideoMode: (() -> Unit)? = null,
    isLoadingOrBuffering: Boolean = false,
    isWaitingForNetwork: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hasVideo = !currentTrack.videoUri.isNullOrEmpty()
    val isCardVideo = hasVideo && (videoDisplayMode == VideoDisplayMode.CARD_CANVAS)

    Box(
        modifier = modifier
            .fillMaxWidth(0.86f)
            .aspectRatio(1f)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = animatedPrimary,
                spotColor = animatedSecondary
            ),
        contentAlignment = Alignment.Center
    ) {
        key(currentTrack.id, currentTrack.videoUri) {
            if (isCardVideo && currentTrack.videoUri != null) {
                BackgroundVideoPlayer(
                    videoUriString = currentTrack.videoUri,
                    isVideoLoop = currentTrack.isVideoLoop,
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    currentPositionFlow = currentPositionFlow,
                    playbackSpeed = playbackSpeed,
                    placeholderTrack = currentTrack,
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
        }

        if (isLoadingOrBuffering || isWaitingForNetwork) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.62f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = if (isWaitingForNetwork) Color(0xFFF59E0B) else animatedPrimary,
                        strokeWidth = 2.dp
                    )
                    androidx.compose.material3.Text(
                        text = if (isWaitingForNetwork) "Esperando red..." else "Cargando stream...",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}

