package com.example.feature.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
 * Estado vacío o lista de pistas con menú de 3 puntos.
 * Rol arquitectónico: sección LazyColumn de PlaylistDetailScreen (fachada intacta).
 */
fun LazyListScope.playlistTrackItems(
    tracks: List<com.example.core.model.Track>,
    currentTrack: com.example.core.model.Track?,
    isPlaying: Boolean,
    isFavoritesVirtual: Boolean,
    isAlbumVirtual: Boolean,
    isArtistVirtual: Boolean,
    allPlaylists: List<com.example.core.model.Playlist>,
    onTrackClick: (com.example.core.model.Track, List<com.example.core.model.Track>) -> Unit,
    onFavoriteToggle: (com.example.core.model.Track) -> Unit,
    onDeleteTrack: (com.example.core.model.Track) -> Unit,
    onAddToPlaylist: (Long, Long) -> Unit,
    onEditTrack: (Long, String, String, String) -> Unit,
    onEditTrackDetails: (Long, String, String, String, android.net.Uri?, Boolean) -> Unit
) {
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
                    onDeleteTrack = { onDeleteTrack(track) },                    playlists = allPlaylists,
                    onAddToPlaylist = { pId -> onAddToPlaylist(pId, track.id) },
                    onEditTrack = { id, t, a, al -> onEditTrack(id, t, a, al) },
                    onEditTrackDetails = onEditTrackDetails,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
}
