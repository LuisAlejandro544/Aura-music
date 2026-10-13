package com.example.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import com.example.core.model.AppWallpaperConfig
import com.example.core.model.AuraTheme
import com.example.core.model.HeadphoneConfig
import com.example.core.model.HeadsetButtonAction
import com.example.core.model.NowPlayingDesignMode
import com.example.core.model.Playlist
import com.example.core.model.Track
import com.example.core.model.WallpaperScreenScope
import com.example.core.model.WidgetConfig
import com.example.core.model.WidgetGridContentMode
import com.example.feature.settings.components.*
import com.example.feature.settings.components.about.behindTheProjectSettingsContent
import com.example.feature.settings.components.appearance.appWallpaperSettingsSection
import com.example.feature.settings.components.widgets.widgetSettingsContent
import com.example.feature.settings.components.ytdlp.ytDlpCookiesAndPoTokenSettingsContent
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import androidx.compose.ui.graphics.Color

/**
 * Menú Aura Dinámica, Streaming & Modo Juego (4 tarjetas).
 * Rol arquitectónico: pantalla intermedia hacia las 4 sub-pantallas.
 */
@Composable
fun SettingsVisualBehaviorMenuScreen(
    isDynamicArtworkColorEnabled: Boolean,
    isMiniPlayerVideoEnabled: Boolean,
    isBackgroundGameModeEnabled: Boolean,
    appWallpaperConfig: AppWallpaperConfig,
    streamingCacheConfig: com.example.core.model.StreamingCacheConfig,
    onNavigate: (SettingsSubScreen) -> Unit,
    onBack: () -> Unit
) {
                    SettingsSubMenuScreen(
                        title = "Aura Dinámica, Streaming & Modo Juego",
                        subtitle = "Elige una categoría para configurar el streaming, colores dinámicos, modo juego o fondo",
                        icon = Icons.Default.Videocam,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onBack = onBack,
                        testTag = "subscreen_visual_behavior"
                    ) {
                        visualBehaviorMenuSection(
                            isDynamicArtworkColorEnabled = isDynamicArtworkColorEnabled,
                            isMiniPlayerVideoEnabled = isMiniPlayerVideoEnabled,
                            isBackgroundGameModeEnabled = isBackgroundGameModeEnabled,
                            appWallpaperConfig = appWallpaperConfig,
                            streamingCacheConfig = streamingCacheConfig,
                            onOpenStreamingAndCache = { onNavigate(SettingsSubScreen.VISUAL_STREAMING_AND_CACHE) },
                            onOpenAuraAndMiniPlayer = { onNavigate(SettingsSubScreen.VISUAL_AURA_AND_MINIPLAYER) },
                            onOpenGameMode = { onNavigate(SettingsSubScreen.VISUAL_GAME_MODE) },
                            onOpenGalleryWallpaper = { onNavigate(SettingsSubScreen.VISUAL_GALLERY_WALLPAPER) }
                        )
                    }
}

/**
 * Sub-pantalla Streaming & Caché Inteligente.
 * Rol arquitectónico: pantalla independiente con retroceso.
 */
@Composable
fun SettingsStreamingCacheScreen(
    streamingCacheConfig: com.example.core.model.StreamingCacheConfig,
    onSelectStreamingVideoPolicy: (com.example.core.model.StreamingVideoNetworkPolicy) -> Unit,
    onChangeStreamingCacheMaxMb: (Int) -> Unit,
    onClearStreamingCache: () -> Unit,
    onBack: () -> Unit
) {
                    SettingsSubMenuScreen(
                        title = "Streaming & Caché Inteligente",
                        subtitle = "Videos Canvas en Streaming (Wi-Fi/Datos) y caché temporal de 50 a 500 MB",
                        icon = Icons.Default.CloudQueue,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onBack = onBack,
                        testTag = "subscreen_visual_streaming_cache"
                    ) {
                        item {
                            com.example.feature.settings.components.appearance.StreamingCacheAndVideoSettingsCard(
                                cacheConfig = streamingCacheConfig,
                                onSelectVideoPolicy = onSelectStreamingVideoPolicy,
                                onChangeMaxCacheMb = onChangeStreamingCacheMaxMb,
                                onClearStreamingCache = onClearStreamingCache
                            )
                        }
                    }
}

/**
 * Sub-pantalla Aura Dinámica & Mini Reproductor.
 * Rol arquitectónico: pantalla independiente con retroceso.
 */
@Composable
fun SettingsAuraMiniPlayerScreen(
    isDynamicArtworkColorEnabled: Boolean,
    onToggleDynamicArtworkColor: (Boolean) -> Unit,
    isMiniPlayerVideoEnabled: Boolean,
    onToggleMiniPlayerVideo: (Boolean) -> Unit,
    onBack: () -> Unit
) {
                    SettingsSubMenuScreen(
                        title = "Aura Dinámica & Mini Reproductor",
                        subtitle = "Armonización de color cada 1 segundo y Video Canvas en el Mini Reproductor",
                        icon = Icons.Default.Palette,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onBack = onBack,
                        testTag = "subscreen_visual_aura_miniplayer"
                    ) {
                        auraAndMiniPlayerSettingsSection(
                            isDynamicArtworkColorEnabled = isDynamicArtworkColorEnabled,
                            onToggleDynamicArtworkColor = onToggleDynamicArtworkColor,
                            isMiniPlayerVideoEnabled = isMiniPlayerVideoEnabled,
                            onToggleMiniPlayerVideo = onToggleMiniPlayerVideo
                        )
                    }
}

/**
 * Sub-pantalla Modo Juego / Ahorro en 2do Plano.
 * Rol arquitectónico: pantalla independiente con retroceso.
 */
@Composable
fun SettingsGameModeScreen(
    isBackgroundGameModeEnabled: Boolean,
    onToggleBackgroundGameMode: (Boolean) -> Unit,
    onBack: () -> Unit
) {
                    SettingsSubMenuScreen(
                        title = "Modo Juego / Ahorro en 2do Plano",
                        subtitle = "Libera GPU, texturas y visualizador al minimizar la app mientras juegas",
                        icon = Icons.Default.SportsEsports,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onBack = onBack,
                        testTag = "subscreen_visual_game_mode"
                    ) {
                        backgroundGameModeSettingsSection(
                            isBackgroundGameModeEnabled = isBackgroundGameModeEnabled,
                            onToggleBackgroundGameMode = onToggleBackgroundGameMode
                        )
                    }
}

/**
 * Sub-pantalla Fondo de Pantalla de Galería.
 * Rol arquitectónico: pantalla independiente con retroceso.
 */
@Composable
fun SettingsGalleryWallpaperScreen(
    appWallpaperConfig: AppWallpaperConfig,
    onToggleWallpaperEnabled: (Boolean) -> Unit,
    onSelectWallpaperImage: (Uri) -> Unit,
    onSelectWallpaperVideo: (Uri) -> Unit,
    onClearWallpaper: () -> Unit,
    onSelectWallpaperScope: (WallpaperScreenScope) -> Unit,
    onChangeDimAlpha: (Float) -> Unit,
    onChangeBlurDp: (Int) -> Unit,
    onBack: () -> Unit
) {
                    SettingsSubMenuScreen(
                        title = "Fondo de Pantalla de Galería",
                        subtitle = "Personaliza el fondo con una foto (WebP) o video corto (MP4), opacidad y blur",
                        icon = Icons.Default.PermMedia,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onBack = onBack,
                        testTag = "subscreen_visual_gallery_wallpaper"
                    ) {
                        appWallpaperSettingsSection(
                            wallpaperConfig = appWallpaperConfig,
                            onToggleWallpaperEnabled = onToggleWallpaperEnabled,
                            onSelectWallpaperImage = onSelectWallpaperImage,
                            onSelectWallpaperVideo = onSelectWallpaperVideo,
                            onClearWallpaper = onClearWallpaper,
                            onSelectWallpaperScope = onSelectWallpaperScope,
                            onChangeDimAlpha = onChangeDimAlpha,
                            onChangeBlurDp = onChangeBlurDp
                        )
                    }
}
