package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.Track
import com.example.feature.nowplaying.NowPlayingScreen
import com.example.viewmodel.MusicViewModel

/**
 * Overlay de Now Playing con deslizamiento suave sin capas negras residuales.
 * Rol arquitectónico: recolecta el estado de reproducción/efectos/letras y cablea
 * la pantalla completa contra el ViewModel.
 */
@Composable
fun AppNowPlayingHost(
    viewModel: MusicViewModel,
    effectiveTrack: Track?,
    isNowPlayingExpanded: Boolean,
    onOpenGlobalEffects: (Int) -> Unit
) {
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val downloadingStreamingIds by viewModel.downloadingStreamingIds.collectAsStateWithLifecycle()
    val downloadedStreamingIds by viewModel.downloadedStreamingIds.collectAsStateWithLifecycle()
    val isAudioBuffering by viewModel.isAudioBuffering.collectAsStateWithLifecycle()
    val isResolvingStreamGlobal by viewModel.isResolvingStream.collectAsStateWithLifecycle()
    val isNetworkStalled by viewModel.isNetworkStalled.collectAsStateWithLifecycle()
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val spatial8DConfig by viewModel.spatial8DConfig.collectAsStateWithLifecycle()
    val vocalClarityConfig by viewModel.vocalClarityConfig.collectAsStateWithLifecycle()
    val reverbConfig by viewModel.reverbConfig.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val playbackPitch by viewModel.playbackPitch.collectAsStateWithLifecycle()
    val crossfadeSeconds by viewModel.crossfadeSeconds.collectAsStateWithLifecycle()
    val isGaplessEnabled by viewModel.isGaplessEnabled.collectAsStateWithLifecycle()
    val isDjAutomixEnabled by viewModel.isDjAutomixEnabled.collectAsStateWithLifecycle()
    val isDjEqCurveEnabled by viewModel.isDjEqCurveEnabled.collectAsStateWithLifecycle()
    val volumeNormalizationConfig by viewModel.volumeNormalizationConfig.collectAsStateWithLifecycle()
    val abLoopState by viewModel.abLoopState.collectAsStateWithLifecycle()
    val eqBands by viewModel.eqBands.collectAsStateWithLifecycle()
    val bassBoostLevel by viewModel.bassBoostLevel.collectAsStateWithLifecycle()
    val currentPreset by viewModel.currentPreset.collectAsStateWithLifecycle()
    val isEqEnabled by viewModel.isEqEnabled.collectAsStateWithLifecycle()
    val eqScopeMode by viewModel.eqScopeMode.collectAsStateWithLifecycle()
    val headphoneConfig by viewModel.headphoneConfig.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
    val isSearchLyricsDialogOpen by viewModel.isSearchLyricsDialogOpen.collectAsStateWithLifecycle()
    val isSearchingLyrics by viewModel.isSearchingLyrics.collectAsStateWithLifecycle()
    val lyricsSearchResults by viewModel.lyricsSearchResults.collectAsStateWithLifecycle()
    val searchLyricsError by viewModel.searchLyricsError.collectAsStateWithLifecycle()
    val videoDisplayMode by viewModel.videoDisplayMode.collectAsStateWithLifecycle()
    val isVideoCanvasActive by viewModel.isVideoCanvasActive.collectAsStateWithLifecycle()
    val nowPlayingDesignMode by viewModel.nowPlayingDesignMode.collectAsStateWithLifecycle()
    val isDynamicArtworkColorEnabled by viewModel.isDynamicArtworkColorEnabled.collectAsStateWithLifecycle()
    val activeMixtapeChapter by viewModel.activeMixtapeChapter.collectAsStateWithLifecycle()
    val activeMixtapeMetadata by viewModel.activeMixtapeMetadata.collectAsStateWithLifecycle()

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
                    currentPositionFlow = viewModel.currentPosition,
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
                    onOpenEqualizer = { onOpenGlobalEffects(0) },
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
                    onDownloadStreamingTrack = { track -> viewModel.downloadCurrentStreamingTrackPermanently(track) },
                    isStreamingTrackDownloading = effectiveTrack?.streamingVideoId?.let { vid -> downloadingStreamingIds.contains(vid) } == true,
                    isStreamingTrackDownloaded = effectiveTrack?.let { trk ->
                        val vid = trk.streamingVideoId
                        (!vid.isNullOrBlank() && downloadedStreamingIds.contains(vid)) ||
                            allTracks.any { local ->
                                (!vid.isNullOrBlank() && local.streamingVideoId == vid) ||
                                    (local.title.equals(trk.title, ignoreCase = true) &&
                                        local.artist.equals(trk.artist, ignoreCase = true))
                            }
                    } == true,
                    isLoadingOrBuffering = effectiveTrack?.isStreamingTrack == true && (isAudioBuffering || isResolvingStreamGlobal),
                    isWaitingForNetwork = effectiveTrack?.isStreamingTrack == true && isNetworkStalled,
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
                    eqScopeMode = eqScopeMode,
                    onSetEqScopeMode = { viewModel.setEqScopeMode(it) },
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
                    visualizerBandsFlow = viewModel.visualizerBands,
                    audioIntensityFlow = viewModel.audioIntensity,
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
}
