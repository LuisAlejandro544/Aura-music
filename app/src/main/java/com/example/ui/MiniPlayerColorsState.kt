package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import com.example.core.model.VideoDisplayMode
import com.example.ui.theme.ArtworkColorExtractor
import com.example.ui.theme.ExtractedArtworkColors
import com.example.viewmodel.MusicViewModel

/**
 * Colores dinámicos del MiniPlayer extraídos de carátula o Video Canvas.
 * Rol arquitectónico: concentra el muestreo cromático cada 1s para que la
 * fachada quede delgada; devuelve los colores ya animados.
 */
data class MiniPlayerColors(
    val animatedPrimary: Color,
    val animatedSecondary: Color
)

@Composable
fun rememberMiniPlayerColors(
    viewModel: MusicViewModel,
    effectiveTrack: Track?,
    isPlaying: Boolean,
    isNowPlayingExpanded: Boolean
): MiniPlayerColors {
    val context = LocalContext.current
    val videoDisplayMode by viewModel.videoDisplayMode.collectAsStateWithLifecycle()
    val isMiniPlayerVideoEnabled by viewModel.isMiniPlayerVideoEnabled.collectAsStateWithLifecycle()
    val isDynamicArtworkColorEnabled by viewModel.isDynamicArtworkColorEnabled.collectAsStateWithLifecycle()
    val isBackgroundGameModeEnabled by viewModel.isBackgroundGameModeEnabled.collectAsStateWithLifecycle()
    val isAppInForeground by com.example.AuraApplication.isAppInForegroundFlow.collectAsStateWithLifecycle()


        val defaultMiniPrimary = MaterialTheme.colorScheme.primary
        val defaultMiniSecondary = MaterialTheme.colorScheme.secondary
        var miniPlayerColors by remember {
            mutableStateOf(
                ExtractedArtworkColors(
                    primary = defaultMiniPrimary,
                    secondary = defaultMiniSecondary,
                    accent = defaultMiniPrimary,
                    ambientTopGlow = defaultMiniPrimary.copy(alpha = 0.25f)
                )
            )
        }

        val liveVideoSnapshot by ArtworkColorExtractor.liveVideoColorsFlow.collectAsStateWithLifecycle()
        var miniSampleIntervalMs by remember {
            mutableLongStateOf(ArtworkColorExtractor.getAdaptiveSampleIntervalMs(context))
        }

        val shouldRunLiveVisualSampling = isAppInForeground || !isBackgroundGameModeEnabled

        LaunchedEffect(
            effectiveTrack?.id,
            effectiveTrack?.albumArtPath,
            effectiveTrack?.videoUri,
            videoDisplayMode,
            isMiniPlayerVideoEnabled,
            isDynamicArtworkColorEnabled
        ) {
            val track = effectiveTrack
            if (track != null && isDynamicArtworkColorEnabled) {
                val isVideo = ((videoDisplayMode != VideoDisplayMode.OFF) || isMiniPlayerVideoEnabled) && !track.videoUri.isNullOrEmpty()
                miniSampleIntervalMs = ArtworkColorExtractor.getAdaptiveSampleIntervalMs(context)
                miniPlayerColors = ArtworkColorExtractor.extractPlaybackColors(
                    context = context,
                    track = track,
                    isVideoActive = isVideo && shouldRunLiveVisualSampling,
                    isDynamicEnabled = isDynamicArtworkColorEnabled,
                    fallbackPrimary = defaultMiniPrimary,
                    fallbackSecondary = defaultMiniSecondary,
                    positionMs = viewModel.currentPosition.value
                )
            } else {
                miniPlayerColors = ExtractedArtworkColors(
                    primary = defaultMiniPrimary,
                    secondary = defaultMiniSecondary,
                    accent = defaultMiniPrimary,
                    ambientTopGlow = defaultMiniPrimary.copy(alpha = 0.25f)
                )
            }
        }

        // Reacción en tiempo real en el MiniPlayer cuando el Video Canvas emite cambios de escena (cada 1 segundo)
        LaunchedEffect(
            liveVideoSnapshot,
            effectiveTrack?.videoUri,
            videoDisplayMode,
            isMiniPlayerVideoEnabled,
            isDynamicArtworkColorEnabled,
            shouldRunLiveVisualSampling
        ) {
            val track = effectiveTrack
            val isVideo = track != null && ((videoDisplayMode != VideoDisplayMode.OFF) || isMiniPlayerVideoEnabled) && !track.videoUri.isNullOrEmpty()
            if (isDynamicArtworkColorEnabled && isVideo && shouldRunLiveVisualSampling) {
                val snapshot = liveVideoSnapshot
                if (snapshot != null && snapshot.videoUri == track?.videoUri) {
                    miniSampleIntervalMs = snapshot.sampleIntervalMs
                    miniPlayerColors = snapshot.colors
                }
            }
        }

        // Respaldo puntual cuando el MiniPlayer tiene la miniatura de video oculta pero la canción tiene video activo:
        // una sola extracción por cambio de pista (sin bucle) para no duplicar el trabajo del sampler.
        LaunchedEffect(
            effectiveTrack?.id,
            effectiveTrack?.videoUri,
            isMiniPlayerVideoEnabled,
            videoDisplayMode,
            isDynamicArtworkColorEnabled
        ) {
            val track = effectiveTrack
            val hasVideo = track != null && !track.videoUri.isNullOrEmpty()
            val isVideoColorActive = hasVideo && (videoDisplayMode != VideoDisplayMode.OFF)
            val hasActiveTextureSampler = isNowPlayingExpanded || isMiniPlayerVideoEnabled
            if (track != null && isDynamicArtworkColorEnabled && isVideoColorActive && !hasActiveTextureSampler && isPlaying && shouldRunLiveVisualSampling) {
                miniSampleIntervalMs = ArtworkColorExtractor.getAdaptiveSampleIntervalMs(context)
                miniPlayerColors = ArtworkColorExtractor.extractPlaybackColors(
                    context = context,
                    track = track,
                    isVideoActive = true,
                    isDynamicEnabled = true,
                    fallbackPrimary = defaultMiniPrimary,
                    fallbackSecondary = defaultMiniSecondary,
                    positionMs = viewModel.currentPosition.value
                )
            }
        }

        val miniTransitionDurationMs = if (isDynamicArtworkColorEnabled && !effectiveTrack?.videoUri.isNullOrEmpty()) {
            minOf(if (miniSampleIntervalMs <= ArtworkColorExtractor.INTERVAL_NORMAL_MS) 1000 else 1400, 450)
        } else {
            600
        }

        val animatedMiniPrimary by animateColorAsState(
            targetValue = miniPlayerColors.primary,
            animationSpec = tween(miniTransitionDurationMs),
            label = "MiniPlayerPrimary"
        )
        val animatedMiniSecondary by animateColorAsState(
            targetValue = miniPlayerColors.secondary,
            animationSpec = tween(miniTransitionDurationMs),
            label = "MiniPlayerSecondary"
        )
    return MiniPlayerColors(
        animatedPrimary = animatedMiniPrimary,
        animatedSecondary = animatedMiniSecondary
    )
}
