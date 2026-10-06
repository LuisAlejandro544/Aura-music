package com.example.ui.screens.nowplaying.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ABLoopState
import com.example.model.RepeatMode
import com.example.model.Track
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Controles de reproducción principales de Now Playing:
 * - Información de pista con desplazamiento automático tipo marquesina (Marquee) para títulos y artistas largos.
 * - Barra deslizadora interactiva (Seekbar) con formato de tiempo `mm:ss` e indicadores visuales de bucle A-B.
 * - Barra compacta de Repetidor de Segmento A-B ([A], [B], estado de bucle y botón de limpiar).
 * - Fila de botones de control (Shuffle, Skip Anterior, Play/Pause grande, Skip Siguiente, Repeat).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingPlaybackControls(
    currentTrack: Track,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    animatedPrimary: Color,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    abLoopState: ABLoopState = ABLoopState(),
    onMarkABPointA: () -> Unit = {},
    onMarkABPointB: () -> Unit = {},
    onClearABLoop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPosition by remember { mutableStateOf(0f) }

    val safeDuration = durationMs.coerceAtLeast(1L)
    val sliderValue = if (isDraggingSlider) {
        sliderDragPosition
    } else {
        (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }

    val displayPositionMs = if (isDraggingSlider) {
        (sliderDragPosition * safeDuration).toLong()
    } else {
        currentPositionMs
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Título con Marquesina fluida, Artista, Etiqueta de formato y botón de Favorito
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .clipToBounds()
            ) {
                Text(
                    text = currentTrack.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            repeatDelayMillis = 1600,
                            initialDelayMillis = 1200,
                            velocity = 34.dp
                        )
                        .testTag("now_playing_marquee_title")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = animatedPrimary.copy(alpha = 0.16f),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = currentTrack.formatBadge(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = animatedPrimary
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = currentTrack.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .weight(1f)
                            .clipToBounds()
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                repeatDelayMillis = 2000,
                                initialDelayMillis = 1600,
                                velocity = 28.dp
                            )
                    )
                }
            }

            IconButton(
                onClick = { onToggleFavorite(currentTrack) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("now_playing_fav_btn")
            ) {
                Icon(
                    imageVector = if (currentTrack.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorito",
                    tint = if (currentTrack.isFavorite) Color(0xFFEF4444) else TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Slider de Progreso, Indicador de Rango A-B y Tiempo
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Franja luminosa que demarca el rango [A - B] sobre la barra de progreso
                if (abLoopState.pointAMs != null) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .height(6.dp)
                    ) {
                        val startFraction = (abLoopState.pointAMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                        val endFraction = ((abLoopState.pointBMs ?: currentPositionMs).toFloat() / safeDuration.toFloat())
                            .coerceIn(startFraction, 1f)
                        val startOffset = maxWidth * startFraction
                        val segmentWidth = (maxWidth * (endFraction - startFraction)).coerceAtLeast(4.dp)

                        Box(
                            modifier = Modifier
                                .offset(x = startOffset)
                                .width(segmentWidth)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (abLoopState.isLoopingActive) {
                                        Color(0xFF10B981).copy(alpha = 0.78f)
                                    } else {
                                        animatedPrimary.copy(alpha = 0.55f)
                                    }
                                )
                        )
                    }
                }

                Slider(
                    value = sliderValue,
                    onValueChange = {
                        isDraggingSlider = true
                        sliderDragPosition = it
                    },
                    onValueChangeFinished = {
                        onSeekTo((sliderDragPosition * safeDuration).toLong())
                        isDraggingSlider = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = animatedPrimary,
                        inactiveTrackColor = animatedPrimary.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("now_playing_slider")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatPlaybackTime(displayPositionMs),
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )

                // Barra Compacta del Repetidor de Segmento A-B integrada junto al tiempo
                CompactABLoopBar(
                    abLoopState = abLoopState,
                    animatedPrimary = animatedPrimary,
                    onMarkA = onMarkABPointA,
                    onMarkB = onMarkABPointB,
                    onClear = onClearABLoop
                )

                Text(
                    text = formatPlaybackTime(durationMs),
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Botonera de Reproducción
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Aleatorio
            IconButton(
                onClick = onToggleShuffle,
                modifier = Modifier.testTag("now_playing_shuffle_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Modo aleatorio",
                    tint = if (shuffleEnabled) animatedPrimary else TextMuted
                )
            }

            // Anterior
            IconButton(
                onClick = onPlayPrevious,
                modifier = Modifier
                    .size(52.dp)
                    .testTag("now_playing_prev_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Pista anterior",
                    tint = TextPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Play / Pause (Botón Grande con Sombra)
            Surface(
                shape = CircleShape,
                color = animatedPrimary,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
            ) {
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("now_playing_play_pause_btn")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Siguiente
            IconButton(
                onClick = onPlayNext,
                modifier = Modifier
                    .size(52.dp)
                    .testTag("now_playing_next_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Pista siguiente",
                    tint = TextPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Repetir
            IconButton(
                onClick = onCycleRepeat,
                modifier = Modifier.testTag("now_playing_repeat_btn")
            ) {
                Icon(
                    imageVector = when (repeatMode) {
                        RepeatMode.ONE -> Icons.Default.RepeatOne
                        RepeatMode.ALL -> Icons.Default.Repeat
                        RepeatMode.OFF -> Icons.Default.Repeat
                    },
                    contentDescription = "Repetir",
                    tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else TextMuted
                )
            }
        }
    }
}

/**
 * Barra compacta del Repetidor de Segmento A-B situada junto al indicador de progreso.
 * Permite marcar [A], marcar [B] y limpiar el bucle con un solo toque sin saturar la pantalla.
 */
@Composable
private fun CompactABLoopBar(
    abLoopState: ABLoopState,
    animatedPrimary: Color,
    onMarkA: () -> Unit,
    onMarkB: () -> Unit,
    onClear: () -> Unit
) {
    val hasA = abLoopState.pointAMs != null
    val hasB = abLoopState.pointBMs != null
    val isActive = abLoopState.isLoopingActive
    val accentGreen = Color(0xFF10B981)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.testTag("now_playing_ab_loop_bar")
    ) {
        // Botón Punto A
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (hasA) animatedPrimary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 30.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = if (hasA) animatedPrimary else Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onMarkA)
                .testTag("ab_loop_set_a_btn")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (hasA) "A ${formatPlaybackTime(abLoopState.pointAMs ?: 0L)}" else "A",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (hasA) animatedPrimary else TextSecondary,
                        fontSize = 11.sp
                    )
                )
            }
        }

        // Botón Punto B
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = when {
                isActive -> accentGreen.copy(alpha = 0.22f)
                hasB -> animatedPrimary.copy(alpha = 0.22f)
                else -> Color.White.copy(alpha = 0.06f)
            },
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 30.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = when {
                        isActive -> accentGreen
                        hasB -> animatedPrimary
                        else -> Color.White.copy(alpha = 0.14f)
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onMarkB)
                .testTag("ab_loop_set_b_btn")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (hasB) "B ${formatPlaybackTime(abLoopState.pointBMs ?: 0L)}" else "B",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isActive -> accentGreen
                            hasB -> animatedPrimary
                            else -> TextSecondary
                        },
                        fontSize = 11.sp
                    )
                )
            }
        }

        // Botón limpiar A-B cuando hay algún punto marcado
        if (hasA || hasB) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.10f),
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClear)
                    .testTag("ab_loop_clear_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Limpiar bucle A-B",
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Formatea milisegundos en formato de tiempo mm:ss o hh:mm:ss.
 */
fun formatPlaybackTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val sec = totalSec % 60
    val min = (totalSec / 60) % 60
    val hours = totalSec / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, min, sec)
    } else {
        String.format("%d:%02d", min, sec)
    }
}
