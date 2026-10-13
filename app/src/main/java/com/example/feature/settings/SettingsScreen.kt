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
    // Configuración de Streaming (Video Canvas Wi-Fi/Datos + Caché 50MB-500MB 30 min + Calidad Opus/M4A + Letras Explorar)
    streamingCacheConfig: com.example.core.model.StreamingCacheConfig = com.example.core.model.StreamingCacheConfig(),
    onSelectStreamingVideoPolicy: (com.example.core.model.StreamingVideoNetworkPolicy) -> Unit = {},
    onChangeStreamingCacheMaxMb: (Int) -> Unit = {},
    onClearStreamingCache: () -> Unit = {},
    onSelectStreamingAudioQuality: (com.example.core.model.StreamingAudioQuality) -> Unit = {},
    onToggleExploreLyrics: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeSubScreen by remember { mutableStateOf(SettingsSubScreen.MAIN_MENU) }
    var editingClickType by remember { mutableStateOf<Int?>(null) }

    val mediaCount = remember(allTracks) {
        allTracks.count { !it.albumArtPath.isNullOrBlank() || !it.videoUri.isNullOrBlank() }
    }

    AnimatedContent(
        targetState = activeSubScreen,
        transitionSpec = {
            val isGoingDeeper = when {
                initialState == SettingsSubScreen.MAIN_MENU && targetState != SettingsSubScreen.MAIN_MENU -> true
                initialState == SettingsSubScreen.VISUAL_BEHAVIOR && (
                    targetState == SettingsSubScreen.VISUAL_STREAMING_AND_CACHE ||
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // Cabecera Principal Directa (Sin barra de pestañas superior redundante)
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Configuración & Ajustes",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Personaliza el reproductor, temas visuales, acústica DSP y medios guardados.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Menú Principal de Navegación por Pantallas Independientes
                    mainSettingsMenuContent(
                        currentTheme = currentTheme,
                        isDynamicArtworkColorEnabled = isDynamicArtworkColorEnabled,
                        isMiniPlayerVideoEnabled = isMiniPlayerVideoEnabled,
                        isBackgroundGameModeEnabled = isBackgroundGameModeEnabled,
                        appWallpaperConfig = appWallpaperConfig,
                        nowPlayingDesignMode = nowPlayingDesignMode,
                        isHeadphoneConnected = headphoneConfig.isHeadphoneConnected,
                        connectedDeviceName = headphoneConfig.connectedDeviceName,
                        mediaCount = mediaCount,
                        widgetConfig = widgetConfig,
                        onOpenPlayerDesign = { activeSubScreen = SettingsSubScreen.PLAYER_DESIGN },
                        onOpenVisualBehavior = { activeSubScreen = SettingsSubScreen.VISUAL_BEHAVIOR },
                        onOpenThemePalettes = { activeSubScreen = SettingsSubScreen.THEME_PALETTES },
                        onOpenWidgets = { activeSubScreen = SettingsSubScreen.WIDGETS },
                        onOpenHeadphones = { activeSubScreen = SettingsSubScreen.HEADPHONES },
                        onOpenStoredMedia = { activeSubScreen = SettingsSubScreen.STORED_MEDIA },
                        onOpenEnginesAndDiagnostics = { activeSubScreen = SettingsSubScreen.ENGINES_AND_DIAGNOSTICS },
                        onOpenDownloadsAndCookies = { activeSubScreen = SettingsSubScreen.DOWNLOADS_AND_COOKIES },
                        onOpenBehindTheProject = { activeSubScreen = SettingsSubScreen.BEHIND_THE_PROJECT },
                        onOpenOnboarding = onOpenOnboarding
                    )
                }
            }

            // PANTALLA INDEPENDIENTE 1: DISEÑO DEL REPRODUCTOR
            SettingsSubScreen.PLAYER_DESIGN -> {
                SettingsSubMenuScreen(
                    title = "Diseño del Reproductor",
                    subtitle = "Elige el estilo visual de la pantalla completa Now Playing",
                    icon = Icons.Default.DashboardCustomize,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU },
                    testTag = "subscreen_player_design"
                ) {
                    playerDesignSettingsContent(
                        currentDesignMode = nowPlayingDesignMode,
                        onSetDesignMode = onSetNowPlayingDesignMode
                    )
                }
            }

            // PANTALLA INDEPENDIENTE 2: AURA DINÁMICA, STREAMING & MODO JUEGO (MENÚ DE 4 TARJETAS)
            SettingsSubScreen.VISUAL_BEHAVIOR -> {
                SettingsSubMenuScreen(
                    title = "Aura Dinámica, Streaming & Modo Juego",
                    subtitle = "Elige una categoría para configurar el streaming, colores dinámicos, modo juego o fondo",
                    icon = Icons.Default.Videocam,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU },
                    testTag = "subscreen_visual_behavior"
                ) {
                    visualBehaviorMenuSection(
                        isDynamicArtworkColorEnabled = isDynamicArtworkColorEnabled,
                        isMiniPlayerVideoEnabled = isMiniPlayerVideoEnabled,
                        isBackgroundGameModeEnabled = isBackgroundGameModeEnabled,
                        appWallpaperConfig = appWallpaperConfig,
                        streamingCacheConfig = streamingCacheConfig,
                        onOpenStreamingAndCache = { activeSubScreen = SettingsSubScreen.VISUAL_STREAMING_AND_CACHE },
                        onOpenAuraAndMiniPlayer = { activeSubScreen = SettingsSubScreen.VISUAL_AURA_AND_MINIPLAYER },
                        onOpenGameMode = { activeSubScreen = SettingsSubScreen.VISUAL_GAME_MODE },
                        onOpenGalleryWallpaper = { activeSubScreen = SettingsSubScreen.VISUAL_GALLERY_WALLPAPER }
                    )
                }
            }

            // SUB-PANTALLA 2.1: STREAMING & CACHÉ INTELIGENTE
            SettingsSubScreen.VISUAL_STREAMING_AND_CACHE -> {
                SettingsSubMenuScreen(
                    title = "Streaming & Caché Inteligente",
                    subtitle = "Videos Canvas en Streaming (Wi-Fi/Datos) y caché temporal de 50 a 500 MB",
                    icon = Icons.Default.CloudQueue,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onBack = { activeSubScreen = SettingsSubScreen.VISUAL_BEHAVIOR },
                    testTag = "subscreen_visual_streaming_cache"
                ) {
                    item {
                        com.example.feature.settings.components.appearance.StreamingCacheAndVideoSettingsCard(
                            cacheConfig = streamingCacheConfig,
                            onSelectVideoPolicy = onSelectStreamingVideoPolicy,
                            onChangeMaxCacheMb = onChangeStreamingCacheMaxMb,
                            onClearStreamingCache = onClearStreamingCache,
                            onSelectAudioQuality = onSelectStreamingAudioQuality,
                            onToggleExploreLyrics = onToggleExploreLyrics
                        )
                    }
                }
            }

            // SUB-PANTALLA 2.2: AURA DINÁMICA (1S) & MINI REPRODUCTOR
            SettingsSubScreen.VISUAL_AURA_AND_MINIPLAYER -> {
                SettingsSubMenuScreen(
                    title = "Aura Dinámica & Mini Reproductor",
                    subtitle = "Armonización de color cada 1 segundo y Video Canvas en el Mini Reproductor",
                    icon = Icons.Default.Palette,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onBack = { activeSubScreen = SettingsSubScreen.VISUAL_BEHAVIOR },
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

            // SUB-PANTALLA 2.3: MODO JUEGO / AHORRO EN 2DO PLANO
            SettingsSubScreen.VISUAL_GAME_MODE -> {
                SettingsSubMenuScreen(
                    title = "Modo Juego / Ahorro en 2do Plano",
                    subtitle = "Libera GPU, texturas y visualizador al minimizar la app mientras juegas",
                    icon = Icons.Default.SportsEsports,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onBack = { activeSubScreen = SettingsSubScreen.VISUAL_BEHAVIOR },
                    testTag = "subscreen_visual_game_mode"
                ) {
                    backgroundGameModeSettingsSection(
                        isBackgroundGameModeEnabled = isBackgroundGameModeEnabled,
                        onToggleBackgroundGameMode = onToggleBackgroundGameMode
                    )
                }
            }

            // SUB-PANTALLA 2.4: FONDO DE PANTALLA DE GALERÍA (IMAGEN WEBP O VIDEO CORTO MP4)
            SettingsSubScreen.VISUAL_GALLERY_WALLPAPER -> {
                SettingsSubMenuScreen(
                    title = "Fondo de Pantalla de Galería",
                    subtitle = "Personaliza el fondo con una foto (WebP) o video corto (MP4), opacidad y blur",
                    icon = Icons.Default.PermMedia,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onBack = { activeSubScreen = SettingsSubScreen.VISUAL_BEHAVIOR },
                    testTag = "subscreen_visual_gallery_wallpaper"
                ) {
                    appWallpaperSettingsSection(
                        wallpaperConfig = appWallpaperConfig,
                        onToggleWallpaperEnabled = onToggleWallpaperEnabled,
                        onSelectWallpaperImage = onSelectWallpaperImage,
                        onSelectWallpaperVideo = onSelectWallpaperVideo,
                        onClearWallpaper = onClearWallpaper,
                        onSelectWallpaperScope = onSelectWallpaperScope,
                        onChangeDimAlpha = onChangeWallpaperDimAlpha,
                        onChangeBlurDp = onChangeWallpaperBlurDp
                    )
                }
            }

            // PANTALLA INDEPENDIENTE 3: PALETA BASE PREDETERMINADA & TEMAS
            SettingsSubScreen.THEME_PALETTES -> {
                SettingsSubMenuScreen(
                    title = "Paleta Base Predeterminada",
                    subtitle = "Colores de acento OLED y sincronización con Material You",
                    icon = Icons.Default.Palette,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU },
                    testTag = "subscreen_theme_palettes"
                ) {
                    themePaletteSettingsContent(
                        currentTheme = currentTheme,
                        onSelectTheme = onSelectTheme
                    )
                }
            }

            // PANTALLA INDEPENDIENTE 4: WIDGET (COLOR DE IMAGEN, ESTIRAMIENTO Y TOP 4 / PLAYLISTS)
            SettingsSubScreen.WIDGETS -> {
                SettingsSubMenuScreen(
                    title = "Widget",
                    subtitle = "Color dinámico de carátula, adaptación al estirar y 4 Canciones / 4 Playlists",
                    icon = Icons.Default.Widgets,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU },
                    testTag = "subscreen_widgets"
                ) {
                    widgetSettingsContent(
                        widgetConfig = widgetConfig,
                        currentTrack = currentTrack,
                        topTracks = topPlayedTracks.ifEmpty { allTracks },
                        playlists = playlists,
                        dynamicPrimaryColor = dynamicPrimaryColor,
                        dynamicSecondaryColor = dynamicSecondaryColor,
                        onSetDynamicColorEnabled = onSetWidgetDynamicColorEnabled,
                        onSetGridContentMode = onSetWidgetGridContentMode,
                        onSetShowProgressInWidget = onSetWidgetShowProgress,
                        onSetColorIntensityPercent = onSetWidgetColorIntensityPercent,
                        onForceSyncWidgets = onForceSyncWidgets
                    )
                }
            }

            // PANTALLA INDEPENDIENTE 4: AURICULARES & ACÚSTICA DSP (MENÚ DE TARJETAS SIN BARRA DE PESTAÑAS)
            SettingsSubScreen.HEADPHONES -> {
                SettingsSubMenuScreen(
                    title = "Auriculares & Acústica DSP",
                    subtitle = "Crossfeed C++20, Balance Estéreo L/R, Seguridad y Gestos",
                    icon = Icons.Default.Headphones,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU },
                    testTag = "subscreen_headphones"
                ) {
                    headphonesMenuSection(
                        headphoneConfig = headphoneConfig,
                        onOpenAcousticsDsp = { activeSubScreen = SettingsSubScreen.HEADPHONES_DSP },
                        onOpenSecurity = { activeSubScreen = SettingsSubScreen.HEADPHONES_SECURITY },
                        onOpenGestures = { activeSubScreen = SettingsSubScreen.HEADPHONES_GESTURES }
                    )
                }
            }

            // SUB-PANTALLA 4.1: ACÚSTICA & DSP
            SettingsSubScreen.HEADPHONES_DSP -> {
                SettingsSubMenuScreen(
                    title = "Acústica & DSP",
                    subtitle = "Modo Bit-Perfect 1:1, Crossfeed C++20 y Balance Estéreo L/R",
                    icon = Icons.Default.GraphicEq,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onBack = { activeSubScreen = SettingsSubScreen.HEADPHONES },
                    testTag = "subscreen_headphones_dsp"
                ) {
                    headphonesAcousticsSection(
                        headphoneConfig = headphoneConfig,
                        onSetCrossfeedEnabled = onSetCrossfeedEnabled,
                        onSetCrossfeedStrength = onSetCrossfeedStrength,
                        onSetBalanceControlEnabled = onSetBalanceControlEnabled,
                        onSetStereoBalance = onSetStereoBalance,
                        onSetBitPerfectEnabled = onSetBitPerfectEnabled,
                        onSetLowLatencyEnabled = onSetLowLatencyEnabled,
                        onSetUsbDacExclusiveEnabled = onSetUsbDacExclusiveEnabled,
                        onSetHiResTargetMode = onSetHiResTargetMode
                    )
                }
            }

            // SUB-PANTALLA 4.2: SEGURIDAD & CONEXIÓN
            SettingsSubScreen.HEADPHONES_SECURITY -> {
                SettingsSubMenuScreen(
                    title = "Seguridad & Conexión",
                    subtitle = "Protección contra desconexiones, Fade-In y Memoria de Volumen",
                    icon = Icons.Default.Security,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onBack = { activeSubScreen = SettingsSubScreen.HEADPHONES },
                    testTag = "subscreen_headphones_security"
                ) {
                    headphonesSecuritySection(
                        headphoneConfig = headphoneConfig,
                        onSetBecomingNoisyGuardEnabled = onSetBecomingNoisyGuardEnabled,
                        onSetFadeInOnResumeEnabled = onSetFadeInOnResumeEnabled,
                        onSetDedicatedVolumeMemoryEnabled = onSetDedicatedVolumeMemoryEnabled
                    )
                }
            }

            // SUB-PANTALLA 4.3: BOTONES Y GESTOS
            SettingsSubScreen.HEADPHONES_GESTURES -> {
                SettingsSubMenuScreen(
                    title = "Botones y Gestos",
                    subtitle = "Personaliza las pulsaciones simples, dobles, triples y prolongadas",
                    icon = Icons.Default.TouchApp,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onBack = { activeSubScreen = SettingsSubScreen.HEADPHONES },
                    testTag = "subscreen_headphones_gestures"
                ) {
                    headphonesGesturesSection(
                        headphoneConfig = headphoneConfig,
                        onSetHeadsetControlsEnabled = onSetHeadsetControlsEnabled,
                        onOpenEditClickType = { editingClickType = it }
                    )
                }
            }

            // PANTALLA INDEPENDIENTE 5: MEDIOS & ALMACENAMIENTO
            SettingsSubScreen.STORED_MEDIA -> {
                SettingsSubMenuScreen(
                    title = "Medios & Almacenamiento",
                    subtitle = "Gestión transparente de carátulas WebP y videos MP4 guardados",
                    icon = Icons.Default.PermMedia,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU },
                    testTag = "subscreen_stored_media"
                ) {
                    storedMediaSettingsTab(
                        allTracks = allTracks,
                        onDeleteTrackArtwork = onDeleteTrackArtwork,
                        onDeleteTrackVideo = onDeleteTrackVideo
                    )
                }
            }

            // PANTALLA INDEPENDIENTE 6: MOTORES NATIVOS, YT-DLP OTA & DIAGNÓSTICO
            SettingsSubScreen.ENGINES_AND_DIAGNOSTICS -> {
                SettingsSubMenuScreen(
                    title = "Motores Nativos & Diagnóstico",
                    subtitle = "Arquitectura C++20, FFmpeg puro, yt-dlp OTA y Aura Monitor",
                    icon = Icons.Default.Build,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU },
                    testTag = "subscreen_engines_diagnostics"
                ) {
                    enginesAndDiagnosticsSettingsContent(onOpenOnboarding = onOpenOnboarding)
                }
            }

            // PANTALLA INDEPENDIENTE: DESCARGAS, COOKIES (ESTILO YTDLNIS) & PO TOKEN WEB
            SettingsSubScreen.DOWNLOADS_AND_COOKIES -> {
                SettingsSubMenuScreen(
                    title = "Descargas & Cookies (YTDLnis)",
                    subtitle = "Estrategia yt-dlp natural, Cookies.txt opcionales, PO Token Web y OTA",
                    icon = Icons.Default.CloudDownload,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU },
                    testTag = "subscreen_downloads_and_cookies"
                ) {
                    ytDlpCookiesAndPoTokenSettingsContent()
                }
            }

            // PANTALLA INDEPENDIENTE 7: DETRÁS DEL PROYECTO (CREADOR & CONTRIBUIDORES)
            SettingsSubScreen.BEHIND_THE_PROJECT -> {
                SettingsSubMenuScreen(
                    title = "Detrás del Proyecto",
                    subtitle = "Creador oficial, contribuidores y perfiles de GitHub",
                    icon = Icons.Default.Groups,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU },
                    testTag = "subscreen_behind_project"
                ) {
                    behindTheProjectSettingsContent()
                }
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

private fun androidx.compose.foundation.lazy.LazyListScope.headphonesMenuSection(
    headphoneConfig: HeadphoneConfig,
    onOpenAcousticsDsp: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenGestures: () -> Unit
) {
    item {
        HeadphoneStatusCard(headphoneConfig = headphoneConfig)
        Spacer(modifier = Modifier.height(16.dp))
    }

    item {
        val dspStatus = buildList {
            if (headphoneConfig.isBitPerfectEnabled) add("Bit-Perfect 1:1")
            if (headphoneConfig.isCrossfeedEnabled) add("Crossfeed activo")
            if (headphoneConfig.isBalanceControlEnabled) add("Balance ${headphoneConfig.formattedBalance}")
        }.joinToString(" • ").ifEmpty { "Modo Bit-Perfect, Crossfeed Bauer/Chu Moy C++20 y Balance Estéreo L/R" }

        SettingsNavigationCard(
            icon = Icons.Default.GraphicEq,
            title = "Acústica & DSP",
            subtitle = dspStatus,
            iconTint = MaterialTheme.colorScheme.primary,
            badgeText = if (headphoneConfig.isBitPerfectEnabled || headphoneConfig.isCrossfeedEnabled) "ACTIVO" else null,
            badgeColor = MaterialTheme.colorScheme.primary,
            onClick = onOpenAcousticsDsp,
            testTag = "headphone_subtab_dsp"
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    item {
        val activeGuards = listOf(
            headphoneConfig.isBecomingNoisyGuardEnabled,
            headphoneConfig.isFadeInOnResumeEnabled,
            headphoneConfig.isDedicatedVolumeMemoryEnabled
        ).count { it }

        SettingsNavigationCard(
            icon = Icons.Default.Security,
            title = "Seguridad & Conexión",
            subtitle = "Protección contra desconexiones, Fade-In suave al reanudar y Memoria de Volumen",
            iconTint = MaterialTheme.colorScheme.secondary,
            badgeText = if (activeGuards > 0) "$activeGuards/3" else null,
            badgeColor = MaterialTheme.colorScheme.secondary,
            onClick = onOpenSecurity,
            testTag = "headphone_subtab_safety"
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    item {
        SettingsNavigationCard(
            icon = Icons.Default.TouchApp,
            title = "Botones y Gestos",
            subtitle = if (headphoneConfig.isHeadsetControlsEnabled) {
                "Controles físicos activos • 1x: ${headphoneConfig.singleClickAction.label}, 2x: ${headphoneConfig.doubleClickAction.label}"
            } else {
                "Personaliza las pulsaciones simples, dobles, triples y prolongadas de tus audífonos"
            },
            iconTint = MaterialTheme.colorScheme.primary,
            badgeText = if (headphoneConfig.isHeadsetControlsEnabled) "ON" else null,
            badgeColor = MaterialTheme.colorScheme.primary,
            onClick = onOpenGestures,
            testTag = "headphone_subtab_gestures"
        )
    }
}
