package com.example.ui.screens.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RepeatMode
import com.example.model.Track
import com.example.model.VideoDisplayMode
import com.example.ui.components.ArtworkImage
import com.example.ui.components.AudioVisualizer
import com.example.ui.theme.ArtworkColorExtractor
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CardBorder
import com.example.ui.theme.ExtractedArtworkColors
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pantalla completa de Reproducción en Curso (Now Playing).
 * Inspirada en la estética envolvente de Spotify pero con un aura de color luminosa,
 * visualizador dinámico de audio en tiempo real, detalles técnicos y cola de reproducción.
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
    sleepTimerState: com.example.model.SleepTimerState = com.example.model.SleepTimerState(),
    onStartSleepTimer: (Int) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onAddSleepTimerMinutes: (Int) -> Unit = {},
    spatial8DConfig: com.example.model.Spatial8DConfig = com.example.model.Spatial8DConfig(),
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
    eqBands: List<com.example.model.EqualizerBand> = emptyList(),
    bassBoostLevel: Int = 0,
    currentPreset: com.example.model.EqualizerPreset = com.example.model.EqualizerPreset.PRESETS.first(),
    onToggleEqEnabled: (Boolean) -> Unit = {},
    onBandLevelChange: (Int, Int) -> Unit = { _, _ -> },
    onBassBoostChange: (Int) -> Unit = {},
    onPresetSelect: (com.example.model.EqualizerPreset) -> Unit = {},
    // Auriculares & Balance Estéreo Fino
    headphoneConfig: com.example.model.HeadphoneConfig = com.example.model.HeadphoneConfig(),
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

    // Extracción dinámica de color: si Video Canvas está activo (en cualquier modo), extrae del video para no interferir
    // con el color de la carátula estática. Si el Canvas está inactivo o no hay video, extrae de la carátula.
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
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPosition by remember { mutableStateOf(0f) }

    val hasVideo = !currentTrack.videoUri.isNullOrEmpty()
    val isFullscreenVideo = hasVideo && (videoDisplayMode == VideoDisplayMode.FULLSCREEN_BACKGROUND)
    val isCardVideo = hasVideo && (videoDisplayMode == VideoDisplayMode.CARD_CANVAS)

    val safeDuration = durationMs.coerceAtLeast(1L)
    val sliderValue = if (isDraggingSlider) {
        sliderDragPosition
    } else {
        (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }

    val displayPositionMs = if (isDraggingSlider) {
        (sliderDragPosition * safeDuration).toLong()
    } else {
        currentPositionMs
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
        // Modo FONDO COMPLETO: Renderiza el video de fondo detrás de toda la pantalla completa
        if (isFullscreenVideo && currentTrack.videoUri != null) {
            com.example.ui.components.BackgroundVideoPlayer(
                videoUriString = currentTrack.videoUri,
                isVideoLoop = currentTrack.isVideoLoop,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 0.dp,
                showIndicator = false
            )

            // Velo oscuro y gradiente cinematográfico para máximo contraste y legibilidad de textos y controles
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.testTag("now_playing_collapse_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Ocultar reproductor",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "REPRODUCIENDO AURA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    )
                    Text(
                        text = currentTrack.album,
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            effectsInitialTab = 1
                            showEffectsSheet = true
                        },
                        modifier = Modifier.testTag("now_playing_effects_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (sleepTimerState.isActive || spatial8DConfig.enabled) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(8.dp)
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Efectos de Audio y Temporizador",
                                tint = if (sleepTimerState.isActive || spatial8DConfig.enabled) MaterialTheme.colorScheme.primary else TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = { showDetailsDialog = true },
                        modifier = Modifier.testTag("now_playing_details_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Detalles técnicos de audio",
                            tint = TextSecondary
                        )
                    }

                    if (hasVideo) {
                        IconButton(
                            onClick = { showVideoModeDialog = true },
                            modifier = Modifier.testTag("now_playing_toggle_video_canvas_btn")
                        ) {
                            Icon(
                                imageVector = when (videoDisplayMode) {
                                    VideoDisplayMode.FULLSCREEN_BACKGROUND -> Icons.Default.Wallpaper
                                    VideoDisplayMode.CARD_CANVAS -> Icons.Default.CropSquare
                                    VideoDisplayMode.OFF -> Icons.Default.VideocamOff
                                },
                                contentDescription = "Modo de Video: ${videoDisplayMode.label}",
                                tint = if (videoDisplayMode != VideoDisplayMode.OFF) animatedPrimary else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // Carátula o Video Canvas con aura luminosa y sombra flotante
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = animatedPrimary,
                        spotColor = animatedSecondary
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCardVideo && currentTrack.videoUri != null) {
                    com.example.ui.components.BackgroundVideoPlayer(
                        videoUriString = currentTrack.videoUri,
                        isVideoLoop = currentTrack.isVideoLoop,
                        isPlaying = isPlaying,
                        currentPositionMs = currentPositionMs,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 24.dp
                    )
                } else {
                    ArtworkImage(
                        track = currentTrack,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 24.dp
                    )

                    // Si está en modo Fondo Completo, mostrar badge interactivo para cambiar de modo al tocar
                    if (isFullscreenVideo) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, animatedPrimary.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onCycleVideoDisplayMode() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wallpaper,
                                    contentDescription = null,
                                    tint = animatedPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (currentTrack.isVideoLoop) "FONDO LOOP" else "FONDO SYNC",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

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

            // Título, Artista, Etiqueta de formato y botón de Favorito
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = animatedPrimary.copy(alpha = 0.16f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = currentTrack.formatBadge(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = animatedPrimary
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = currentTrack.artist,
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = { onToggleFavorite(currentTrack) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("now_playing_fav_btn")
                ) {
                    Icon(
                        imageVector = if (currentTrack.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorito",
                        tint = if (currentTrack.isFavorite) Color(0xFFEF4444) else TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Slider de Progreso y Tiempo
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        isDraggingSlider = true
                        sliderDragPosition = it
                    },
                    onValueChangeFinished = {
                        onSeekTo((sliderDragPosition * safeDuration).toLong())
                        isDraggingSlider = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = animatedPrimary,
                        inactiveTrackColor = animatedPrimary.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("now_playing_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatMillis(displayPositionMs),
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                    Text(
                        text = formatMillis(durationMs),
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Controles de Reproducción Principales
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Aleatorio
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.testTag("now_playing_shuffle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Modo aleatorio",
                        tint = if (shuffleEnabled) animatedPrimary else TextMuted
                    )
                }

                // Anterior
                IconButton(
                    onClick = onPlayPrevious,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("now_playing_prev_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Pista anterior",
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play / Pause (Botón Grande con Gradiente)
                Surface(
                    shape = CircleShape,
                    color = animatedPrimary,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                ) {
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("now_playing_play_pause_btn")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Siguiente
                IconButton(
                    onClick = onPlayNext,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("now_playing_next_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Pista siguiente",
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repetir
                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier.testTag("now_playing_repeat_btn")
                ) {
                    Icon(
                        imageVector = when (repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            RepeatMode.ALL -> Icons.Default.Repeat
                            RepeatMode.OFF -> Icons.Default.Repeat
                        },
                        contentDescription = "Repetir",
                        tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else TextMuted
                    )
                }
            }

            // Barra de Balance Estéreo Fino L/R en Tiempo Real (si el usuario activó la opción)
            if (headphoneConfig.isBalanceControlEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceCard.copy(alpha = 0.92f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                        .testTag("now_playing_balance_control_bar")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Balance Estéreo L/R:",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                            }
                            Text(
                                text = headphoneConfig.formattedBalance,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            )
                            if (kotlin.math.abs(headphoneConfig.stereoBalance) > 0.02f) {
                                TextButton(
                                    onClick = { onSetStereoBalance(0.0f) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text(
                                        text = "Centrar",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                }
                            }
                        }
                        Slider(
                            value = headphoneConfig.stereoBalance,
                            onValueChange = onSetStereoBalance,
                            valueRange = -1.0f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.secondary,
                                activeTrackColor = MaterialTheme.colorScheme.secondary,
                                inactiveTrackColor = Color(0xFF232736)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .testTag("now_playing_live_balance_slider")
                        )
                    }
                }
            }

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
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                containerColor = SurfaceCard
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "Cola de Reproducción",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxHeight(0.6f)
                    ) {
                        itemsIndexed(queue) { index, track ->
                            val isSelected = index == currentIndex
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                                        modifier = Modifier.width(28.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else TextPrimary
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = track.artist,
                                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                            maxLines = 1
                                        )
                                    }
                                    if (isSelected && isPlaying) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Diálogo de detalles técnicos de la pista
        if (showDetailsDialog) {
            AlertDialog(
                onDismissRequest = { showDetailsDialog = false },
                title = { Text("Detalles del Archivo de Audio") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailItem("Título", currentTrack.title)
                        DetailItem("Artista", currentTrack.artist)
                        DetailItem("Álbum", currentTrack.album)
                        DetailItem("Formato", currentTrack.formatBadge())
                        DetailItem("Tipo MIME", currentTrack.mimeType)
                        if (currentTrack.fileSizeFormatted.isNotBlank()) {
                            DetailItem("Tamaño", currentTrack.fileSizeFormatted)
                        }
                        DetailItem("Origen", if (currentTrack.folderName.isNotBlank()) currentTrack.folderName else "Almacenamiento Local")
                        if (!currentTrack.videoUri.isNullOrEmpty()) {
                            DetailItem("Video Canvas", if (currentTrack.isVideoLoop) "Loop Continuo (≤ 10s)" else "Sincronizado con Audio (> 10s)")
                        }
                    }
                },
                confirmButton = {
                    Row {
                        if (onEditTrack != null || onEditTrackDetails != null || onEditTrackDetailsWithVideo != null) {
                            TextButton(
                                onClick = {
                                    showDetailsDialog = false
                                    showEditDialog = true
                                }
                            ) {
                                Text("Editar Información", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        TextButton(onClick = { showDetailsDialog = false }) {
                            Text("Cerrar")
                        }
                    }
                }
            )
        }

        if (showEditDialog && (onEditTrack != null || onEditTrackDetails != null || onEditTrackDetailsWithVideo != null)) {
            com.example.ui.components.EditTrackDialog(
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

        if (showVideoModeDialog && hasVideo) {
            AlertDialog(
                onDismissRequest = { showVideoModeDialog = false },
                containerColor = SurfaceElevatedDark,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = animatedPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Modo de Video",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Elige cómo visualizar el video asociado a esta canción:",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        VideoDisplayMode.values().forEach { mode ->
                            val isSelected = (videoDisplayMode == mode)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) animatedPrimary.copy(alpha = 0.16f) else SurfaceCard,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) animatedPrimary else CardBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        onSetVideoDisplayMode(mode)
                                        showVideoModeDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when (mode) {
                                            VideoDisplayMode.FULLSCREEN_BACKGROUND -> Icons.Default.Wallpaper
                                            VideoDisplayMode.CARD_CANVAS -> Icons.Default.CropSquare
                                            VideoDisplayMode.OFF -> Icons.Default.VideocamOff
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) animatedPrimary else TextSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) animatedPrimary else TextPrimary
                                            )
                                        )
                                        Text(
                                            text = mode.description,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            onSetVideoDisplayMode(mode)
                                            showVideoModeDialog = false
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = animatedPrimary,
                                            unselectedColor = TextSecondary
                                        )
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showVideoModeDialog = false }) {
                        Text("Listo", color = animatedPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (showEffectsSheet) {
            com.example.ui.components.AudioEffectsBottomSheet(
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

@Composable
private fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "$label:", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        Text(text = value, color = TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatMillis(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val sec = totalSec % 60
    val min = (totalSec / 60) % 60
    val hours = totalSec / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, min, sec)
    } else {
        String.format("%d:%02d", min, sec)
    }
}
