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
 * Carrusel horizontal de escuchado recientemente.
 * Rol arquitectónico: sección LazyColumn de HomeScreen (fachada intacta).
 */
fun LazyListScope.homeRecentCarousel(
    recentTracks: List<com.example.core.model.Track>,
    currentTrack: com.example.core.model.Track?,
    isPlaying: Boolean,
    onTrackClick: (com.example.core.model.Track, List<com.example.core.model.Track>) -> Unit
) {
                item {
                    Text(
                        text = "Recientes para ti",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(recentTracks.take(8), key = { it.id }) { track ->
                            HomeAlbumCard(
                                track = track,
                                isPlaying = isPlaying && currentTrack?.id == track.id,
                                onClick = { onTrackClick(track, recentTracks) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }
}
