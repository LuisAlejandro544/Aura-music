package com.example.ui.screens.nowplaying.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.RepeatMode
import com.example.model.Track
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Controles de reproducción principales de Now Playing:
 * - Información de pista (título, artista, badge de formato y botón reactivo de favoritos en rojo).
 * - Barra deslizadora interactiva (Seekbar) con formato de tiempo `mm:ss`.
 * - Fila de botones de control (Shuffle, Skip Anterior, Play/Pause grande, Skip Siguiente, Repeat).
 */
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
        // Título, Artista, Etiqueta de formato y botón de Favorito
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrack.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        overflow = TextOverflow.Ellipsis
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

        Spacer(modifier = Modifier.height(16.dp))

        // Slider de Progreso y Tiempo
        Column(modifier = Modifier.fillMaxWidth()) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatPlaybackTime(displayPositionMs),
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )
                Text(
                    text = formatPlaybackTime(durationMs),
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
