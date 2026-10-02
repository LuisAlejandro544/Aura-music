package com.example.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.ui.components.ArtworkImage
import com.example.ui.components.TrackListItem
import com.example.ui.navigation.LibraryTab
import com.example.ui.navigation.NavScreen
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pantalla de Tu Biblioteca con pestañas modulares (Canciones, Álbumes, Artistas, Playlists, Favoritos),
 * barra de búsqueda en tiempo real, creación de listas y filtros ágiles.
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
    onCreatePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onAddToPlaylist: (Long, Long) -> Unit,
    onNavigate: (NavScreen) -> Unit,
    onEditTrack: (Long, String, String, String) -> Unit = { _, _, _, _ -> },
    onEditTrackDetails: (Long, String, String, String, android.net.Uri?, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onRenamePlaylist: (Long, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showRenamePlaylistDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var renamePlaylistName by remember { mutableStateOf("") }
    var renamePlaylistDesc by remember { mutableStateOf("") }
    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistDesc by remember { mutableStateOf("") }

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
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
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
                            onRename = {
                                playlistToRename = playlist
                                renamePlaylistName = playlist.name
                                renamePlaylistDesc = playlist.description
                                showRenamePlaylistDialog = true
                            },
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
                            onClick = {
                                tracks.firstOrNull()?.let { onTrackClick(it, tracks) }
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
                            onClick = {
                                tracks.firstOrNull()?.let { onTrackClick(it, tracks) }
                            }
                        )
                    }
                }
            }
        }
    }

    // Cuadro de Diálogo para Crear Lista
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Crear Lista de Reproducción", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Nombre de la lista") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPlaylistDesc,
                        onValueChange = { newPlaylistDesc = it },
                        label = { Text("Descripción (opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName, newPlaylistDesc)
                            newPlaylistName = ""
                            newPlaylistDesc = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Cuadro de Diálogo para Renombrar Lista
    if (showRenamePlaylistDialog && playlistToRename != null) {
        AlertDialog(
            onDismissRequest = { showRenamePlaylistDialog = false },
            title = { Text("Renombrar Lista de Reproducción", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = renamePlaylistName,
                        onValueChange = { renamePlaylistName = it },
                        label = { Text("Nombre de la lista") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = renamePlaylistDesc,
                        onValueChange = { renamePlaylistDesc = it },
                        label = { Text("Descripción (opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renamePlaylistName.isNotBlank()) {
                            onRenamePlaylist(playlistToRename!!.id, renamePlaylistName, renamePlaylistDesc)
                            showRenamePlaylistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenamePlaylistDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun FavoritesPlaylistBannerCard(
    trackCount: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                            SurfaceCard
                        )
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tus Me Gusta",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$trackCount canciones favoritas sincronizadas",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Abrir Tus Me Gusta",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun PlaylistRowItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = SurfaceCard,
        border = CardBorder.let { androidx.compose.foundation.BorderStroke(1.dp, it) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                if (playlist.description.isNotBlank()) {
                    Text(
                        text = playlist.description,
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opciones de lista",
                        tint = TextMuted
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Abrir lista") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Renombrar lista") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar lista", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumGroupCard(
    albumName: String,
    artist: String,
    trackCount: Int,
    representativeTrack: Track?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = SurfaceCard,
        border = CardBorder.let { androidx.compose.foundation.BorderStroke(1.dp, it) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkImage(
                track = representativeTrack,
                modifier = Modifier.size(54.dp),
                cornerRadius = 8.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = albumName,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$artist • $trackCount canción(es)",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = "Reproducir álbum",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun ArtistGroupCard(
    artistName: String,
    trackCount: Int,
    representativeTrack: Track?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = SurfaceCard,
        border = CardBorder.let { androidx.compose.foundation.BorderStroke(1.dp, it) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                modifier = Modifier.size(50.dp)
            ) {
                ArtworkImage(
                    track = representativeTrack,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 25.dp
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$trackCount canción(es) disponible(s)",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    maxLines = 1
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary
            )
        }
    }
}

@Composable
private fun EmptyLibraryView(onImportClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(60.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Sin canciones en tu biblioteca",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Importa canciones de tu almacenamiento para comenzar a escuchar.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onImportClick,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Importar Música", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyPlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(54.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
