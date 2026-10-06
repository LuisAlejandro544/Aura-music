package com.example.ui.components

import android.net.Uri
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.model.Track
import com.example.ui.theme.CardBorder
import java.io.File
import kotlin.math.abs

/**
 * Componente de reproducción de Video de Fondo (Canvas / Video Sincronizado).
 * 
 * Modos de reproducción:
 * 1. Loop Corto (Canvas <= 10s): Se reproduce en bucle continuo infinito silenciado,
 *    pausándose únicamente si la música se detiene.
 * 2. Video Largo Sincronizado (> 10s): El video se sincroniza milimétricamente con el
 *    tiempo de reproducción de la canción (currentPositionMs) y saltos de búsqueda (seekTo).
 *
 * Mantiene la estética Dark Luxury OLED con atenuación y sombras sutiles,
 * eliminando fondos negros o parpadeos mediante precarga de posición y placeholder.
 */
@OptIn(UnstableApi::class)
@Composable
fun BackgroundVideoPlayer(
    videoUriString: String,
    isVideoLoop: Boolean,
    isPlaying: Boolean,
    currentPositionMs: Long,
    playbackSpeed: Float = 1.0f,
    placeholderTrack: Track? = null,
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 24.dp,
    fitHorizontalInFullscreen: Boolean = false
) {
    val context = LocalContext.current
    var isFirstFrameRendered by remember(videoUriString) { mutableStateOf(false) }
    var videoDimensions by remember(videoUriString) { mutableStateOf<Pair<Int, Int>?>(null) }

    // Detección reactiva de si el video es horizontal (16:9 / panorámico) o vertical (9:16)
    val isHorizontalVideo = remember(videoDimensions) {
        val (w, h) = videoDimensions ?: return@remember false
        w > (h * 1.15f)
    }

    // Modo de redimensionado elegible por el usuario:
    // - Si fitHorizontalInFullscreen es false (Modo Rellenar / Recortar): usa RESIZE_MODE_ZOOM para que el video
    //   ocupe el 100% de la pantalla de arriba a abajo sin franjas ni cortes horizontales, tomando la región central.
    // - Si fitHorizontalInFullscreen es true (Modo Adaptado): usa RESIZE_MODE_FIT en videos horizontales para
    //   mostrar todo el fotograma sin recortar rostros ni laterales.
    val targetResizeMode = when {
        cornerRadius == 0.dp && isHorizontalVideo && fitHorizontalInFullscreen -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        else -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }

    // Instancia de ExoPlayer dedicada a renderizado visual silenciado por videoUriString
    val videoPlayer = remember(videoUriString) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f // Silenciado: el audio proviene exclusivamente del motor DSP principal
            repeatMode = if (isVideoLoop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF

            val mediaItem = if (videoUriString.startsWith("content://") || videoUriString.startsWith("file://")) {
                MediaItem.fromUri(Uri.parse(videoUriString))
            } else {
                MediaItem.fromUri(Uri.fromFile(File(videoUriString)))
            }
            setMediaItem(mediaItem)
            setPlaybackSpeed(playbackSpeed)

            // Sincronizar posición inicial exacta antes de prepare() para evitar el re-buffering en negro
            if (!isVideoLoop && currentPositionMs > 0L) {
                seekTo(currentPositionMs)
            }

            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    isFirstFrameRendered = true
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        videoDimensions = Pair(videoSize.width, videoSize.height)
                    }
                }
            })

            prepare()
            if (isPlaying) {
                play()
            }
        }
    }

    // Sincronizar velocidad de reproducción en tiempo real con la velocidad de la música
    LaunchedEffect(playbackSpeed, videoPlayer) {
        videoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    // Gestionar play / pause según el estado del reproductor de música principal
    LaunchedEffect(isPlaying, videoPlayer) {
        if (isPlaying) {
            videoPlayer.play()
        } else {
            videoPlayer.pause()
        }
    }

    // Gestionar sincronización temporal para videos largos (> 10s)
    LaunchedEffect(currentPositionMs, isVideoLoop, videoPlayer) {
        if (!isVideoLoop) {
            val playerPos = videoPlayer.currentPosition
            // Si hay un desfase superior a 850ms (ej. seek manual o rebobinado), resincronizar
            if (abs(playerPos - currentPositionMs) > 850L) {
                videoPlayer.seekTo(currentPositionMs)
            }
        }
    }

    // Liberación estricta de recursos de códec y hardware al cambiar de video o desmontar
    DisposableEffect(videoPlayer) {
        onDispose {
            videoPlayer.stop()
            videoPlayer.clearMediaItems()
            videoPlayer.release()
        }
    }

    val videoAlpha by animateFloatAsState(
        targetValue = if (isFirstFrameRendered) 1f else 0f,
        animationSpec = tween(280),
        label = "VideoFadeInAlpha"
    )

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

        // Capa 2: Reproductor de video con obturador transparente, actualización reactiva y fade-in suave
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
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
