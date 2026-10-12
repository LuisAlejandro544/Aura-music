package com.example.feature.nowplaying

import android.net.Uri
import androidx.compose.runtime.Composable
import com.example.core.data.importer.FFmpegNativeEngine
import com.example.core.model.ABLoopState
import com.example.core.model.EqualizerBand
import com.example.core.model.EqualizerPreset
import com.example.core.model.EqualizerScopeMode
import com.example.core.model.HeadphoneConfig
import com.example.core.model.LyricSearchResult
import com.example.core.model.LyricsState
import com.example.core.model.NowPlayingDesignMode
import com.example.core.model.ReverbConfig
import com.example.core.model.ReverbPreset
import com.example.core.model.SleepTimerState
import com.example.core.model.Spatial8DConfig
import com.example.core.model.Track
import com.example.core.model.VideoDisplayMode
import com.example.core.model.VocalClarityConfig
import com.example.core.model.VolumeNormalizationConfig
import com.example.feature.nowplaying.components.AudioSpecsDialog
import com.example.ui.components.EditTrackDialog
import com.example.feature.nowplaying.components.FullScreenLyricsScreen
import com.example.feature.nowplaying.components.NowPlayingDesignSelectorDialog
import com.example.feature.nowplaying.components.NowPlayingQueueSheet
import com.example.ui.components.SearchLyricsDialog
import com.example.feature.nowplaying.components.VideoDisplayModeDialog
import com.example.ui.components.AudioEffectsBottomSheet
import kotlinx.coroutines.flow.StateFlow

/**
 * Host de diálogos y hojas modales de Now Playing.
 * Rol arquitectónico: agrupa los 8 overlays (cola, detalles, edición, video,
 * efectos, letras, karaoke y diseño) para que la fachada quede delgada.
 */
@Composable
fun NowPlayingDialogsHost(
    currentTrack: Track?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    currentPositionFlow: StateFlow<Long>?,
    durationMs: Long,
    queue: List<Track>,
    currentIndex: Int,
    onTrackSelectFromQueue: (Int) -> Unit,
    showQueueSheet: Boolean,
    onDismissQueue: () -> Unit,
    showDetailsDialog: Boolean,
    onEditTrack: ((trackId: Long, newTitle: String, newArtist: String, newAlbum: String) -> Unit)?,
    onEditTrackDetails: ((trackId: Long, newTitle: String, newArtist: String, newAlbum: String, customArtUri: Uri?, removeArtwork: Boolean) -> Unit)?,
    onEditTrackDetailsWithVideo: ((trackId: Long, newTitle: String, newArtist: String, newAlbum: String, customArtUri: Uri?, removeArtwork: Boolean, customVideoUri: Uri?, removeVideo: Boolean, forceLoop: Boolean?) -> Unit)?,
    onOpenEditDetails: () -> Unit,
    onDismissDetails: () -> Unit,
    showEditDialog: Boolean,
    onEditTrackDetailsWithLoopStyle: ((trackId: Long, newTitle: String, newArtist: String, newAlbum: String, customArtUri: Uri?, removeArtwork: Boolean, customVideoUri: Uri?, removeVideo: Boolean, forceLoop: Boolean?, loopStyle: FFmpegNativeEngine.CanvasLoopStyle) -> Unit)?,
    onDismissEdit: () -> Unit,
    showVideoModeDialog: Boolean,
    hasVideo: Boolean,
    videoDisplayMode: VideoDisplayMode,
    animatedPrimary: androidx.compose.ui.graphics.Color,
    animatedSecondary: androidx.compose.ui.graphics.Color,
    onSetVideoDisplayMode: (VideoDisplayMode) -> Unit,
    onDismissVideoMode: () -> Unit,
    showEffectsSheet: Boolean,
    onDismissEffects: () -> Unit,
    isEqEnabled: Boolean,
    eqScopeMode: EqualizerScopeMode,
    onSetEqScopeMode: (EqualizerScopeMode) -> Unit,
    eqBands: List<EqualizerBand>,
    bassBoostLevel: Int,
    currentPreset: EqualizerPreset,
    onToggleEqEnabled: (Boolean) -> Unit,
    onBandLevelChange: (Int, Int) -> Unit,
    onBassBoostChange: (Int) -> Unit,
    onPresetSelect: (EqualizerPreset) -> Unit,
    vocalClarityConfig: VocalClarityConfig,
    onSetVocalClarityEnabled: (Boolean) -> Unit,
    onSetVocalClarityStrength: (Float) -> Unit,
    sleepTimerState: SleepTimerState,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onAddSleepTimerMinutes: (Int) -> Unit,
    spatial8DConfig: Spatial8DConfig,
    onSet8DEnabled: (Boolean) -> Unit,
    onSet8DMode16D: (Boolean) -> Unit,
    onSet8DOrbitSpeed: (Float) -> Unit,
    onSet8DSpatialIntensity: (Float) -> Unit,
    onSet8DRoomDepth: (Float) -> Unit,
    reverbConfig: ReverbConfig,
    onSetReverbEnabled: (Boolean) -> Unit,
    onSetReverbPreset: (ReverbPreset) -> Unit,
    onSetReverbCustomParameters: (roomSize: Float, decayMs: Int, levelDb: Float) -> Unit,
    playbackSpeed: Float,
    onSetPlaybackSpeed: (Float) -> Unit,
    playbackPitch: Float,
    onSetPlaybackPitch: (Float) -> Unit,
    onResetSpeedAndPitch: () -> Unit,
    crossfadeSeconds: Int,
    onSetCrossfadeSeconds: (Int) -> Unit,
    isGaplessEnabled: Boolean,
    onSetGaplessEnabled: (Boolean) -> Unit,
    isDjAutomixEnabled: Boolean,
    onSetDjAutomixEnabled: (Boolean) -> Unit,
    isDjEqCurveEnabled: Boolean,
    onSetDjEqCurveEnabled: (Boolean) -> Unit,
    volumeNormalizationConfig: VolumeNormalizationConfig,
    onSetVolumeNormalizationEnabled: (Boolean) -> Unit,
    onSetVolumeNormalizationMode: (Int) -> Unit,
    abLoopState: ABLoopState,
    onMarkABPointA: () -> Unit,
    onMarkABPointB: () -> Unit,
    onToggleABLoopEnabled: (Boolean) -> Unit,
    onAdjustABPointA: (Long) -> Unit,
    onAdjustABPointB: (Long) -> Unit,
    onClearABLoop: () -> Unit,
    headphoneConfig: HeadphoneConfig,
    onSetCrossfeedEnabled: (Boolean) -> Unit,
    onSetCrossfeedStrength: (Int) -> Unit,
    onSetBalanceControlEnabled: (Boolean) -> Unit,
    onSetStereoBalance: (Float) -> Unit,
    effectsInitialTab: Int,
    isSearchLyricsDialogOpen: Boolean,
    isSearchingLyrics: Boolean,
    lyricsSearchResults: List<LyricSearchResult>,
    searchLyricsError: String?,
    lyricsState: LyricsState,
    onCloseSearchLyrics: () -> Unit,
    onSearchLyrics: (title: String, artist: String) -> Unit,
    onSelectLyricSearchResult: (LyricSearchResult) -> Unit,
    isFullScreenLyricsOpen: Boolean,
    audioIntensity: Float,
    audioIntensityFlow: StateFlow<Float>?,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onCloseFullScreenLyrics: () -> Unit,
    onOpenSearchLyrics: () -> Unit,
    onImportLyricsUri: (Uri) -> Unit,
    onSaveCustomLyrics: (String) -> Unit,
    onFetchOnlineLyrics: () -> Unit,
    showDesignSelectorDialog: Boolean,
    nowPlayingDesignMode: NowPlayingDesignMode,
    onSetNowPlayingDesignMode: (NowPlayingDesignMode) -> Unit,
    onDismissDesign: () -> Unit
) {
    if (currentTrack == null) return
            // Hoja Inferior: Cola de Reproducción (Up Next)
            if (showQueueSheet) {
                NowPlayingQueueSheet(
                    queue = queue,
                    currentIndex = currentIndex,
                    isPlaying = isPlaying,
                    onTrackSelect = onTrackSelectFromQueue,
                    onDismissRequest = { onDismissQueue() }
                )
            }

            // Diálogo de detalles técnicos de la pista
            if (showDetailsDialog) {
                AudioSpecsDialog(
                    currentTrack = currentTrack,
                    canEdit = onEditTrack != null || onEditTrackDetails != null || onEditTrackDetailsWithVideo != null,
                    onOpenEditDialog = { onOpenEditDetails() },
                    onDismissRequest = { onDismissDetails() }
                )
            }

            // Diálogo de edición de metadatos, carátula y video
            if (showEditDialog && (onEditTrack != null || onEditTrackDetails != null || onEditTrackDetailsWithVideo != null)) {
                EditTrackDialog(
                    track = currentTrack,
                    onDismiss = { onDismissEdit() },
                    onConfirm = { trackId, title, artist, album, customArtUri, removeArtwork, customVideoUri, removeVideo ->
                        if (onEditTrackDetailsWithVideo != null) {
                            onEditTrackDetailsWithVideo(trackId, title, artist, album, customArtUri, removeArtwork, customVideoUri, removeVideo, null)
                        } else if (onEditTrackDetails != null) {
                            onEditTrackDetails(trackId, title, artist, album, customArtUri, removeArtwork)
                        } else if (onEditTrack != null) {
                            onEditTrack(trackId, title, artist, album)
                        }
                    },
                    onConfirmWithLoopOption = { trackId, title, artist, album, customArtUri, removeArtwork, customVideoUri, removeVideo, forceLoop ->
                        if (onEditTrackDetailsWithVideo != null) {
                            onEditTrackDetailsWithVideo(trackId, title, artist, album, customArtUri, removeArtwork, customVideoUri, removeVideo, forceLoop)
                        } else if (onEditTrackDetails != null) {
                            onEditTrackDetails(trackId, title, artist, album, customArtUri, removeArtwork)
                        } else if (onEditTrack != null) {
                            onEditTrack(trackId, title, artist, album)
                        }
                    },
                    onConfirmWithLoopStyle = { trackId, title, artist, album, customArtUri, removeArtwork, customVideoUri, removeVideo, forceLoop, loopStyle ->
                        if (onEditTrackDetailsWithLoopStyle != null) {
                            onEditTrackDetailsWithLoopStyle(trackId, title, artist, album, customArtUri, removeArtwork, customVideoUri, removeVideo, forceLoop, loopStyle)
                        } else if (onEditTrackDetailsWithVideo != null) {
                            onEditTrackDetailsWithVideo(trackId, title, artist, album, customArtUri, removeArtwork, customVideoUri, removeVideo, forceLoop)
                        } else if (onEditTrackDetails != null) {
                            onEditTrackDetails(trackId, title, artist, album, customArtUri, removeArtwork)
                        } else if (onEditTrack != null) {
                            onEditTrack(trackId, title, artist, album)
                        }
                    }
                )
            }

            // Diálogo de selección de modo de video
            if (showVideoModeDialog && hasVideo) {
                VideoDisplayModeDialog(
                    videoDisplayMode = videoDisplayMode,
                    animatedPrimary = animatedPrimary,
                    onSetVideoDisplayMode = onSetVideoDisplayMode,
                    onDismissRequest = { onDismissVideoMode() }
                )
            }

            // Hoja modal unificada de efectos de audio
            if (showEffectsSheet) {
                AudioEffectsBottomSheet(
                    onDismissRequest = { onDismissEffects() },
                    isEqEnabled = isEqEnabled,
                    eqScopeMode = eqScopeMode,
                    onSetEqScopeMode = onSetEqScopeMode,
                    eqBands = eqBands,
                    bassBoostLevel = bassBoostLevel,
                    currentPreset = currentPreset,
                    onToggleEqEnabled = onToggleEqEnabled,
                    onBandLevelChange = onBandLevelChange,
                    onBassBoostChange = onBassBoostChange,
                    onPresetSelect = onPresetSelect,
                    vocalClarityConfig = vocalClarityConfig,
                    onSetVocalClarityEnabled = onSetVocalClarityEnabled,
                    onSetVocalClarityStrength = onSetVocalClarityStrength,
                    sleepTimerState = sleepTimerState,
                    onStartSleepTimer = onStartSleepTimer,
                    onCancelSleepTimer = onCancelSleepTimer,
                    onAddSleepTimerMinutes = onAddSleepTimerMinutes,
                    spatial8DConfig = spatial8DConfig,
                    onSet8DEnabled = onSet8DEnabled,
                    onSet8DMode16D = onSet8DMode16D,
                    onSet8DOrbitSpeed = onSet8DOrbitSpeed,
                    onSet8DSpatialIntensity = onSet8DSpatialIntensity,
                    onSet8DRoomDepth = onSet8DRoomDepth,
                    reverbConfig = reverbConfig,
                    onSetReverbEnabled = onSetReverbEnabled,
                    onSetReverbPreset = onSetReverbPreset,
                    onSetReverbCustomParameters = onSetReverbCustomParameters,
                    playbackSpeed = playbackSpeed,
                    onSetPlaybackSpeed = onSetPlaybackSpeed,
                    playbackPitch = playbackPitch,
                    onSetPlaybackPitch = onSetPlaybackPitch,
                    onResetSpeedAndPitch = onResetSpeedAndPitch,
                    crossfadeSeconds = crossfadeSeconds,
                    onSetCrossfadeSeconds = onSetCrossfadeSeconds,
                    isGaplessEnabled = isGaplessEnabled,
                    onSetGaplessEnabled = onSetGaplessEnabled,
                    isDjAutomixEnabled = isDjAutomixEnabled,
                    onSetDjAutomixEnabled = onSetDjAutomixEnabled,
                    isDjEqCurveEnabled = isDjEqCurveEnabled,
                    onSetDjEqCurveEnabled = onSetDjEqCurveEnabled,
                    volumeNormalizationConfig = volumeNormalizationConfig,
                    onSetVolumeNormalizationEnabled = onSetVolumeNormalizationEnabled,
                    onSetVolumeNormalizationMode = onSetVolumeNormalizationMode,
                    abLoopState = abLoopState,
                    onMarkABPointA = onMarkABPointA,
                    onMarkABPointB = onMarkABPointB,
                    onToggleABLoopEnabled = onToggleABLoopEnabled,
                    onAdjustABPointA = onAdjustABPointA,
                    onAdjustABPointB = onAdjustABPointB,
                    onClearABLoop = onClearABLoop,
                    headphoneConfig = headphoneConfig,
                    onSetCrossfeedEnabled = onSetCrossfeedEnabled,
                    onSetCrossfeedStrength = onSetCrossfeedStrength,
                    onSetBalanceControlEnabled = onSetBalanceControlEnabled,
                    onSetStereoBalance = onSetStereoBalance,
                    initialTab = effectsInitialTab
                )
            }

            // Diálogo para buscar letras personalizadas y elegir versiones con recomendación oficial
            if (isSearchLyricsDialogOpen) {
                SearchLyricsDialog(
                    currentTrack = currentTrack,
                    isSearching = isSearchingLyrics,
                    searchResults = lyricsSearchResults,
                    searchError = searchLyricsError,
                    accentColor = animatedPrimary,
                    onDismissRequest = onCloseSearchLyrics,
                    onSearch = onSearchLyrics,
                    onSelectResult = onSelectLyricSearchResult
                )
            }

            // Modo Karaoke a Pantalla Completa Inmersivo (Full Screen Lyrics)
            if (isFullScreenLyricsOpen && currentTrack != null) {
                FullScreenLyricsScreen(
                    currentTrack = currentTrack,
                    lyricsState = lyricsState,
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    currentPositionFlow = currentPositionFlow,
                    durationMs = durationMs,
                    animatedPrimary = animatedPrimary,
                    animatedSecondary = animatedSecondary,
                    audioIntensity = audioIntensity,
                    audioIntensityFlow = audioIntensityFlow,
                    onSeekTo = onSeekTo,
                    onTogglePlayPause = onTogglePlayPause,
                    onPlayNext = onPlayNext,
                    onPlayPrevious = onPlayPrevious,
                    onClose = { onCloseFullScreenLyrics() },
                    onOpenSearchLyrics = onOpenSearchLyrics,
                    onImportLyricsUri = onImportLyricsUri,
                    onSaveCustomLyrics = onSaveCustomLyrics,
                    onFetchOnlineLyrics = onFetchOnlineLyrics
                )
            }

            // Diálogo para seleccionar el diseño del reproductor (Clásico vs Cinemático Canvas)
            if (showDesignSelectorDialog) {
                NowPlayingDesignSelectorDialog(
                    currentDesignMode = nowPlayingDesignMode,
                    animatedPrimary = animatedPrimary,
                    onSetDesignMode = onSetNowPlayingDesignMode,
                    onDismissRequest = { onDismissDesign() }
                )
            }
}
