package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.BottomNavBar
import com.example.ui.components.MiniPlayer
import com.example.ui.navigation.NavScreen
import com.example.ui.screens.equalizer.EqualizerScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.importmusic.ImportMusicScreen
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.nowplaying.NowPlayingScreen
import com.example.ui.screens.playlist.PlaylistDetailScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.theme.AuraMusicTheme
import com.example.ui.theme.BackgroundDark
import com.example.viewmodel.MusicViewModel

/**
 * Actividad Principal de Aura Music.
 * Configura Edge-to-Edge, vincula el ViewModel central, la barra de navegación,
 * el mini reproductor persistente y la vista expandida Now Playing.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: MusicViewModel = viewModel()
            val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()

            AuraMusicTheme(auraTheme = currentTheme) {
                AuraMusicApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AuraMusicApp(viewModel: MusicViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsStateWithLifecycle()

    // Estados de reproducción
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()

    // Estados de datos
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val recentlyAddedTracks by viewModel.recentlyAddedTracks.collectAsStateWithLifecycle()
    val topPlayedTracks by viewModel.topPlayedTracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
    val selectedPlaylistTracks by viewModel.selectedPlaylistTracks.collectAsStateWithLifecycle()

    // Estados de UI y Ecualizador
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedLibraryTab by viewModel.selectedLibraryTab.collectAsStateWithLifecycle()
    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importStatusMessage by viewModel.importStatusMessage.collectAsStateWithLifecycle()

    val eqBands by viewModel.eqBands.collectAsStateWithLifecycle()
    val bassBoostLevel by viewModel.bassBoostLevel.collectAsStateWithLifecycle()
    val currentPreset by viewModel.currentPreset.collectAsStateWithLifecycle()
    val isEqEnabled by viewModel.isEqEnabled.collectAsStateWithLifecycle()

    // Manejo de botón Atrás
    val canGoBack = isNowPlayingExpanded || (currentScreen !is NavScreen.Home)
    BackHandler(enabled = canGoBack) {
        viewModel.handleBackPress()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = BackgroundDark,
            contentWindowInsets = WindowInsets.statusBars,
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BackgroundDark)
                ) {
                    // Mini reproductor flotante
                    if (currentTrack != null && !isNowPlayingExpanded) {
                        MiniPlayer(
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            currentPositionMs = currentPosition,
                            durationMs = duration,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onSkipNext = { viewModel.playNext() },
                            onOpenEqualizer = { viewModel.navigateTo(NavScreen.Equalizer) },
                            onClick = { viewModel.setNowPlayingExpanded(true) }
                        )
                    }

                    // Barra de navegación inferior
                    BottomNavBar(
                        currentScreen = currentScreen,
                        onNavigate = { viewModel.navigateTo(it) }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentScreen) {
                    is NavScreen.Home -> HomeScreen(
                        allTracks = allTracks,
                        favoriteTracks = favoriteTracks,
                        recentlyAddedTracks = recentlyAddedTracks,
                        topPlayedTracks = topPlayedTracks,
                        playlists = playlists,
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        onTrackClick = { track, list -> viewModel.playTrack(track, list) },
                        onFavoriteToggle = { viewModel.toggleFavorite(it) },
                        onDeleteTrack = { viewModel.deleteTrack(it) },
                        onAddToPlaylist = { playlistId, trackId -> viewModel.addTrackToPlaylist(playlistId, trackId) },
                        onNavigate = { viewModel.navigateTo(it) },
                        onSelectLibraryTab = { viewModel.setLibraryTab(it) },
                        onEditTrack = { id, t, a, al -> viewModel.updateTrackInfo(id, t, a, al) }
                    )

                    is NavScreen.Library -> LibraryScreen(
                        allTracks = allTracks,
                        favoriteTracks = favoriteTracks,
                        playlists = playlists,
                        selectedTab = selectedLibraryTab,
                        onTabSelected = { viewModel.setLibraryTab(it) },
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        onTrackClick = { track, list -> viewModel.playTrack(track, list) },
                        onFavoriteToggle = { viewModel.toggleFavorite(it) },
                        onDeleteTrack = { viewModel.deleteTrack(it) },
                        onOpenPlaylist = { viewModel.openPlaylist(it) },
                        onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, desc) },
                        onDeletePlaylist = { viewModel.deletePlaylist(it) },
                        onAddToPlaylist = { playlistId, trackId -> viewModel.addTrackToPlaylist(playlistId, trackId) },
                        onNavigate = { viewModel.navigateTo(it) },
                        onEditTrack = { id, t, a, al -> viewModel.updateTrackInfo(id, t, a, al) }
                    )

                    is NavScreen.Import -> ImportMusicScreen(
                        allTracks = allTracks,
                        isImporting = isImporting,
                        importStatusMessage = importStatusMessage,
                        onImportUris = { viewModel.importUris(it) },
                        onImportFolder = { viewModel.importFolder(it) },
                        onSeedDemoTracks = { viewModel.seedDemoTracks() },
                        onClearLibrary = { viewModel.clearAllTracks() },
                        onDismissStatusMessage = { viewModel.dismissImportStatus() }
                    )

                    is NavScreen.Equalizer -> EqualizerScreen(
                        isEnabled = isEqEnabled,
                        bands = eqBands,
                        bassBoostLevel = bassBoostLevel,
                        currentPreset = currentPreset,
                        onToggleEnabled = { viewModel.setEqEnabled(it) },
                        onBandLevelChange = { index, level -> viewModel.setBandLevel(index, level) },
                        onBassBoostChange = { viewModel.setBassBoost(it) },
                        onPresetSelect = { viewModel.applyPreset(it) },
                        onBack = { viewModel.handleBackPress() }
                    )

                    is NavScreen.PlaylistDetail -> PlaylistDetailScreen(
                        playlist = selectedPlaylist,
                        tracks = selectedPlaylistTracks,
                        allPlaylists = playlists,
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        onBack = { viewModel.handleBackPress() },
                        onTrackClick = { track, list -> viewModel.playTrack(track, list) },
                        onFavoriteToggle = { viewModel.toggleFavorite(it) },
                        onRemoveFromPlaylist = { pId, tId -> viewModel.removeTrackFromPlaylist(pId, tId) },
                        onAddToPlaylist = { pId, tId -> viewModel.addTrackToPlaylist(pId, tId) },
                        onEditTrack = { id, t, a, al -> viewModel.updateTrackInfo(id, t, a, al) }
                    )

                    is NavScreen.Settings -> SettingsScreen(
                        currentTheme = currentTheme,
                        onSelectTheme = { viewModel.setTheme(it) }
                    )
                }
            }
        }

        // Pantalla Now Playing en modal animado deslizable con fondo 100% opaco
        AnimatedVisibility(
            visible = isNowPlayingExpanded && currentTrack != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
        ) {
            NowPlayingScreen(
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                currentPositionMs = currentPosition,
                durationMs = duration,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                queue = queue,
                currentIndex = currentIndex,
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onSeekTo = { viewModel.seekTo(it) },
                onPlayNext = { viewModel.playNext() },
                onPlayPrevious = { viewModel.playPrevious() },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onCycleRepeat = { viewModel.cycleRepeatMode() },
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onTrackSelectFromQueue = { index ->
                    queue.getOrNull(index)?.let { viewModel.playTrack(it, queue) }
                },
                onOpenEqualizer = {
                    viewModel.setNowPlayingExpanded(false)
                    viewModel.navigateTo(NavScreen.Equalizer)
                },
                onCollapse = { viewModel.setNowPlayingExpanded(false) },
                onEditTrack = { id, t, a, al -> viewModel.updateTrackInfo(id, t, a, al) }
            )
        }
    }
}
