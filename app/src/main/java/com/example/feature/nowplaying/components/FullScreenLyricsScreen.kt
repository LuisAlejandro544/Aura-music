package com.example.feature.nowplaying.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.LyricsState
import com.example.core.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Vista Inmersiva de Letras y Karaoke a Pantalla Completa (Full Screen Lyrics).
 * Experiencia de alta fidelidad de estudio:
 * - Lienzo a pantalla completa con fondo degradado dinámico y halo ambiental que respira con el audio en C++20.
 * - Desplazamiento automático inteligente con seguimiento de tempo en tiempo real.
 * - Tipografía ampliada con resaltado neón, escala dinámica y resplandor para la frase activa.
 * - Salto instantáneo en la canción al pulsar cualquier frase.
 * - Botón flotante de sincronización ("Volver a la canción") al realizar desplazamiento manual.
 * - Barra de transporte flotante inferior con controles de reproducción, marquesina fluida y seekbar interactiva.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenLyricsScreen(
    currentTrack: Track?,
    lyricsState: LyricsState,
    isPlaying: Boolean,
    currentPositionMs: Long = 0L,
    currentPositionFlow: StateFlow<Long>? = null,
    durationMs: Long,
    animatedPrimary: Color,
    animatedSecondary: Color,
    audioIntensity: Float = 0.15f,
    audioIntensityFlow: StateFlow<Float>? = null,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onClose: () -> Unit,
    onOpenSearchLyrics: () -> Unit = {},
    onImportLyricsUri: (android.net.Uri) -> Unit = {},
    onSaveCustomLyrics: (String) -> Unit = {},
    onFetchOnlineLyrics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onClose() }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var userScrolledManually by remember { mutableStateOf(false) }
    // Guardia para distinguir el scroll programático (auto-follow) del gesto real del
    // usuario: mientras está en true, isScrollInProgress se ignora y no rompe el seguimiento.
    var isAutoScrolling by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }

    // Launcher del sistema para archivos .lrc / .txt
    val importLyricsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) onImportLyricsUri(uri)
    }

    val collectedPosition = currentPositionFlow?.collectAsStateWithLifecycle()

    // Índice de la frase activa mediante búsqueda binaria O(log N) + derivedStateOf
    // para que la lista de letras solo se recomponga cuando cambie la línea activa
    val activeIndex by remember(lyricsState.lines, collectedPosition, currentPositionMs) {
        derivedStateOf {
            val posMs = collectedPosition?.value ?: currentPositionMs
            findActiveLyricIndexBinary(lyricsState.lines, posMs)
        }
    }

    // Seguimiento de scroll manual: solo el gesto del usuario rompe el auto-follow.
    // El scroll programático (animateScrollToItem del auto-follow o del botón Sincronizar)
    // activa isScrollInProgress, por eso se ignora mientras isAutoScrolling está activo.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !isAutoScrolling) {
            userScrolledManually = true
        }
    }

    // Auto-scroll centrado a la frase actual: al pulsar "Sincronizar con audio" se
    // reanuda y sigue solo hasta que el usuario vuelva a arrastrar la pantalla.
    LaunchedEffect(activeIndex, userScrolledManually) {
        if (!userScrolledManually && activeIndex >= 0 && lyricsState.lines.isNotEmpty()) {
            val targetScroll = (activeIndex - 2).coerceAtLeast(0)
            isAutoScrolling = true
            try {
                listState.animateScrollToItem(targetScroll)
            } finally {
                isAutoScrolling = false
            }
        }
    }

    // Al cambiar de canción, retomar el seguimiento automático desde el inicio.
    LaunchedEffect(lyricsState.lines) {
        userScrolledManually = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .pointerInput(Unit) { detectTapGestures {} }
            .testTag("full_screen_lyrics_screen")
    ) {
        // Fondo degradado dinámico inmersivo aislado en componente pequeño
        FullScreenLyricsReactiveBackground(
            animatedPrimary = animatedPrimary,
            animatedSecondary = animatedSecondary,
            fallbackIntensity = audioIntensity,
            audioIntensityFlow = audioIntensityFlow
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp)
        ) {
            FullScreenLyricsTopBar(
                animatedPrimary = animatedPrimary,
                onClose = onClose,
                onOpenSearchLyrics = onOpenSearchLyrics,
                onImportClick = {
                    importLyricsLauncher.launch(
                        arrayOf("text/*", "application/octet-stream", "*/*")
                    )
                },
                onEditClick = { showPasteDialog = true }
            )
            FullScreenLyricsContent(
                listState = listState,
                lyricsState = lyricsState,
                activeIndex = activeIndex,
                animatedPrimary = animatedPrimary,
                userScrolledManually = userScrolledManually,
                coroutineScope = coroutineScope,
                onSeekTo = onSeekTo,
                onOpenSearchLyrics = onOpenSearchLyrics,
                onImportClick = {
                    importLyricsLauncher.launch(
                        arrayOf("text/*", "application/octet-stream", "*/*")
                    )
                },
                onUserScrollConsumed = { userScrolledManually = false },
                onAutoScrollStateChange = { auto -> isAutoScrolling = auto }
            )
            FullScreenLyricsTransportBar(
                currentTrack = currentTrack,
                animatedPrimary = animatedPrimary,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                currentPositionFlow = currentPositionFlow,
                durationMs = durationMs,
                onClose = onClose,
                onSeekTo = onSeekTo,
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext,
                onPlayPrevious = onPlayPrevious
            )
        }
        if (showPasteDialog) {
            FullScreenLyricsPasteDialog(
                initialText = lyricsState.plainLyrics,
                onSaveLyrics = {
                    onSaveCustomLyrics(it)
                    showPasteDialog = false
                },
                onDismiss = { showPasteDialog = false }
            )
        }
    }
}

@Composable
private fun FullScreenLyricsReactiveBackground(
    animatedPrimary: Color,
    animatedSecondary: Color,
    fallbackIntensity: Float,
    audioIntensityFlow: StateFlow<Float>?
) {
    val collectedIntensity = audioIntensityFlow?.collectAsStateWithLifecycle()
    val liveIntensity = collectedIntensity?.value ?: fallbackIntensity
    val ambientGlow = animatedPrimary.copy(
        alpha = (0.30f + (liveIntensity.coerceIn(0f, 1f) * 0.35f)).coerceIn(0.20f, 0.70f)
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ambientGlow,
                        animatedSecondary.copy(alpha = 0.22f),
                        BackgroundDark.copy(alpha = 0.95f),
                        BackgroundDark
                    )
                )
            )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FullScreenLyricsSeekBarSection(
    fallbackPositionMs: Long,
    currentPositionFlow: StateFlow<Long>?,
    durationMs: Long,
    animatedPrimary: Color,
    onSeekTo: (Long) -> Unit
) {
    val collectedPosition = currentPositionFlow?.collectAsStateWithLifecycle()
    val livePositionMs = collectedPosition?.value ?: fallbackPositionMs
    val progressRatio = if (durationMs > 0L) {
        (livePositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Slider(
        value = progressRatio,
        onValueChange = { ratio ->
            val target = (ratio * durationMs).toLong()
            onSeekTo(target)
        },
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = animatedPrimary,
            inactiveTrackColor = Color.White.copy(alpha = 0.22f)
        ),
        thumb = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(animatedPrimary.copy(alpha = 0.35f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .shadow(elevation = 3.dp, shape = CircleShape)
                        .background(Color.White, CircleShape)
                )
            }
        },
        track = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.22f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressRatio.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(animatedPrimary)
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = formatPlaybackTime(livePositionMs),
            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp)
        )
        Text(
            text = formatPlaybackTime(durationMs),
            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp)
        )
    }
}

