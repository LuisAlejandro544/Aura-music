package com.example.ui.screens.nowplaying.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SleepTimerState
import com.example.model.Spatial8DConfig
import com.example.model.Track
import com.example.model.VideoDisplayMode
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Barra superior de la pantalla completa Now Playing.
 *
 * Características:
 * - Botón de repliegue/cierre suave hacia abajo.
 * - Título "REPRODUCIENDO AURA" y nombre del álbum con ajuste elástico contra desbordes.
 * - Botón de Letras Sincronizadas Karaoke (Mic).
 * - Botón de acceso a Efectos de Audio y Temporizador (con badge indicadora activa).
 * - Botón de especificaciones técnicas y edición.
 * - Botón de Video Canvas con tamaño geométrico fijo (40dp x 38dp) que jamás se deforma ni se aplasta.
 */
@Composable
fun NowPlayingTopBar(
    currentTrack: Track,
    sleepTimerState: SleepTimerState,
    spatial8DConfig: Spatial8DConfig,
    videoDisplayMode: VideoDisplayMode,
    animatedPrimary: Color,
    onCollapse: () -> Unit,
    onOpenEffects: () -> Unit,
    onOpenDetails: () -> Unit,
    onOpenVideoMode: () -> Unit,
    isLyricsActive: Boolean = false,
    onToggleLyrics: () -> Unit = {},
    isABLoopActive: Boolean = false,
    isVocalClarityActive: Boolean = false,
    onOpenDesignSelector: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hasVideo = !currentTrack.videoUri.isNullOrEmpty()
    val hasActiveEffect = sleepTimerState.isActive || spatial8DConfig.enabled || isABLoopActive || isVocalClarityActive

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onCollapse,
            modifier = Modifier
                .size(48.dp)
                .testTag("now_playing_collapse_btn")
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Ocultar reproductor",
                tint = TextPrimary,
                modifier = Modifier.size(32.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Botón de Letras Karaoke (Mic)
            IconButton(
                onClick = onToggleLyrics,
                modifier = Modifier
                    .size(42.dp)
                    .testTag("now_playing_lyrics_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Letras Karaoke",
                    tint = if (isLyricsActive) animatedPrimary else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Botón de Efectos de Audio y Temporizador
            IconButton(
                onClick = onOpenEffects,
                modifier = Modifier
                    .size(42.dp)
                    .testTag("now_playing_effects_btn")
            ) {
                BadgedBox(
                    badge = {
                        if (hasActiveEffect) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(8.dp)
                            )
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Efectos de Audio y Temporizador",
                        tint = if (hasActiveEffect) MaterialTheme.colorScheme.primary else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Botón de Detalles Técnicos
            IconButton(
                onClick = onOpenDetails,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("now_playing_details_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Detalles técnicos de audio",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Botón de Selector de Diseño del Reproductor
            if (onOpenDesignSelector != null) {
                IconButton(
                    onClick = onOpenDesignSelector,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("now_playing_design_selector_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.DashboardCustomize,
                        contentDescription = "Cambiar diseño del reproductor",
                        tint = animatedPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Botón de Modo de Video (tamaño ergonómico y robusto de alto contraste, cero deformación)
            if (hasVideo) {
                Surface(
                    onClick = onOpenVideoMode,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (videoDisplayMode != VideoDisplayMode.OFF) animatedPrimary else Color.White.copy(alpha = 0.40f)
                    ),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("now_playing_toggle_video_canvas_btn")
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (videoDisplayMode) {
                                VideoDisplayMode.FULLSCREEN_BACKGROUND -> Icons.Default.Fullscreen
                                VideoDisplayMode.FULLSCREEN_ADAPTED -> Icons.Default.AspectRatio
                                VideoDisplayMode.CARD_CANVAS -> Icons.Default.CropSquare
                                VideoDisplayMode.OFF -> Icons.Default.VideocamOff
                            },
                            contentDescription = "Modo de Video: ${videoDisplayMode.label}",
                            tint = if (videoDisplayMode != VideoDisplayMode.OFF) animatedPrimary else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
