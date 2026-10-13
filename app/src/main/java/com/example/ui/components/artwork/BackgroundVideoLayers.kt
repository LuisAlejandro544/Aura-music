package com.example.ui.components

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.core.model.Track
import com.example.ui.theme.ArtworkColorExtractor
import com.example.ui.theme.CardBorder
import com.example.ui.theme.ExtractedArtworkColors
import android.view.TextureView
import android.view.View
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
/**
 * Capas visuales del video: carátula, TextureView GPU y degradado.
 * Rol arquitectónico: sección UI de BackgroundVideoPlayer (API intacta).
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun BackgroundVideoLayers(
    videoPlayer: androidx.media3.exoplayer.ExoPlayer,
    videoAlpha: Float,
    placeholderTrack: Track?,
    cornerRadius: androidx.compose.ui.unit.Dp,
    isHorizontalVideo: Boolean,
    fitHorizontalInFullscreen: Boolean,
    targetResizeMode: Int,
    onPlayerView: (androidx.media3.ui.PlayerView) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .border(if (cornerRadius > 0.dp) 1.dp else 0.dp, CardBorder, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        // Capa 1: Carátula que se muestra únicamente mientras el decodificador prepara el primer cuadro
        // (desaparece suavemente al renderizar el video para no generar cortes horizontales detrás del video)
        if (placeholderTrack != null && videoAlpha < 0.99f) {
            ArtworkImage(
                track = placeholderTrack,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (1f - videoAlpha).coerceIn(0f, 1f) },
                cornerRadius = cornerRadius
            )
        } else if (placeholderTrack == null && videoAlpha < 0.99f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F16))
            )
        }

        // Capa 2: Reproductor de video sobre TextureView acelerado por GPU, sin perforar SurfaceFlinger
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.aura_background_video_player, null, false) as PlayerView).apply {
                    onPlayerView(this)
                    player = videoPlayer
                    useController = false
                    resizeMode = targetResizeMode
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                onPlayerView(playerView)
                if (playerView.player != videoPlayer) {
                    playerView.player = videoPlayer
                }
                if (playerView.resizeMode != targetResizeMode) {
                    playerView.resizeMode = targetResizeMode
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = videoAlpha }
        )

        // Capa 3: Degradado sutil oscuro para garantizar contraste con controles flotantes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = if (cornerRadius == 0.dp && isHorizontalVideo && fitHorizontalInFullscreen) 0.45f else 0.25f),
                            Color.Transparent,
                            Color.Black.copy(alpha = if (cornerRadius == 0.dp) 0.65f else 0.45f)
                        )
                    )
                )
        )
    }
}
