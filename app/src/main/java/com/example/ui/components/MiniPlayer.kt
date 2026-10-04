package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Track
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Mini reproductor flotante Aura Sound con arquitectura Dark Luxury Neo-Glass y fondo dinámico tintado.
 *
 * Características:
 * - Soporte para carátula estática o Video Canvas miniatura configurable por el usuario.
 * - Fondo completamente tintado y degradado con los colores extraídos de la pista/video actual.
 * - Desplazamiento horizontal automático (Marquee) para títulos y artistas largos en reproducción.
 * - Barra de progreso, botón de reproducción y acentos sincronizados con la paleta activa.
 * - Controles ergonómicos estándar: Anterior (⏮️), Play/Pausa (⏯️) y Siguiente (⏭️).
 * - Permanece visible sobre la barra de navegación inferior con acceso instantáneo a Now Playing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    currentTrack: Track?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onClick: () -> Unit,
    dynamicPrimary: Color = MaterialTheme.colorScheme.primary,
    dynamicSecondary: Color = MaterialTheme.colorScheme.secondary,
    isMiniPlayerVideoEnabled: Boolean = true,
    playbackSpeed: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    if (currentTrack == null) return

    val progress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val gradientBorder = Brush.horizontalGradient(
        listOf(
            dynamicPrimary.copy(alpha = 0.85f),
            dynamicSecondary.copy(alpha = 0.65f)
        )
    )

    // Fondo del mini reproductor completamente tintado con el color dinámico extraído
    val tintedBackgroundBrush = Brush.horizontalGradient(
        listOf(
            dynamicPrimary.copy(alpha = 0.35f),
            dynamicSecondary.copy(alpha = 0.22f),
            SurfaceElevatedDark.copy(alpha = 0.95f)
        )
    )

    val hasVideo = !currentTrack.videoUri.isNullOrEmpty()
    val showVideoThumb = isMiniPlayerVideoEnabled && hasVideo

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("mini_player"),
        color = SurfaceElevatedDark,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, gradientBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(tintedBackgroundBrush)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mini Carátula o Video Canvas Miniatura según preferencia del usuario
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (showVideoThumb && currentTrack.videoUri != null) {
                        BackgroundVideoPlayer(
                            videoUriString = currentTrack.videoUri,
                            isVideoLoop = currentTrack.isVideoLoop,
                            isPlaying = isPlaying,
                            currentPositionMs = currentPositionMs,
                            playbackSpeed = playbackSpeed,
                            placeholderTrack = currentTrack,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 10.dp
                        )
                    } else {
                        ArtworkImage(
                            track = currentTrack,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 10.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Título y artista con Marquesina fluida para evitar recortes
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                ) {
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                repeatDelayMillis = 1600,
                                initialDelayMillis = 1200,
                                velocity = 30.dp
                            )
                            .testTag("mini_player_marquee_title")
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentTrack.artist,
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                repeatDelayMillis = 2000,
                                initialDelayMillis = 1600,
                                velocity = 25.dp
                            )
                    )
                }

                // Botón Canción Anterior
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("mini_player_skip_previous")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Pista anterior",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Botón Play/Pause Tintado
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("mini_player_play_pause")
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = dynamicPrimary,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Botón Siguiente
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("mini_player_skip_next")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Siguiente canción",
                        tint = TextPrimary
                    )
                }
            }

            // Barra delgada de progreso en la parte inferior tintada
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = dynamicPrimary,
                trackColor = dynamicPrimary.copy(alpha = 0.20f)
            )
        }
    }
}
