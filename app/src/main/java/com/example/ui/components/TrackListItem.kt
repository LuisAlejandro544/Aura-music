package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.unit.sp
import com.example.model.Playlist
import com.example.model.Track
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Elemento de fila para cada canción en la lista, inspirado en el diseño de Spotify
 * pero enriquecido con etiquetas de formato de audio, estado activo iluminado y acciones rápidas.
 */
@Composable
fun TrackListItem(
    track: Track,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onTrackClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDeleteTrack: () -> Unit,
    playlists: List<Playlist>,
    onAddToPlaylist: (Long) -> Unit,
    onEditTrack: ((trackId: Long, newTitle: String, newArtist: String, newAlbum: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    val activeBackground = if (isCurrentTrack) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(activeBackground)
            .clickable(onClick = onTrackClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("track_item_${track.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Portada
        ArtworkImage(
            track = track,
            modifier = Modifier.size(52.dp),
            cornerRadius = 8.dp
        )

        Spacer(modifier = Modifier.width(14.dp))

        // Título y artista
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Etiqueta de formato de audio
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = track.formatBadge(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                Text(
                    text = "${track.artist} • ${track.formattedDuration()}",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Botón de Favorito
        IconButton(
            onClick = onFavoriteToggle,
            modifier = Modifier
                .size(48.dp)
                .testTag("track_fav_${track.id}")
        ) {
            Icon(
                imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (track.isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                tint = if (track.isFavorite) Color(0xFFEF4444) else TextMuted
            )
        }

        // Menú de opciones adicionales
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("track_menu_${track.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Más opciones de la canción",
                    tint = TextSecondary
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Añadir a Playlist") },
                    onClick = {
                        showMenu = false
                        showPlaylistDialog = true
                    }
                )
                if (onEditTrack != null) {
                    DropdownMenuItem(
                        text = { Text("Editar información") },
                        onClick = {
                            showMenu = false
                            showEditDialog = true
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Eliminar de biblioteca", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDeleteTrack()
                    }
                )
            }
        }
    }

    if (showEditDialog && onEditTrack != null) {
        EditTrackDialog(
            track = track,
            onDismiss = { showEditDialog = false },
            onConfirm = { trackId, title, artist, album ->
                onEditTrack(trackId, title, artist, album)
            }
        )
    }

    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("Añadir a Playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Text("No tienes listas de reproducción creadas aún. Crea una desde Tu Biblioteca.")
                } else {
                    Column {
                        playlists.forEach { playlist ->
                            TextButton(
                                onClick = {
                                    onAddToPlaylist(playlist.id)
                                    showPlaylistDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(playlist.name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}
