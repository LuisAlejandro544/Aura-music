package com.example.feature.home

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
 * Pantalla de Inicio Aura Sound con estética Dark Luxury OLED, acentos vibrantes de neón,
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
    onOpenPlaylist: (Playlist) -> Unit = {},
    onEditTrack: (Long, String, String, String) -> Unit = { _, _, _, _ -> },
    onEditTrackDetails: (Long, String, String, String, android.net.Uri?, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Buenos días"
            in 12..19 -> "Buenas tardes"
            else -> "Buenas noches"
        }
    }

    val mostPlayedPlaylist = remember(playlists) {
        playlists.maxWithOrNull(
            compareBy<Playlist> { it.totalPlays }
                .thenBy { it.trackCount }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Encabezado con Saludo Principal
        homeGreetingHeader(greeting = greeting)

        // Si la biblioteca está vacía, mostrar tarjeta de bienvenida centrada y optimizada para pantallas anchas
        if (allTracks.isEmpty()) {
            homeWelcomeEmpty(onNavigateImport = { onNavigate(NavScreen.Import) })
        } else {
            // Cuadrícula de accesos directos con gradientes modernos
            homeQuickTiles(
                mostPlayedPlaylist = mostPlayedPlaylist,
                favoriteCount = favoriteTracks.size,
                recentCount = recentlyAddedTracks.size,
                topPlayedTracks = topPlayedTracks,
                onTrackClick = onTrackClick,
                onNavigate = onNavigate,
                onSelectLibraryTab = onSelectLibraryTab,
                onOpenPlaylist = onOpenPlaylist
            )

            // Sección: Escuchado recientemente (Carrusel Horizontal)
            if (recentlyAddedTracks.isNotEmpty()) {
                homeRecentCarousel(
                    recentTracks = recentlyAddedTracks,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    onTrackClick = onTrackClick
                )
            }

            // Sección principal de canciones: Populares en tu biblioteca (o todas las pistas de la biblioteca)
            homePopularSection(
                popularTracks = if (topPlayedTracks.isNotEmpty()) topPlayedTracks else allTracks,
                sectionTitle = "Populares en tu biblioteca",
                onNavigate = onNavigate,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                playlists = playlists,
                onTrackClick = onTrackClick,
                onFavoriteToggle = onFavoriteToggle,
                onDeleteTrack = onDeleteTrack,
                onAddToPlaylist = onAddToPlaylist,
                onEditTrack = onEditTrack,
                onEditTrackDetails = onEditTrackDetails
            )
    }
}
}
