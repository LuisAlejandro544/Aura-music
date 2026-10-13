package com.example.feature.nowplaying.components
import com.example.core.model.RepeatMode

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ABLoopState
import com.example.core.model.LyricsState
import com.example.core.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
/**
 * Seekbar cinemática con tiempos y bucle A-B.
 * Rol arquitectónico: pieza de NowPlayingCinematicLayout (fachada intacta).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CinematicSeekBarSection(
    fallbackPositionMs: Long,
    currentPositionFlow: StateFlow<Long>?,
    durationMs: Long,
    animatedPrimary: Color,
    onSeekTo: (Long) -> Unit
) {
    val collectedPosition = currentPositionFlow?.collectAsStateWithLifecycle()
    val livePositionMs = collectedPosition?.value ?: fallbackPositionMs

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragValue by remember { mutableFloatStateOf(0f) }

    val safeDuration = if (durationMs > 0L) durationMs.toFloat() else 1f
    val currentProgress = if (isDraggingSlider) {
        sliderDragValue
    } else {
        (livePositionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    }

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
            activeTrackColor = animatedPrimary,
            inactiveTrackColor = Color.White.copy(alpha = 0.24f)
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
                    .background(Color.White.copy(alpha = 0.24f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(currentProgress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(animatedPrimary)
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .testTag("cinematic_progress_slider")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val displayPositionMs = if (isDraggingSlider) {
            (sliderDragValue * safeDuration).toLong()
        } else {
            livePositionMs
        }
        Text(
            text = formatCinematicTimeMs(displayPositionMs),
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Text(
            text = formatCinematicTimeMs(durationMs),
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}


private fun formatCinematicTimeMs(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
