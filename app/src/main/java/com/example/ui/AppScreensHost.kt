package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.Track
import com.example.feature.home.HomeScreen
import com.example.feature.importmusic.ImportMusicScreen
import com.example.feature.library.LibraryScreen
import com.example.feature.onboarding.OnboardingScreen
import com.example.feature.playlist.PlaylistDetailScreen
import com.example.feature.settings.SettingsScreen
import com.example.ui.navigation.NavScreen
import com.example.viewmodel.MusicViewModel

/**
 * Contenido de pantallas con transiciones animadas.
 * Rol arquitectónico: recolecta el estado de biblioteca/importación/ajustes y
 * cablea las 7 pantallas contra el ViewModel.
 */
@Composable
fun AppScreensHost(
    viewModel: MusicViewModel,
    currentScreen: NavScreen,
    effectiveTrack: Track?,
    animatedMiniPrimary: Color,
    animatedMiniSecondary: Color
) {
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val recentlyAddedTracks by viewModel.recentlyAddedTracks.collectAsStateWithLifecycle()
    val topPlayedTracks by viewModel.topPlayedTracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
    val selectedPlaylistTracks by viewModel.selectedPlaylistTracks.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedLibraryTab by viewModel.selectedLibraryTab.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importStatusMessage by viewModel.importStatusMessage.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadingStreamingIds by viewModel.downloadingStreamingIds.collectAsStateWithLifecycle()
    val downloadedStreamingIds by viewModel.downloadedStreamingIds.collectAsStateWithLifecycle()
    val isCreatingMixtape by viewModel.isCreatingMixtape.collectAsStateWithLifecycle()
    val mixtapeProgress by viewModel.mixtapeProgress.collectAsStateWithLifecycle()
    val mixtapeStatusMessage by viewModel.mixtapeStatusMessage.collectAsStateWithLifecycle()
    val isDynamicArtworkColorEnabled by viewModel.isDynamicArtworkColorEnabled.collectAsStateWithLifecycle()
    val isMiniPlayerVideoEnabled by viewModel.isMiniPlayerVideoEnabled.collectAsStateWithLifecycle()
    val isBackgroundGameModeEnabled by viewModel.isBackgroundGameModeEnabled.collectAsStateWithLifecycle()
    val appWallpaperConfig by viewModel.appWallpaperConfig.collectAsStateWithLifecycle()
    val nowPlayingDesignMode by viewModel.nowPlayingDesignMode.collectAsStateWithLifecycle()
    val headphoneConfig by viewModel.headphoneConfig.collectAsStateWithLifecycle()

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

                                is NavScreen.Streaming -> {
                                    val streamingQuery by viewModel.streamingSearchQuery.collectAsStateWithLifecycle()
                                    val streamingFilter by viewModel.streamingSelectedFilter.collectAsStateWithLifecycle()
                                    val streamingResults by viewModel.streamingSearchResults.collectAsStateWithLifecycle()
                                    val isStreamingSearching by viewModel.isStreamingSearching.collectAsStateWithLifecycle()
                                    val isResolvingStream by viewModel.isResolvingStream.collectAsStateWithLifecycle()
                                    val resolvingVideoId by viewModel.resolvingVideoId.collectAsStateWithLifecycle()
                                    val nextSimilarPreview by viewModel.nextSimilarTrackPreview.collectAsStateWithLifecycle()
                                    val streamingCacheCfg by viewModel.streamingCacheConfig.collectAsStateWithLifecycle()

                                    com.example.feature.streaming.StreamingExploreScreen(
                                        searchQuery = streamingQuery,
                                        selectedFilter = streamingFilter,
                                        searchResults = streamingResults,
                                        isSearching = isStreamingSearching,
                                        isResolvingStream = isResolvingStream,
                                        resolvingVideoId = resolvingVideoId,
                                        currentTrack = effectiveTrack,
                                        nextSimilarTrackPreview = nextSimilarPreview,
                                        cacheConfig = streamingCacheCfg,
                                        downloadingVideoIds = downloadingStreamingIds,
                                        downloadedVideoIds = downloadedStreamingIds,
                                        localLibraryTracks = allTracks,
                                        onSearchQueryChange = { viewModel.updateStreamingSearchQuery(it) },
                                        onSearchSubmit = { viewModel.performStreamingSearch(it) },
                                        onFilterChange = { viewModel.setStreamingSearchFilter(it) },
                                        onPlayStreamingItem = { viewModel.playStreamingItem(it) },
                                        onDownloadStreamingItem = { viewModel.downloadStreamingItemPermanently(it) },
                                        onOpenNowPlaying = { viewModel.setNowPlayingExpanded(true) }
                                    )
                                }

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
                                    },
                                    onCreateMixtape = { tracksToMix, title, crossfadeSec, onDone ->
                                        viewModel.createMixtape(tracksToMix, title, crossfadeSec, onDone)
                                    },
                                    onCreateAutoRemix = { trackA, trackB, title, style, crossfadeSec, onDone ->
                                        viewModel.createAutoRemix(trackA, trackB, title, style, crossfadeSec, onDone)
                                    },
                                    isCreatingMixtape = isCreatingMixtape,
                                    mixtapeProgress = mixtapeProgress,
                                    mixtapeStatusMessage = mixtapeStatusMessage,
                                    onOpenPlaylistImportDialog = { viewModel.openPlaylistImportDialog() }
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
                                    },
                                    onOpenPlaylistImportDialog = { viewModel.openPlaylistImportDialog() }
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
                                    onCreateAutoRemix = { trackA, trackB, title, style, crossfadeSec, onDone ->
                                        viewModel.createAutoRemix(trackA, trackB, title, style, crossfadeSec, onDone)
                                    },
                                    isCreatingMixtape = isCreatingMixtape,
                                    mixtapeProgress = mixtapeProgress,
                                    mixtapeStatusMessage = mixtapeStatusMessage
                                )

                                is NavScreen.Settings -> {
                                    val widgetConfig by viewModel.widgetConfig.collectAsStateWithLifecycle()
                                    SettingsScreen(
                                        currentTheme = viewModel.currentTheme.collectAsStateWithLifecycle().value,
                                        onSelectTheme = { viewModel.setTheme(it) },
                                        onOpenOnboarding = { viewModel.reopenOnboarding() },
                                        isDynamicArtworkColorEnabled = isDynamicArtworkColorEnabled,
                                        onToggleDynamicArtworkColor = { viewModel.toggleDynamicArtworkColor(it) },
                                        isMiniPlayerVideoEnabled = isMiniPlayerVideoEnabled,
                                        onToggleMiniPlayerVideo = { viewModel.toggleMiniPlayerVideoEnabled() },
                                        isBackgroundGameModeEnabled = isBackgroundGameModeEnabled,
                                        onToggleBackgroundGameMode = { viewModel.setBackgroundGameModeEnabled(it) },
                                        appWallpaperConfig = appWallpaperConfig,
                                        onToggleWallpaperEnabled = { viewModel.setAppWallpaperEnabled(it) },
                                        onSelectWallpaperImage = { viewModel.setCustomWallpaperImage(it) },
                                        onSelectWallpaperVideo = { viewModel.setCustomWallpaperVideo(it) },
                                        onClearWallpaper = { viewModel.clearCustomWallpaper() },
                                        onSelectWallpaperScope = { viewModel.setAppWallpaperScope(it) },
                                        onChangeWallpaperDimAlpha = { viewModel.setAppWallpaperDimAlpha(it) },
                                        onChangeWallpaperBlurDp = { viewModel.setAppWallpaperBlurDp(it) },
                                        nowPlayingDesignMode = nowPlayingDesignMode,
                                        onSetNowPlayingDesignMode = { viewModel.setNowPlayingDesignMode(it) },
                                        widgetConfig = widgetConfig,
                                        currentTrack = effectiveTrack,
                                        topPlayedTracks = topPlayedTracks,
                                        playlists = playlists,
                                        dynamicPrimaryColor = animatedMiniPrimary,
                                        dynamicSecondaryColor = animatedMiniSecondary,
                                        onSetWidgetDynamicColorEnabled = { viewModel.setWidgetDynamicColorEnabled(it) },
                                        onSetWidgetGridContentMode = { viewModel.setWidgetGridContentMode(it) },
                                        onSetWidgetShowProgress = { viewModel.setWidgetShowProgress(it) },
                                        onSetWidgetColorIntensityPercent = { viewModel.setWidgetColorIntensityPercent(it) },
                                        onForceSyncWidgets = { viewModel.forceSyncAllWidgets() },
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
                                        onDeleteTrackVideo = { viewModel.deleteTrackVideo(it) },
                                        streamingCacheConfig = viewModel.streamingCacheConfig.collectAsStateWithLifecycle().value,
                                        onSelectStreamingVideoPolicy = { viewModel.setStreamingVideoNetworkPolicy(it) },
                                        onChangeStreamingCacheMaxMb = { viewModel.setStreamingCacheMaxSizeMb(it) },
                                        onClearStreamingCache = { viewModel.clearStreamingCache() }
                                    )
                                }
                            }
                        }
}
