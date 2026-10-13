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
 * Cuadrícula de accesos directos con gradientes.
 * Rol arquitectónico: sección LazyColumn de HomeScreen (fachada intacta).
 */
fun LazyListScope.homeQuickTiles(
    mostPlayedPlaylist: com.example.core.model.Playlist?,
    favoriteCount: Int,
    recentCount: Int,
    topPlayedTracks: List<com.example.core.model.Track>,
    onTrackClick: (com.example.core.model.Track, List<com.example.core.model.Track>) -> Unit,
    onNavigate: (com.example.ui.navigation.NavScreen) -> Unit,
    onSelectLibraryTab: (com.example.ui.navigation.LibraryTab) -> Unit,
    onOpenPlaylist: (com.example.core.model.Playlist) -> Unit
) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeQuickTile(
                        title = "Canciones Favoritas",
                        subtitle = "${favoriteCount} pistas",
                        icon = Icons.Default.Favorite,
                        gradient = Brush.linearGradient(
                            listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))
                        ),
                        onClick = {
                            onSelectLibraryTab(LibraryTab.FAVORITES)
                            onNavigate(NavScreen.Library)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    HomeQuickTile(
                        title = "Añadidas Recientes",
                        subtitle = "${recentCount} pistas",
                        icon = Icons.Default.History,
                        gradient = Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                        ),
                        onClick = {
                            onSelectLibraryTab(LibraryTab.SONGS)
                            onNavigate(NavScreen.Library)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeQuickTile(
                        title = "Más Escuchadas",
                        subtitle = "${topPlayedTracks.size} pistas",
                        icon = Icons.Default.TrendingUp,
                        gradient = Brush.linearGradient(
                            listOf(Color(0xFFF97316), Color(0xFFF43F5E))
                        ),
                        onClick = {
                            if (topPlayedTracks.isNotEmpty()) {
                                onTrackClick(topPlayedTracks.first(), topPlayedTracks)
                            } else {
                                onNavigate(NavScreen.Library)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    HomeQuickTile(
                        title = mostPlayedPlaylist?.name ?: "Playlists",
                        subtitle = when {
                            mostPlayedPlaylist != null && mostPlayedPlaylist.totalPlays > 0 ->
                                "${mostPlayedPlaylist.trackCount} pistas • ${mostPlayedPlaylist.totalPlays} reprod."
                            mostPlayedPlaylist != null ->
                                "${mostPlayedPlaylist.trackCount} pistas"
                            else -> "Crear lista"
                        },
                        icon = Icons.Default.QueueMusic,
                        gradient = Brush.linearGradient(
                            listOf(Color(0xFF10B981), Color(0xFF06B6D4))
                        ),
                        onClick = {
                            if (mostPlayedPlaylist != null) {
                                onOpenPlaylist(mostPlayedPlaylist)
                            } else {
                                onSelectLibraryTab(LibraryTab.PLAYLISTS)
                                onNavigate(NavScreen.Library)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
}
