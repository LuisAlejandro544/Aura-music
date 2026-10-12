package com.example.feature.nowplaying.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.core.model.LyricsState
import com.example.core.model.RepeatMode
import com.example.core.model.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * Rama cinemática de Now Playing (estilo Spotify con Canvas fullscreen).
 * Rol arquitectónico: envuelve NowPlayingCinematicLayout con callbacks ya cableados.
 */
@Composable
fun NowPlayingCinematicHost(
    currentTrack: Track,
    isPlaying: Boolean,
    currentPositionMs: Long,
    currentPositionFlow: StateFlow<Long>?,
    durationMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    lyricsState: LyricsState,
    animatedPrimary: Color,
    animatedSecondary: Color,
    audioIntensity: Float,
    audioIntensityFlow: StateFlow<Float>?,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onCollapse: () -> Unit,
    onOpenEffects: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenDesignSelector: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenFullScreenLyrics: () -> Unit,
    onOpenDetails: () -> Unit,
    collectionContextTitle: String,
    onDownloadStreamingTrack: ((Track) -> Unit)?,
    isStreamingTrackDownloading: Boolean,
    isStreamingTrackDownloaded: Boolean,
    isLoadingOrBuffering: Boolean,
    isVideoLoading: Boolean,
    isWaitingForNetwork: Boolean
) {
    NowPlayingCinematicLayout(
        currentTrack = currentTrack,
        isPlaying = isPlaying,
        currentPositionMs = currentPositionMs,
        currentPositionFlow = currentPositionFlow,
        durationMs = durationMs,
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode,
        lyricsState = lyricsState,
        animatedPrimary = animatedPrimary,
        animatedSecondary = animatedSecondary,
        audioIntensity = audioIntensity,
        audioIntensityFlow = audioIntensityFlow,
        onTogglePlayPause = onTogglePlayPause,
        onSeekTo = onSeekTo,
        onPlayNext = onPlayNext,
        onPlayPrevious = onPlayPrevious,
        onToggleShuffle = onToggleShuffle,
        onCycleRepeat = onCycleRepeat,
        onToggleFavorite = onToggleFavorite,
        onCollapse = onCollapse,
        onOpenEffects = onOpenEffects,
        onOpenQueue = onOpenQueue,
        onOpenDesignSelector = onOpenDesignSelector,
        onOpenLyrics = onOpenLyrics,
        onOpenFullScreenLyrics = onOpenFullScreenLyrics,
        onOpenDetails = onOpenDetails,
        collectionContextTitle = collectionContextTitle,
        onDownloadStreamingTrack = if (currentTrack.isStreamingTrack && onDownloadStreamingTrack != null) {
            { onDownloadStreamingTrack(currentTrack) }
        } else null,
        isStreamingTrackDownloading = isStreamingTrackDownloading,
        isStreamingTrackDownloaded = isStreamingTrackDownloaded,
        isLoadingOrBuffering = isLoadingOrBuffering,
        isVideoLoading = isVideoLoading,
        isWaitingForNetwork = isWaitingForNetwork
    )
}
