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
 * Menú Aura Dinámica, Streaming & Modo Juego (6 tarjetas).
 * Rol arquitectónico: pantalla intermedia hacia las 6 sub-pantallas.
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
                            onOpenAudioQuality = { onNavigate(SettingsSubScreen.VISUAL_AUDIO_QUALITY) },
                            onOpenDataSaver = { onNavigate(SettingsSubScreen.VISUAL_DATA_SAVER) },
                            onOpenAuraAndMiniPlayer = { onNavigate(SettingsSubScreen.VISUAL_AURA_AND_MINIPLAYER) },
                            onOpenGameMode = { onNavigate(SettingsSubScreen.VISUAL_GAME_MODE) },
                            onOpenGalleryWallpaper = { onNavigate(SettingsSubScreen.VISUAL_GALLERY_WALLPAPER) }
                        )
                    }
}

/**
 * Sub-pantalla Streaming & Caché Inteligente (solo Video Canvas + Caché).
 * Rol arquitectónico: pantalla independiente con retroceso. Calidad y Ahorro
 * viven en sus propias pantallas para no amontonar controles.
 */
@Composable
fun SettingsStreamingCacheScreen(
    streamingCacheConfig: com.example.core.model.StreamingCacheConfig,
    onSelectStreamingVideoPolicy: (com.example.core.model.StreamingVideoNetworkPolicy) -> Unit,
    onChangeStreamingCacheMaxMb: (Int) -> Unit,
    onClearStreamingCache: () -> Unit,
    onSelectStreamingAudioQuality: (com.example.core.model.StreamingAudioQuality) -> Unit = {},
    onToggleStreamingDataSaver: (Boolean) -> Unit = {},
    onBack: () -> Unit
) {
                    SettingsSubMenuScreen(
                        title = "Streaming & Caché Inteligente",
                        subtitle = "Video Canvas en Explorar y caché temporal de 50 a 500 MB",
                        icon = Icons.Default.CloudQueue,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onBack = onBack,
                        testTag = "subscreen_visual_streaming_cache"
                    ) {
                        item {
                            com.example.feature.settings.components.appearance.StreamingVideoPolicyCard(
                                cacheConfig = streamingCacheConfig,
                                onSelectVideoPolicy = onSelectStreamingVideoPolicy
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        item {
                            com.example.feature.settings.components.appearance.StreamingCacheUsageCard(
                                cacheConfig = streamingCacheConfig,
                                onChangeMaxCacheMb = onChangeStreamingCacheMaxMb,
                                onClearStreamingCache = onClearStreamingCache
                            )
                        }
                    }
}

/**
 * Sub-pantalla Calidad de Audio (pantalla propia Opus/M4A).
 * Rol arquitectónico: pantalla independiente con retroceso.
 */
@Composable
fun SettingsAudioQualityScreen(
    streamingCacheConfig: com.example.core.model.StreamingCacheConfig,
    onSelectStreamingAudioQuality: (com.example.core.model.StreamingAudioQuality) -> Unit,
    onBack: () -> Unit
) {
                    SettingsSubMenuScreen(
                        title = "Calidad de Audio",
                        subtitle = "Opus / M4A para Explorar y descargas permanentes con yt-dlp",
                        icon = Icons.Default.HighQuality,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onBack = onBack,
                        testTag = "subscreen_visual_audio_quality"
                    ) {
                        item {
                            com.example.feature.settings.components.appearance.StreamingAudioQualityCard(
                                cacheConfig = streamingCacheConfig,
                                onSelectAudioQuality = onSelectStreamingAudioQuality
                            )
                        }
                    }
}

/**
 * Sub-pantalla Ahorro de Datos (pantalla propia, solo Explorar).
 * Rol arquitectónico: pantalla independiente con retroceso, sin tocar música local.
 */
@Composable
fun SettingsDataSaverScreen(
    streamingCacheConfig: com.example.core.model.StreamingCacheConfig,
    onToggleStreamingDataSaver: (Boolean) -> Unit,
    onBack: () -> Unit
) {
                    SettingsSubMenuScreen(
                        title = "Ahorro de Datos",
                        subtitle = "Solo Explorar: Opus ahorro, sin video ni pre-descarga. Local intacto.",
                        icon = Icons.Default.SignalWifiOff,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onBack = onBack,
                        testTag = "subscreen_visual_data_saver"
                    ) {
                        item {
                            com.example.feature.settings.components.appearance.StreamingDataSaverCard(
                                cacheConfig = streamingCacheConfig,
                                onToggleDataSaver = onToggleStreamingDataSaver
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
    isProcessing: Boolean = false,
    processingLabel: String = "",
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
                            onChangeBlurDp = onChangeBlurDp,
                            isProcessing = isProcessing,
                            processingLabel = processingLabel
                        )
                    }
}
