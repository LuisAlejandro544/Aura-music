package com.example.feature.settings.components.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.sp
import com.example.core.model.Playlist
import com.example.core.model.Track
import com.example.core.model.WidgetConfig
import com.example.core.model.WidgetGridContentMode
import com.example.ui.components.ArtworkImage
import com.example.ui.components.PlaylistCoverCollage
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary


/**
 * Vista previa en vivo de ambos widgets con colores dinámicos de carátula.
 * Rol arquitectónico: previsualización interactiva antes de aplicar cambios.
 */
@Composable
internal fun WidgetLivePreviewSection(
    widgetConfig: WidgetConfig,
    currentTrack: Track?,
    topTracks: List<Track>,
    playlists: List<Playlist>,
    dynamicPrimary: Color,
    dynamicSecondary: Color
) {
    val intensity = (widgetConfig.colorIntensityPercent / 100f).coerceIn(0.35f, 1f)
    val activePrimary = if (widgetConfig.isDynamicColorEnabled) dynamicPrimary else Color(0xFF8B5CF6)
    val activeSecondary = if (widgetConfig.isDynamicColorEnabled) dynamicSecondary else Color(0xFF06B6D4)

    val previewGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF131B2E).copy(alpha = 1f),
            activePrimary.copy(alpha = 0.36f * intensity),
            activeSecondary.copy(alpha = 0.28f * intensity)
        )
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VISTA PREVIA EN VIVO",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.1.sp
                )
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = activePrimary.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, activePrimary.copy(alpha = 0.45f))
            ) {
                Text(
                    text = if (widgetConfig.isDynamicColorEnabled) "COLOR DE IMAGEN ACTIVO" else "COLOR BASE FIJO",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Vista previa 1: Widget Reproductor Adaptativo (Estirado Mediano con Progreso y Favorito)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF0A0E17))
                .background(previewGradient)
                .border(1.dp, activePrimary.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtworkImage(
                        track = currentTrack,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${currentTrack?.formatBadge() ?: "AURA"} • C++20 DSP",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0),
                                fontSize = 9.sp
                            )
                        )
                        Text(
                            text = currentTrack?.title ?: "Aura Music",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentTrack?.artist ?: "Reanudación directa en 2do plano",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = if (currentTrack?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (currentTrack?.isFavorite == true) Color(0xFFEF4444) else Color(0xFFCBD5E1),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(activePrimary, activeSecondary))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                if (widgetConfig.showProgressInWidget) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1:24",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFE2E8F0), fontSize = 10.sp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.22f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.45f)
                                    .fillMaxHeight()
                                    .background(Brush.horizontalGradient(listOf(activePrimary, activeSecondary)))
                            )
                        }
                        Text(
                            text = "3:42",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8), fontSize = 10.sp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Vista previa 2: Segundo Widget Independiente (4 Canciones más escuchadas o 4 Playlists)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF0A0E17))
                .background(previewGradient)
                .border(1.dp, activeSecondary.copy(alpha = 0.40f), RoundedCornerShape(22.dp))
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (widgetConfig.gridContentMode == WidgetGridContentMode.TOP_SONGS) {
                        "🔥 WIDGET 2: TUS 4 CANCIONES MÁS ESCUCHADAS"
                    } else {
                        "💿 WIDGET 2: TUS 4 PLAYLISTS PRINCIPALES"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E8F0),
                        fontSize = 10.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                val previewLabels: List<Pair<String, String>> = if (widgetConfig.gridContentMode == WidgetGridContentMode.TOP_SONGS) {
                    val list = topTracks.take(4).map { it.title to it.artist }
                    if (list.size == 4) list else (list + listOf(
                        "Canción Top 1" to "Artista",
                        "Canción Top 2" to "Artista",
                        "Canción Top 3" to "Artista",
                        "Canción Top 4" to "Artista"
                    )).take(4)
                } else {
                    val list = playlists.take(4).map { it.name to "${it.trackCount} canciones" }
                    if (list.size == 4) list else (list + listOf(
                        "Tus Me Gusta" to "Favoritos",
                        "Playlist #2" to "Biblioteca",
                        "Playlist #3" to "Biblioteca",
                        "Playlist #4" to "Biblioteca"
                    )).take(4)
                }

                for (row in 0..1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (col in 0..1) {
                            val idx = row * 2 + col
                            val itemPair = previewLabels[idx]
                            val sampleTrack = topTracks.getOrNull(idx)
                            val samplePlaylist = playlists.getOrNull(idx)

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                                    .padding(7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (widgetConfig.gridContentMode == WidgetGridContentMode.PLAYLISTS && samplePlaylist != null) {
                                    PlaylistCoverCollage(
                                        playlist = samplePlaylist,
                                        modifier = Modifier.size(34.dp),
                                        cornerRadius = 8.dp
                                    )
                                } else {
                                    ArtworkImage(
                                        track = sampleTrack ?: currentTrack,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = itemPair.first,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = itemPair.second,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 9.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
