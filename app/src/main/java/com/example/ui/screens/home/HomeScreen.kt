package com.example.ui.screens.home

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
import java.util.Calendar

/**
 * Pantalla de Inicio inspirada en la experiencia de Spotify pero con colores vibrantes,
 * saludo dinámico según la hora del día, accesos rápidos de cuadrícula y colecciones recientes.
 */
@Composable
fun HomeScreen(
    allTracks: List<Track>,
    favoriteTracks: List<Track>,
    recentlyAddedTracks: List<Track>,
    topPlayedTracks: List<Track>,
    playlists: List<Playlist>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onTrackClick: (Track, List<Track>) -> Unit,
    onFavoriteToggle: (Track) -> Unit,
    onDeleteTrack: (Long) -> Unit,
    onAddToPlaylist: (Long, Long) -> Unit,
    onNavigate: (NavScreen) -> Unit,
    onSelectLibraryTab: (LibraryTab) -> Unit,
    onEditTrack: (Long, String, String, String) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Buenos días"
            in 12..19 -> "Buenas tardes"
            else -> "Buenas noches"
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Encabezado con Saludo y Atajo a Ajustes
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "Tu música local, sin conexión",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                }

                IconButton(
                    onClick = { onNavigate(NavScreen.Settings) },
                    modifier = Modifier.testTag("home_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ajustes",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Si la biblioteca está vacía, mostrar tarjeta de bienvenida para importar
        if (allTracks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    border = CardBorder.let { androidx.compose.foundation.BorderStroke(1.dp, it) }
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.LibraryMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Tu biblioteca está vacía",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Aura Music respeta tu privacidad: no escanea tu teléfono automáticamente. Importa tus archivos o prueba con canciones demo.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onNavigate(NavScreen.Import) },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("home_import_cta")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Importar mi Música", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Cuadrícula de accesos directos estilo Spotify con gradientes modernos
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeQuickTile(
                        title = "Canciones Favoritas",
                        subtitle = "${favoriteTracks.size} pistas",
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
                        subtitle = "${recentlyAddedTracks.size} pistas",
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
                        title = "Ecualizador FX",
                        subtitle = "Sonido optimizado",
                        icon = Icons.Default.Tune,
                        gradient = Brush.linearGradient(
                            listOf(Color(0xFF10B981), Color(0xFF06B6D4))
                        ),
                        onClick = { onNavigate(NavScreen.Equalizer) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // Sección: Escuchado recientemente (Carrusel Horizontal)
            if (recentlyAddedTracks.isNotEmpty()) {
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
                        items(recentlyAddedTracks.take(8)) { track ->
                            HomeAlbumCard(
                                track = track,
                                isPlaying = isPlaying && currentTrack?.id == track.id,
                                onClick = { onTrackClick(track, recentlyAddedTracks) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

            // Sección: Canciones Favoritas o Más escuchadas
            val tracksSection = if (favoriteTracks.isNotEmpty()) favoriteTracks else topPlayedTracks
            val sectionTitle = if (favoriteTracks.isNotEmpty()) "Tus favoritas" else "Populares en tu biblioteca"

            if (tracksSection.isNotEmpty()) {
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

                items(tracksSection.take(6)) { track ->
                    TrackListItem(
                        track = track,
                        isCurrentTrack = currentTrack?.id == track.id,
                        isPlaying = isPlaying,
                        onTrackClick = { onTrackClick(track, tracksSection) },
                        onFavoriteToggle = { onFavoriteToggle(track) },
                        onDeleteTrack = { onDeleteTrack(track.id) },
                        playlists = playlists,
                        onAddToPlaylist = { playlistId -> onAddToPlaylist(playlistId, track.id) },
                        onEditTrack = { id, t, a, al -> onEditTrack(id, t, a, al) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeQuickTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
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
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HomeAlbumCard(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(136.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            ArtworkImage(
                track = track,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 12.dp
            )
            // Botón flotante de reproducción en esquina
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
