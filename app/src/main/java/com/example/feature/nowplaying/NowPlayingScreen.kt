package com.example.feature.nowplaying
import com.example.core.model.RepeatMode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import com.example.core.model.ABLoopState
import com.example.core.model.EqualizerBand
import com.example.core.model.EqualizerPreset
import com.example.core.model.EqualizerScopeMode
import com.example.core.model.HeadphoneConfig
import com.example.core.model.ReverbConfig
import com.example.core.model.ReverbPreset
import com.example.core.model.SleepTimerState
import com.example.core.model.Spatial8DConfig
import com.example.core.model.Track
import com.example.core.model.VideoDisplayMode
import com.example.ui.components.AudioVisualizer
import com.example.core.model.LyricSearchResult
import com.example.core.model.NowPlayingDesignMode
import com.example.feature.nowplaying.components.*
import com.example.ui.theme.BackgroundDark

/**
 * Pantalla completa de Reproducción en Curso (Now Playing).
 * Arquitectura Modular (MVVM):
 * Orquestador principal de UI que integra submódulos especializados en [com.example.feature.nowplaying.components]:
 * - [NowPlayingTopBar]: Barra superior con botones de colapso, insignia de efectos, video y ficha técnica.
 * - [NowPlayingArtworkCard]: Carátula flotante y soporte para los 3 modos de Video Canvas.
 * - [AudioVisualizer]: Visualizador animado de ondas en tiempo real.
 * - [NowPlayingPlaybackControls]: Información de pista, favorito, barra de progreso interactiva (Seekbar) y controles de reproducción.
 * - [NowPlayingBalanceBar]: Control de Balance Estéreo L/R en vivo con centrado a 0%.
 * - [NowPlayingQueueSheet]: Hoja modal de la cola de reproducción ("Up Next").
 * - [AudioSpecsDialog]: Ficha técnica de audio y acceso a edición.
 * - [VideoDisplayModeDialog]: Diálogo de selección de los 3 modos de video.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    currentTrack: Track?,
    isPlaying: Boolean,
    currentPositionMs: Long = 0L,
    currentPositionFlow: StateFlow<Long>? = null,
    durationMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    queue: List<Track>,
    currentIndex: Int,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onTrackSelectFromQueue: (Int) -> Unit,
    onOpenEqualizer: () -> Unit,
    onCollapse: () -> Unit,
    onEditTrack: ((trackId: Long, newTitle: String, newArtist: String, newAlbum: String) -> Unit)? = null,
    onEditTrackDetails: ((trackId: Long, newTitle: String, newArtist: String, newAlbum: String, customArtUri: android.net.Uri?, removeArtwork: Boolean) -> Unit)? = null,
    onEditTrackDetailsWithVideo: ((trackId: Long, newTitle: String, newArtist: String, newAlbum: String, customArtUri: android.net.Uri?, removeArtwork: Boolean, customVideoUri: android.net.Uri?, removeVideo: Boolean, forceLoop: Boolean?) -> Unit)? = null,
    onEditTrackDetailsWithLoopStyle: ((trackId: Long, newTitle: String, newArtist: String, newAlbum: String, customArtUri: android.net.Uri?, removeArtwork: Boolean, customVideoUri: android.net.Uri?, removeVideo: Boolean, forceLoop: Boolean?, loopStyle: com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle) -> Unit)? = null,
    isVideoCanvasActive: Boolean = true,
    videoDisplayMode: VideoDisplayMode = VideoDisplayMode.FULLSCREEN_BACKGROUND,
    isDynamicArtworkColorEnabled: Boolean = true,
    onToggleVideoCanvas: () -> Unit = {},
    onSetVideoDisplayMode: (VideoDisplayMode) -> Unit = {},
    onCycleVideoDisplayMode: () -> Unit = {},
    sleepTimerState: SleepTimerState = SleepTimerState(),
    onStartSleepTimer: (Int) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onAddSleepTimerMinutes: (Int) -> Unit = {},
    spatial8DConfig: Spatial8DConfig = Spatial8DConfig(),
    onSet8DEnabled: (Boolean) -> Unit = {},
    onSet8DMode16D: (Boolean) -> Unit = {},
    onSet8DOrbitSpeed: (Float) -> Unit = {},
    onSet8DSpatialIntensity: (Float) -> Unit = {},
    onSet8DRoomDepth: (Float) -> Unit = {},
    // Clarificador de Voces HD C++20 (Mid-Side)
    vocalClarityConfig: com.example.core.model.VocalClarityConfig = com.example.core.model.VocalClarityConfig(),
    onSetVocalClarityEnabled: (Boolean) -> Unit = {},
    onSetVocalClarityStrength: (Float) -> Unit = {},
    // Suite Reverb & Filtros Acústicos
    reverbConfig: ReverbConfig = ReverbConfig(),
    onSetReverbEnabled: (Boolean) -> Unit = {},
    onSetReverbPreset: (ReverbPreset) -> Unit = {},
    onSetReverbCustomParameters: (roomSize: Float, decayMs: Int, levelDb: Float) -> Unit = { _, _, _ -> },
    playbackSpeed: Float = 1.0f,
    onSetPlaybackSpeed: (Float) -> Unit = {},
    playbackPitch: Float = 1.0f,
    onSetPlaybackPitch: (Float) -> Unit = {},
    onResetSpeedAndPitch: () -> Unit = {},
    crossfadeSeconds: Int = 0,
    onSetCrossfadeSeconds: (Int) -> Unit = {},
    isGaplessEnabled: Boolean = true,
    onSetGaplessEnabled: (Boolean) -> Unit = {},
    isDjAutomixEnabled: Boolean = false,
    onSetDjAutomixEnabled: (Boolean) -> Unit = {},
    isDjEqCurveEnabled: Boolean = true,
    onSetDjEqCurveEnabled: (Boolean) -> Unit = {},
    volumeNormalizationConfig: com.example.core.model.VolumeNormalizationConfig = com.example.core.model.VolumeNormalizationConfig(),
    onSetVolumeNormalizationEnabled: (Boolean) -> Unit = {},
    onSetVolumeNormalizationMode: (Int) -> Unit = {},
    // Repetidor de Segmento A-B
    abLoopState: ABLoopState = ABLoopState(),
    onMarkABPointA: () -> Unit = {},
    onMarkABPointB: () -> Unit = {},
    onToggleABLoopEnabled: (Boolean) -> Unit = {},
    onAdjustABPointA: (Long) -> Unit = {},
    onAdjustABPointB: (Long) -> Unit = {},
    onClearABLoop: () -> Unit = {},
    // Parámetros de Ecualizador C++20 integrados (desactivado por defecto)
    isEqEnabled: Boolean = false,
    eqScopeMode: EqualizerScopeMode = EqualizerScopeMode.GLOBAL_ALL_TRACKS,
    onSetEqScopeMode: (EqualizerScopeMode) -> Unit = {},
    eqBands: List<EqualizerBand> = emptyList(),
    bassBoostLevel: Int = 0,
    currentPreset: EqualizerPreset = EqualizerPreset.PRESETS.first(),
    onToggleEqEnabled: (Boolean) -> Unit = {},
    onBandLevelChange: (Int, Int) -> Unit = { _, _ -> },
    onBassBoostChange: (Int) -> Unit = {},
    onPresetSelect: (EqualizerPreset) -> Unit = {},
    // Auriculares & Balance Estéreo Fino
    headphoneConfig: HeadphoneConfig = HeadphoneConfig(),
    onSetCrossfeedEnabled: (Boolean) -> Unit = {},
    onSetCrossfeedStrength: (Int) -> Unit = {},
    onSetBalanceControlEnabled: (Boolean) -> Unit = {},
    onSetStereoBalance: (Float) -> Unit = {},
    // Visualizador espectral C++20, intensidad acústica y Letras Sincronizadas
    visualizerBands: FloatArray? = null,
    visualizerBandsFlow: StateFlow<FloatArray>? = null,
    audioIntensity: Float = 0.15f,
    audioIntensityFlow: StateFlow<Float>? = null,
    lyricsState: com.example.core.model.LyricsState = com.example.core.model.LyricsState(),
    isSearchLyricsDialogOpen: Boolean = false,
    isSearchingLyrics: Boolean = false,
    lyricsSearchResults: List<LyricSearchResult> = emptyList(),
    searchLyricsError: String? = null,
    onFetchOnlineLyrics: () -> Unit = {},
    onSaveCustomLyrics: (String) -> Unit = {},
    onImportLyricsUri: (android.net.Uri) -> Unit = {},
    onOpenSearchLyrics: () -> Unit = {},
    onCloseSearchLyrics: () -> Unit = {},
    onSearchLyrics: (title: String, artist: String) -> Unit = { _, _ -> },
    onSelectLyricSearchResult: (LyricSearchResult) -> Unit = {},
    activeMixtapeChapter: com.example.core.model.MixtapeChapter? = null,
    mixtapeChapterIndex: Int = -1,
    mixtapeTotalChapters: Int = 0,
    nowPlayingDesignMode: NowPlayingDesignMode = NowPlayingDesignMode.AUTO,
    onSetNowPlayingDesignMode: (NowPlayingDesignMode) -> Unit = {},
    collectionContextTitle: String = "Tu Biblioteca",
    onDownloadStreamingTrack: ((Track) -> Unit)? = null,
    isStreamingTrackDownloading: Boolean = false,
    isStreamingTrackDownloaded: Boolean = false,
    isLoadingOrBuffering: Boolean = false,
    isWaitingForNetwork: Boolean = false,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onCollapse()
    }

    if (currentTrack == null) return

    val visual = rememberNowPlayingVisualState(
        track = currentTrack,
        videoDisplayMode = videoDisplayMode,
        isDynamicEnabled = isDynamicArtworkColorEnabled,
        currentPositionMs = currentPositionMs,
        currentPositionFlow = currentPositionFlow,
        isWaitingForNetwork = isWaitingForNetwork
    )
    val animatedPrimary = visual.animatedPrimary
    val animatedSecondary = visual.animatedSecondary
    val animatedTopGlow = visual.animatedTopGlow
    val isVideoVisual = visual.isVideoVisual
    val effectiveVideoLoading = visual.effectiveVideoLoading
    val effectiveWaitingNetwork = visual.effectiveWaitingNetwork
    val isFullscreenVideo = visual.isFullscreenVideo
    val hasVideo = visual.hasVideo

    var showQueueSheet by remember { mutableStateOf(false) }
    var showEffectsSheet by remember { mutableStateOf(false) }
    var effectsInitialTab by remember { mutableIntStateOf(0) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showVideoModeDialog by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var isFullScreenLyricsOpen by remember { mutableStateOf(false) }
    var showDesignSelectorDialog by remember { mutableStateOf(false) }

    // Determinación del diseño visual activo (Automático o forzado por el usuario)
    val isCinematicActive = remember(nowPlayingDesignMode, isFullscreenVideo, currentTrack.videoUri) {
        isCinematicCanvasActive(nowPlayingDesignMode, isFullscreenVideo, currentTrack.videoUri)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .pointerInput(Unit) {
                detectTapGestures {
                    // Consumir toques en áreas vacías para que no traspasen a las pantallas de fondo
                }
            }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                // Interceptar clics residuales en cualquier espacio de NowPlayingScreen
            }
            .testTag("now_playing_screen")
    ) {
        // Modo FONDO COMPLETO (Rellenar o Adaptado): Renderiza el video detrás de toda la pantalla
        if (isFullscreenVideo && currentTrack.videoUri != null) {
            NowPlayingFullscreenBackground(
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                currentPositionFlow = currentPositionFlow,
                playbackSpeed = playbackSpeed,
                videoDisplayMode = videoDisplayMode
            )
        }

        // Halo de luz ambiental decorativo aislado en su propio componente pequeño
        // para que audioIntensity NO recomponga toda NowPlayingScreen
        NowPlayingAmbientTopGlow(
            animatedTopGlow = animatedTopGlow,
            isFullscreenVideo = isFullscreenVideo,
            fallbackIntensity = audioIntensity,
            audioIntensityFlow = audioIntensityFlow
        )

        if (isCinematicActive && !showLyrics) {
            NowPlayingCinematicHost(
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
                onOpenEffects = {
                    effectsInitialTab = 1
                    showEffectsSheet = true
                },
                onOpenQueue = { showQueueSheet = true },
                onOpenDesignSelector = { showDesignSelectorDialog = true },
                onOpenLyrics = { showLyrics = true },
                onOpenFullScreenLyrics = { isFullScreenLyricsOpen = true },
                onOpenDetails = { showDetailsDialog = true },
                collectionContextTitle = collectionContextTitle,
                onDownloadStreamingTrack = onDownloadStreamingTrack,
                isStreamingTrackDownloading = isStreamingTrackDownloading,
                isStreamingTrackDownloaded = isStreamingTrackDownloaded,
                isLoadingOrBuffering = isLoadingOrBuffering,
                isVideoLoading = effectiveVideoLoading,
                isWaitingForNetwork = effectiveWaitingNetwork
            )
        } else {
            NowPlayingClassicLayout(
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                currentPositionFlow = currentPositionFlow,
                durationMs = durationMs,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                animatedPrimary = animatedPrimary,
                animatedSecondary = animatedSecondary,
                sleepTimerState = sleepTimerState,
                spatial8DConfig = spatial8DConfig,
                videoDisplayMode = videoDisplayMode,
                showLyrics = showLyrics,
                abLoopState = abLoopState,
                vocalClarityEnabled = vocalClarityConfig.enabled,
                lyricsState = lyricsState,
                visualizerBands = visualizerBands,
                visualizerBandsFlow = visualizerBandsFlow,
                audioIntensity = audioIntensity,
                playbackSpeed = playbackSpeed,
                headphoneConfig = headphoneConfig,
                queueSize = queue.size,
                isContentLoading = isLoadingOrBuffering || effectiveVideoLoading,
                effectiveWaitingNetwork = effectiveWaitingNetwork,
                activeMixtapeChapter = activeMixtapeChapter,
                mixtapeChapterIndex = mixtapeChapterIndex,
                mixtapeTotalChapters = mixtapeTotalChapters,
                onCollapse = onCollapse,
                onTogglePlayPause = onTogglePlayPause,
                onSeekTo = onSeekTo,
                onPlayNext = onPlayNext,
                onPlayPrevious = onPlayPrevious,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
                onToggleFavorite = onToggleFavorite,
                onCycleVideoDisplayMode = onCycleVideoDisplayMode,
                onSetStereoBalance = onSetStereoBalance,
                onFetchOnlineLyrics = onFetchOnlineLyrics,
                onSaveCustomLyrics = onSaveCustomLyrics,
                onImportLyricsUri = onImportLyricsUri,
                onOpenSearchLyrics = onOpenSearchLyrics,
                onMarkABPointA = onMarkABPointA,
                onMarkABPointB = onMarkABPointB,
                onClearABLoop = onClearABLoop,
                onDownloadStreamingTrack = onDownloadStreamingTrack,
                isStreamingTrackDownloading = isStreamingTrackDownloading,
                isStreamingTrackDownloaded = isStreamingTrackDownloaded,
                onOpenEffectsTab = {
                    effectsInitialTab = it
                    showEffectsSheet = true
                },
                onOpenDetails = { showDetailsDialog = true },
                onOpenVideoMode = { showVideoModeDialog = true },
                onToggleLyrics = { showLyrics = !showLyrics },
                onOpenDesignSelector = { showDesignSelectorDialog = true },
                onOpenFullScreenLyrics = { isFullScreenLyricsOpen = true },
                onCloseLyrics = { showLyrics = false },
                onOpenQueue = { showQueueSheet = true }
            )
    }

        NowPlayingDialogsHost(
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            currentPositionMs = currentPositionMs,
            currentPositionFlow = currentPositionFlow,
            durationMs = durationMs,
            queue = queue,
            currentIndex = currentIndex,
            onTrackSelectFromQueue = onTrackSelectFromQueue,
            showQueueSheet = showQueueSheet,
            onDismissQueue = { showQueueSheet = false },
            showDetailsDialog = showDetailsDialog,
            onEditTrack = onEditTrack,
            onEditTrackDetails = onEditTrackDetails,
            onEditTrackDetailsWithVideo = onEditTrackDetailsWithVideo,
            onOpenEditDetails = { showDetailsDialog = false; showEditDialog = true },
            onDismissDetails = { showDetailsDialog = false },
            showEditDialog = showEditDialog,
            onEditTrackDetailsWithLoopStyle = onEditTrackDetailsWithLoopStyle,
            onDismissEdit = { showEditDialog = false },
            showVideoModeDialog = showVideoModeDialog,
            hasVideo = hasVideo,
            videoDisplayMode = videoDisplayMode,
            animatedPrimary = animatedPrimary,
            animatedSecondary = animatedSecondary,
            onSetVideoDisplayMode = onSetVideoDisplayMode,
            onDismissVideoMode = { showVideoModeDialog = false },
            showEffectsSheet = showEffectsSheet,
            onDismissEffects = { showEffectsSheet = false },
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
            effectsInitialTab = effectsInitialTab,
            isSearchLyricsDialogOpen = isSearchLyricsDialogOpen,
            isSearchingLyrics = isSearchingLyrics,
            lyricsSearchResults = lyricsSearchResults,
            searchLyricsError = searchLyricsError,
            lyricsState = lyricsState,
            onCloseSearchLyrics = onCloseSearchLyrics,
            onSearchLyrics = onSearchLyrics,
            onSelectLyricSearchResult = onSelectLyricSearchResult,
            isFullScreenLyricsOpen = isFullScreenLyricsOpen,
            audioIntensity = audioIntensity,
            audioIntensityFlow = audioIntensityFlow,
            onSeekTo = onSeekTo,
            onTogglePlayPause = onTogglePlayPause,
            onPlayNext = onPlayNext,
            onPlayPrevious = onPlayPrevious,
            onCloseFullScreenLyrics = { isFullScreenLyricsOpen = false },
            onOpenSearchLyrics = onOpenSearchLyrics,
            onImportLyricsUri = onImportLyricsUri,
            onSaveCustomLyrics = onSaveCustomLyrics,
            onFetchOnlineLyrics = onFetchOnlineLyrics,
            showDesignSelectorDialog = showDesignSelectorDialog,
            nowPlayingDesignMode = nowPlayingDesignMode,
            onSetNowPlayingDesignMode = onSetNowPlayingDesignMode,
            onDismissDesign = { showDesignSelectorDialog = false }
        )
    }
}

@Composable
private fun NowPlayingAmbientTopGlow(
    animatedTopGlow: Color,
    isFullscreenVideo: Boolean,
    fallbackIntensity: Float,
    audioIntensityFlow: StateFlow<Float>?
) {
    val collectedIntensity = audioIntensityFlow?.collectAsStateWithLifecycle()
    val liveIntensity = collectedIntensity?.value ?: fallbackIntensity
    val dynamicAuraGlow = animatedTopGlow.copy(
        alpha = (0.24f + (liveIntensity.coerceIn(0f, 1f) * 0.32f)).coerceIn(0.18f, 0.75f)
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        dynamicAuraGlow,
                        Color.Transparent,
                        if (isFullscreenVideo) Color.Transparent else BackgroundDark
                    )
                )
            )
    )
}
