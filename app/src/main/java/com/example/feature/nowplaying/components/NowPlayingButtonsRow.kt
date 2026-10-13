package com.example.feature.nowplaying.components
import com.example.core.model.RepeatMode

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import com.example.core.model.ABLoopState
import com.example.core.model.Track
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
/**
 * Fila de controles: shuffle, anterior, play/pausa, siguiente y repeat.
 * Rol arquitectónico: pieza de NowPlayingPlaybackControls (fachada pública intacta).
 */
@Composable
fun NowPlayingButtonsRow(
    shuffleEnabled: Boolean,
    animatedPrimary: Color,
    isPlaying: Boolean,
    isLoadingOrBuffering: Boolean,
    isWaitingForNetwork: Boolean,
    repeatMode: RepeatMode,
    onToggleShuffle: () -> Unit,
    onPlayPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onCycleRepeat: () -> Unit
) {
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

            // Play / Pause (Botón Grande con Sombra y Anillo de Carga en Streaming)
            Box(contentAlignment = Alignment.Center) {
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
                if (isLoadingOrBuffering || isWaitingForNetwork) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(76.dp)
                            .testTag("now_playing_play_pause_loading_ring"),
                        color = if (isWaitingForNetwork) Color(0xFFF59E0B) else Color.White,
                        strokeWidth = 3.dp
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
