package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.AuraApplication
import com.example.model.AppWallpaperConfig
import com.example.model.WallpaperMediaType
import com.example.model.WallpaperScreenScope
import com.example.ui.navigation.NavScreen
import java.io.File

/**
 * Capa de Fondo de Pantalla Personalizado de Galería (Imagen WebP o Video corto MP4)
 * para las pantallas principales de Aura Music (Biblioteca, Inicio, Playlists, etc.).
 *
 * Características:
 * - Se activa según el alcance elegido por el usuario en Ajustes ([WallpaperScreenScope]).
 * - Soporta imágenes locales en WebP de alta nitidez y videos cortos en bucle continuo sin audio.
 * - Incluye control de desenfoque ajustable ([AppWallpaperConfig.blurRadiusDp]) y velo de
 *   oscurecimiento OLED ([AppWallpaperConfig.dimOverlayAlpha]) para que textos y tarjetas se lean siempre bien.
 * - Respeta automáticamente el Modo Juego en 2do Plano deteniendo el video cuando la app no es visible.
 */
@Composable
fun AppWallpaperBackground(
    config: AppWallpaperConfig,
    currentScreen: NavScreen,
    isNowPlayingExpanded: Boolean,
    isBackgroundGameModeEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!config.hasValidMedia || isNowPlayingExpanded || currentScreen is NavScreen.Onboarding) {
        return
    }

    val isVisibleInCurrentScreen = when (config.screenScope) {
        WallpaperScreenScope.LIBRARY_ONLY ->
            currentScreen is NavScreen.Library || currentScreen is NavScreen.PlaylistDetail
        WallpaperScreenScope.HOME_AND_LIBRARY ->
            currentScreen is NavScreen.Home || currentScreen is NavScreen.Library || currentScreen is NavScreen.PlaylistDetail
        WallpaperScreenScope.ALL_SCREENS ->
            true
    }

    if (!isVisibleInCurrentScreen) return

    val mediaFile = remember(config.mediaPath) {
        File(config.mediaPath)
    }
    if (!mediaFile.exists()) return

    val context = LocalContext.current
    val isAppInForeground by AuraApplication.isAppInForegroundFlow.collectAsStateWithLifecycle()
    val shouldPlayVideo = isAppInForeground || !isBackgroundGameModeEnabled
    val blurModifier = if (config.blurRadiusDp > 0) {
        Modifier.blur(config.blurRadiusDp.dp)
    } else {
        Modifier
    }

    Box(modifier = modifier.fillMaxSize()) {
        key(config.mediaType, config.mediaPath) {
            when (config.mediaType) {
                WallpaperMediaType.IMAGE -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(mediaFile)
                            .crossfade(300)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(blurModifier)
                    )
                }

                WallpaperMediaType.VIDEO -> {
                    BackgroundVideoPlayer(
                        videoUriString = mediaFile.absolutePath,
                        isVideoLoop = true,
                        isPlaying = shouldPlayVideo,
                        currentPositionMs = 0L,
                        playbackSpeed = 1.0f,
                        enableLiveColorSampling = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(blurModifier),
                        cornerRadius = 0.dp
                    )
                }

                WallpaperMediaType.NONE -> {}
            }
        }

        // Velo de oscurecimiento OLED configurable para garantizar legibilidad de textos y tarjetas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = config.dimOverlayAlpha.coerceIn(0.25f, 0.92f)))
        )
    }
}
