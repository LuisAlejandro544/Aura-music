package com.example.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
 * Contenido por pestaña: canciones, favoritos, playlists, álbumes y artistas.
 * Rol arquitectónico: sección LazyColumn de LibraryScreen (fachada intacta).
 */
fun LazyListScope.libraryTabContent(
    selectedTab: com.example.ui.navigation.LibraryTab,
    filteredTracks: List<com.example.core.model.Track>,
    favoriteTracks: List<com.example.core.model.Track>,
    playlists: List<com.example.core.model.Playlist>,
    albumsList: List<Map.Entry<String, List<com.example.core.model.Track>>>,
    artistsList: List<Map.Entry<String, List<com.example.core.model.Track>>>,
    currentTrack: com.example.core.model.Track?,
    isPlaying: Boolean,
    onTrackClick: (com.example.core.model.Track, List<com.example.core.model.Track>) -> Unit,
    onFavoriteToggle: (com.example.core.model.Track) -> Unit,
    onDeleteTrack: (Long) -> Unit,
    onOpenPlaylist: (com.example.core.model.Playlist) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onAddToPlaylist: (Long, Long) -> Unit,
    onEditTrack: (Long, String, String, String) -> Unit,
    onEditTrackDetails: (Long, String, String, String, android.net.Uri?, Boolean) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onRenamePlaylistClick: (com.example.core.model.Playlist) -> Unit,
    onOpenPlaylistImport: () -> Unit,
    onNavigateImport: () -> Unit
) {
        // Contenido según la pestaña seleccionada
        when (selectedTab) {
            LibraryTab.SONGS -> {
                if (filteredTracks.isEmpty()) {
                    item { EmptyLibraryView(onImportClick = { onNavigateImport() }) }
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

                // 2. Acceso directo a Importador Universal de Playlists (Spotify y YouTube Music)
                item {
                    OutlinedButton(
                        onClick = { onOpenPlaylistImport() },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF1DB954).copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = SurfaceCard,
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("library_playlists_tab_import_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = null,
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Importar Playlist de Spotify o YouTube Music",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 3. Playlists del usuario
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
                            onRename = { onRenamePlaylistClick(playlist) },
                            onDelete = { onDeletePlaylist(playlist.id) }
                        )
                    }
                }
            }

            LibraryTab.ALBUMS -> {
                if (albumsList.isEmpty()) {
                    item { EmptyLibraryView(onImportClick = { onNavigateImport() }) }
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
                    item { EmptyLibraryView(onImportClick = { onNavigateImport() }) }
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
