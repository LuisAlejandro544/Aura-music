package com.example.feature.nowplaying.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.LyricsState
import com.example.core.model.RepeatMode
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.flow.StateFlow

/**
 * Controles principales cinemáticos (shuffle, anterior, play 64dp, siguiente, repeat).
 * Rol arquitectónico: transporte con halo reactivo y anillo de carga de streaming.
 */
@Composable
fun CinematicMainControls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    animatedPrimary: Color,
    audioIntensity: Float,
    audioIntensityFlow: StateFlow<Float>?,
    isLoadingOrBuffering: Boolean,
    isVideoLoading: Boolean,
    isWaitingForNetwork: Boolean,
    onToggleShuffle: () -> Unit,
    onPlayPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onCycleRepeat: () -> Unit
) {
                // 5. Controles Principales de Reproducción
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Aleatorio (Shuffle)
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("cinematic_shuffle_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Modo aleatorio",
                            tint = if (shuffleEnabled) animatedPrimary else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Canción Anterior
                    IconButton(
                        onClick = onPlayPrevious,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("cinematic_prev_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Canción anterior",
                            tint = TextPrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Botón Play / Pausa Central Circular Blanco de 64dp con halo aislado y anillo de carga en vivo
                    Box(contentAlignment = Alignment.Center) {
                        CinematicPlayButtonHalo(
                            isPlaying = isPlaying,
                            animatedPrimary = animatedPrimary,
                            fallbackIntensity = audioIntensity,
                            audioIntensityFlow = audioIntensityFlow
                        )
                        Surface(
                            onClick = onTogglePlayPause,
                            shape = CircleShape,
                            color = Color.White,
                            border = BorderStroke(1.5.dp, animatedPrimary.copy(alpha = 0.65f)),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .testTag("cinematic_play_pause_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                    tint = Color.Black,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        if (isLoadingOrBuffering || isVideoLoading || isWaitingForNetwork) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(72.dp)
                                    .testTag("cinematic_play_pause_loading_ring"),
                                color = if (isWaitingForNetwork) Color(0xFFF59E0B) else animatedPrimary,
                                strokeWidth = 3.dp
                            )
                        }
                    }

                    // Canción Siguiente
                    IconButton(
                        onClick = onPlayNext,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("cinematic_next_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Siguiente canción",
                            tint = TextPrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Repetir
                    IconButton(
                        onClick = onCycleRepeat,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("cinematic_repeat_btn")
                    ) {
                        Icon(
                            imageVector = when (repeatMode) {
                                RepeatMode.ONE -> Icons.Default.RepeatOne
                                RepeatMode.ALL -> Icons.Default.Repeat
                                RepeatMode.OFF -> Icons.Default.Repeat
                            },
                            contentDescription = "Modo de repetición",
                            tint = if (repeatMode != RepeatMode.OFF) animatedPrimary else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
}

/**
 * Fila de utilidades cinemática (efectos, diseño, letras y cola).
 * Rol arquitectónico: accesos secundarios con área táctil de 48dp.
 */
@Composable
fun CinematicUtilityRow(
    lyricsState: LyricsState,
    animatedPrimary: Color,
    onOpenEffects: () -> Unit,
    onOpenDesignSelector: () -> Unit,
    onOpenFullScreenLyrics: () -> Unit,
    onOpenQueue: () -> Unit
) {
                // 6. Fila de Utilidades Inferiores
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ecualizador y Efectos
                    IconButton(
                        onClick = onOpenEffects,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("cinematic_effects_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Efectos de audio y ecualizador",
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Selector de Diseño del Reproductor
                    IconButton(
                        onClick = onOpenDesignSelector,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("cinematic_layout_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewCarousel,
                            contentDescription = "Estilo de reproductor",
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Letras Sincronizadas (Karaoke a Pantalla Completa)
                    IconButton(
                        onClick = onOpenFullScreenLyrics,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("cinematic_lyrics_icon_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Letras sincronizadas",
                            tint = if (lyricsState.isSynced) animatedPrimary else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Cola de Reproducción
                    IconButton(
                        onClick = onOpenQueue,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("cinematic_queue_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = "Cola de reproducción",
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
}

/**
 * Vista previa deslizable a la letra con acento armónico reactivo.
 * Rol arquitectónico: puerta al karaoke con insignia KARAOKE SING.
 */
@Composable
fun CinematicLyricsPreview(
    animatedPrimary: Color,
    onOpenFullScreenLyrics: () -> Unit
) {
                // 7. Tarjeta Deslizable / Acceso Inferior a "Vista previa de la letra" con acento armónico reactivo
                Surface(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    color = animatedPrimary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, animatedPrimary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .clickable { onOpenFullScreenLyrics() }
                        .testTag("cinematic_lyrics_sheet_preview")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.70f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Vista previa de la letra",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            )
                        }
                        Text(
                            text = "KARAOKE SING",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = animatedPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }
}
