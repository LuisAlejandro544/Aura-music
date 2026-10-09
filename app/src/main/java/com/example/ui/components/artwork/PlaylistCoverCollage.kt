package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.Playlist
import com.example.model.Track
import java.io.File

/**
 * Renderizador inteligente de carátula para Listas de Reproducción (Playlists), Álbumes y Artistas.
 *
 * Lógica visual adaptativa:
 * 1. Si la Playlist tiene una imagen personalizada (`customArtPath` en WebP), la muestra a tamaño completo.
 * 2. Si NO tiene imagen personalizada, genera dinámicamente un Collage de hasta 4 canciones:
 *    - 0 canciones: Muestra arte procedural o icono estilizado de la lista.
 *    - 1 canción: Muestra la carátula de esa única canción a cuadro completo (1x1).
 *    - 2 canciones: Collage dividido de 2 fotos lado a lado (mitad izquierda y mitad derecha).
 *    - 3 canciones: Collage de 3 fotos (2 arriba y 1 panorámica abajo, o izquierda completa y 2 a la derecha).
 *    - 4 o más canciones: Cuadrícula 2x2 con las carátulas de las primeras 4 canciones añadidas.
 */
@Composable
fun PlaylistCoverCollage(
    playlist: Playlist,
    tracksOverride: List<Track>? = null,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp
) {
    val context = LocalContext.current
    val customArtPath = playlist.customArtPath
    val tracks = tracksOverride ?: playlist.previewTracks
    val gap = 1.5.dp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            // 1. Imagen personalizada asignada por el usuario a la Playlist
            !customArtPath.isNullOrEmpty() && File(customArtPath).exists() -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(customArtPath))
                        .crossfade(true)
                        .build(),
                    contentDescription = "Portada de ${playlist.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 2. Sin canciones -> Icono estilizado o arte procedural
            tracks.isEmpty() -> {
                if (playlist.id == -1L) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize(0.45f)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxSize(0.48f)
                        )
                    }
                }
            }

            // 3. Exactamente 1 canción -> 1 sola imagen completa
            tracks.size == 1 -> {
                ArtworkImage(
                    track = tracks[0],
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0.dp
                )
            }

            // 4. Exactamente 2 canciones -> Collage de 2 imágenes lado a lado
            tracks.size == 2 -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    ArtworkImage(
                        track = tracks[0],
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        cornerRadius = 0.dp
                    )
                    Spacer(modifier = Modifier.width(gap))
                    ArtworkImage(
                        track = tracks[1],
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        cornerRadius = 0.dp
                    )
                }
            }

            // 5. Exactamente 3 canciones -> Collage de 3 fotos (2 arriba y 1 abajo ocupando el ancho)
            tracks.size == 3 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        ArtworkImage(
                            track = tracks[0],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            cornerRadius = 0.dp
                        )
                        Spacer(modifier = Modifier.width(gap))
                        ArtworkImage(
                            track = tracks[1],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            cornerRadius = 0.dp
                        )
                    }
                    Spacer(modifier = Modifier.height(gap))
                    ArtworkImage(
                        track = tracks[2],
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        cornerRadius = 0.dp
                    )
                }
            }

            // 6. 4 o más canciones -> Collage cuadrícula 2x2 de las primeras 4 canciones
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        ArtworkImage(
                            track = tracks[0],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            cornerRadius = 0.dp
                        )
                        Spacer(modifier = Modifier.width(gap))
                        ArtworkImage(
                            track = tracks[1],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            cornerRadius = 0.dp
                        )
                    }
                    Spacer(modifier = Modifier.height(gap))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        ArtworkImage(
                            track = tracks[2],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            cornerRadius = 0.dp
                        )
                        Spacer(modifier = Modifier.width(gap))
                        ArtworkImage(
                            track = tracks[3],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            cornerRadius = 0.dp
                        )
                    }
                }
            }
        }
    }
}
