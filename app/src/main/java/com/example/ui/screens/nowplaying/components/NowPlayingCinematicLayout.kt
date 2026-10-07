package com.example.ui.screens.nowplaying.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ABLoopState
import com.example.model.LyricsState
import com.example.model.RepeatMode
import com.example.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

/**
 * Diseño Cinemático Inmersivo de Now Playing (Estilo Canvas).
 *
 * Características:
 * - Área visual completa y despejada para el Video Canvas en movimiento.
 * - Barra superior compacta con indicador de procedencia ("Reproduciendo desde...").
 * - Frase lírica en vivo flotante sobre el video al compás de la música.
 * - Tercio inferior con degradado ergonómico de alta legibilidad:
 *   - Miniatura cuadrada de carátula oficial (54dp) con esquinas redondeadas.
 *   - Título con marquesina fluida y nombre del artista.
 *   - Botón de Me Gusta (corazón) interactivo.
 *   - Barra de progreso delgada interactiva con tiempos precisos.
 *   - Botonera principal con Play/Pausa circular blanco de 64dp.
 *   - Fila de utilidades inferiores (EQ FX, selector de diseño, cola de reproducción).
 *   - Píldora inferior "Vista previa de la letra" para acceso directo al Karaoke.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingCinematicLayout(
    currentTrack: Track,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    lyricsState: LyricsState,
    animatedPrimary: Color,
    animatedSecondary: Color,
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
    collectionContextTitle: String = "Tu Biblioteca",
    modifier: Modifier = Modifier
) {
    // Buscar la línea lírica actual sincronizada para mostrarla flotando
    val activeLyricLine = remember(lyricsState.lines, currentPositionMs) {
        if (lyricsState.isSynced && lyricsState.lines.isNotEmpty()) {
            lyricsState.lines.lastOrNull { it.timeMs <= currentPositionMs }?.text
        } else {
            null
        }
    }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragValue by remember { mutableFloatStateOf(0f) }

    val safeDuration = if (durationMs > 0L) durationMs.toFloat() else 1f
    val currentProgress = if (isDraggingSlider) {
        sliderDragValue
    } else {
        (currentPositionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("now_playing_cinematic_layout")
    ) {
        // Degradado oscuro superior e inferior para garantizar 100% de contraste
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.40f),
                            Color.Black.copy(alpha = 0.88f),
                            BackgroundDark.copy(alpha = 0.98f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Barra Superior Cinemática
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("cinematic_collapse_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Ocultar reproductor",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "REPRODUCIENDO DESDE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = collectionContextTitle.ifBlank { currentTrack.album },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenDesignSelector,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("cinematic_design_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DashboardCustomize,
                            contentDescription = "Cambiar diseño del reproductor",
                            tint = animatedPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenDetails,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("cinematic_more_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opciones y ficha técnica",
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // 2. Área Central Despejada para el Video Canvas
            Spacer(modifier = Modifier.weight(1f))

            // Frase de letra flotante en vivo sobre el video (Estilo Spotify Canvas)
            if (!activeLyricLine.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.50f),
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpenLyrics() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = animatedPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activeLyricLine,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 3. Fila de Información de Pista (Miniatura + Título/Artista + Favorito)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Miniatura cuadrada de carátula oficial de 54dp
                ArtworkImage(
                    track = currentTrack,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .shadow(4.dp, RoundedCornerShape(10.dp))
                        .testTag("cinematic_mini_artwork"),
                    cornerRadius = 10.dp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        ),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentTrack.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Botón de Favorito
                IconButton(
                    onClick = { onToggleFavorite(currentTrack) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("cinematic_favorite_btn")
                ) {
                    Icon(
                        imageVector = if (currentTrack.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (currentTrack.isFavorite) "Quitar de favoritos" else "Guardar en favoritos",
                        tint = if (currentTrack.isFavorite) Color(0xFFEF4444) else TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 4. Barra de Progreso (Seekbar Delgada)
            Slider(
                value = currentProgress,
                onValueChange = { newVal ->
                    isDraggingSlider = true
                    sliderDragValue = newVal
                },
                onValueChangeFinished = {
                    val targetMs = (sliderDragValue * safeDuration).toLong()
                    onSeekTo(targetMs)
                    isDraggingSlider = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .testTag("cinematic_progress_slider")
            )

            // Tiempos numéricos (0:05 / 3:49)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val displayPositionMs = if (isDraggingSlider) {
                    (sliderDragValue * safeDuration).toLong()
                } else {
                    currentPositionMs
                }
                Text(
                    text = formatTimeMs(displayPositionMs),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = formatTimeMs(durationMs),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Controles Principales de Reproducción
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Aleatorio (Shuffle)
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("cinematic_shuffle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Modo aleatorio",
                        tint = if (shuffleEnabled) animatedPrimary else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Canción Anterior
                IconButton(
                    onClick = onPlayPrevious,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("cinematic_prev_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Canción anterior",
                        tint = TextPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Botón Play / Pausa Central Circular Blanco de 64dp
                Surface(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .testTag("cinematic_play_pause_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = Color.Black,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                // Canción Siguiente
                IconButton(
                    onClick = onPlayNext,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("cinematic_next_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Siguiente canción",
                        tint = TextPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Repetir
                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("cinematic_repeat_btn")
                ) {
                    Icon(
                        imageVector = when (repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            RepeatMode.ALL -> Icons.Default.Repeat
                            RepeatMode.OFF -> Icons.Default.Repeat
                        },
                        contentDescription = "Modo de repetición",
                        tint = if (repeatMode != RepeatMode.OFF) animatedPrimary else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 6. Fila de Utilidades Inferiores
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ecualizador y Efectos
                IconButton(
                    onClick = onOpenEffects,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("cinematic_effects_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Efectos de audio y ecualizador",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Selector de Diseño del Reproductor
                IconButton(
                    onClick = onOpenDesignSelector,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("cinematic_layout_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewCarousel,
                        contentDescription = "Estilo de reproductor",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Letras Sincronizadas
                IconButton(
                    onClick = onOpenLyrics,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("cinematic_lyrics_icon_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Letras sincronizadas",
                        tint = if (lyricsState.isSynced) animatedPrimary else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Cola de Reproducción
                IconButton(
                    onClick = onOpenQueue,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("cinematic_queue_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Cola de reproducción",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 7. Tarjeta Deslizable / Acceso Inferior a "Vista previa de la letra"
            Surface(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .clickable { onOpenFullScreenLyrics() }
                    .testTag("cinematic_lyrics_sheet_preview")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.70f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Vista previa de la letra",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }
                    Text(
                        text = "KARAOKE SING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = animatedPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }
    }
}

private fun formatTimeMs(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
