package com.example.feature.nowplaying.components
import com.example.core.model.RepeatMode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ABLoopState
import com.example.core.model.LyricsState
import com.example.core.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

/**
 * Diseño Cinemático Inmersivo de Now Playing (Estilo Canvas).
 *
 * Características:
 * - Área visual completa y despejada para el Video Canvas en movimiento.
 * - Barra superior compacta con indicador de procedencia ("Reproduciendo desde...").
 * - Frase lírica en vivo flotante sobre el video al compás de la música.
 * - Tercio inferior con degradado ergonómico de alta legibilidad:
 *   - Miniatura cuadrada de carátula oficial (54dp) con esquinas redondeadas.
 *   - Título con marquesina fluida y nombre del artista.
 *   - Botón de Me Gusta (corazón) interactivo.
 *   - Barra de progreso delgada interactiva con tiempos precisos.
 *   - Botonera principal con Play/Pausa circular blanco de 64dp.
 *   - Fila de utilidades inferiores (EQ FX, selector de diseño, cola de reproducción).
 *   - Píldora inferior "Vista previa de la letra" para acceso directo al Karaoke.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingCinematicLayout(
    currentTrack: Track,
    isPlaying: Boolean,
    currentPositionMs: Long = 0L,
    currentPositionFlow: StateFlow<Long>? = null,
    durationMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    lyricsState: LyricsState,
    animatedPrimary: Color,
    animatedSecondary: Color,
    audioIntensity: Float = 0.15f,
    audioIntensityFlow: StateFlow<Float>? = null,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onCollapse: () -> Unit,
    onOpenEffects: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenDesignSelector: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenFullScreenLyrics: () -> Unit,
    onOpenDetails: () -> Unit,
    collectionContextTitle: String = "Tu Biblioteca",
    onDownloadStreamingTrack: (() -> Unit)? = null,
    isStreamingTrackDownloading: Boolean = false,
    isStreamingTrackDownloaded: Boolean = false,
    isLoadingOrBuffering: Boolean = false,
    isVideoLoading: Boolean = false,
    isWaitingForNetwork: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("now_playing_cinematic_layout")
    ) {
        // Halo de resplandor ambiental y viñeta dinámica superior/inferior aislado en su propio
        // componente pequeño para que audioIntensity NO recomponga todo el layout cinemático
        CinematicReactiveVignette(
            animatedPrimary = animatedPrimary,
            animatedSecondary = animatedSecondary,
            fallbackIntensity = audioIntensity,
            audioIntensityFlow = audioIntensityFlow
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CinematicTopBar(
                currentTrack = currentTrack,
                animatedPrimary = animatedPrimary,
                isLoadingOrBuffering = isLoadingOrBuffering,
                isVideoLoading = isVideoLoading,
                isWaitingForNetwork = isWaitingForNetwork,
                collectionContextTitle = collectionContextTitle,
                onCollapse = onCollapse,
                onDownloadStreamingTrack = onDownloadStreamingTrack,
                isStreamingTrackDownloading = isStreamingTrackDownloading,
                isStreamingTrackDownloaded = isStreamingTrackDownloaded,
                onOpenDesignSelector = onOpenDesignSelector,
                onOpenDetails = onOpenDetails
            )

            // 2. Área Central Despejada para el Video Canvas
            Spacer(modifier = Modifier.weight(1f))

            // Frase de letra flotante en vivo sobre el video aislada en su propio ámbito de composición
            CinematicFloatingLyricPill(
                lyricsState = lyricsState,
                fallbackPositionMs = currentPositionMs,
                currentPositionFlow = currentPositionFlow,
                animatedPrimary = animatedPrimary,
                onOpenFullScreenLyrics = onOpenFullScreenLyrics
            )

            CinematicTrackInfoRow(
                currentTrack = currentTrack,
                animatedPrimary = animatedPrimary,
                isLoadingOrBuffering = isLoadingOrBuffering,
                isVideoLoading = isVideoLoading,
                isWaitingForNetwork = isWaitingForNetwork,
                onToggleFavorite = onToggleFavorite
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 4. Barra de Progreso y tiempos aislados en su propio ámbito de composición
            CinematicSeekBarSection(
                fallbackPositionMs = currentPositionMs,
                currentPositionFlow = currentPositionFlow,
                durationMs = durationMs,
                animatedPrimary = animatedPrimary,
                onSeekTo = onSeekTo
            )

            Spacer(modifier = Modifier.height(8.dp))

            CinematicMainControls(
                isPlaying = isPlaying,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                animatedPrimary = animatedPrimary,
                audioIntensity = audioIntensity,
                audioIntensityFlow = audioIntensityFlow,
                isLoadingOrBuffering = isLoadingOrBuffering,
                isVideoLoading = isVideoLoading,
                isWaitingForNetwork = isWaitingForNetwork,
                onToggleShuffle = onToggleShuffle,
                onPlayPrevious = onPlayPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext,
                onCycleRepeat = onCycleRepeat
            )

            Spacer(modifier = Modifier.height(10.dp))

            CinematicUtilityRow(
                lyricsState = lyricsState,
                animatedPrimary = animatedPrimary,
                onOpenEffects = onOpenEffects,
                onOpenDesignSelector = onOpenDesignSelector,
                onOpenFullScreenLyrics = onOpenFullScreenLyrics,
                onOpenQueue = onOpenQueue
            )

            Spacer(modifier = Modifier.height(6.dp))

            CinematicLyricsPreview(
                animatedPrimary = animatedPrimary,
                onOpenFullScreenLyrics = onOpenFullScreenLyrics
            )
        }
    }
}

@Composable
private fun CinematicReactiveVignette(
    animatedPrimary: Color,
    animatedSecondary: Color,
    fallbackIntensity: Float,
    audioIntensityFlow: StateFlow<Float>?
) {
    val collectedIntensity = audioIntensityFlow?.collectAsStateWithLifecycle()
    val liveIntensity = collectedIntensity?.value ?: fallbackIntensity
    val dynamicBottomGlow = animatedPrimary.copy(
        alpha = (0.28f + (liveIntensity.coerceIn(0f, 1f) * 0.24f)).coerceIn(0.18f, 0.60f)
    )
    val dynamicTopGlow = animatedSecondary.copy(alpha = 0.35f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        dynamicTopGlow,
                        Color.Black.copy(alpha = 0.40f),
                        Color.Transparent,
                        Color.Transparent,
                        dynamicBottomGlow,
                        animatedSecondary.copy(alpha = 0.35f),
                        BackgroundDark.copy(alpha = 0.92f),
                        BackgroundDark
                    ),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
    )
}

@Composable
internal fun CinematicPlayButtonHalo(
    isPlaying: Boolean,
    animatedPrimary: Color,
    fallbackIntensity: Float,
    audioIntensityFlow: StateFlow<Float>?
) {
    val collectedIntensity = audioIntensityFlow?.collectAsStateWithLifecycle()
    val liveIntensity = collectedIntensity?.value ?: fallbackIntensity
    Box(
        modifier = Modifier
            .size(74.dp)
            .background(
                animatedPrimary.copy(
                    alpha = if (isPlaying) (0.22f + liveIntensity.coerceIn(0f, 1f) * 0.25f).coerceIn(0.16f, 0.55f) else 0.12f
                ),
                CircleShape
            )
    )
}

private fun formatTimeMs(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

@Composable
private fun CinematicFloatingLyricPill(
    lyricsState: LyricsState,
    fallbackPositionMs: Long,
    currentPositionFlow: StateFlow<Long>?,
    animatedPrimary: Color,
    onOpenFullScreenLyrics: () -> Unit
) {
    val collectedPosition = currentPositionFlow?.collectAsStateWithLifecycle()
    val activeLyricLine by remember(lyricsState.lines, lyricsState.isSynced, collectedPosition, fallbackPositionMs) {
        derivedStateOf {
            if (lyricsState.isSynced && lyricsState.lines.isNotEmpty()) {
                val posMs = collectedPosition?.value ?: fallbackPositionMs
                val idx = findActiveLyricIndexBinary(lyricsState.lines, posMs)
                if (idx >= 0 && lyricsState.lines[idx].timeMs <= posMs) {
                    lyricsState.lines[idx].text
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    if (!activeLyricLine.isNullOrBlank()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.55f),
            border = BorderStroke(1.2.dp, animatedPrimary.copy(alpha = 0.65f)),
            shadowElevation = 4.dp,
            modifier = Modifier
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onOpenFullScreenLyrics() }
                .testTag("cinematic_floating_lyric_pill")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = animatedPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = activeLyricLine ?: "",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CinematicSeekBarSection(
    fallbackPositionMs: Long,
    currentPositionFlow: StateFlow<Long>?,
    durationMs: Long,
    animatedPrimary: Color,
    onSeekTo: (Long) -> Unit
) {
    val collectedPosition = currentPositionFlow?.collectAsStateWithLifecycle()
    val livePositionMs = collectedPosition?.value ?: fallbackPositionMs

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragValue by remember { mutableFloatStateOf(0f) }

    val safeDuration = if (durationMs > 0L) durationMs.toFloat() else 1f
    val currentProgress = if (isDraggingSlider) {
        sliderDragValue
    } else {
        (livePositionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    }

    Slider(
        value = currentProgress,
        onValueChange = { newVal ->
            isDraggingSlider = true
            sliderDragValue = newVal
        },
        onValueChangeFinished = {
            val targetMs = (sliderDragValue * safeDuration).toLong()
            onSeekTo(targetMs)
            isDraggingSlider = false
        },
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = animatedPrimary,
            inactiveTrackColor = Color.White.copy(alpha = 0.24f)
        ),
        thumb = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(animatedPrimary.copy(alpha = 0.35f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .shadow(elevation = 3.dp, shape = CircleShape)
                        .background(Color.White, CircleShape)
                )
            }
        },
        track = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.24f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(currentProgress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(animatedPrimary)
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .testTag("cinematic_progress_slider")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val displayPositionMs = if (isDraggingSlider) {
            (sliderDragValue * safeDuration).toLong()
        } else {
            livePositionMs
        }
        Text(
            text = formatTimeMs(displayPositionMs),
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Text(
            text = formatTimeMs(durationMs),
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}
