package com.example.feature.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.Track
import com.example.core.model.NowPlayingDesignMode
import com.example.core.model.VideoDisplayMode
import com.example.ui.components.BackgroundVideoLoadingState
import com.example.ui.theme.ArtworkColorExtractor
import com.example.ui.theme.ExtractedArtworkColors
import kotlinx.coroutines.flow.StateFlow

/**
 * Estado visual reactivo de Now Playing (colores dinámicos y banderas de video).
 * Rol arquitectónico: concentra LaunchedEffect de extracción cromática y animaciones
 * para que la fachada NowPlayingScreen quede como orquestador delgado.
 */
data class NowPlayingVisualState(
    val animatedPrimary: Color,
    val animatedSecondary: Color,
    val animatedTopGlow: Color,
    val isVideoVisual: Boolean,
    val isRemoteVideo: Boolean,
    val effectiveVideoLoading: Boolean,
    val effectiveWaitingNetwork: Boolean,
    val isFullscreenVideo: Boolean,
    val hasVideo: Boolean
)

/**
 * Determina si el diseño cinemático Canvas está activo (forzado o automático).
 */
fun isCinematicCanvasActive(
    designMode: NowPlayingDesignMode,
    isFullscreenVideo: Boolean,
    videoUri: String?
): Boolean = when (designMode) {
    NowPlayingDesignMode.CINEMATIC_CANVAS -> true
    NowPlayingDesignMode.CLASSIC -> false
    NowPlayingDesignMode.AUTO -> isFullscreenVideo && !videoUri.isNullOrEmpty()
}

@Composable
fun rememberNowPlayingVisualState(
    track: Track,
    videoDisplayMode: VideoDisplayMode,
    isDynamicEnabled: Boolean,
    currentPositionMs: Long,
    currentPositionFlow: StateFlow<Long>?,
    isWaitingForNetwork: Boolean
): NowPlayingVisualState {
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

        val isVideoVisual = (videoDisplayMode != VideoDisplayMode.OFF) && !track.videoUri.isNullOrEmpty()
        val isRemoteVideo = isVideoVisual && (
            track.videoUri.orEmpty().startsWith("http://", ignoreCase = true) ||
                track.videoUri.orEmpty().startsWith("https://", ignoreCase = true)
            )
        val liveVideoSnapshot by ArtworkColorExtractor.liveVideoColorsFlow.collectAsStateWithLifecycle()
        val isVideoBufferingLive by BackgroundVideoLoadingState.isVideoBufferingFlow.collectAsStateWithLifecycle()
        val isVideoNetworkStalledLive by BackgroundVideoLoadingState.isVideoNetworkStalledFlow.collectAsStateWithLifecycle()
        val effectiveVideoLoading = track.isStreamingTrack && isRemoteVideo && isVideoBufferingLive
        val effectiveWaitingNetwork = track.isStreamingTrack && (isWaitingForNetwork || (isRemoteVideo && isVideoNetworkStalledLive))
        var activeSampleIntervalMs by remember {
            mutableLongStateOf(ArtworkColorExtractor.getAdaptiveSampleIntervalMs(context))
        }

        // Extracción inicial y respaldo reactivo cuando cambia la pista, modo de video o preferencia
        LaunchedEffect(
            track.id,
            track.albumArtPath,
            track.videoUri,
            videoDisplayMode,
            isDynamicEnabled
        ) {
            activeSampleIntervalMs = ArtworkColorExtractor.getAdaptiveSampleIntervalMs(context)
            activeColors = ArtworkColorExtractor.extractPlaybackColors(
                context = context,
                track = track,
                isVideoActive = isVideoVisual,
                isDynamicEnabled = isDynamicEnabled,
                fallbackPrimary = fallbackPrimary,
                fallbackSecondary = fallbackSecondary,
                positionMs = currentPositionFlow?.value ?: currentPositionMs
            )
        }

        // Recibir instantáneamente cada cambio de paleta emitido en tiempo real por el TextureView del video
        LaunchedEffect(liveVideoSnapshot, isVideoVisual, isDynamicEnabled, track.videoUri) {
            if (isDynamicEnabled && isVideoVisual) {
                val snapshot = liveVideoSnapshot
                if (snapshot != null && snapshot.videoUri == track.videoUri) {
                    activeSampleIntervalMs = snapshot.sampleIntervalMs
                    activeColors = snapshot.colors
                }
            }
        }

        val colorTransitionDurationMs = if (isVideoVisual && isDynamicEnabled) {
            if (activeSampleIntervalMs <= ArtworkColorExtractor.INTERVAL_NORMAL_MS) 1000 else 1400
        } else {
            600
        }

        val animatedPrimary by animateColorAsState(
            targetValue = activeColors.primary,
            animationSpec = tween(colorTransitionDurationMs),
            label = "PrimaryAuraColor"
        )
        val animatedSecondary by animateColorAsState(
            targetValue = activeColors.secondary,
            animationSpec = tween(colorTransitionDurationMs),
            label = "SecondaryAuraColor"
        )
        val animatedTopGlow by animateColorAsState(
            targetValue = activeColors.ambientTopGlow,
            animationSpec = tween(colorTransitionDurationMs),
            label = "TopAuraGlow"
        )
    val hasVideo = !track.videoUri.isNullOrEmpty()
    val isFullscreenVideo = hasVideo && videoDisplayMode.isFullscreen
    return NowPlayingVisualState(
        animatedPrimary = animatedPrimary,
        animatedSecondary = animatedSecondary,
        animatedTopGlow = animatedTopGlow,
        isVideoVisual = isVideoVisual,
        isRemoteVideo = isRemoteVideo,
        effectiveVideoLoading = effectiveVideoLoading,
        effectiveWaitingNetwork = effectiveWaitingNetwork,
        isFullscreenVideo = isFullscreenVideo,
        hasVideo = hasVideo
    )
}
