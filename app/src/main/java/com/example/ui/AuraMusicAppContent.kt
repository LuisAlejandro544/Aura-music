package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.VideoDisplayMode
import com.example.ui.components.BottomNavBar
import com.example.ui.components.MiniPlayer
import com.example.ui.components.PackageUpdateBanner
import com.example.ui.navigation.NavScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.importmusic.ImportMusicScreen
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.nowplaying.NowPlayingScreen
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.screens.playlist.PlaylistDetailScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.theme.ArtworkColorExtractor
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.ExtractedArtworkColors
import com.example.viewmodel.MusicViewModel

/**
 * Contenido principal y host de navegación de Aura Music.
 * Desacopla de MainActivity la estructura visual de Scaffold, transiciones de pantalla,
 * superposición fluida de NowPlayingScreen y mini reproductor flotante.
 */
@Composable
fun AuraMusicAppContent(viewModel: MusicViewModel) {
    val context = LocalContext.current

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

    // Estados de Room
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
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importStatusMessage by viewModel.importStatusMessage.collectAsStateWithLifecycle()

    val eqBands by viewModel.eqBands.collectAsStateWithLifecycle()
    val bassBoostLevel by viewModel.bassBoostLevel.collectAsStateWithLifecycle()
    val currentPreset by viewModel.currentPreset.collectAsStateWithLifecycle()
    val isEqEnabled by viewModel.isEqEnabled.collectAsStateWithLifecycle()

    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val spatial8DConfig by viewModel.spatial8DConfig.collectAsStateWithLifecycle()
    val vocalClarityConfig by viewModel.vocalClarityConfig.collectAsStateWithLifecycle()
    val packageUpdateState by viewModel.packageUpdateState.collectAsStateWithLifecycle()
    val reverbConfig by viewModel.reverbConfig.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val playbackPitch by viewModel.playbackPitch.collectAsStateWithLifecycle()
    val crossfadeSeconds by viewModel.crossfadeSeconds.collectAsStateWithLifecycle()
    val isGaplessEnabled by viewModel.isGaplessEnabled.collectAsStateWithLifecycle()
    val isDjAutomixEnabled by viewModel.isDjAutomixEnabled.collectAsStateWithLifecycle()
    val isDjEqCurveEnabled by viewModel.isDjEqCurveEnabled.collectAsStateWithLifecycle()
    val volumeNormalizationConfig by viewModel.volumeNormalizationConfig.collectAsStateWithLifecycle()
    val abLoopState by viewModel.abLoopState.collectAsStateWithLifecycle()
    val videoDisplayMode by viewModel.videoDisplayMode.collectAsStateWithLifecycle()
    val isVideoCanvasActive by viewModel.isVideoCanvasActive.collectAsStateWithLifecycle()
    val nowPlayingDesignMode by viewModel.nowPlayingDesignMode.collectAsStateWithLifecycle()
    val isDynamicArtworkColorEnabled by viewModel.isDynamicArtworkColorEnabled.collectAsStateWithLifecycle()
    val isMiniPlayerVideoEnabled by viewModel.isMiniPlayerVideoEnabled.collectAsStateWithLifecycle()
    val headphoneConfig by viewModel.headphoneConfig.collectAsStateWithLifecycle()
    val visualizerBands by viewModel.visualizerBands.collectAsStateWithLifecycle()
    val audioIntensity by viewModel.audioIntensity.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
    val isSearchLyricsDialogOpen by viewModel.isSearchLyricsDialogOpen.collectAsStateWithLifecycle()
    val isSearchingLyrics by viewModel.isSearchingLyrics.collectAsStateWithLifecycle()
    val lyricsSearchResults by viewModel.lyricsSearchResults.collectAsStateWithLifecycle()
    val searchLyricsError by viewModel.searchLyricsError.collectAsStateWithLifecycle()
    val pendingIncomingAudioUris by viewModel.pendingIncomingAudioUris.collectAsStateWithLifecycle()
    val pendingIncomingVideoUri by viewModel.pendingIncomingVideoUri.collectAsStateWithLifecycle()
    val pendingIncomingWebLink by viewModel.pendingIncomingWebLink.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()

    // Estados de Mixtape Continuo con Capítulos Reactivos
    val isCreatingMixtape by viewModel.isCreatingMixtape.collectAsStateWithLifecycle()
    val mixtapeProgress by viewModel.mixtapeProgress.collectAsStateWithLifecycle()
    val mixtapeStatusMessage by viewModel.mixtapeStatusMessage.collectAsStateWithLifecycle()
    val activeMixtapeChapter by viewModel.activeMixtapeChapter.collectAsStateWithLifecycle()
    val activeMixtapeMetadata by viewModel.activeMixtapeMetadata.collectAsStateWithLifecycle()

    // Si la pista actual es un Mixtape continuo, virtualizamos la pista activa con el capítulo en curso
    val effectiveTrack = remember(currentTrack, activeMixtapeChapter) {
        val track = currentTrack
        val chapter = activeMixtapeChapter
        if (track != null && chapter != null) {
            chapter.toVirtualTrack(track)
        } else {
            track
        }
    }

    var showGlobalAudioEffectsSheet by remember { mutableStateOf(false) }
    var initialAudioEffectsTab by remember { mutableIntStateOf(0) }

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

    LaunchedEffect(effectiveTrack?.id, effectiveTrack?.albumArtPath, effectiveTrack?.videoUri, isDynamicArtworkColorEnabled) {
        if (effectiveTrack != null && isDynamicArtworkColorEnabled) {
            val isVideo = (videoDisplayMode != VideoDisplayMode.OFF) && !effectiveTrack.videoUri.isNullOrEmpty()
            miniPlayerColors = ArtworkColorExtractor.extractPlaybackColors(
                context = context,
                track = effectiveTrack,
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

    val canGoBack = isNowPlayingExpanded || (currentScreen !is NavScreen.Home && currentScreen !is NavScreen.Onboarding)
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
                if (currentScreen !is NavScreen.Onboarding) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BackgroundDark)
                    ) {
                        AnimatedVisibility(
                            visible = effectiveTrack != null && !isNowPlayingExpanded,
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
                                currentTrack = effectiveTrack,
                                isPlaying = isPlaying,
                                currentPositionMs = currentPosition,
                                durationMs = duration,
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onSkipNext = { viewModel.playNext() },
                                onSkipPrevious = { viewModel.playPrevious() },
                                onClick = { viewModel.setNowPlayingExpanded(true) },
                                dynamicPrimary = animatedMiniPrimary,
                                dynamicSecondary = animatedMiniSecondary,
                                isMiniPlayerVideoEnabled = isMiniPlayerVideoEnabled,
                                playbackSpeed = playbackSpeed
                            )
                        }

                        BottomNavBar(
                            currentScreen = currentScreen,
                            onNavigate = { viewModel.navigateTo(it) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (currentScreen !is NavScreen.Onboarding) {
                    PackageUpdateBanner(
                        state = packageUpdateState,
                        onApplyAndRestart = { viewModel.applyPendingPackageUpdateAndRestart() }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
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
                            is NavScreen.Onboarding -> OnboardingScreen(
                                onFinishOnboarding = { viewModel.completeOnboarding() }
                            )

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
                                onDeleteTrack = { trackId -> allTracks.find { it.id == trackId }?.let { viewModel.deleteTrack(it) } },
                                onAddToPlaylist = { playlistId, trackId -> viewModel.addTrackToPlaylist(playlistId, trackId) },
                                onNavigate = { viewModel.navigateTo(it) },
                                onSelectLibraryTab = { viewModel.setLibraryTab(it) },
                                onOpenPlaylist = { viewModel.openPlaylist(it) },
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
                                onDeleteTrack = { trackId -> allTracks.find { it.id == trackId }?.let { viewModel.deleteTrack(it) } },
                                onOpenPlaylist = { viewModel.openPlaylist(it) },
                                onOpenAlbum = { viewModel.openAlbum(it) },
                                onOpenArtist = { viewModel.openArtist(it) },
                                onCreatePlaylist = { name, desc, artUri -> viewModel.createPlaylist(name, desc, artUri) },
                                onRenamePlaylist = { pId, name, desc, artUri, removeArt ->
                                    viewModel.updatePlaylist(pId, name, desc, artUri, removeArt)
                                },
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
                                onImportVideoAsMusicWithLoopStyle = { videoUri, title, artist, album, attachCanvas, forceLoop, trimSilence, loopStyle ->
                                    viewModel.importVideoAsTrack(videoUri, title, artist, album, attachCanvas, forceLoop, trimSilence, loopStyle) { createdTrack ->
                                        viewModel.playTrack(createdTrack)
                                        viewModel.setNowPlayingExpanded(true)
                                    }
                                },
                                onDownloadFromLink = { info, title, artist, canvas, trimSilence ->
                                    viewModel.importFromWebVideoLink(info, title, artist, canvas, trimSilence) { createdTrack ->
                                        viewModel.playTrack(createdTrack)
                                        viewModel.setNowPlayingExpanded(true)
                                    }
                                },
                                onDownloadFromLinkWithLoopStyle = { info, title, artist, canvas, trimSilence, loopStyle ->
                                    viewModel.importFromWebVideoLink(info, title, artist, canvas, trimSilence, loopStyle) { createdTrack ->
                                        viewModel.playTrack(createdTrack)
                                        viewModel.setNowPlayingExpanded(true)
                                    }
                                }
                            )

                            is NavScreen.PlaylistDetail -> PlaylistDetailScreen(
                                playlist = selectedPlaylist,
                                tracks = selectedPlaylistTracks,
                                allTracks = allTracks,
                                allPlaylists = playlists,
                                currentTrack = effectiveTrack,
                                isPlaying = isPlaying,
                                onBack = { viewModel.handleBackPress() },
                                onTrackClick = { track, list -> viewModel.playTrack(track, list) },
                                onFavoriteToggle = { viewModel.toggleFavorite(it) },
                                onRemoveFromPlaylist = { pId, tId -> viewModel.removeTrackFromPlaylist(pId, tId) },
                                onAddToPlaylist = { pId, tId -> viewModel.addTrackToPlaylist(pId, tId) },
                                onRenamePlaylist = { pId, name, desc, artUri, removeArt ->
                                    viewModel.updatePlaylist(pId, name, desc, artUri, removeArt)
                                },
                                onDeleteTrackFromLibrary = { trackId -> allTracks.find { it.id == trackId }?.let { viewModel.deleteTrack(it) } },
                                onEditTrack = { id, t, a, al -> viewModel.updateTrackInfo(id, t, a, al) },
                                onEditTrackDetails = { id, t, a, al, art, removeArt ->
                                    viewModel.updateTrackDetails(id, t, a, al, art, removeArt)
                                },
                                onCreateMixtape = { tracksToMix, title, crossfadeSec, onDone ->
                                    viewModel.createMixtape(tracksToMix, title, crossfadeSec, onDone)
                                },
                                isCreatingMixtape = isCreatingMixtape,
                                mixtapeProgress = mixtapeProgress,
                                mixtapeStatusMessage = mixtapeStatusMessage
                            )

                            is NavScreen.Settings -> SettingsScreen(
                                currentTheme = viewModel.currentTheme.collectAsStateWithLifecycle().value,
                                onSelectTheme = { viewModel.setTheme(it) },
                                onOpenOnboarding = { viewModel.reopenOnboarding() },
                                isDynamicArtworkColorEnabled = isDynamicArtworkColorEnabled,
                                onToggleDynamicArtworkColor = { viewModel.toggleDynamicArtworkColor(it) },
                                isMiniPlayerVideoEnabled = isMiniPlayerVideoEnabled,
                                onToggleMiniPlayerVideo = { viewModel.toggleMiniPlayerVideoEnabled() },
                                nowPlayingDesignMode = nowPlayingDesignMode,
                                onSetNowPlayingDesignMode = { viewModel.setNowPlayingDesignMode(it) },
                                headphoneConfig = headphoneConfig,
                                onUpdateHeadphoneConfig = { viewModel.updateHeadphoneConfig(it) },
                                onSetCrossfeedEnabled = { viewModel.setCrossfeedEnabled(it) },
                                onSetCrossfeedStrength = { viewModel.setCrossfeedStrength(it) },
                                onSetBalanceControlEnabled = { viewModel.setBalanceControlEnabled(it) },
                                onSetStereoBalance = { viewModel.setStereoBalance(it) },
                                onSetBitPerfectEnabled = { viewModel.setBitPerfectEnabled(it) },
                                onSetLowLatencyEnabled = { viewModel.setLowLatencyAAudioEnabled(it) },
                                onSetUsbDacExclusiveEnabled = { viewModel.setUsbDacExclusiveEnabled(it) },
                                onSetHiResTargetMode = { viewModel.setHiResTargetMode(it) },
                                onSetBecomingNoisyGuardEnabled = { viewModel.setBecomingNoisyGuardEnabled(it) },
                                onSetFadeInOnResumeEnabled = { viewModel.setFadeInOnResumeEnabled(it) },
                                onSetDedicatedVolumeMemoryEnabled = { viewModel.setDedicatedVolumeMemoryEnabled(it) },
                                onSetHeadsetControlsEnabled = { viewModel.setHeadsetControlsEnabled(it) },
                                onSetHeadsetAction = { type, action -> viewModel.setHeadsetAction(type, action) },
                                allTracks = allTracks,
                                onDeleteTrackArtwork = { viewModel.deleteTrackArtwork(it) },
                                onDeleteTrackVideo = { viewModel.deleteTrackVideo(it) }
                            )
                        }
                    }
                }
            }
        }

        // Pantalla Now Playing con deslizamiento suave sin capas negras residuales
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
                currentTrack = effectiveTrack,
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
                onEditTrackDetailsWithLoopStyle = { id, t, a, al, art, removeArt, video, removeVideo, forceLoop, loopStyle ->
                    viewModel.updateTrackDetails(id, t, a, al, art, removeArt, video, removeVideo, forceLoop, loopStyle)
                },
                isVideoCanvasActive = isVideoCanvasActive,
                videoDisplayMode = videoDisplayMode,
                nowPlayingDesignMode = nowPlayingDesignMode,
                onSetNowPlayingDesignMode = { viewModel.setNowPlayingDesignMode(it) },
                collectionContextTitle = selectedPlaylist?.name ?: currentTrack?.album ?: "Tu Biblioteca",
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
                onSet8DMode16D = { viewModel.set8DMode16D(it) },
                onSet8DOrbitSpeed = { viewModel.set8DOrbitSpeed(it) },
                onSet8DSpatialIntensity = { viewModel.set8DSpatialIntensity(it) },
                onSet8DRoomDepth = { viewModel.set8DRoomDepth(it) },
                vocalClarityConfig = vocalClarityConfig,
                onSetVocalClarityEnabled = { viewModel.setVocalClarityEnabled(it) },
                onSetVocalClarityStrength = { viewModel.setVocalClarityStrength(it) },
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
                isDjAutomixEnabled = isDjAutomixEnabled,
                onSetDjAutomixEnabled = { viewModel.setDjAutomixEnabled(it) },
                isDjEqCurveEnabled = isDjEqCurveEnabled,
                onSetDjEqCurveEnabled = { viewModel.setDjEqCurveEnabled(it) },
                volumeNormalizationConfig = volumeNormalizationConfig,
                onSetVolumeNormalizationEnabled = { viewModel.setVolumeNormalizationEnabled(it) },
                onSetVolumeNormalizationMode = { viewModel.setVolumeNormalizationMode(it) },
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
                isSearchLyricsDialogOpen = isSearchLyricsDialogOpen,
                isSearchingLyrics = isSearchingLyrics,
                lyricsSearchResults = lyricsSearchResults,
                searchLyricsError = searchLyricsError,
                onFetchOnlineLyrics = { viewModel.fetchOnlineLyrics() },
                onSaveCustomLyrics = { viewModel.saveCustomLyrics(it) },
                onImportLyricsUri = { viewModel.importLyricsFromUri(it) },
                onOpenSearchLyrics = { viewModel.openSearchLyricsDialog() },
                onCloseSearchLyrics = { viewModel.closeSearchLyricsDialog() },
                onSearchLyrics = { title, artist -> viewModel.searchLyricsOptions(title, artist) },
                onSelectLyricSearchResult = { viewModel.selectLyricSearchResult(it) },
                activeMixtapeChapter = activeMixtapeChapter,
                mixtapeChapterIndex = activeMixtapeMetadata?.chapters?.indexOfFirst { it.startMs == activeMixtapeChapter?.startMs } ?: -1,
                mixtapeTotalChapters = activeMixtapeMetadata?.chapters?.size ?: 0
            )
        }

        // Hospedador de diálogos emergentes y hojas modulares
        GlobalDialogsHost(
            viewModel = viewModel,
            showGlobalAudioEffectsSheet = showGlobalAudioEffectsSheet,
            initialAudioEffectsTab = initialAudioEffectsTab,
            onDismissAudioEffectsSheet = { showGlobalAudioEffectsSheet = false },
            pendingIncomingAudioUris = pendingIncomingAudioUris,
            pendingIncomingVideoUri = pendingIncomingVideoUri,
            pendingIncomingWebLink = pendingIncomingWebLink,
            downloadProgress = downloadProgress,
            isSearchLyricsDialogOpen = isSearchLyricsDialogOpen,
            isSearchingLyrics = isSearchingLyrics,
            lyricsSearchResults = lyricsSearchResults,
            searchLyricsError = searchLyricsError,
            eqBands = eqBands,
            bassBoostLevel = bassBoostLevel,
            currentPreset = currentPreset,
            isEqEnabled = isEqEnabled,
            spatial8DConfig = spatial8DConfig,
            vocalClarityConfig = vocalClarityConfig,
            reverbConfig = reverbConfig,
            sleepTimerState = sleepTimerState,
            playbackSpeed = playbackSpeed,
            playbackPitch = playbackPitch,
            crossfadeSeconds = crossfadeSeconds,
            isGaplessEnabled = isGaplessEnabled,
            abLoopState = abLoopState,
            headphoneConfig = headphoneConfig
        )
    }
}
