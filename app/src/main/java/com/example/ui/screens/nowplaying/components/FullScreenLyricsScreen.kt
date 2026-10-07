package com.example.ui.screens.nowplaying.components

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
import com.example.model.LyricsState
import com.example.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Vista Inmersiva de Letras y Karaoke a Pantalla Completa (Full Screen Lyrics).
 * Experiencia de alta fidelidad estilo Spotify / Apple Music Sing:
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
    currentPositionMs: Long,
    durationMs: Long,
    animatedPrimary: Color,
    animatedSecondary: Color,
    audioIntensity: Float = 0.15f,
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
    var showPasteDialog by remember { mutableStateOf(false) }

    // Launcher del sistema para archivos .lrc / .txt
    val importLyricsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) onImportLyricsUri(uri)
    }

    // Índice de la frase activa
    val activeIndex = remember(currentPositionMs, lyricsState.lines) {
        if (lyricsState.lines.isEmpty()) -1
        else {
            val idx = lyricsState.lines.indexOfLast { it.timeMs <= currentPositionMs }
            if (idx >= 0) idx else 0
        }
    }

    // Seguimiento de scroll manual
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            userScrolledManually = true
        }
    }

    // Auto-scroll centrado a la frase actual si no hay desplazamiento manual
    LaunchedEffect(activeIndex, userScrolledManually) {
        if (!userScrolledManually && activeIndex >= 0 && lyricsState.lines.isNotEmpty()) {
            val targetScroll = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .pointerInput(Unit) { detectTapGestures {} }
            .testTag("full_screen_lyrics_screen")
    ) {
        // Fondo degradado dinámico inmersivo
        val ambientGlow = animatedPrimary.copy(
            alpha = (0.30f + (audioIntensity.coerceIn(0f, 1f) * 0.35f)).coerceIn(0.20f, 0.70f)
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp)
        ) {
            // Barra superior flotante
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("full_screen_lyrics_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Cerrar modo karaoke",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = animatedPrimary.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, animatedPrimary.copy(alpha = 0.45f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = animatedPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "KARAOKE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                                color = animatedPrimary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenSearchLyrics,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar versión de letras",
                            tint = animatedPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            importLyricsLauncher.launch(
                                arrayOf("text/*", "application/octet-stream", "*/*")
                            )
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Importar archivo local",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { showPasteDialog = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar letra",
                            tint = TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // Contenedor principal de letras
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    lyricsState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = animatedPrimary, modifier = Modifier.size(42.dp))
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Sincronizando letras para karaoke...",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                                )
                            }
                        }
                    }

                    lyricsState.lines.isNotEmpty() -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(top = 28.dp, bottom = 120.dp)
                        ) {
                            itemsIndexed(lyricsState.lines) { index, line ->
                                val isCurrent = (index == activeIndex)
                                val textScale by animateFloatAsState(
                                    targetValue = if (isCurrent) 1.05f else 1.0f,
                                    animationSpec = tween(280),
                                    label = "LyricsLineScale"
                                )

                                val lineAlpha by animateFloatAsState(
                                    targetValue = when {
                                        isCurrent -> 1.0f
                                        index < activeIndex -> 0.35f
                                        else -> 0.65f
                                    },
                                    animationSpec = tween(280),
                                    label = "LyricsLineAlpha"
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSeekTo(line.timeMs) }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = line.text,
                                        style = if (isCurrent) {
                                            MaterialTheme.typography.headlineSmall.copy(
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                lineHeight = 32.sp,
                                                color = animatedPrimary
                                            )
                                        } else {
                                            MaterialTheme.typography.titleLarge.copy(
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                lineHeight = 28.sp,
                                                color = Color.White.copy(alpha = lineAlpha)
                                            )
                                        },
                                        modifier = Modifier.scale(textScale)
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        // Vista vacía cuando no hay letras disponibles
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = animatedPrimary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No hay letras disponibles para esta canción",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        textAlign = TextAlign.Center
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Busca en la base de datos libre LRCLIB o importa tu archivo .LRC",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = onOpenSearchLyrics,
                                        colors = ButtonDefaults.buttonColors(containerColor = animatedPrimary)
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Buscar Letras")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            importLyricsLauncher.launch(
                                                arrayOf("text/*", "application/octet-stream", "*/*")
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Importar .LRC")
                                    }
                                }
                            }
                        }
                    }
                }

                // Botón flotante "Volver a la canción" cuando el usuario hace scroll manual
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = userScrolledManually && activeIndex >= 0 && lyricsState.lines.isNotEmpty(),
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(200))
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = animatedPrimary,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .clickable {
                                    userScrolledManually = false
                                    coroutineScope.launch {
                                        val targetScroll = (activeIndex - 2).coerceAtLeast(0)
                                        listState.animateScrollToItem(targetScroll)
                                    }
                                }
                                .testTag("full_screen_lyrics_sync_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Sincronizar con audio",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Barra de transporte flotante inferior
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF131722).copy(alpha = 0.94f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                shadowElevation = 14.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("full_screen_lyrics_player_bar")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Fila de metadatos y botón de salir
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArtworkImage(
                            track = currentTrack,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clipToBounds()
                        ) {
                            Text(
                                text = currentTrack?.title ?: "Sin reproducción",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                ),
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clipToBounds()
                                    .basicMarquee()
                            )
                            Text(
                                text = currentTrack?.artist ?: "Aura Music",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "Salir de pantalla completa",
                                tint = animatedPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Seekbar / Slider de progreso compacto con bolita clásica
                    val progressRatio = if (durationMs > 0) {
                        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
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
                            inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                        ),
                        thumb = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(animatedPrimary.copy(alpha = 0.40f), CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(13.dp)
                                        .shadow(elevation = 3.dp, shape = CircleShape)
                                        .background(Color.White, CircleShape)
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
                            text = formatPlaybackTime(currentPositionMs),
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp)
                        )
                        Text(
                            text = formatPlaybackTime(durationMs),
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp)
                        )
                    }

                    // Controles de transporte
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onPlayPrevious,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Pista anterior",
                                tint = TextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(18.dp))

                        Surface(
                            shape = CircleShape,
                            color = animatedPrimary,
                            modifier = Modifier
                                .size(50.dp)
                                .clickable { onTogglePlayPause() },
                            shadowElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                    tint = Color.Black,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(18.dp))

                        IconButton(
                            onClick = onPlayNext,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Pista siguiente",
                                tint = TextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }

        // Diálogo para ingresar o editar letra manualmente
        if (showPasteDialog) {
            var rawText by remember { mutableStateOf(lyricsState.plainLyrics) }
            AlertDialog(
                onDismissRequest = { showPasteDialog = false },
                title = { Text("Editar o Pegar Letra") },
                text = {
                    Column {
                        Text(
                            text = "Pega aquí la letra sincronizada (.LRC) o texto plano:",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = rawText,
                            onValueChange = { rawText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            placeholder = { Text("[00:12.50] Tu letra aquí...") }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onSaveCustomLyrics(rawText)
                            showPasteDialog = false
                        }
                    ) {
                        Text("Guardar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasteDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
