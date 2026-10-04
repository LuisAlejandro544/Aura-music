package com.example

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.CardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.model.VideoDisplayMode
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
import com.example.ui.theme.ArtworkColorExtractor
import com.example.ui.theme.AuraMusicTheme
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.ExtractedArtworkColors
import com.example.viewmodel.MusicViewModel

/**
 * Actividad Principal de Aura Music.
 * Configura Edge-to-Edge, vincula el ViewModel central reactivo,
 * navegación con transiciones animadas fluidas, mini reproductor persistente,
 * control de botones físicos de auriculares y vista completa Now Playing.
 */
class MainActivity : ComponentActivity() {

    private var musicViewModel: MusicViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: MusicViewModel = viewModel()
            musicViewModel = viewModel

            // Procesar Intent de inicio ("Abrir con..." o "Compartir con...")
            LaunchedEffect(intent) {
                viewModel.onIncomingIntent(intent)
            }

            val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()

            val currentDensity = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                    density = currentDensity.density,
                    fontScale = 1.0f
                )
            ) {
                AuraMusicTheme(auraTheme = currentTheme) {
                    AuraMusicApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        musicViewModel?.onIncomingIntent(intent)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        musicViewModel?.let { vm ->
            if (vm.headphoneController.onKeyEvent(event.keyCode, event)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
fun AuraMusicApp(viewModel: MusicViewModel) {
    val context = LocalContext.current

    // Solicitud del permiso de notificaciones para Android 13+ (API 33+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { /* Notificaciones concedidas o denegadas */ }

        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

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
    val reverbConfig by viewModel.reverbConfig.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val playbackPitch by viewModel.playbackPitch.collectAsStateWithLifecycle()
    val crossfadeSeconds by viewModel.crossfadeSeconds.collectAsStateWithLifecycle()
    val isGaplessEnabled by viewModel.isGaplessEnabled.collectAsStateWithLifecycle()
    val abLoopState by viewModel.abLoopState.collectAsStateWithLifecycle()
    val videoDisplayMode by viewModel.videoDisplayMode.collectAsStateWithLifecycle()
    val isVideoCanvasActive by viewModel.isVideoCanvasActive.collectAsStateWithLifecycle()
    val isDynamicArtworkColorEnabled by viewModel.isDynamicArtworkColorEnabled.collectAsStateWithLifecycle()
    val headphoneConfig by viewModel.headphoneConfig.collectAsStateWithLifecycle()
    val visualizerBands by viewModel.visualizerBands.collectAsStateWithLifecycle()
    val audioIntensity by viewModel.audioIntensity.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
    val pendingIncomingAudioUris by viewModel.pendingIncomingAudioUris.collectAsStateWithLifecycle()
    val pendingIncomingVideoUri by viewModel.pendingIncomingVideoUri.collectAsStateWithLifecycle()
    val pendingIncomingWebLink by viewModel.pendingIncomingWebLink.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()

    var showGlobalAudioEffectsSheet by remember { mutableStateOf(false) }
    var initialAudioEffectsTab by remember { mutableIntStateOf(0) }

    // Paleta de color dinámica para el Mini Reproductor sincronizada con la pista activa
    val defaultMiniPrimary = MaterialTheme.colorScheme.primary
    val defaultMiniSecondary = MaterialTheme.colorScheme.secondary
    var miniPlayerColors by remember {
        mutableStateOf(
            ExtractedArtworkColors(
                primary = defaultMiniPrimary,
                secondary = defaultMiniSecondary,
                accent = defaultMiniPrimary,
                ambientTopGlow = defaultMiniPrimary.copy(alpha = 0.25f)
            )
        )
    }

    LaunchedEffect(currentTrack?.id, currentTrack?.albumArtPath, currentTrack?.videoUri, isDynamicArtworkColorEnabled) {
        if (currentTrack != null && isDynamicArtworkColorEnabled) {
            val isVideo = (videoDisplayMode != VideoDisplayMode.OFF) && !currentTrack?.videoUri.isNullOrEmpty()
            miniPlayerColors = ArtworkColorExtractor.extractPlaybackColors(
                context = context,
                track = currentTrack,
                isVideoActive = isVideo,
                isDynamicEnabled = isDynamicArtworkColorEnabled,
                fallbackPrimary = defaultMiniPrimary,
                fallbackSecondary = defaultMiniSecondary,
                positionMs = currentPosition
            )
        } else {
            miniPlayerColors = ExtractedArtworkColors(
                primary = defaultMiniPrimary,
                secondary = defaultMiniSecondary,
                accent = defaultMiniPrimary,
                ambientTopGlow = defaultMiniPrimary.copy(alpha = 0.25f)
            )
        }
    }

    val animatedMiniPrimary by animateColorAsState(
        targetValue = miniPlayerColors.primary,
        animationSpec = tween(600),
        label = "MiniPlayerPrimary"
    )
    val animatedMiniSecondary by animateColorAsState(
        targetValue = miniPlayerColors.secondary,
        animationSpec = tween(600),
        label = "MiniPlayerSecondary"
    )

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
                    // Mini reproductor flotante con transición suave y fondo tintado dinámico
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
                            onSkipPrevious = { viewModel.playPrevious() },
                            onClick = { viewModel.setNowPlayingExpanded(true) },
                            dynamicPrimary = animatedMiniPrimary,
                            dynamicSecondary = animatedMiniSecondary
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
                            downloadProgress = downloadProgress,
                            onImportUris = { uris, trimSilence -> viewModel.importUris(uris, trimSilence) },
                            onImportFolder = { treeUri, trimSilence -> viewModel.importFolder(treeUri, trimSilence) },
                            onSeedDemoTracks = { viewModel.seedDemoTracks() },
                            onClearLibrary = { viewModel.clearAllTracks() },
                            onDismissStatusMessage = { viewModel.dismissImportStatus() },
                            onImportVideoAsMusic = { videoUri, title, artist, album, attachCanvas, forceLoop, trimSilence ->
                                viewModel.importVideoAsTrack(videoUri, title, artist, album, attachCanvas, forceLoop, trimSilence) { createdTrack ->
                                    viewModel.playTrack(createdTrack)
                                    viewModel.setNowPlayingExpanded(true)
                                }
                            },
                            onDownloadFromLink = { info, title, artist, canvas, trimSilence ->
                                viewModel.importFromWebVideoLink(info, title, artist, canvas, trimSilence) { createdTrack ->
                                    viewModel.playTrack(createdTrack)
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
                            onToggleDynamicArtworkColor = { viewModel.toggleDynamicArtworkColor(it) },
                            headphoneConfig = headphoneConfig,
                            onUpdateHeadphoneConfig = { viewModel.updateHeadphoneConfig(it) },
                            onSetCrossfeedEnabled = { viewModel.setCrossfeedEnabled(it) },
                            onSetCrossfeedStrength = { viewModel.setCrossfeedStrength(it) },
                            onSetBalanceControlEnabled = { viewModel.setBalanceControlEnabled(it) },
                            onSetStereoBalance = { viewModel.setStereoBalance(it) },
                            onSetBecomingNoisyGuardEnabled = { viewModel.setBecomingNoisyGuardEnabled(it) },
                            onSetFadeInOnResumeEnabled = { viewModel.setFadeInOnResumeEnabled(it) },
                            onSetDedicatedVolumeMemoryEnabled = { viewModel.setDedicatedVolumeMemoryEnabled(it) },
                            onSetHeadsetControlsEnabled = { viewModel.setHeadsetControlsEnabled(it) },
                            onSetHeadsetAction = { type, action -> viewModel.setHeadsetAction(type, action) }
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
                videoDisplayMode = videoDisplayMode,
                isDynamicArtworkColorEnabled = isDynamicArtworkColorEnabled,
                onToggleVideoCanvas = { viewModel.toggleVideoCanvas() },
                onSetVideoDisplayMode = { viewModel.setVideoDisplayMode(it) },
                onCycleVideoDisplayMode = { viewModel.cycleVideoDisplayMode() },
                sleepTimerState = sleepTimerState,
                onStartSleepTimer = { viewModel.startSleepTimer(it) },
                onCancelSleepTimer = { viewModel.cancelSleepTimer() },
                onAddSleepTimerMinutes = { viewModel.addSleepTimerMinutes(it) },
                spatial8DConfig = spatial8DConfig,
                onSet8DEnabled = { viewModel.set8DEnabled(it) },
                onSet8DOrbitSpeed = { viewModel.set8DOrbitSpeed(it) },
                onSet8DSpatialIntensity = { viewModel.set8DSpatialIntensity(it) },
                onSet8DRoomDepth = { viewModel.set8DRoomDepth(it) },
                reverbConfig = reverbConfig,
                onSetReverbEnabled = { viewModel.setReverbEnabled(it) },
                onSetReverbPreset = { viewModel.setReverbPreset(it) },
                onSetReverbCustomParameters = { roomSize, decayMs, levelDb ->
                    viewModel.setReverbCustomParameters(roomSize, decayMs, levelDb)
                },
                playbackSpeed = playbackSpeed,
                onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
                playbackPitch = playbackPitch,
                onSetPlaybackPitch = { viewModel.setPlaybackPitch(it) },
                onResetSpeedAndPitch = { viewModel.resetSpeedAndPitch() },
                crossfadeSeconds = crossfadeSeconds,
                onSetCrossfadeSeconds = { viewModel.setCrossfadeSeconds(it) },
                isGaplessEnabled = isGaplessEnabled,
                onSetGaplessEnabled = { viewModel.setGaplessEnabled(it) },
                abLoopState = abLoopState,
                onMarkABPointA = { viewModel.markABPointA() },
                onMarkABPointB = { viewModel.markABPointB() },
                onToggleABLoopEnabled = { viewModel.toggleABLoopEnabled(it) },
                onAdjustABPointA = { viewModel.adjustABPointA(it) },
                onAdjustABPointB = { viewModel.adjustABPointB(it) },
                onClearABLoop = { viewModel.clearABLoop() },
                isEqEnabled = isEqEnabled,
                eqBands = eqBands,
                bassBoostLevel = bassBoostLevel,
                currentPreset = currentPreset,
                onToggleEqEnabled = { viewModel.setEqEnabled(it) },
                onBandLevelChange = { index, level -> viewModel.setBandLevel(index, level) },
                onBassBoostChange = { viewModel.setBassBoost(it) },
                onPresetSelect = { viewModel.applyPreset(it) },
                headphoneConfig = headphoneConfig,
                onSetCrossfeedEnabled = { viewModel.setCrossfeedEnabled(it) },
                onSetCrossfeedStrength = { viewModel.setCrossfeedStrength(it) },
                onSetBalanceControlEnabled = { viewModel.setBalanceControlEnabled(it) },
                onSetStereoBalance = { viewModel.setStereoBalance(it) },
                visualizerBands = visualizerBands,
                audioIntensity = audioIntensity,
                lyricsState = lyricsState,
                onFetchOnlineLyrics = { viewModel.fetchOnlineLyrics() },
                onSaveCustomLyrics = { viewModel.saveCustomLyrics(it) }
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
                spatial8DConfig = spatial8DConfig,
                onSet8DEnabled = { viewModel.set8DEnabled(it) },
                onSet8DOrbitSpeed = { viewModel.set8DOrbitSpeed(it) },
                onSet8DSpatialIntensity = { viewModel.set8DSpatialIntensity(it) },
                onSet8DRoomDepth = { viewModel.set8DRoomDepth(it) },
                reverbConfig = reverbConfig,
                onSetReverbEnabled = { viewModel.setReverbEnabled(it) },
                onSetReverbPreset = { viewModel.setReverbPreset(it) },
                onSetReverbCustomParameters = { roomSize, decayMs, levelDb ->
                    viewModel.setReverbCustomParameters(roomSize, decayMs, levelDb)
                },
                sleepTimerState = sleepTimerState,
                onStartSleepTimer = { viewModel.startSleepTimer(it) },
                onCancelSleepTimer = { viewModel.cancelSleepTimer() },
                onAddSleepTimerMinutes = { viewModel.addSleepTimerMinutes(it) },
                playbackSpeed = playbackSpeed,
                onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
                playbackPitch = playbackPitch,
                onSetPlaybackPitch = { viewModel.setPlaybackPitch(it) },
                onResetSpeedAndPitch = { viewModel.resetSpeedAndPitch() },
                crossfadeSeconds = crossfadeSeconds,
                onSetCrossfadeSeconds = { viewModel.setCrossfadeSeconds(it) },
                isGaplessEnabled = isGaplessEnabled,
                onSetGaplessEnabled = { viewModel.setGaplessEnabled(it) },
                abLoopState = abLoopState,
                onMarkABPointA = { viewModel.markABPointA() },
                onMarkABPointB = { viewModel.markABPointB() },
                onToggleABLoopEnabled = { viewModel.toggleABLoopEnabled(it) },
                onAdjustABPointA = { viewModel.adjustABPointA(it) },
                onAdjustABPointB = { viewModel.adjustABPointB(it) },
                onClearABLoop = { viewModel.clearABLoop() },
                headphoneConfig = headphoneConfig,
                onSetCrossfeedEnabled = { viewModel.setCrossfeedEnabled(it) },
                onSetCrossfeedStrength = { viewModel.setCrossfeedStrength(it) },
                onSetBalanceControlEnabled = { viewModel.setBalanceControlEnabled(it) },
                onSetStereoBalance = { viewModel.setStereoBalance(it) },
                initialTab = initialAudioEffectsTab
            )
        }

        // Diálogo emergente interactivo cuando se abre o comparte un archivo de audio ("Abrir con..." o "Compartir con...")
        if (pendingIncomingAudioUris != null && pendingIncomingAudioUris!!.isNotEmpty()) {
            val incomingCount = pendingIncomingAudioUris!!.size
            var incomingTrimSilence by remember(pendingIncomingAudioUris) { mutableStateOf(true) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.clearPendingIncomingAudio() },
                title = {
                    Text(
                        text = if (incomingCount == 1) "Importar y Reproducir Audio" else "Importar $incomingCount Pistas de Audio",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Se guardará una copia en el almacenamiento privado de Aura Music y comenzará la reproducción.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Eliminar silencios al inicio y final",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Recorta automáticamente espacios silenciosos antes y después de la canción.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Switch(
                                    checked = incomingTrimSilence,
                                    onCheckedChange = { incomingTrimSilence = it },
                                    colors = androidx.compose.material3.SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF10B981)
                                    )
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.Button(
                        onClick = { viewModel.confirmIncomingAudioImport(incomingTrimSilence) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Importar y Reproducir", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { viewModel.clearPendingIncomingAudio() }
                    ) {
                        Text("Cancelar", color = TextSecondary)
                    }
                },
                containerColor = com.example.ui.theme.SurfaceCard,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Diálogo emergente interactivo cuando se abre o comparte un archivo de video ("Abrir con..." o SnapTube)
        if (pendingIncomingVideoUri != null) {
            com.example.ui.components.VideoToMusicDialog(
                videoUri = pendingIncomingVideoUri!!,
                onDismiss = { viewModel.clearPendingIncomingVideo() },
                onConfirm = { title, artist, album, attachAsCanvas, forceLoop, trimSilence ->
                    viewModel.importVideoAsTrack(
                        videoUri = pendingIncomingVideoUri!!,
                        title = title,
                        artist = artist,
                        album = album,
                        attachAsCanvas = attachAsCanvas,
                        forceLoop = forceLoop,
                        trimSilence = trimSilence
                    ) { track ->
                        viewModel.playTrack(track)
                        viewModel.setNowPlayingExpanded(true)
                    }
                    viewModel.clearPendingIncomingVideo()
                }
            )
        }

        // Diálogo emergente interactivo cuando se comparte un enlace web o video online
        if (pendingIncomingWebLink != null) {
            com.example.ui.components.DownloadFromLinkDialog(
                initialUrl = pendingIncomingWebLink!!,
                downloadProgress = downloadProgress,
                onDismiss = { viewModel.clearPendingIncomingWebLink() },
                onConfirmDownload = { resolvedInfo, title, artist, attachAsCanvas, trimSilence ->
                    viewModel.importFromWebVideoLink(
                        resolvedInfo = resolvedInfo,
                        customTitle = title,
                        customArtist = artist,
                        attachAsCanvas = attachAsCanvas,
                        trimSilence = trimSilence
                    ) { track ->
                        viewModel.playTrack(track)
                        viewModel.setNowPlayingExpanded(true)
                        viewModel.clearPendingIncomingWebLink()
                    }
                }
            )
        }

        // Tarjeta flotante de telemetría en tiempo real si la descarga ocurre en segundo plano
        if (downloadProgress.isDownloading && pendingIncomingWebLink == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 84.dp),
                contentAlignment = androidx.compose.ui.Alignment.BottomCenter
            ) {
                androidx.compose.material3.Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, CardBorder),
                    elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                progress = { if (downloadProgress.totalBytes > 0) downloadProgress.progressFraction else 0f },
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = CardBorder,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = downloadProgress.phase.ifBlank { "Descargando..." },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = TextPrimary
                                ),
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = downloadProgress.formattedSpeed,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (downloadProgress.totalBytes > 0) {
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { downloadProgress.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = CardBorder
                            )
                        } else {
                            androidx.compose.material3.LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = CardBorder
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = downloadProgress.formattedProgress,
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                        )
                    }
                }
            }
        }
    }
}
