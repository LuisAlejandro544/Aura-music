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
 * Controles de reproducción principales de Now Playing:
 * - Información de pista con desplazamiento automático tipo marquesina (Marquee) para títulos y artistas largos.
 * - Barra deslizadora interactiva (Seekbar) con formato de tiempo `mm:ss` e indicadores visuales de bucle A-B.
 * - Barra compacta de Repetidor de Segmento A-B ([A], [B], estado de bucle y botón de limpiar).
 * - Fila de botones de control (Shuffle, Skip Anterior, Play/Pause grande, Skip Siguiente, Repeat).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingPlaybackControls(
    currentTrack: Track,
    isPlaying: Boolean,
    currentPositionMs: Long = 0L,
    currentPositionFlow: StateFlow<Long>? = null,
    durationMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    animatedPrimary: Color,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    abLoopState: ABLoopState = ABLoopState(),
    onMarkABPointA: () -> Unit = {},
    onMarkABPointB: () -> Unit = {},
    onClearABLoop: () -> Unit = {},
    activeMixtapeChapter: com.example.core.model.MixtapeChapter? = null,
    mixtapeChapterIndex: Int = -1,
    mixtapeTotalChapters: Int = -1,
    isLoadingOrBuffering: Boolean = false,
    isWaitingForNetwork: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Título con Marquesina fluida, Artista, Etiqueta de formato y botón de Favorito
        NowPlayingTrackHeader(
            currentTrack = currentTrack,
            activeMixtapeChapter = activeMixtapeChapter,
            mixtapeChapterIndex = mixtapeChapterIndex,
            mixtapeTotalChapters = mixtapeTotalChapters,
            animatedPrimary = animatedPrimary,
            onToggleFavorite = onToggleFavorite
        )

        // Slider de Progreso, Indicador de Rango A-B y Tiempo aislados en su propio ámbito de composición
        NowPlayingSeekBarSection(
            fallbackPositionMs = currentPositionMs,
            currentPositionFlow = currentPositionFlow,
            durationMs = durationMs,
            animatedPrimary = animatedPrimary,
            onSeekTo = onSeekTo,
            abLoopState = abLoopState,
            onMarkABPointA = onMarkABPointA,
            onMarkABPointB = onMarkABPointB,
            onClearABLoop = onClearABLoop
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Botonera de Reproducción
        NowPlayingButtonsRow(
            shuffleEnabled = shuffleEnabled,
            animatedPrimary = animatedPrimary,
            isPlaying = isPlaying,
            isLoadingOrBuffering = isLoadingOrBuffering,
            isWaitingForNetwork = isWaitingForNetwork,
            repeatMode = repeatMode,
            onToggleShuffle = onToggleShuffle,
            onPlayPrevious = onPlayPrevious,
            onTogglePlayPause = onTogglePlayPause,
            onPlayNext = onPlayNext,
            onCycleRepeat = onCycleRepeat
        )
    }
}
