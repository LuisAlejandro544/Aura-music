package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Configura Edge-to-Edge, vincula el ViewModel central reactivo,
 * navegación con transiciones animadas fluidas, mini reproductor persistente
 * y vista completa Now Playing con fondo 100% opaco OLED.
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

    // Estados de reproducción Media3
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()

    // Estados de datos de Room
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

    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val spatial8DConfig by viewModel.spatial8DConfig.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val playbackPitch by viewModel.playbackPitch.collectAsStateWithLifecycle()
    val crossfadeSeconds by viewModel.crossfadeSeconds.collectAsStateWithLifecycle()
    val isGaplessEnabled by viewModel.isGaplessEnabled.collectAsStateWithLifecycle()
    val isVideoCanvasActive by viewModel.isVideoCanvasActive.collectAsStateWithLifecycle()
    val isDynamicArtworkColorEnabled by viewModel.isDynamicArtworkColorEnabled.collectAsStateWithLifecycle()

    var showGlobalAudioEffectsSheet by remember { mutableStateOf(false) }
    var initialAudioEffectsTab by remember { mutableIntStateOf(0) }

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
                    // Mini reproductor flotante con transición suave
                    AnimatedVisibility(
                        visible = currentTrack != null && !isNowPlayingExpanded,
                        enter = fadeIn(animationSpec = tween(220)) + slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ),
                        exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(
                            targetOffsetY = { it / 2 },
                            animationSpec = tween(180)
                        )
                    ) {
                        MiniPlayer(
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            currentPositionMs = currentPosition,
                            durationMs = duration,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onSkipNext = { viewModel.playNext() },
                            onOpenEqualizer = {
                                initialAudioEffectsTab = 0
                                showGlobalAudioEffectsSheet = true
                            },
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
                // Transición animada fluida entre pantallas del sistema
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(240)) + slideInHorizontally(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ) { it / 8 })
                            .togetherWith(
                                fadeOut(animationSpec = tween(180)) + slideOutHorizontally(
                                    animationSpec = tween(180)
                                ) { -it / 8 }
                            )
                    },
                    label = "ScreenTransition"
                ) { targetScreen ->
                    when (targetScreen) {
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
                            onEditTrack = { id, t, a, al -> viewModel.updateTrackInfo(id, t, a, al) },
                            onEditTrackDetails = { id, t, a, al, art, removeArt ->
                                viewModel.updateTrackDetails(id, t, a, al, art, removeArt)
                            }
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
                            onRenamePlaylist = { pId, name, desc -> viewModel.updatePlaylist(pId, name, desc) },
                            onDeletePlaylist = { viewModel.deletePlaylist(it) },
                            onAddToPlaylist = { playlistId, trackId -> viewModel.addTrackToPlaylist(playlistId, trackId) },
                            onNavigate = { viewModel.navigateTo(it) },
                            onEditTrack = { id, t, a, al -> viewModel.updateTrackInfo(id, t, a, al) },
                            onEditTrackDetails = { id, t, a, al, art, removeArt ->
                                viewModel.updateTrackDetails(id, t, a, al, art, removeArt)
                            }
                        )

                        is NavScreen.Import -> ImportMusicScreen(
                            allTracks = allTracks,
                            isImporting = isImporting,
                            importStatusMessage = importStatusMessage,
                            onImportUris = { viewModel.importUris(it) },
                            onImportFolder = { viewModel.importFolder(it) },
                            onSeedDemoTracks = { viewModel.seedDemoTracks() },
                            onClearLibrary = { viewModel.clearAllTracks() },
                            onDismissStatusMessage = { viewModel.dismissImportStatus() },
                            onImportVideoAsMusic = { videoUri, title, artist, album, attachCanvas, forceLoop ->
                                viewModel.importVideoAsTrack(videoUri, title, artist, album, attachCanvas, forceLoop) { createdTrack ->
                                    viewModel.playTrack(createdTrack, listOf(createdTrack))
                                    viewModel.setNowPlayingExpanded(true)
                                }
                            }
                        )

                        is NavScreen.Equalizer -> {
                            LaunchedEffect(Unit) {
                                initialAudioEffectsTab = 0
                                showGlobalAudioEffectsSheet = true
                                viewModel.handleBackPress()
                            }
                            HomeScreen(
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
                                onEditTrack = { id, t, a, al -> viewModel.updateTrackInfo(id, t, a, al) },
                                onEditTrackDetails = { id, t, a, al, art, removeArt ->
                                    viewModel.updateTrackDetails(id, t, a, al, art, removeArt)
                                }
                            )
                        }

                        is NavScreen.PlaylistDetail -> PlaylistDetailScreen(
                            playlist = selectedPlaylist,
                            tracks = selectedPlaylistTracks,
                            allTracks = allTracks,
                            allPlaylists = playlists,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            onBack = { viewModel.handleBackPress() },
                            onTrackClick = { track, list -> viewModel.playTrack(track, list) },
                            onFavoriteToggle = { viewModel.toggleFavorite(it) },
                            onRemoveFromPlaylist = { pId, tId -> viewModel.removeTrackFromPlaylist(pId, tId) },
                            onAddToPlaylist = { pId, tId -> viewModel.addTrackToPlaylist(pId, tId) },
                            onRenamePlaylist = { pId, name, desc -> viewModel.updatePlaylist(pId, name, desc) },
                            onEditTrack = { id, t, a, al -> viewModel.updateTrackInfo(id, t, a, al) },
                            onEditTrackDetails = { id, t, a, al, art, removeArt ->
                                viewModel.updateTrackDetails(id, t, a, al, art, removeArt)
                            }
                        )

                        is NavScreen.Settings -> SettingsScreen(
                            currentTheme = currentTheme,
                            onSelectTheme = { viewModel.setTheme(it) },
                            isDynamicArtworkColorEnabled = isDynamicArtworkColorEnabled,
                            onToggleDynamicArtworkColor = { viewModel.toggleDynamicArtworkColor(it) }
                        )
                    }
                }
            }
        }

        // Pantalla Now Playing con deslizamiento suave sin fondo negro residual
        AnimatedVisibility(
            visible = isNowPlayingExpanded && currentTrack != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 220)
            ),
            modifier = Modifier.fillMaxSize()
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
                    initialAudioEffectsTab = 0
                    showGlobalAudioEffectsSheet = true
                },
                onCollapse = { viewModel.setNowPlayingExpanded(false) },
                onEditTrack = { id, t, a, al -> viewModel.updateTrackInfo(id, t, a, al) },
                onEditTrackDetails = { id, t, a, al, art, removeArt ->
                    viewModel.updateTrackDetails(id, t, a, al, art, removeArt)
                },
                onEditTrackDetailsWithVideo = { id, t, a, al, art, removeArt, video, removeVideo, forceLoop ->
                    viewModel.updateTrackDetails(id, t, a, al, art, removeArt, video, removeVideo, forceLoop)
                },
                isVideoCanvasActive = isVideoCanvasActive,
                isDynamicArtworkColorEnabled = isDynamicArtworkColorEnabled,
                onToggleVideoCanvas = { viewModel.toggleVideoCanvas() },
                sleepTimerState = sleepTimerState,
                onStartSleepTimer = { viewModel.startSleepTimer(it) },
                onCancelSleepTimer = { viewModel.cancelSleepTimer() },
                onAddSleepTimerMinutes = { viewModel.addSleepTimerMinutes(it) },
                spatial8DConfig = spatial8DConfig,
                onSet8DEnabled = { viewModel.set8DEnabled(it) },
                onSet8DOrbitSpeed = { viewModel.set8DOrbitSpeed(it) },
                onSet8DSpatialIntensity = { viewModel.set8DSpatialIntensity(it) },
                onSet8DRoomDepth = { viewModel.set8DRoomDepth(it) },
                playbackSpeed = playbackSpeed,
                onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
                playbackPitch = playbackPitch,
                onSetPlaybackPitch = { viewModel.setPlaybackPitch(it) },
                onResetSpeedAndPitch = { viewModel.resetSpeedAndPitch() },
                crossfadeSeconds = crossfadeSeconds,
                onSetCrossfadeSeconds = { viewModel.setCrossfadeSeconds(it) },
                isGaplessEnabled = isGaplessEnabled,
                onSetGaplessEnabled = { viewModel.setGaplessEnabled(it) },
                isEqEnabled = isEqEnabled,
                eqBands = eqBands,
                bassBoostLevel = bassBoostLevel,
                currentPreset = currentPreset,
                onToggleEqEnabled = { viewModel.setEqEnabled(it) },
                onBandLevelChange = { index, level -> viewModel.setBandLevel(index, level) },
                onBassBoostChange = { viewModel.setBassBoost(it) },
                onPresetSelect = { viewModel.applyPreset(it) }
            )
        }

        // Hoja modal unificada de Efectos de Audio y Ecualizador accesible globalmente
        if (showGlobalAudioEffectsSheet) {
            com.example.ui.components.AudioEffectsBottomSheet(
                onDismissRequest = { showGlobalAudioEffectsSheet = false },
                isEqEnabled = isEqEnabled,
                eqBands = eqBands,
                bassBoostLevel = bassBoostLevel,
                currentPreset = currentPreset,
                onToggleEqEnabled = { viewModel.setEqEnabled(it) },
                onBandLevelChange = { index, level -> viewModel.setBandLevel(index, level) },
                onBassBoostChange = { viewModel.setBassBoost(it) },
                onPresetSelect = { viewModel.applyPreset(it) },
                sleepTimerState = sleepTimerState,
                onStartSleepTimer = { viewModel.startSleepTimer(it) },
                onCancelSleepTimer = { viewModel.cancelSleepTimer() },
                onAddSleepTimerMinutes = { viewModel.addSleepTimerMinutes(it) },
                spatial8DConfig = spatial8DConfig,
                onSet8DEnabled = { viewModel.set8DEnabled(it) },
                onSet8DOrbitSpeed = { viewModel.set8DOrbitSpeed(it) },
                onSet8DSpatialIntensity = { viewModel.set8DSpatialIntensity(it) },
                onSet8DRoomDepth = { viewModel.set8DRoomDepth(it) },
                playbackSpeed = playbackSpeed,
                onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
                playbackPitch = playbackPitch,
                onSetPlaybackPitch = { viewModel.setPlaybackPitch(it) },
                onResetSpeedAndPitch = { viewModel.resetSpeedAndPitch() },
                crossfadeSeconds = crossfadeSeconds,
                onSetCrossfadeSeconds = { viewModel.setCrossfadeSeconds(it) },
                isGaplessEnabled = isGaplessEnabled,
                onSetGaplessEnabled = { viewModel.setGaplessEnabled(it) },
                initialTab = initialAudioEffectsTab
            )
        }
    }
}
