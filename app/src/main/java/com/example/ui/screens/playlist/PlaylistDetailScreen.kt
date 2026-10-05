package com.example.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Playlist
import com.example.model.Track
import com.example.ui.components.TrackListItem
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pantalla de Detalle de Lista de Reproducción.
 * Permite visualizar, reproducir y gestionar canciones en una lista específica.
 * Incluye opción para renombrar la playlist, añadir canciones desde la biblioteca
 * y reproducir de manera sincronizada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist?,
    tracks: List<Track>,
    allTracks: List<Track> = emptyList(),
    allPlaylists: List<Playlist>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onFavoriteToggle: (Track) -> Unit,
    onDeleteTrackFromLibrary: (Long) -> Unit = {},
    onRemoveFromPlaylist: (Long, Long) -> Unit,
    onAddToPlaylist: (Long, Long) -> Unit,
    onRenamePlaylist: (Long, String, String, android.net.Uri?, Boolean) -> Unit = { _, _, _, _, _ -> },
    onEditTrack: (Long, String, String, String) -> Unit = { _, _, _, _ -> },
    onEditTrackDetails: (Long, String, String, String, android.net.Uri?, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBack()
    }

    if (playlist == null) return

    val isFavoritesVirtual = playlist.id == -1L
    val isAlbumVirtual = playlist.id == -2L
    val isArtistVirtual = playlist.id == -3L
    val isVirtualCollection = playlist.id < 0L

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameName by remember { mutableStateOf(playlist.name) }
    var renameDesc by remember { mutableStateOf(playlist.description) }

    var showAddTracksDialog by remember { mutableStateOf(false) }
    var trackSearchQuery by remember { mutableStateOf("") }

    val availableToAdd = remember(allTracks, tracks, trackSearchQuery) {
        val existingIds = tracks.map { it.id }.toSet()
        allTracks.filter { it.id !in existingIds && (
            trackSearchQuery.isBlank() ||
            it.title.contains(trackSearchQuery, ignoreCase = true) ||
            it.artist.contains(trackSearchQuery, ignoreCase = true)
        )}
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("playlist_detail_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )

                if (!isVirtualCollection) {
                    IconButton(
                        onClick = {
                            renameName = playlist.name
                            renameDesc = playlist.description
                            showRenameDialog = true
                        },
                        modifier = Modifier.testTag("rename_playlist_header_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar lista",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cabecera visual con Portada Personalizada o Collage de 1 a 4 canciones
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .then(
                            if (!isVirtualCollection) {
                                Modifier.clickable {
                                    renameName = playlist.name
                                    renameDesc = playlist.description
                                    showRenameDialog = true
                                }
                            } else Modifier
                        )
                ) {
                    com.example.ui.components.PlaylistCoverCollage(
                        playlist = playlist,
                        tracksOverride = tracks.take(4),
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 18.dp
                    )
                    if (!isVirtualCollection) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Cambiar imagen de lista",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val collectionBadge = when {
                        isFavoritesVirtual -> "COLECCIÓN FAVORITOS"
                        isAlbumVirtual -> "ÁLBUM"
                        isArtistVirtual -> "ARTISTA"
                        else -> "PLAYLIST PERSONALIZADA"
                    }
                    Text(
                        text = collectionBadge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (playlist.description.isNotBlank()) {
                        Text(
                            text = playlist.description,
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = "${tracks.size} canción(es) disponible(s)",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Fila de Botones de Acción (Reproducir, Aleatorio y Añadir Canciones)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tracks.isNotEmpty()) {
                    Button(
                        onClick = { onTrackClick(tracks.first(), tracks) },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reproducir", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val shuffled = tracks.shuffled()
                            onTrackClick(shuffled.first(), shuffled)
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Aleatorio", fontWeight = FontWeight.Bold)
                    }
                }

                if (!isVirtualCollection && allTracks.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = { showAddTracksDialog = true },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(if (tracks.isEmpty()) 1f else 0.9f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Añadir", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (tracks.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (isFavoritesVirtual) Icons.Default.FavoriteBorder else Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isFavoritesVirtual) "No tienes canciones con Me Gusta" else "Esta lista está vacía",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isFavoritesVirtual) {
                            "Pulsa el icono de corazón en cualquier canción para sincronizarla aquí automáticamente."
                        } else {
                            "Añade canciones pulsando el botón 'Añadir' arriba o desde el menú de 3 puntos de cada canción."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            items(tracks, key = { it.id }) { track ->
                TrackListItem(
                    track = track,
                    isCurrentTrack = currentTrack?.id == track.id,
                    isPlaying = isPlaying,
                    onTrackClick = { onTrackClick(track, tracks) },
                    onFavoriteToggle = { onFavoriteToggle(track) },
                    onDeleteTrack = {
                        when {
                            isFavoritesVirtual -> onFavoriteToggle(track)
                            isAlbumVirtual || isArtistVirtual -> onDeleteTrackFromLibrary(track.id)
                            else -> onRemoveFromPlaylist(playlist.id, track.id)
                        }
                    },
                    playlists = allPlaylists,
                    onAddToPlaylist = { pId -> onAddToPlaylist(pId, track.id) },
                    onEditTrack = { id, t, a, al -> onEditTrack(id, t, a, al) },
                    onEditTrackDetails = onEditTrackDetails,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
    }

    // Cuadro de diálogo para editar nombre, descripción e imagen de la playlist
    if (showRenameDialog && !isVirtualCollection) {
        com.example.ui.screens.library.components.RenamePlaylistDialog(
            playlist = playlist,
            tracksPreview = tracks.take(4),
            onDismissRequest = { showRenameDialog = false },
            onRenamePlaylist = { pId, name, desc, artUri, removeArt ->
                onRenamePlaylist(pId, name, desc, artUri, removeArt)
                showRenameDialog = false
            }
        )
    }

    // Cuadro de diálogo para añadir canciones desde la biblioteca
    if (showAddTracksDialog) {
        AlertDialog(
            onDismissRequest = { showAddTracksDialog = false },
            title = { Text("Añadir Canciones a '${playlist.name}'", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    OutlinedTextField(
                        value = trackSearchQuery,
                        onValueChange = { trackSearchQuery = it },
                        placeholder = { Text("Filtrar canciones...", color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (availableToAdd.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (trackSearchQuery.isBlank()) "Todas tus canciones ya están en esta lista." else "No se encontraron canciones.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            items(availableToAdd, key = { it.id }) { track ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SurfaceCard,
                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, CardBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onAddToPlaylist(playlist.id, track.id)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = track.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                            )
                                            Text(
                                                text = track.artist,
                                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.AddCircle,
                                            contentDescription = "Añadir a la lista",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddTracksDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}
