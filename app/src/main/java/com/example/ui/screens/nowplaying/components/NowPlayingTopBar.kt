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
 * Arquitectura: Componente modular de UI que presenta:
 * - Botón de repliegue/cierre suave hacia abajo.
 * - Cabecera "REPRODUCIENDO AURA" y nombre del álbum.
 * - Botón de acceso a Efectos de Audio y Temporizador (con badge indicadora activa).
 * - Botón de especificaciones técnicas y edición.
 * - Botón de selección de Modo de Video (Fondo completo, Canvas o Off).
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
    modifier: Modifier = Modifier
) {
    val hasVideo = !currentTrack.videoUri.isNullOrEmpty()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onCollapse,
            modifier = Modifier.testTag("now_playing_collapse_btn")
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Ocultar reproductor",
                tint = TextPrimary,
                modifier = Modifier.size(32.dp)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "REPRODUCIENDO AURA",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            )
            Text(
                text = currentTrack.album,
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onOpenEffects,
                modifier = Modifier.testTag("now_playing_effects_btn")
            ) {
                BadgedBox(
                    badge = {
                        if (sleepTimerState.isActive || spatial8DConfig.enabled) {
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
                        tint = if (sleepTimerState.isActive || spatial8DConfig.enabled) MaterialTheme.colorScheme.primary else TextSecondary
                    )
                }
            }

            IconButton(
                onClick = onOpenDetails,
                modifier = Modifier.testTag("now_playing_details_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Detalles técnicos de audio",
                    tint = TextSecondary
                )
            }

            if (hasVideo) {
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    onClick = onOpenVideoMode,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.70f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (videoDisplayMode != VideoDisplayMode.OFF) animatedPrimary else Color.White.copy(alpha = 0.40f)
                    ),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("now_playing_toggle_video_canvas_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (videoDisplayMode) {
                                VideoDisplayMode.FULLSCREEN_BACKGROUND -> Icons.Default.Fullscreen
                                VideoDisplayMode.CARD_CANVAS -> Icons.Default.CropSquare
                                VideoDisplayMode.OFF -> Icons.Default.VideocamOff
                            },
                            contentDescription = "Modo de Video: ${videoDisplayMode.label}",
                            tint = if (videoDisplayMode != VideoDisplayMode.OFF) animatedPrimary else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (videoDisplayMode) {
                                VideoDisplayMode.FULLSCREEN_BACKGROUND -> "Fondo"
                                VideoDisplayMode.CARD_CANVAS -> "Lienzo"
                                VideoDisplayMode.OFF -> "Off"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (videoDisplayMode != VideoDisplayMode.OFF) animatedPrimary else Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}
