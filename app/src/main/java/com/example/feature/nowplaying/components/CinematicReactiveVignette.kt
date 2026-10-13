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
 * Viñeta reactiva a la intensidad del audio.
 * Rol arquitectónico: pieza de NowPlayingCinematicLayout (fachada intacta).
 */
@Composable
internal fun CinematicReactiveVignette(
    animatedPrimary: Color,
    animatedSecondary: Color,
    fallbackIntensity: Float,
    audioIntensityFlow: StateFlow<Float>?
) {
    val collectedIntensity = audioIntensityFlow?.collectAsStateWithLifecycle()
    val liveIntensity = collectedIntensity?.value ?: fallbackIntensity
    val dynamicBottomGlow = animatedPrimary.copy(
        alpha = (0.28f + (liveIntensity.coerceIn(0f, 1f) * 0.24f)).coerceIn(0.18f, 0.60f)
    )
    val dynamicTopGlow = animatedSecondary.copy(alpha = 0.35f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        dynamicTopGlow,
                        Color.Black.copy(alpha = 0.40f),
                        Color.Transparent,
                        Color.Transparent,
                        dynamicBottomGlow,
                        animatedSecondary.copy(alpha = 0.35f),
                        BackgroundDark.copy(alpha = 0.92f),
                        BackgroundDark
                    ),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
    )
}
