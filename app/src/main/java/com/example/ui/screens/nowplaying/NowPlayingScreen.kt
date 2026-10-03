package com.example.ui.screens.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EqualizerBand
import com.example.model.EqualizerPreset
import com.example.model.HeadphoneConfig
import com.example.model.RepeatMode
import com.example.model.SleepTimerState
import com.example.model.Spatial8DConfig
import com.example.model.Track
import com.example.model.VideoDisplayMode
import com.example.ui.components.AudioEffectsBottomSheet
import com.example.ui.components.AudioVisualizer
import com.example.ui.components.BackgroundVideoPlayer
import com.example.ui.components.EditTrackDialog
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
    playbackSpeed: Float = 1.0f,
    onSetPlaybackSpeed: (Float) -> Unit = {},
    playbackPitch: Float = 1.0f,
    onSetPlaybackPitch: (Float) -> Unit = {},
    onResetSpeedAndPitch: () -> Unit = {},
    crossfadeSeconds: Int = 0,
    onSetCrossfadeSeconds: (Int) -> Unit = {},
    isGaplessEnabled: Boolean = true,
    onSetGaplessEnabled: (Boolean) -> Unit = {},
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

    // Extracción dinámica de color: si Video Canvas está activo, extrae del video para armonizar la UI
    LaunchedEffect(
        currentTrack.id,
        currentTrack.albumArtPath,
        currentTrack.videoUri,
        videoDisplayMode,
        isDynamicArtworkColorEnabled
    ) {
        val isVideoVisual = (videoDisplayMode != VideoDisplayMode.OFF) && !currentTrack.videoUri.isNullOrEmpty()
        activeColors = ArtworkColorExtractor.extractPlaybackColors(
            context = context,
            track = currentTrack,
            isVideoActive = isVideoVisual,
            isDynamicEnabled = isDynamicArtworkColorEnabled,
            fallbackPrimary = fallbackPrimary,
            fallbackSecondary = fallbackSecondary
        )
    }

    val animatedPrimary by animateColorAsState(
        targetValue = activeColors.primary,
        animationSpec = tween(400),
        label = "PrimaryAuraColor"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = activeColors.secondary,
        animationSpec = tween(400),
        label = "SecondaryAuraColor"
    )
    val animatedTopGlow by animateColorAsState(
        targetValue = activeColors.ambientTopGlow,
        animationSpec = tween(400),
        label = "TopAuraGlow"
    )

    var showQueueSheet by remember { mutableStateOf(false) }
    var showEffectsSheet by remember { mutableStateOf(false) }
    var effectsInitialTab by remember { mutableIntStateOf(0) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showVideoModeDialog by remember { mutableStateOf(false) }

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
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 0.dp,
                showIndicator = false
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

        // Halo de luz ambiental decorativo sobre fondo sincronizado con video o carátula
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            animatedTopGlow,
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
                onOpenVideoMode = { showVideoModeDialog = true }
            )

            Spacer(modifier = Modifier.weight(0.5f))

            // Carátula o Video Canvas con aura luminosa y sombra flotante
            NowPlayingArtworkCard(
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                videoDisplayMode = videoDisplayMode,
                animatedPrimary = animatedPrimary,
                animatedSecondary = animatedSecondary,
                onCycleVideoDisplayMode = onCycleVideoDisplayMode
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Visualizador dinámico de audio en tiempo real
            AudioVisualizer(
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxWidth(0.9f),
                barCount = 32,
                barHeight = 40.dp,
                customColor = animatedPrimary
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
                onToggleFavorite = onToggleFavorite
            )

            // Barra de Balance Estéreo Fino L/R en Tiempo Real
            NowPlayingBalanceBar(
                headphoneConfig = headphoneConfig,
                onSetStereoBalance = onSetStereoBalance,
                modifier = Modifier.padding(top = 10.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Atajos Inferiores: Ecualizador y Cola de Reproducción
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        effectsInitialTab = 0
                        showEffectsSheet = true
                    },
                    modifier = Modifier.testTag("now_playing_eq_shortcut")
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ecualizador FX", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = { showQueueSheet = true },
                    modifier = Modifier.testTag("now_playing_queue_btn")
                ) {
                    Icon(Icons.Default.QueueMusic, contentDescription = null, tint = TextSecondary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cola (${queue.size})", color = TextSecondary)
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
                playbackSpeed = playbackSpeed,
                onSetPlaybackSpeed = onSetPlaybackSpeed,
                playbackPitch = playbackPitch,
                onSetPlaybackPitch = onSetPlaybackPitch,
                onResetSpeedAndPitch = onResetSpeedAndPitch,
                crossfadeSeconds = crossfadeSeconds,
                onSetCrossfadeSeconds = onSetCrossfadeSeconds,
                isGaplessEnabled = isGaplessEnabled,
                onSetGaplessEnabled = onSetGaplessEnabled,
                headphoneConfig = headphoneConfig,
                onSetCrossfeedEnabled = onSetCrossfeedEnabled,
                onSetCrossfeedStrength = onSetCrossfeedStrength,
                onSetBalanceControlEnabled = onSetBalanceControlEnabled,
                onSetStereoBalance = onSetStereoBalance,
                initialTab = effectsInitialTab
            )
        }
    }
}
