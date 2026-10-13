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
 * Píldora flotante con la frase lírica activa.
 * Rol arquitectónico: pieza de NowPlayingCinematicLayout (fachada intacta).
 */
@Composable
internal fun CinematicFloatingLyricPill(
    lyricsState: LyricsState,
    fallbackPositionMs: Long,
    currentPositionFlow: StateFlow<Long>?,
    animatedPrimary: Color,
    onOpenFullScreenLyrics: () -> Unit
) {
    val collectedPosition = currentPositionFlow?.collectAsStateWithLifecycle()
    val activeLyricLine by remember(lyricsState.lines, lyricsState.isSynced, collectedPosition, fallbackPositionMs) {
        derivedStateOf {
            if (lyricsState.isSynced && lyricsState.lines.isNotEmpty()) {
                val posMs = collectedPosition?.value ?: fallbackPositionMs
                val idx = findActiveLyricIndexBinary(lyricsState.lines, posMs)
                if (idx >= 0 && lyricsState.lines[idx].timeMs <= posMs) {
                    lyricsState.lines[idx].text
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    if (!activeLyricLine.isNullOrBlank()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.55f),
            border = BorderStroke(1.2.dp, animatedPrimary.copy(alpha = 0.65f)),
            shadowElevation = 4.dp,
            modifier = Modifier
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onOpenFullScreenLyrics() }
                .testTag("cinematic_floating_lyric_pill")
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
                    text = activeLyricLine ?: "",
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
}
