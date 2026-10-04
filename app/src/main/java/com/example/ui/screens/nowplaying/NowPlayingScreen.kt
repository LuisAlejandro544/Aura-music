package com.example.ui.screens.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ABLoopState
import com.example.model.EqualizerBand
import com.example.model.EqualizerPreset
import com.example.model.HeadphoneConfig
import com.example.model.RepeatMode
import com.example.model.ReverbConfig
import com.example.model.ReverbPreset
import com.example.model.SleepTimerState
import com.example.model.Spatial8DConfig
import com.example.model.Track
import com.example.model.VideoDisplayMode
import com.example.ui.components.AudioEffectsBottomSheet
import com.example.ui.components.AudioVisualizer
import com.example.ui.components.BackgroundVideoPlayer
import com.example.ui.components.EditTrackDialog
import com.example.ui.components.SearchLyricsDialog
import com.example.model.LyricSearchResult
import com.example.ui.screens.nowplaying.components.*
import com.example.ui.theme.ArtworkColorExtractor
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.ExtractedArtworkColors
import com.example.ui.theme.TextSecondary

/**
 * Pantalla completa de Reproducción en Curso (Now Playing).
 * Arquitectura Modular (MVVM):
 * Orquestador principal de UI que integra submódulos especializados en [com.example.ui.screens.nowplaying.components]:
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
    currentPositionMs: Long,
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
    onSet8DOrbitSpeed: (Float) -> Unit = {},
    onSet8DSpatialIntensity: (Float) -> Unit = {},
    onSet8DRoomDepth: (Float) -> Unit = {},
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
    // Repetidor de Segmento A-B
    abLoopState: ABLoopState = ABLoopState(),
    onMarkABPointA: () -> Unit = {},
    onMarkABPointB: () -> Unit = {},
    onToggleABLoopEnabled: (Boolean) -> Unit = {},
    onAdjustABPointA: (Long) -> Unit = {},
    onAdjustABPointB: (Long) -> Unit = {},
    onClearABLoop: () -> Unit = {},
    // Parámetros de Ecualizador C++20 integrados
    isEqEnabled: Boolean = true,
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
    audioIntensity: Float = 0.15f,
    lyricsState: com.example.model.LyricsState = com.example.model.LyricsState(),
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
    modifier: Modifier = Modifier
) {
    BackHandler {
        onCollapse()
    }

    if (currentTrack == null) return

    val context = LocalContext.current
    val fallbackPrimary = MaterialTheme.colorScheme.primary
    val fallbackSecondary = MaterialTheme.colorScheme.secondary

    var activeColors by remember {
        mutableStateOf(
            ExtractedArtworkColors(
                primary = fallbackPrimary,
                secondary = fallbackSecondary,
                accent = fallbackPrimary,
                ambientTopGlow = fallbackPrimary.copy(alpha = 0.28f)
            )
        )
    }

    val isVideoVisual = (videoDisplayMode != VideoDisplayMode.OFF) && !currentTrack.videoUri.isNullOrEmpty()

    // Extracción dinámica de color: si Video Canvas está activo, extrae del video para armonizar la UI
    LaunchedEffect(
        currentTrack.id,
        currentTrack.albumArtPath,
        currentTrack.videoUri,
        videoDisplayMode,
        isDynamicArtworkColorEnabled
    ) {
        activeColors = ArtworkColorExtractor.extractPlaybackColors(
            context = context,
            track = currentTrack,
            isVideoActive = isVideoVisual,
            isDynamicEnabled = isDynamicArtworkColorEnabled,
            fallbackPrimary = fallbackPrimary,
            fallbackSecondary = fallbackSecondary,
            positionMs = currentPositionMs
        )
    }

    // Muestreo dinámico continuo de fotogramas del Video Canvas en tiempo casi real según la posición de reproducción
    if (isVideoVisual && isPlaying && isDynamicArtworkColorEnabled) {
        val intervalStep = (currentPositionMs / 300L).coerceAtLeast(0L)
        LaunchedEffect(currentTrack.id, intervalStep) {
            activeColors = ArtworkColorExtractor.extractPlaybackColors(
                context = context,
                track = currentTrack,
                isVideoActive = true,
                isDynamicEnabled = true,
                fallbackPrimary = fallbackPrimary,
                fallbackSecondary = fallbackSecondary,
                positionMs = currentPositionMs
            )
        }
    }

    val animatedPrimary by animateColorAsState(
        targetValue = activeColors.primary,
        animationSpec = tween(220),
        label = "PrimaryAuraColor"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = activeColors.secondary,
        animationSpec = tween(220),
        label = "SecondaryAuraColor"
    )
    val animatedTopGlow by animateColorAsState(
        targetValue = activeColors.ambientTopGlow,
        animationSpec = tween(220),
        label = "TopAuraGlow"
    )

    var showQueueSheet by remember { mutableStateOf(false) }
    var showEffectsSheet by remember { mutableStateOf(false) }
    var effectsInitialTab by remember { mutableIntStateOf(0) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showVideoModeDialog by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    val hasVideo = !currentTrack.videoUri.isNullOrEmpty()
    val isFullscreenVideo = hasVideo && (videoDisplayMode == VideoDisplayMode.FULLSCREEN_BACKGROUND)

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
        // Modo FONDO COMPLETO: Renderiza el video de fondo detrás de toda la pantalla completa
        if (isFullscreenVideo && currentTrack.videoUri != null) {
            BackgroundVideoPlayer(
                videoUriString = currentTrack.videoUri,
                isVideoLoop = currentTrack.isVideoLoop,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                playbackSpeed = playbackSpeed,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 0.dp
            )

            // Velo oscuro y gradiente cinematográfico para máximo contraste y legibilidad
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.65f),
                                Color.Black.copy(alpha = 0.50f),
                                BackgroundDark.copy(alpha = 0.94f)
                            )
                        )
                    )
            )
        }

        // Halo de luz ambiental decorativo sobre fondo sincronizado con video o carátula,
        // respirando en tiempo real con la intensidad acústica procesada en C++20
        val dynamicAuraGlow = animatedTopGlow.copy(
            alpha = (0.24f + (audioIntensity.coerceIn(0f, 1f) * 0.32f)).coerceIn(0.18f, 0.75f)
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
                onOpenEffects = {
                    effectsInitialTab = 1
                    showEffectsSheet = true
                },
                onOpenDetails = { showDetailsDialog = true },
                onOpenVideoMode = { showVideoModeDialog = true },
                isLyricsActive = showLyrics,
                onToggleLyrics = { showLyrics = !showLyrics },
                isABLoopActive = abLoopState.isLoopingActive
            )

            Spacer(modifier = Modifier.weight(0.5f))

            // Carátula / Video Canvas O Letras Sincronizadas (.LRC) Karaoke
            if (showLyrics) {
                NowPlayingLyricsCard(
                    currentTrack = currentTrack,
                    lyricsState = lyricsState,
                    currentPositionMs = currentPositionMs,
                    animatedPrimary = animatedPrimary,
                    onSeekTo = onSeekTo,
                    onFetchOnlineLyrics = onFetchOnlineLyrics,
                    onSaveCustomLyrics = onSaveCustomLyrics,
                    onImportLyricsUri = onImportLyricsUri,
                    onOpenSearchLyrics = onOpenSearchLyrics,
                    onCloseLyrics = { showLyrics = false }
                )
            } else {
                NowPlayingArtworkCard(
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    videoDisplayMode = videoDisplayMode,
                    animatedPrimary = animatedPrimary,
                    animatedSecondary = animatedSecondary,
                    playbackSpeed = playbackSpeed,
                    onCycleVideoDisplayMode = onCycleVideoDisplayMode,
                    audioIntensity = audioIntensity,
                    onOpenVideoMode = { showVideoModeDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Visualizador dinámico de audio en tiempo real ligado directamente a C++20 DSP
            AudioVisualizer(
                isPlaying = isPlaying,
                realBands = visualizerBands,
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
                onClearABLoop = onClearABLoop
            )

            // Barra de Balance Estéreo Fino L/R en Tiempo Real
            NowPlayingBalanceBar(
                headphoneConfig = headphoneConfig,
                onSetStereoBalance = onSetStereoBalance,
                modifier = Modifier.padding(top = 10.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Atajos Inferiores: Ecualizador, Letras Karaoke y Cola de Reproducción
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ecualizador
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            effectsInitialTab = 0
                            showEffectsSheet = true
                        }
                        .testTag("now_playing_eq_shortcut")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = animatedSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "EQ FX",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = animatedSecondary,
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                    }
                }

                // Letras / Carátula
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showLyrics = !showLyrics }
                        .testTag("now_playing_lyrics_shortcut")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (showLyrics) Icons.Default.Album else Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (showLyrics) animatedPrimary else TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (showLyrics) "Carátula" else "Letras",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (showLyrics) FontWeight.Bold else FontWeight.Medium,
                                color = if (showLyrics) animatedPrimary else TextSecondary,
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                    }
                }

                // Cola de Reproducción
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showQueueSheet = true }
                        .testTag("now_playing_queue_btn")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Cola (${queue.size})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary,
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))
        }

        // Hoja Inferior: Cola de Reproducción (Up Next)
        if (showQueueSheet) {
            NowPlayingQueueSheet(
                queue = queue,
                currentIndex = currentIndex,
                isPlaying = isPlaying,
                onTrackSelect = onTrackSelectFromQueue,
                onDismissRequest = { showQueueSheet = false }
            )
        }

        // Diálogo de detalles técnicos de la pista
        if (showDetailsDialog) {
            AudioSpecsDialog(
                currentTrack = currentTrack,
                canEdit = onEditTrack != null || onEditTrackDetails != null || onEditTrackDetailsWithVideo != null,
                onOpenEditDialog = {
                    showDetailsDialog = false
                    showEditDialog = true
                },
                onDismissRequest = { showDetailsDialog = false }
            )
        }

        // Diálogo de edición de metadatos, carátula y video
        if (showEditDialog && (onEditTrack != null || onEditTrackDetails != null || onEditTrackDetailsWithVideo != null)) {
            EditTrackDialog(
                track = currentTrack,
                onDismiss = { showEditDialog = false },
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
                }
            )
        }

        // Diálogo de selección de modo de video
        if (showVideoModeDialog && hasVideo) {
            VideoDisplayModeDialog(
                videoDisplayMode = videoDisplayMode,
                animatedPrimary = animatedPrimary,
                onSetVideoDisplayMode = onSetVideoDisplayMode,
                onDismissRequest = { showVideoModeDialog = false }
            )
        }

        // Hoja modal unificada de efectos de audio
        if (showEffectsSheet) {
            AudioEffectsBottomSheet(
                onDismissRequest = { showEffectsSheet = false },
                isEqEnabled = isEqEnabled,
                eqBands = eqBands,
                bassBoostLevel = bassBoostLevel,
                currentPreset = currentPreset,
                onToggleEqEnabled = onToggleEqEnabled,
                onBandLevelChange = onBandLevelChange,
                onBassBoostChange = onBassBoostChange,
                onPresetSelect = onPresetSelect,
                sleepTimerState = sleepTimerState,
                onStartSleepTimer = onStartSleepTimer,
                onCancelSleepTimer = onCancelSleepTimer,
                onAddSleepTimerMinutes = onAddSleepTimerMinutes,
                spatial8DConfig = spatial8DConfig,
                onSet8DEnabled = onSet8DEnabled,
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
    }
}
