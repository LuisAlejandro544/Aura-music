package com.example.feature.library

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
import com.example.core.model.Playlist
import com.example.core.model.Track
import com.example.ui.components.TrackListItem
import com.example.ui.navigation.LibraryTab
import com.example.ui.navigation.NavScreen
import com.example.feature.library.components.*
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pantalla de Tu Biblioteca con pestañas modulares (Canciones, Álbumes, Artistas, Playlists, Favoritos),
 * barra de búsqueda en tiempo real, creación de listas y filtros ágiles.
 * Arquitectura Modular (MVVM):
 * Delega la representación de elementos y vistas en submódulos especializados en [com.example.feature.library.components]:
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
    onCreateMixtape: ((List<Track>, String, Int, ((Track) -> Unit)?) -> Unit)? = null,
    onCreateAutoRemix: ((Track, Track, String, com.example.core.model.AutoRemixStyle, Int, ((Track) -> Unit)?) -> Unit)? = null,
    isCreatingMixtape: Boolean = false,
    mixtapeProgress: Float = 0f,
    mixtapeStatusMessage: String? = null,
    onOpenPlaylistImportDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var showAutoRemixDialog by remember { mutableStateOf(false) }
    var createdRemixTrack by remember { mutableStateOf<Track?>(null) }

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
        // Título de la pantalla y botones de Nueva Lista / Importar Playlist
        libraryHeader(
            onOpenPlaylistImportDialog = onOpenPlaylistImportDialog,
            onNewListClick = { showCreatePlaylistDialog = true }
        )

        librarySearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange
        )

        libraryTabsRow(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected
        )

        libraryPlayActions(
            filteredTracks = filteredTracks,
            favoriteTracks = favoriteTracks,
            selectedTab = selectedTab,
            onTrackClick = onTrackClick,
            canAutoRemix = allTracks.count { !it.isStreamingTrack } >= 2 && onCreateAutoRemix != null,
            onOpenAutoRemix = {
                createdRemixTrack = null
                showAutoRemixDialog = true
            }
        )

        libraryTabContent(
            selectedTab = selectedTab,
            filteredTracks = filteredTracks,
            favoriteTracks = favoriteTracks,
            playlists = playlists,
            albumsList = albumsList,
            artistsList = artistsList,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            onTrackClick = onTrackClick,
            onFavoriteToggle = onFavoriteToggle,
            onDeleteTrack = onDeleteTrack,
            onOpenPlaylist = onOpenPlaylist,
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist,
            onAddToPlaylist = onAddToPlaylist,
            onEditTrack = onEditTrack,
            onEditTrackDetails = onEditTrackDetails,
            onDeletePlaylist = onDeletePlaylist,
            onRenamePlaylistClick = { playlistToRename = it },
            onOpenPlaylistImport = onOpenPlaylistImportDialog,
            onNavigateImport = { onNavigate(NavScreen.Import) }
        )
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

    // Cuadro de Diálogo para crear Auto-Remix / Mashup de 2 Canciones o Mixtape desde Biblioteca
    if (showAutoRemixDialog) {
        com.example.ui.components.CreateMixtapeDialog(
            initialTitle = "Aura Biblioteca Mix",
            tracks = allTracks,
            isCreating = isCreatingMixtape,
            progress = mixtapeProgress,
            statusMessage = mixtapeStatusMessage,
            onDismissRequest = {
                showAutoRemixDialog = false
                createdRemixTrack = null
            },
            onCreateMixtape = { title, crossfadeSec ->
                onCreateMixtape?.invoke(allTracks, title, crossfadeSec) { created ->
                    createdRemixTrack = created
                }
            },
            onCreateAutoRemix = { trackA, trackB, title, style, crossfadeSec ->
                onCreateAutoRemix?.invoke(trackA, trackB, title, style, crossfadeSec) { created ->
                    createdRemixTrack = created
                }
            },
            initialModeAutoRemix = true,
            createdTrack = createdRemixTrack,
            onPlayCreatedTrack = { created ->
                onTrackClick(created, listOf(created))
                showAutoRemixDialog = false
            }
        )
    }
}
