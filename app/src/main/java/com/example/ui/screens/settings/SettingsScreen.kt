package com.example.ui.screens.settings

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
import com.example.model.AuraTheme
import com.example.model.HeadphoneConfig
import com.example.model.HeadsetButtonAction
import com.example.model.NowPlayingDesignMode
import com.example.model.Track
import com.example.ui.screens.settings.components.*
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Menús independientes a pantalla completa dentro de Ajustes.
 * Cuando es [MAIN_MENU], muestra el listado de tarjetas estilo "Diseño del Reproductor" y las pestañas principales.
 * Al tocar cualquiera de los apartados de ajustes, navega hacia una pantalla dedicada a pantalla completa.
 */
enum class SettingsSubScreen {
    MAIN_MENU,
    PLAYER_DESIGN,
    VISUAL_BEHAVIOR,
    THEME_PALETTES,
    HEADPHONES,
    STORED_MEDIA,
    ENGINES_AND_DIAGNOSTICS
}

/**
 * Pantalla de Configuración & Ajustes de Aura Music (< 500 líneas).
 * Arquitectura Modular (MVVM):
 * - El apartado principal ahora se denomina "Ajustes" y organiza las opciones en tarjetas interactivas
 *   con el mismo diseño limpio que "Diseño del Reproductor" (icono, título, resumen activo y flecha `>`).
 * - Cambio crucial: Al pulsar cada apartado en "Ajustes", en lugar de abrir un diálogo modal emergente,
 *   se abre una pantalla/menú independiente a pantalla completa ([SettingsSubMenuScreen]) con botón de retroceso
 *   y soporte nativo de navegación hacia atrás.
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
    nowPlayingDesignMode: NowPlayingDesignMode = NowPlayingDesignMode.AUTO,
    onSetNowPlayingDesignMode: (NowPlayingDesignMode) -> Unit = {},
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
    modifier: Modifier = Modifier
) {
    var activeSubScreen by remember { mutableStateOf(SettingsSubScreen.MAIN_MENU) }
    var headphoneSectionTab by remember { mutableIntStateOf(0) }
    var editingClickType by remember { mutableStateOf<Int?>(null) }

    val mediaCount = remember(allTracks) {
        allTracks.count { !it.albumArtPath.isNullOrBlank() || !it.videoUri.isNullOrBlank() }
    }

    AnimatedContent(
        targetState = activeSubScreen,
        transitionSpec = {
            if (targetState != SettingsSubScreen.MAIN_MENU) {
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
                        nowPlayingDesignMode = nowPlayingDesignMode,
                        isHeadphoneConnected = headphoneConfig.isHeadphoneConnected,
                        connectedDeviceName = headphoneConfig.connectedDeviceName,
                        mediaCount = mediaCount,
                        onOpenPlayerDesign = { activeSubScreen = SettingsSubScreen.PLAYER_DESIGN },
                        onOpenVisualBehavior = { activeSubScreen = SettingsSubScreen.VISUAL_BEHAVIOR },
                        onOpenThemePalettes = { activeSubScreen = SettingsSubScreen.THEME_PALETTES },
                        onOpenHeadphones = { activeSubScreen = SettingsSubScreen.HEADPHONES },
                        onOpenStoredMedia = { activeSubScreen = SettingsSubScreen.STORED_MEDIA },
                        onOpenEnginesAndDiagnostics = { activeSubScreen = SettingsSubScreen.ENGINES_AND_DIAGNOSTICS },
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

            // PANTALLA INDEPENDIENTE 2: AURA DINÁMICA & VIDEO EN MINI REPRODUCTOR
            SettingsSubScreen.VISUAL_BEHAVIOR -> {
                SettingsSubMenuScreen(
                    title = "Aura Dinámica & Video",
                    subtitle = "Armonización cromática en tiempo real y Video Canvas miniatura",
                    icon = Icons.Default.Videocam,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU },
                    testTag = "subscreen_visual_behavior"
                ) {
                    visualCustomizationSettingsContent(
                        isDynamicArtworkColorEnabled = isDynamicArtworkColorEnabled,
                        onToggleDynamicArtworkColor = onToggleDynamicArtworkColor,
                        isMiniPlayerVideoEnabled = isMiniPlayerVideoEnabled,
                        onToggleMiniPlayerVideo = onToggleMiniPlayerVideo
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

            // PANTALLA INDEPENDIENTE 4: AURICULARES & ACÚSTICA DSP
            SettingsSubScreen.HEADPHONES -> {
                SettingsSubMenuScreen(
                    title = "Auriculares & Acústica DSP",
                    subtitle = "Crossfeed C++20, Balance Estéreo L/R, Seguridad y Gestos",
                    icon = Icons.Default.Headphones,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onBack = { activeSubScreen = SettingsSubScreen.MAIN_MENU },
                    testTag = "subscreen_headphones"
                ) {
                    headphonesFullSection(
                        headphoneConfig = headphoneConfig,
                        headphoneSectionTab = headphoneSectionTab,
                        onSelectSubTab = { headphoneSectionTab = it },
                        onSetCrossfeedEnabled = onSetCrossfeedEnabled,
                        onSetCrossfeedStrength = onSetCrossfeedStrength,
                        onSetBalanceControlEnabled = onSetBalanceControlEnabled,
                        onSetStereoBalance = onSetStereoBalance,
                        onSetBitPerfectEnabled = onSetBitPerfectEnabled,
                        onSetLowLatencyEnabled = onSetLowLatencyEnabled,
                        onSetUsbDacExclusiveEnabled = onSetUsbDacExclusiveEnabled,
                        onSetHiResTargetMode = onSetHiResTargetMode,
                        onSetBecomingNoisyGuardEnabled = onSetBecomingNoisyGuardEnabled,
                        onSetFadeInOnResumeEnabled = onSetFadeInOnResumeEnabled,
                        onSetDedicatedVolumeMemoryEnabled = onSetDedicatedVolumeMemoryEnabled,
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

private fun androidx.compose.foundation.lazy.LazyListScope.headphonesFullSection(
    headphoneConfig: HeadphoneConfig,
    headphoneSectionTab: Int,
    onSelectSubTab: (Int) -> Unit,
    onSetCrossfeedEnabled: (Boolean) -> Unit,
    onSetCrossfeedStrength: (Int) -> Unit,
    onSetBalanceControlEnabled: (Boolean) -> Unit,
    onSetStereoBalance: (Float) -> Unit,
    onSetBitPerfectEnabled: (Boolean) -> Unit,
    onSetLowLatencyEnabled: (Boolean) -> Unit,
    onSetUsbDacExclusiveEnabled: (Boolean) -> Unit,
    onSetHiResTargetMode: (Int) -> Unit,
    onSetBecomingNoisyGuardEnabled: (Boolean) -> Unit,
    onSetFadeInOnResumeEnabled: (Boolean) -> Unit,
    onSetDedicatedVolumeMemoryEnabled: (Boolean) -> Unit,
    onSetHeadsetControlsEnabled: (Boolean) -> Unit,
    onOpenEditClickType: (Int) -> Unit
) {
    item {
        HeadphoneStatusCard(headphoneConfig = headphoneConfig)
        Spacer(modifier = Modifier.height(16.dp))
    }

    item {
        ScrollableTabRow(
            selectedTabIndex = headphoneSectionTab,
            containerColor = SurfaceCard,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = headphoneSectionTab == 0,
                onClick = { onSelectSubTab(0) },
                text = { Text("1. Acústica & DSP", style = MaterialTheme.typography.labelMedium) },
                icon = { Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("headphone_subtab_dsp")
            )
            Tab(
                selected = headphoneSectionTab == 1,
                onClick = { onSelectSubTab(1) },
                text = { Text("2. Seguridad & Conexión", style = MaterialTheme.typography.labelMedium) },
                icon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("headphone_subtab_safety")
            )
            Tab(
                selected = headphoneSectionTab == 2,
                onClick = { onSelectSubTab(2) },
                text = { Text("3. Botones y Gestos", style = MaterialTheme.typography.labelMedium) },
                icon = { Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("headphone_subtab_gestures")
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    when (headphoneSectionTab) {
        0 -> headphonesAcousticsSection(
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
        1 -> headphonesSecuritySection(
            headphoneConfig = headphoneConfig,
            onSetBecomingNoisyGuardEnabled = onSetBecomingNoisyGuardEnabled,
            onSetFadeInOnResumeEnabled = onSetFadeInOnResumeEnabled,
            onSetDedicatedVolumeMemoryEnabled = onSetDedicatedVolumeMemoryEnabled
        )
        2 -> headphonesGesturesSection(
            headphoneConfig = headphoneConfig,
            onSetHeadsetControlsEnabled = onSetHeadsetControlsEnabled,
            onOpenEditClickType = onOpenEditClickType
        )
    }
}
