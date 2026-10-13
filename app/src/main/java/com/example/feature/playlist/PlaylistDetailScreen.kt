package com.example.feature.playlist

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
import com.example.core.model.Playlist
import com.example.core.model.Track
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
    onCreateMixtape: ((List<Track>, String, Int, ((Track) -> Unit)?) -> Unit)? = null,
    onCreateAutoRemix: ((Track, Track, String, com.example.core.model.AutoRemixStyle, Int, ((Track) -> Unit)?) -> Unit)? = null,
    isCreatingMixtape: Boolean = false,
    mixtapeProgress: Float = 0f,
    mixtapeStatusMessage: String? = null,
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

    var showCreateMixtapeDialog by remember { mutableStateOf(false) }
    var openMixtapeInAutoRemixMode by remember { mutableStateOf(false) }
    var createdMixtapeTrack by remember { mutableStateOf<Track?>(null) }

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
        playlistDetailHeader(
            playlist = playlist,
            tracks = tracks,
            isFavoritesVirtual = isFavoritesVirtual,
            isAlbumVirtual = isAlbumVirtual,
            isArtistVirtual = isArtistVirtual,
            isVirtualCollection = isVirtualCollection,
            onBack = onBack,
            onRenameClick = {
                renameName = playlist.name
                renameDesc = playlist.description
                showRenameDialog = true
            }
        )

        playlistDetailActions(
            tracks = tracks,
            isVirtualCollection = isVirtualCollection,
            allTracks = allTracks,
            onTrackClick = onTrackClick,
            onAddClick = { showAddTracksDialog = true },
            onOpenMixtape = { autoRemix ->
                createdMixtapeTrack = null
                openMixtapeInAutoRemixMode = autoRemix
                showCreateMixtapeDialog = true
            }
        )

        playlistTrackItems(
            tracks = tracks,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            isFavoritesVirtual = isFavoritesVirtual,
            isAlbumVirtual = isAlbumVirtual,
            isArtistVirtual = isArtistVirtual,
            allPlaylists = allPlaylists,
            onTrackClick = onTrackClick,
            onFavoriteToggle = onFavoriteToggle,
            onDeleteTrack = { track ->
                when {
                    isFavoritesVirtual -> onFavoriteToggle(track)
                    isAlbumVirtual || isArtistVirtual -> onDeleteTrackFromLibrary(track.id)
                    else -> onRemoveFromPlaylist(playlist.id, track.id)
                }
            },
            onAddToPlaylist = onAddToPlaylist,
            onEditTrack = onEditTrack,
            onEditTrackDetails = onEditTrackDetails
        )

    }
    // Cuadro de diálogo para editar nombre, descripción e imagen de la playlist
    if (showRenameDialog && !isVirtualCollection) {
        com.example.feature.library.components.RenamePlaylistDialog(
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
        PlaylistAddTracksDialog(
            playlistName = playlist.name,
            playlistId = playlist.id,
            query = trackSearchQuery,
            onQueryChange = { trackSearchQuery = it },
            available = availableToAdd,
            onAdd = { pId, tId -> onAddToPlaylist(pId, tId) },
            onDismiss = { showAddTracksDialog = false }
        )
    }

    // Cuadro de diálogo para crear Mixtape continuo o Auto-Remix de 2 canciones locales con FFmpeg
    if (showCreateMixtapeDialog) {
        com.example.ui.components.CreateMixtapeDialog(
            initialTitle = "${playlist.name} (Continuous Mix)",
            tracks = tracks,
            isCreating = isCreatingMixtape,
            progress = mixtapeProgress,
            statusMessage = mixtapeStatusMessage,
            onDismissRequest = {
                showCreateMixtapeDialog = false
                createdMixtapeTrack = null
            },
            onCreateMixtape = { title, crossfadeSec ->
                onCreateMixtape?.invoke(tracks, title, crossfadeSec) { created ->
                    createdMixtapeTrack = created
                }
            },
            onCreateAutoRemix = { trackA, trackB, title, style, crossfadeSec ->
                onCreateAutoRemix?.invoke(trackA, trackB, title, style, crossfadeSec) { created ->
                    createdMixtapeTrack = created
                }
            },
            initialModeAutoRemix = openMixtapeInAutoRemixMode,
            createdTrack = createdMixtapeTrack,
            onPlayCreatedTrack = { created ->
                onTrackClick(created, listOf(created))
                showCreateMixtapeDialog = false
            }
        )
    }
}
