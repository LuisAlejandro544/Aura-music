package com.example.feature.nowplaying.components
import com.example.core.model.RepeatMode

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import com.example.core.model.ABLoopState
import com.example.core.model.Track
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
/**
 * Barra de progreso con flujo de posición y marcas de bucle A-B.
 * Rol arquitectónico: pieza de NowPlayingPlaybackControls (fachada pública intacta).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NowPlayingSeekBarSection(
    fallbackPositionMs: Long,
    currentPositionFlow: StateFlow<Long>?,
    durationMs: Long,
    animatedPrimary: Color,
    onSeekTo: (Long) -> Unit,
    abLoopState: ABLoopState,
    onMarkABPointA: () -> Unit,
    onMarkABPointB: () -> Unit,
    onClearABLoop: () -> Unit
) {
    val collectedPosition = currentPositionFlow?.collectAsStateWithLifecycle()
    val livePositionMs = collectedPosition?.value ?: fallbackPositionMs

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPosition by remember { mutableFloatStateOf(0f) }

    val safeDuration = durationMs.coerceAtLeast(1L)
    val sliderValue = if (isDraggingSlider) {
        sliderDragPosition
    } else {
        (livePositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }

    val displayPositionMs = if (isDraggingSlider) {
        (sliderDragPosition * safeDuration).toLong()
    } else {
        livePositionMs
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (abLoopState.pointAMs != null) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .height(6.dp)
                ) {
                    val startFraction = (abLoopState.pointAMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                    val endFraction = ((abLoopState.pointBMs ?: livePositionMs).toFloat() / safeDuration.toFloat())
                        .coerceIn(startFraction, 1f)
                    val startOffset = maxWidth * startFraction
                    val segmentWidth = (maxWidth * (endFraction - startFraction)).coerceAtLeast(4.dp)

                    Box(
                        modifier = Modifier
                            .offset(x = startOffset)
                            .width(segmentWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (abLoopState.isLoopingActive) {
                                    Color(0xFF10B981).copy(alpha = 0.78f)
                                } else {
                                    animatedPrimary.copy(alpha = 0.55f)
                                }
                            )
                    )
                }
            }

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
                thumb = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(animatedPrimary.copy(alpha = 0.35f), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(13.dp)
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
                                .fillMaxWidth(sliderValue.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(animatedPrimary)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("now_playing_slider")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatPlaybackTime(displayPositionMs),
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
            )

            CompactABLoopBar(
                abLoopState = abLoopState,
                animatedPrimary = animatedPrimary,
                onMarkA = onMarkABPointA,
                onMarkB = onMarkABPointB,
                onClear = onClearABLoop
            )

            Text(
                text = formatPlaybackTime(durationMs),
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
            )
        }
    }
}

/**
 * Barra compacta del Repetidor de Segmento A-B situada junto al indicador de progreso.
 * Permite marcar [A], marcar [B] y limpiar el bucle con un solo toque sin saturar la pantalla.
 */
@Composable
internal fun CompactABLoopBar(
    abLoopState: ABLoopState,
    animatedPrimary: Color,
    onMarkA: () -> Unit,
    onMarkB: () -> Unit,
    onClear: () -> Unit
) {
    val hasA = abLoopState.pointAMs != null
    val hasB = abLoopState.pointBMs != null
    val isActive = abLoopState.isLoopingActive
    val accentGreen = Color(0xFF10B981)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.testTag("now_playing_ab_loop_bar")
    ) {
        // Botón Punto A
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (hasA) animatedPrimary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 30.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = if (hasA) animatedPrimary else Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onMarkA)
                .testTag("ab_loop_set_a_btn")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (hasA) "A ${formatPlaybackTime(abLoopState.pointAMs ?: 0L)}" else "A",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (hasA) animatedPrimary else TextSecondary,
                        fontSize = 11.sp
                    )
                )
            }
        }

        // Botón Punto B
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = when {
                isActive -> accentGreen.copy(alpha = 0.22f)
                hasB -> animatedPrimary.copy(alpha = 0.22f)
                else -> Color.White.copy(alpha = 0.06f)
            },
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 30.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = when {
                        isActive -> accentGreen
                        hasB -> animatedPrimary
                        else -> Color.White.copy(alpha = 0.14f)
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onMarkB)
                .testTag("ab_loop_set_b_btn")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (hasB) "B ${formatPlaybackTime(abLoopState.pointBMs ?: 0L)}" else "B",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isActive -> accentGreen
                            hasB -> animatedPrimary
                            else -> TextSecondary
                        },
                        fontSize = 11.sp
                    )
                )
            }
        }

        // Botón limpiar A-B cuando hay algún punto marcado
        if (hasA || hasB) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.10f),
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClear)
                    .testTag("ab_loop_clear_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Limpiar bucle A-B",
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Formatea milisegundos en formato de tiempo mm:ss o hh:mm:ss.
 */
fun formatPlaybackTime(ms: Long): String {
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
