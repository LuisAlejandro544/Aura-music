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
    cornerRadius: androidx.compose.ui.unit.Dp = 24.dp
) {
    val context = LocalContext.current
    var isFirstFrameRendered by remember { mutableStateOf(false) }

    // Instancia de ExoPlayer dedicada a renderizado visual silenciado
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
            })

            prepare()
            if (isPlaying) {
                play()
            }
        }
    }

    // Sincronizar velocidad de reproducción en tiempo real con la velocidad de la música
    LaunchedEffect(playbackSpeed) {
        videoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    // Gestionar play / pause según el estado del reproductor de música principal
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            videoPlayer.play()
        } else {
            videoPlayer.pause()
        }
    }

    // Gestionar sincronización temporal para videos largos (> 10s)
    LaunchedEffect(currentPositionMs, isVideoLoop) {
        if (!isVideoLoop) {
            val playerPos = videoPlayer.currentPosition
            // Si hay un desfase superior a 800ms (ej. seek manual o rebobinado), resincronizar
            if (abs(playerPos - currentPositionMs) > 850L) {
                videoPlayer.seekTo(currentPositionMs)
            }
        }
    }

    // Liberación estricta de recursos de códec y hardware al desmontar el Composable
    DisposableEffect(videoPlayer) {
        onDispose {
            videoPlayer.stop()
            videoPlayer.release()
        }
    }

    val videoAlpha by animateFloatAsState(
        targetValue = if (isFirstFrameRendered) 1f else 0f,
        animationSpec = tween(220),
        label = "VideoFadeInAlpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .border(1.dp, CardBorder, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        // Capa 1: Carátula o fondo estético que se muestra mientras el decodificador prepara el primer cuadro
        if (placeholderTrack != null) {
            ArtworkImage(
                track = placeholderTrack,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = cornerRadius
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F16))
            )
        }

        // Capa 2: Reproductor de video con obturador transparente y fade-in suave al renderizar
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = videoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    // Desactivar el obturador negro que ExoPlayer muestra por defecto al cargar
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
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
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.45f)
                        )
                    )
                )
        )
    }
}
