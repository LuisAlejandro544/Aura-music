package com.example.ui.screens.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Playlist
import com.example.model.Track
import com.example.ui.components.TrackListItem
import com.example.ui.navigation.LibraryTab
import com.example.ui.navigation.NavScreen
import com.example.ui.screens.library.components.*
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pantalla de Tu Biblioteca con pestañas modulares (Canciones, Álbumes, Artistas, Playlists, Favoritos),
 * barra de búsqueda en tiempo real, creación de listas y filtros ágiles.
 * Arquitectura Modular (MVVM):
 * Delega la representación de elementos y vistas en submódulos especializados en [com.example.ui.screens.library.components]:
 * - [FavoritesPlaylistBannerCard]: Tarjeta destacada de "Tus Me Gusta".
 * - [PlaylistRowItem]: Fila de lista de reproducción con menú contextual.
 * - [AlbumGroupCard] / [ArtistGroupCard]: Vistas agrupadas de álbumes y artistas.
 * - [CreatePlaylistDialog] / [RenamePlaylistDialog]: Diálogos modales para gestión de listas.
 * - [EmptyLibraryView] / [EmptyPlaceholder]: Estados vacíos con botón de importación SAF.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    allTracks: List<Track>,
    favoriteTracks: List<Track>,
    playlists: List<Playlist>,
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    currentTrack: Track?,
    isPlaying: Boolean,
    onTrackClick: (Track, List<Track>) -> Unit,
    onFavoriteToggle: (Track) -> Unit,
    onDeleteTrack: (Long) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenAlbum: (String) -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    onCreatePlaylist: (String, String, android.net.Uri?) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onAddToPlaylist: (Long, Long) -> Unit,
    onNavigate: (NavScreen) -> Unit,
    onEditTrack: (Long, String, String, String) -> Unit = { _, _, _, _ -> },
    onEditTrackDetails: (Long, String, String, String, android.net.Uri?, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onRenamePlaylist: (Long, String, String, android.net.Uri?, Boolean) -> Unit = { _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }

    // Filtrar pistas por búsqueda
    val filteredTracks = remember(allTracks, searchQuery) {
        if (searchQuery.isBlank()) allTracks
        else allTracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    val albumsList = remember(allTracks) {
        allTracks.groupBy { it.album }.entries.toList()
    }

    val artistsList = remember(allTracks) {
        allTracks.groupBy { it.artist }.entries.toList()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Título de la pantalla y botón de Añadir Lista
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tu Biblioteca",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                FilledTonalButton(
                    onClick = { showCreatePlaylistDialog = true },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("create_playlist_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nueva Lista", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Barra de Búsqueda
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Buscar canciones, artistas o álbumes...", color = TextMuted) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda", tint = TextMuted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = CardBorder,
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_search_input")
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Pestañas (Filter Chips)
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(LibraryTab.values()) { tab ->
                    val isSelected = tab == selectedTab
                    FilterChip(
                        selected = isSelected,
                        onClick = { onTabSelected(tab) },
                        label = {
                            Text(
                                text = tab.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            containerColor = SurfaceCard,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) MaterialTheme.colorScheme.primary else CardBorder
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("tab_${tab.name}")
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // Botones de Reproducir Todo / Aleatorio
        if (filteredTracks.isNotEmpty() && (selectedTab == LibraryTab.SONGS || selectedTab == LibraryTab.FAVORITES)) {
            val listToPlay = if (selectedTab == LibraryTab.FAVORITES) favoriteTracks else filteredTracks
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (listToPlay.isNotEmpty()) onTrackClick(listToPlay.first(), listToPlay)
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("play_all_btn")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reproducir", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            if (listToPlay.isNotEmpty()) {
                                val shuffled = listToPlay.shuffled()
                                onTrackClick(shuffled.first(), shuffled)
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shuffle_all_btn")
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Aleatorio", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // Contenido según la pestaña seleccionada
        when (selectedTab) {
            LibraryTab.SONGS -> {
                if (filteredTracks.isEmpty()) {
                    item { EmptyLibraryView(onImportClick = { onNavigate(NavScreen.Import) }) }
                } else {
                    items(filteredTracks, key = { it.id }) { track ->
                        TrackListItem(
                            track = track,
                            isCurrentTrack = currentTrack?.id == track.id,
                            isPlaying = isPlaying,
                            onTrackClick = { onTrackClick(track, filteredTracks) },
                            onFavoriteToggle = { onFavoriteToggle(track) },
                            onDeleteTrack = { onDeleteTrack(track.id) },
                            playlists = playlists,
                            onAddToPlaylist = { playlistId -> onAddToPlaylist(playlistId, track.id) },
                            onEditTrack = { id, t, a, al -> onEditTrack(id, t, a, al) },
                            onEditTrackDetails = onEditTrackDetails,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }

            LibraryTab.FAVORITES -> {
                if (favoriteTracks.isEmpty()) {
                    item {
                        EmptyPlaceholder(
                            icon = Icons.Default.FavoriteBorder,
                            title = "Sin canciones favoritas aún",
                            subtitle = "Toca el icono de corazón en cualquier canción para guardarla aquí."
                        )
                    }
                } else {
                    items(favoriteTracks, key = { it.id }) { track ->
                        TrackListItem(
                            track = track,
                            isCurrentTrack = currentTrack?.id == track.id,
                            isPlaying = isPlaying,
                            onTrackClick = { onTrackClick(track, favoriteTracks) },
                            onFavoriteToggle = { onFavoriteToggle(track) },
                            onDeleteTrack = { onDeleteTrack(track.id) },
                            playlists = playlists,
                            onAddToPlaylist = { playlistId -> onAddToPlaylist(playlistId, track.id) },
                            onEditTrack = { id, t, a, al -> onEditTrack(id, t, a, al) },
                            onEditTrackDetails = onEditTrackDetails,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }

            LibraryTab.PLAYLISTS -> {
                // 1. Tarjeta Especial Destacada "Tus Me Gusta"
                item {
                    FavoritesPlaylistBannerCard(
                        trackCount = favoriteTracks.size,
                        onClick = {
                            onOpenPlaylist(
                                Playlist(
                                    id = -1L,
                                    name = "Tus Me Gusta",
                                    description = "Canciones favoritas guardadas",
                                    trackCount = favoriteTracks.size
                                )
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 2. Playlists del usuario
                if (playlists.isEmpty()) {
                    item {
                        EmptyPlaceholder(
                            icon = Icons.Default.QueueMusic,
                            title = "No hay listas personalizadas",
                            subtitle = "Crea tu primera lista pulsando 'Nueva Lista' arriba."
                        )
                    }
                } else {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistRowItem(
                            playlist = playlist,
                            onClick = { onOpenPlaylist(playlist) },
                            onRename = { playlistToRename = playlist },
                            onDelete = { onDeletePlaylist(playlist.id) }
                        )
                    }
                }
            }

            LibraryTab.ALBUMS -> {
                if (albumsList.isEmpty()) {
                    item { EmptyLibraryView(onImportClick = { onNavigate(NavScreen.Import) }) }
                } else {
                    items(albumsList, key = { it.key }) { (albumName, tracks) ->
                        AlbumGroupCard(
                            albumName = albumName,
                            artist = tracks.firstOrNull()?.artist ?: "Varios artistas",
                            trackCount = tracks.size,
                            representativeTrack = tracks.firstOrNull(),
                            previewTracks = tracks.take(4),
                            onClick = {
                                onOpenAlbum(albumName)
                            }
                        )
                    }
                }
            }

            LibraryTab.ARTISTS -> {
                if (artistsList.isEmpty()) {
                    item { EmptyLibraryView(onImportClick = { onNavigate(NavScreen.Import) }) }
                } else {
                    items(artistsList, key = { it.key }) { (artistName, tracks) ->
                        ArtistGroupCard(
                            artistName = artistName,
                            trackCount = tracks.size,
                            representativeTrack = tracks.firstOrNull(),
                            previewTracks = tracks.take(4),
                            onClick = {
                                onOpenArtist(artistName)
                            }
                        )
                    }
                }
            }
        }
    }

    // Cuadro de Diálogo para Crear Lista
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            onCreatePlaylist = onCreatePlaylist
        )
    }

    // Cuadro de Diálogo para Renombrar Lista
    if (playlistToRename != null) {
        RenamePlaylistDialog(
            playlist = playlistToRename!!,
            onDismissRequest = { playlistToRename = null },
            onRenamePlaylist = onRenamePlaylist
        )
    }
}
