package com.example.feature.nowplaying.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import com.example.core.model.Track
import com.example.feature.streaming.StreamingPlatformBadge
import com.example.ui.components.AnimatedDownloadingArrowIcon
import com.example.ui.components.ArtworkImage
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Barra superior cinemática (colapso, procedencia y acciones).
 * Rol arquitectónico: cabecera del layout cinemático con insignia de plataforma.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CinematicTopBar(
    currentTrack: Track,
    animatedPrimary: Color,
    isLoadingOrBuffering: Boolean,
    isVideoLoading: Boolean,
    isWaitingForNetwork: Boolean,
    collectionContextTitle: String,
    onCollapse: () -> Unit,
    onDownloadStreamingTrack: (() -> Unit)?,
    isStreamingTrackDownloading: Boolean,
    isStreamingTrackDownloaded: Boolean,
    onOpenDesignSelector: () -> Unit,
    onOpenDetails: () -> Unit
) {
                // 1. Barra Superior Cinemática
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onCollapse,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("cinematic_collapse_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Ocultar reproductor",
                                tint = TextPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        currentTrack.streamingPlatform?.let { platform ->
                            com.example.feature.streaming.StreamingPlatformBadge(platform = platform)
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = if (isWaitingForNetwork) {
                                "SIN RED • ESPERANDO CONEXIÓN..."
                            } else if (isLoadingOrBuffering || isVideoLoading) {
                                "CARGANDO STREAM..."
                            } else {
                                "REPRODUCIENDO DESDE"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isWaitingForNetwork) Color(0xFFF59E0B) else if (isLoadingOrBuffering || isVideoLoading) animatedPrimary else TextSecondary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = collectionContextTitle.ifBlank { currentTrack.album },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee()
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentTrack.isStreamingTrack && onDownloadStreamingTrack != null) {
                            IconButton(
                                onClick = {
                                    if (!isStreamingTrackDownloading && !isStreamingTrackDownloaded) {
                                        onDownloadStreamingTrack()
                                    }
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .testTag("cinematic_download_streaming_btn")
                            ) {
                                when {
                                    isStreamingTrackDownloading -> {
                                        com.example.ui.components.AnimatedDownloadingArrowIcon(
                                            tint = animatedPrimary,
                                            size = 22.dp
                                        )
                                    }
                                    isStreamingTrackDownloaded -> {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Descargado en biblioteca local",
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = "Descargar pista en biblioteca local",
                                            tint = animatedPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(
                            onClick = onOpenDesignSelector,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("cinematic_design_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DashboardCustomize,
                                contentDescription = "Cambiar diseño del reproductor",
                                tint = animatedPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = onOpenDetails,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("cinematic_more_btn")
                            ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Opciones y ficha técnica",
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
}

/**
 * Fila de información de pista cinemática (miniatura, título/artista y favorito).
 * Rol arquitectónico: identidad de la pista con marquesina y anillo de carga.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CinematicTrackInfoRow(
    currentTrack: Track,
    animatedPrimary: Color,
    isLoadingOrBuffering: Boolean,
    isVideoLoading: Boolean,
    isWaitingForNetwork: Boolean,
    onToggleFavorite: (Track) -> Unit
) {
                // 3. Fila de Información de Pista (Miniatura + Título/Artista + Favorito)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Miniatura cuadrada de carátula oficial de 54dp con indicador de carga de video/red
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .shadow(4.dp, RoundedCornerShape(10.dp))
                            .testTag("cinematic_mini_artwork"),
                        contentAlignment = Alignment.Center
                    ) {
                        ArtworkImage(
                            track = currentTrack,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 10.dp
                        )
                        if (isLoadingOrBuffering || isVideoLoading || isWaitingForNetwork) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.48f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = if (isWaitingForNetwork) Color(0xFFF59E0B) else animatedPrimary,
                                    strokeWidth = 2.4.dp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clipToBounds()
                    ) {
                        Text(
                            text = currentTrack.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            ),
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clipToBounds()
                                .basicMarquee()
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clipToBounds()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = animatedPrimary.copy(alpha = 0.18f),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = currentTrack.formatBadge(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = animatedPrimary,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = currentTrack.artist,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Botón de Favorito
                    IconButton(
                        onClick = { onToggleFavorite(currentTrack) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("cinematic_favorite_btn")
                    ) {
                        Icon(
                            imageVector = if (currentTrack.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (currentTrack.isFavorite) "Quitar de favoritos" else "Guardar en favoritos",
                            tint = if (currentTrack.isFavorite) Color(0xFFEF4444) else TextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
}
