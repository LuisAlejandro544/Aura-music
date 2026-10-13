package com.example.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.Playlist
import com.example.core.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.components.TrackListItem
import com.example.ui.navigation.LibraryTab
import com.example.ui.navigation.NavScreen
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Calendar
/**
 * Populares en tu biblioteca con lista completa.
 * Rol arquitectónico: sección LazyColumn de HomeScreen (fachada intacta).
 */
fun LazyListScope.homePopularSection(
    popularTracks: List<com.example.core.model.Track>,
    sectionTitle: String,
    onNavigate: (com.example.ui.navigation.NavScreen) -> Unit,
    currentTrack: com.example.core.model.Track?,
    isPlaying: Boolean,
    playlists: List<com.example.core.model.Playlist>,
    onTrackClick: (com.example.core.model.Track, List<com.example.core.model.Track>) -> Unit,
    onFavoriteToggle: (com.example.core.model.Track) -> Unit,
    onDeleteTrack: (Long) -> Unit,
    onAddToPlaylist: (Long, Long) -> Unit,
    onEditTrack: (Long, String, String, String) -> Unit,
    onEditTrackDetails: (Long, String, String, String, android.net.Uri?, Boolean) -> Unit
) {

            if (popularTracks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sectionTitle,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        TextButton(onClick = { onNavigate(NavScreen.Library) }) {
                            Text("Ver todas", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(popularTracks.take(8), key = { it.id }) { track ->
                    TrackListItem(
                        track = track,
                        isCurrentTrack = currentTrack?.id == track.id,
                        isPlaying = isPlaying,
                        onTrackClick = { onTrackClick(track, popularTracks) },
                        onFavoriteToggle = { onFavoriteToggle(track) },
                        onDeleteTrack = { onDeleteTrack(track.id) },
                        playlists = playlists,
                        onAddToPlaylist = { playlistId -> onAddToPlaylist(playlistId, track.id) },
                        onEditTrack = { id, t, a, al -> onEditTrack(id, t, a, al) },
                        onEditTrackDetails = onEditTrackDetails,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
}
