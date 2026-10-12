package com.example.feature.nowplaying.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.core.model.Track
import com.example.core.model.VideoDisplayMode
import com.example.ui.components.BackgroundVideoPlayer
import com.example.ui.theme.BackgroundDark
import kotlinx.coroutines.flow.StateFlow

/**
 * Fondo de video fullscreen con velo cinematográfico para Now Playing.
 * Rol arquitectónico: capa de fondo del diseño cinemático con crossfade atómico.
 */
@Composable
fun NowPlayingFullscreenBackground(
    currentTrack: Track,
    isPlaying: Boolean,
    currentPositionMs: Long,
    currentPositionFlow: StateFlow<Long>?,
    playbackSpeed: Float,
    videoDisplayMode: VideoDisplayMode
) {
    val videoUri = currentTrack.videoUri ?: return
    key(currentTrack.id, currentTrack.videoUri) {
        BackgroundVideoPlayer(
            videoUriString = videoUri,
            isVideoLoop = currentTrack.isVideoLoop,
            isPlaying = isPlaying,
            currentPositionMs = currentPositionMs,
            currentPositionFlow = currentPositionFlow,
            playbackSpeed = playbackSpeed,
            placeholderTrack = currentTrack,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 0.dp,
            fitHorizontalInFullscreen = (videoDisplayMode == VideoDisplayMode.FULLSCREEN_ADAPTED)
        )
    }

    // Velo oscuro y gradiente cinematográfico para máximo contraste y legibilidad
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.65f),
                        Color.Black.copy(alpha = 0.50f),
                        BackgroundDark.copy(alpha = 0.94f)
                    )
                )
            )
    )
}
