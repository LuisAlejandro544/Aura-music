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
 * Menús independientes a pantalla completa dentro de Ajustes.
 * Cuando es [MAIN_MENU], muestra el listado de tarjetas estilo "Diseño del Reproductor" y las pestañas principales.
 * Al tocar cualquiera de los apartados de ajustes, navega hacia una pantalla dedicada a pantalla completa.
 */
enum class SettingsSubScreen {
    MAIN_MENU,
    PLAYER_DESIGN,
    VISUAL_BEHAVIOR,
    VISUAL_STREAMING_AND_CACHE,
    VISUAL_AUDIO_QUALITY,
    VISUAL_DATA_SAVER,
    VISUAL_AURA_AND_MINIPLAYER,
    VISUAL_GAME_MODE,
    VISUAL_GALLERY_WALLPAPER,
    THEME_PALETTES,
    WIDGETS,
    HEADPHONES,
    HEADPHONES_DSP,
    HEADPHONES_SECURITY,
    HEADPHONES_GESTURES,
    STORED_MEDIA,
    ENGINES_AND_DIAGNOSTICS,
    DOWNLOADS_AND_COOKIES,
    BEHIND_THE_PROJECT
}

/**
 * Pantalla de Configuración & Ajustes de Aura Music (< 500 líneas).
 * Arquitectura Modular (MVVM):
 * - El apartado principal ahora se denomina "Ajustes" y organiza las opciones en tarjetas interactivas
 *   con el mismo diseño limpio que "Diseño del Reproductor" (icono, título, resumen activo y flecha `>`).
 * - Cambio crucial: Al pulsar cada apartado en "Ajustes" (y sus sub-apartados en Auriculares), en lugar de abrir
 *   modales emergentes o barras de pestañas obsoletas, se abre una pantalla/menú independiente a pantalla completa
 *   ([SettingsSubMenuScreen]) con botón de retroceso y soporte nativo de navegación hacia atrás.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: AuraTheme,
    onSelectTheme: (AuraTheme) -> Unit,
    isDynamicArtworkColorEnabled: Boolean = true,
    onToggleDynamicArtworkColor: (Boolean) -> Unit = {},
    isMiniPlayerVideoEnabled: Boolean = true,
    onToggleMiniPlayerVideo: (Boolean) -> Unit = {},
    isBackgroundGameModeEnabled: Boolean = true,
    onToggleBackgroundGameMode: (Boolean) -> Unit = {},
    appWallpaperConfig: AppWallpaperConfig = AppWallpaperConfig(),
    onToggleWallpaperEnabled: (Boolean) -> Unit = {},
    onSelectWallpaperImage: (Uri) -> Unit = {},
    onSelectWallpaperVideo: (Uri) -> Unit = {},
    onClearWallpaper: () -> Unit = {},
    onSelectWallpaperScope: (WallpaperScreenScope) -> Unit = {},
    onChangeWallpaperDimAlpha: (Float) -> Unit = {},
    onChangeWallpaperBlurDp: (Int) -> Unit = {},
    nowPlayingDesignMode: NowPlayingDesignMode = NowPlayingDesignMode.AUTO,
    onSetNowPlayingDesignMode: (NowPlayingDesignMode) -> Unit = {},
    // Configuración de Widgets de Escritorio
    widgetConfig: WidgetConfig = WidgetConfig(),
    currentTrack: Track? = null,
    topPlayedTracks: List<Track> = emptyList(),
    playlists: List<Playlist> = emptyList(),
    dynamicPrimaryColor: Color = Color(0xFF8B5CF6),
    dynamicSecondaryColor: Color = Color(0xFF06B6D4),
    onSetWidgetDynamicColorEnabled: (Boolean) -> Unit = {},
    onSetWidgetGridContentMode: (WidgetGridContentMode) -> Unit = {},
    onSetWidgetShowProgress: (Boolean) -> Unit = {},
    onSetWidgetColorIntensityPercent: (Int) -> Unit = {},
    onForceSyncWidgets: () -> Unit = {},
    // Configuración de Auriculares
    headphoneConfig: HeadphoneConfig = HeadphoneConfig(),
    onUpdateHeadphoneConfig: (HeadphoneConfig) -> Unit = {},
    onSetCrossfeedEnabled: (Boolean) -> Unit = {},
    onSetCrossfeedStrength: (Int) -> Unit = {},
    onSetBalanceControlEnabled: (Boolean) -> Unit = {},
    onSetStereoBalance: (Float) -> Unit = {},
    onSetBitPerfectEnabled: (Boolean) -> Unit = {},
    onSetLowLatencyEnabled: (Boolean) -> Unit = {},
    onSetUsbDacExclusiveEnabled: (Boolean) -> Unit = {},
    onSetHiResTargetMode: (Int) -> Unit = {},
    onSetBecomingNoisyGuardEnabled: (Boolean) -> Unit = {},
    onSetFadeInOnResumeEnabled: (Boolean) -> Unit = {},
    onSetDedicatedVolumeMemoryEnabled: (Boolean) -> Unit = {},
    onSetHeadsetControlsEnabled: (Boolean) -> Unit = {},
    onSetHeadsetAction: (Int, HeadsetButtonAction) -> Unit = { _, _ -> },
    // Transparencia de Almacenamiento y Gestión Multimedia
    allTracks: List<Track> = emptyList(),
    onDeleteTrackArtwork: (Track) -> Unit = {},
    onDeleteTrackVideo: (Track) -> Unit = {},
    onOpenOnboarding: () -> Unit = {},
    // Configuración de Streaming (Video Canvas Wi-Fi/Datos + Caché 50MB-500MB 30 min + Calidad Opus/M4A + Ahorro Datos)
    streamingCacheConfig: com.example.core.model.StreamingCacheConfig = com.example.core.model.StreamingCacheConfig(),
    onSelectStreamingVideoPolicy: (com.example.core.model.StreamingVideoNetworkPolicy) -> Unit = {},
    onChangeStreamingCacheMaxMb: (Int) -> Unit = {},
    onClearStreamingCache: () -> Unit = {},
    onSelectStreamingAudioQuality: (com.example.core.model.StreamingAudioQuality) -> Unit = {},
    onToggleStreamingDataSaver: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeSubScreen by remember { mutableStateOf(SettingsSubScreen.MAIN_MENU) }
    var editingClickType by remember { mutableStateOf<Int?>(null) }

    AnimatedContent(
        targetState = activeSubScreen,
        transitionSpec = {
            val isGoingDeeper = when {
                initialState == SettingsSubScreen.MAIN_MENU && targetState != SettingsSubScreen.MAIN_MENU -> true
                initialState == SettingsSubScreen.VISUAL_BEHAVIOR && (
                    targetState == SettingsSubScreen.VISUAL_STREAMING_AND_CACHE ||
                        targetState == SettingsSubScreen.VISUAL_AUDIO_QUALITY ||
                        targetState == SettingsSubScreen.VISUAL_DATA_SAVER ||
                        targetState == SettingsSubScreen.VISUAL_AURA_AND_MINIPLAYER ||
                        targetState == SettingsSubScreen.VISUAL_GAME_MODE ||
                        targetState == SettingsSubScreen.VISUAL_GALLERY_WALLPAPER
                    ) -> true
                initialState == SettingsSubScreen.HEADPHONES && (
                    targetState == SettingsSubScreen.HEADPHONES_DSP ||
                        targetState == SettingsSubScreen.HEADPHONES_SECURITY ||
                        targetState == SettingsSubScreen.HEADPHONES_GESTURES
                    ) -> true
                else -> false
            }
            if (isGoingDeeper) {
                (fadeIn(animationSpec = tween(220)) + slideInHorizontally(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) { it / 4 }).togetherWith(
                    fadeOut(animationSpec = tween(160)) + slideOutHorizontally(
                        animationSpec = tween(160)
                    ) { -it / 4 }
                )
            } else {
                (fadeIn(animationSpec = tween(220)) + slideInHorizontally(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) { -it / 4 }).togetherWith(
                    fadeOut(animationSpec = tween(160)) + slideOutHorizontally(
                        animationSpec = tween(160)
                    ) { it / 4 }
                )
            }
        },
        label = "SettingsSubScreenTransition",
        modifier = modifier.fillMaxSize()
    ) { subScreen ->
        when (subScreen) {
            SettingsSubScreen.MAIN_MENU -> {
                SettingsMainMenuScreen(
                currentTheme = currentTheme,
                isDynamicArtworkColorEnabled = isDynamicArtworkColorEnabled,
                isMiniPlayerVideoEnabled = isMiniPlayerVideoEnabled,
                isBackgroundGameModeEnabled = isBackgroundGameModeEnabled,
                appWallpaperConfig = appWallpaperConfig,
                nowPlayingDesignMode = nowPlayingDesignMode,
                headphoneConfig = headphoneConfig,
                widgetConfig = widgetConfig,
                allTracks = allTracks,
                onNavigate = { activeSubScreen = it },
                onOpenOnboarding = onOpenOnboarding
                )
            }

            // PANTALLA INDEPENDIENTE 1: DISEÑO DEL REPRODUCTOR
            SettingsSubScreen.PLAYER_DESIGN -> {
                SettingsPlayerDesignScreen(
                nowPlayingDesignMode = nowPlayingDesignMode,
                onSetNowPlayingDesignMode = onSetNowPlayingDesignMode,
                onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU }
                )
            }

            // PANTALLA INDEPENDIENTE 2: AURA DINÁMICA, STREAMING & MODO JUEGO (MENÚ DE 4 TARJETAS)
            SettingsSubScreen.VISUAL_BEHAVIOR -> {
                SettingsVisualBehaviorMenuScreen(
                isDynamicArtworkColorEnabled = isDynamicArtworkColorEnabled,
                isMiniPlayerVideoEnabled = isMiniPlayerVideoEnabled,
                isBackgroundGameModeEnabled = isBackgroundGameModeEnabled,
                appWallpaperConfig = appWallpaperConfig,
                streamingCacheConfig = streamingCacheConfig,
                onNavigate = { activeSubScreen = it },
                onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU }
                )
            }

            // SUB-PANTALLA 2.1: STREAMING & CACHÉ INTELIGENTE (VIDEO + CACHÉ)
            SettingsSubScreen.VISUAL_STREAMING_AND_CACHE -> {
                SettingsStreamingCacheScreen(
                streamingCacheConfig = streamingCacheConfig,
                onSelectStreamingVideoPolicy = onSelectStreamingVideoPolicy,
                onChangeStreamingCacheMaxMb = onChangeStreamingCacheMaxMb,
                onClearStreamingCache = onClearStreamingCache,
                onSelectStreamingAudioQuality = onSelectStreamingAudioQuality,
                onToggleStreamingDataSaver = onToggleStreamingDataSaver,
                onBack = { activeSubScreen = SettingsSubScreen.VISUAL_BEHAVIOR }
                )
            }

            // SUB-PANTALLA 2.2: CALIDAD DE AUDIO (PANTALLA PROPIA OPUS/M4A)
            SettingsSubScreen.VISUAL_AUDIO_QUALITY -> {
                SettingsAudioQualityScreen(
                streamingCacheConfig = streamingCacheConfig,
                onSelectStreamingAudioQuality = onSelectStreamingAudioQuality,
                onBack = { activeSubScreen = SettingsSubScreen.VISUAL_BEHAVIOR }
                )
            }

            // SUB-PANTALLA 2.3: AHORRO DE DATOS (PANTALLA PROPIA, SOLO EXPLORAR)
            SettingsSubScreen.VISUAL_DATA_SAVER -> {
                SettingsDataSaverScreen(
                streamingCacheConfig = streamingCacheConfig,
                onToggleStreamingDataSaver = onToggleStreamingDataSaver,
                onBack = { activeSubScreen = SettingsSubScreen.VISUAL_BEHAVIOR }
                )
            }

            // SUB-PANTALLA 2.2: AURA DINÁMICA (1S) & MINI REPRODUCTOR
            SettingsSubScreen.VISUAL_AURA_AND_MINIPLAYER -> {
                SettingsAuraMiniPlayerScreen(
                isDynamicArtworkColorEnabled = isDynamicArtworkColorEnabled,
                onToggleDynamicArtworkColor = onToggleDynamicArtworkColor,
                isMiniPlayerVideoEnabled = isMiniPlayerVideoEnabled,
                onToggleMiniPlayerVideo = onToggleMiniPlayerVideo,
                onBack = { activeSubScreen = SettingsSubScreen.VISUAL_BEHAVIOR }
                )
            }

            // SUB-PANTALLA 2.3: MODO JUEGO / AHORRO EN 2DO PLANO
            SettingsSubScreen.VISUAL_GAME_MODE -> {
                SettingsGameModeScreen(
                isBackgroundGameModeEnabled = isBackgroundGameModeEnabled,
                onToggleBackgroundGameMode = onToggleBackgroundGameMode,
                onBack = { activeSubScreen = SettingsSubScreen.VISUAL_BEHAVIOR }
                )
            }

            // SUB-PANTALLA 2.4: FONDO DE PANTALLA DE GALERÍA (IMAGEN WEBP O VIDEO CORTO MP4)
            SettingsSubScreen.VISUAL_GALLERY_WALLPAPER -> {
                SettingsGalleryWallpaperScreen(
                appWallpaperConfig = appWallpaperConfig,
                onToggleWallpaperEnabled = onToggleWallpaperEnabled,
                onSelectWallpaperImage = onSelectWallpaperImage,
                onSelectWallpaperVideo = onSelectWallpaperVideo,
                onClearWallpaper = onClearWallpaper,
                onSelectWallpaperScope = onSelectWallpaperScope,
                onChangeDimAlpha = onChangeWallpaperDimAlpha,
                onChangeBlurDp = onChangeWallpaperBlurDp,
                onBack = { activeSubScreen = SettingsSubScreen.VISUAL_BEHAVIOR }
                )
            }

            // PANTALLA INDEPENDIENTE 3: PALETA BASE PREDETERMINADA & TEMAS
            SettingsSubScreen.THEME_PALETTES -> {
                SettingsThemePalettesScreen(
                currentTheme = currentTheme,
                onSelectTheme = onSelectTheme,
                onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU }
                )
            }

            // PANTALLA INDEPENDIENTE 4: WIDGET (COLOR DE IMAGEN, ESTIRAMIENTO Y TOP 4 / PLAYLISTS)
            SettingsSubScreen.WIDGETS -> {
                SettingsWidgetScreen(
                widgetConfig = widgetConfig,
                currentTrack = currentTrack,
                topPlayedTracks = topPlayedTracks,
                allTracks = allTracks,
                playlists = playlists,
                dynamicPrimaryColor = dynamicPrimaryColor,
                dynamicSecondaryColor = dynamicSecondaryColor,
                onSetWidgetDynamicColorEnabled = onSetWidgetDynamicColorEnabled,
                onSetWidgetGridContentMode = onSetWidgetGridContentMode,
                onSetWidgetShowProgress = onSetWidgetShowProgress,
                onSetWidgetColorIntensityPercent = onSetWidgetColorIntensityPercent,
                onForceSyncWidgets = onForceSyncWidgets,
                onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU }
                )
            }

            // PANTALLA INDEPENDIENTE 4: AURICULARES & ACÚSTICA DSP (MENÚ DE TARJETAS SIN BARRA DE PESTAÑAS)
            SettingsSubScreen.HEADPHONES -> {
                SettingsHeadphonesMenuScreen(
                headphoneConfig = headphoneConfig,
                onNavigate = { activeSubScreen = it },
                onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU }
                )
            }

            // SUB-PANTALLA 4.1: ACÚSTICA & DSP
            SettingsSubScreen.HEADPHONES_DSP -> {
                SettingsHeadphoneDspScreen(
                headphoneConfig = headphoneConfig,
                onSetCrossfeedEnabled = onSetCrossfeedEnabled,
                onSetCrossfeedStrength = onSetCrossfeedStrength,
                onSetBalanceControlEnabled = onSetBalanceControlEnabled,
                onSetStereoBalance = onSetStereoBalance,
                onSetBitPerfectEnabled = onSetBitPerfectEnabled,
                onSetLowLatencyEnabled = onSetLowLatencyEnabled,
                onSetUsbDacExclusiveEnabled = onSetUsbDacExclusiveEnabled,
                onSetHiResTargetMode = onSetHiResTargetMode,
                onBack = { activeSubScreen = SettingsSubScreen.HEADPHONES }
                )
            }

            // SUB-PANTALLA 4.2: SEGURIDAD & CONEXIÓN
            SettingsSubScreen.HEADPHONES_SECURITY -> {
                SettingsHeadphoneSecurityScreen(
                headphoneConfig = headphoneConfig,
                onSetBecomingNoisyGuardEnabled = onSetBecomingNoisyGuardEnabled,
                onSetFadeInOnResumeEnabled = onSetFadeInOnResumeEnabled,
                onSetDedicatedVolumeMemoryEnabled = onSetDedicatedVolumeMemoryEnabled,
                onBack = { activeSubScreen = SettingsSubScreen.HEADPHONES }
                )
            }

            // SUB-PANTALLA 4.3: BOTONES Y GESTOS
            SettingsSubScreen.HEADPHONES_GESTURES -> {
                SettingsHeadphoneGesturesScreen(
                headphoneConfig = headphoneConfig,
                onSetHeadsetControlsEnabled = onSetHeadsetControlsEnabled,
                onEditClickType = { editingClickType = it },
                onBack = { activeSubScreen = SettingsSubScreen.HEADPHONES }
                )
            }

            // PANTALLA INDEPENDIENTE 5: MEDIOS & ALMACENAMIENTO
            SettingsSubScreen.STORED_MEDIA -> {
                SettingsStoredMediaScreen(
                allTracks = allTracks,
                onDeleteTrackArtwork = onDeleteTrackArtwork,
                onDeleteTrackVideo = onDeleteTrackVideo,
                onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU }
                )
            }

            // PANTALLA INDEPENDIENTE 6: MOTORES NATIVOS, YT-DLP OTA & DIAGNÓSTICO
            SettingsSubScreen.ENGINES_AND_DIAGNOSTICS -> {
                SettingsEnginesScreen(
                onOpenOnboarding = onOpenOnboarding,
                onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU }
                )
            }

            // PANTALLA INDEPENDIENTE: DESCARGAS, COOKIES (ESTILO YTDLNIS) & PO TOKEN WEB
            SettingsSubScreen.DOWNLOADS_AND_COOKIES -> {
                SettingsDownloadsScreen(
                onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU }
                )
            }

            // PANTALLA INDEPENDIENTE 7: DETRÁS DEL PROYECTO (CREADOR & CONTRIBUIDORES)
            SettingsSubScreen.BEHIND_THE_PROJECT -> {
                SettingsBehindProjectScreen(
                onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU }
                )
            }
        }
    }

    // Diálogo Modal para seleccionar acción de botón de audífonos
    if (editingClickType != null) {
        HeadsetButtonActionDialog(
            clickType = editingClickType!!,
            headphoneConfig = headphoneConfig,
            onSetHeadsetAction = onSetHeadsetAction,
            onDismissRequest = { editingClickType = null }
        )
    }
}

