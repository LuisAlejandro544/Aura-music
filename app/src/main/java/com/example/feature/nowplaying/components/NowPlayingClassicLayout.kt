package com.example.feature.nowplaying.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.core.model.ABLoopState
import com.example.core.model.HeadphoneConfig
import com.example.core.model.LyricsState
import com.example.core.model.MixtapeChapter
import com.example.core.model.RepeatMode
import com.example.core.model.SleepTimerState
import com.example.core.model.Spatial8DConfig
import com.example.core.model.Track
import com.example.core.model.VideoDisplayMode
import com.example.feature.nowplaying.NowPlayingShortcutRow
import com.example.ui.components.AudioVisualizer
import kotlinx.coroutines.flow.StateFlow

/**
 * Columna del diseño clásico de Now Playing (TopBar, carátula/letras, visualizador,
 * controles, balance y atajos).
 * Rol arquitectónico: rama clásica del orquestador NowPlayingScreen.
 */
@Composable
fun NowPlayingClassicLayout(
    currentTrack: Track,
    isPlaying: Boolean,
    currentPositionMs: Long,
    currentPositionFlow: StateFlow<Long>?,
    durationMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    animatedPrimary: Color,
    animatedSecondary: Color,
    sleepTimerState: SleepTimerState,
    spatial8DConfig: Spatial8DConfig,
    videoDisplayMode: VideoDisplayMode,
    showLyrics: Boolean,
    abLoopState: ABLoopState,
    vocalClarityEnabled: Boolean,
    lyricsState: LyricsState,
    visualizerBands: FloatArray?,
    visualizerBandsFlow: StateFlow<FloatArray>?,
    audioIntensity: Float,
    playbackSpeed: Float,
    headphoneConfig: HeadphoneConfig,
    queueSize: Int,
    isContentLoading: Boolean,
    effectiveWaitingNetwork: Boolean,
    activeMixtapeChapter: com.example.core.model.MixtapeChapter?,
    mixtapeChapterIndex: Int,
    mixtapeTotalChapters: Int,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onCycleVideoDisplayMode: () -> Unit,
    onSetStereoBalance: (Float) -> Unit,
    onFetchOnlineLyrics: () -> Unit,
    onSaveCustomLyrics: (String) -> Unit,
    onImportLyricsUri: (android.net.Uri) -> Unit,
    onOpenSearchLyrics: () -> Unit,
    onMarkABPointA: () -> Unit,
    onMarkABPointB: () -> Unit,
    onClearABLoop: () -> Unit,
    onDownloadStreamingTrack: ((Track) -> Unit)?,
    isStreamingTrackDownloading: Boolean,
    isStreamingTrackDownloaded: Boolean,
    onOpenEffectsTab: (Int) -> Unit,
    onOpenDetails: () -> Unit,
    onOpenVideoMode: () -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenDesignSelector: () -> Unit,
    onOpenFullScreenLyrics: () -> Unit,
    onCloseLyrics: () -> Unit,
    onOpenQueue: () -> Unit
) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Barra Superior
                    NowPlayingTopBar(
                        currentTrack = currentTrack,
                        sleepTimerState = sleepTimerState,
                        spatial8DConfig = spatial8DConfig,
                        videoDisplayMode = videoDisplayMode,
                        animatedPrimary = animatedPrimary,
                        onCollapse = onCollapse,
                        onOpenEffects = { onOpenEffectsTab(1) },
                        onOpenDetails = { onOpenDetails() },
                        onOpenVideoMode = { onOpenVideoMode() },
                        isLyricsActive = showLyrics,
                        onToggleLyrics = { onToggleLyrics() },
                        isABLoopActive = abLoopState.isLoopingActive,
                        isVocalClarityActive = vocalClarityEnabled,
                        onOpenDesignSelector = { onOpenDesignSelector() },
                        onDownloadStreamingTrack = if (currentTrack.isStreamingTrack && onDownloadStreamingTrack != null) {
                            { onDownloadStreamingTrack(currentTrack) }
                        } else null,
                        isStreamingTrackDownloading = isStreamingTrackDownloading,
                        isStreamingTrackDownloaded = isStreamingTrackDownloaded
                    )

                Spacer(modifier = Modifier.weight(0.5f))

                // Carátula / Video Canvas O Letras Sincronizadas (.LRC) Karaoke
                if (showLyrics) {
                    NowPlayingLyricsCard(
                        currentTrack = currentTrack,
                        lyricsState = lyricsState,
                        currentPositionMs = currentPositionMs,
                        currentPositionFlow = currentPositionFlow,
                        animatedPrimary = animatedPrimary,
                        onSeekTo = onSeekTo,
                        onFetchOnlineLyrics = onFetchOnlineLyrics,
                        onSaveCustomLyrics = onSaveCustomLyrics,
                        onImportLyricsUri = onImportLyricsUri,
                        onOpenSearchLyrics = onOpenSearchLyrics,
                        onOpenFullScreen = { onOpenFullScreenLyrics() },
                        onCloseLyrics = { onCloseLyrics() }
                    )
                } else {
                    NowPlayingArtworkCard(
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        currentPositionMs = currentPositionMs,
                        currentPositionFlow = currentPositionFlow,
                        videoDisplayMode = videoDisplayMode,
                        animatedPrimary = animatedPrimary,
                        animatedSecondary = animatedSecondary,
                        playbackSpeed = playbackSpeed,
                        onCycleVideoDisplayMode = onCycleVideoDisplayMode,
                        audioIntensity = audioIntensity,
                        onOpenVideoMode = { onOpenVideoMode() },
                        isLoadingOrBuffering = isContentLoading,
                        isWaitingForNetwork = effectiveWaitingNetwork
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Visualizador dinámico de audio en tiempo real aislado (recolecta bandsFlow localmente)
                AudioVisualizer(
                    isPlaying = isPlaying,
                    realBands = visualizerBands,
                    bandsFlow = visualizerBandsFlow,
                    modifier = Modifier.fillMaxWidth(0.9f),
                    barCount = 28,
                    barHeight = 40.dp,
                    customPrimaryColor = animatedPrimary,
                    customSecondaryColor = animatedSecondary
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Controles de Reproducción y Seekbar
                NowPlayingPlaybackControls(
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    currentPositionFlow = currentPositionFlow,
                    durationMs = durationMs,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    animatedPrimary = animatedPrimary,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekTo = onSeekTo,
                    onPlayNext = onPlayNext,
                    onPlayPrevious = onPlayPrevious,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onToggleFavorite = onToggleFavorite,
                    abLoopState = abLoopState,
                    onMarkABPointA = onMarkABPointA,
                    onMarkABPointB = onMarkABPointB,
                    onClearABLoop = onClearABLoop,
                    activeMixtapeChapter = activeMixtapeChapter,
                    mixtapeChapterIndex = mixtapeChapterIndex,
                    mixtapeTotalChapters = mixtapeTotalChapters,
                    isLoadingOrBuffering = isContentLoading,
                    isWaitingForNetwork = effectiveWaitingNetwork
                )

                // Barra de Balance Estéreo Fino L/R en Tiempo Real
                NowPlayingBalanceBar(
                    headphoneConfig = headphoneConfig,
                    onSetStereoBalance = onSetStereoBalance,
                    modifier = Modifier.padding(top = 10.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                NowPlayingShortcutRow(
                    animatedPrimary = animatedPrimary,
                    animatedSecondary = animatedSecondary,
                    showLyrics = showLyrics,
                    queueSize = queueSize,
                    onOpenEq = { onOpenEffectsTab(it) },
                    onToggleLyrics = { onToggleLyrics() },
                    onOpenQueue = { onOpenQueue() }
                )

                Spacer(modifier = Modifier.weight(0.5f))
            }
}
