package com.example.ui.screens.settings

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
import com.example.model.Track
import com.example.ui.screens.settings.components.*
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pantalla de Temas y Ajustes para Aura Music.
 * Arquitectura Modular (MVVM):
 * Actúa como pantalla orquestadora dividida en submódulos especializados en [com.example.ui.screens.settings.components]:
 * 1. [appearanceSettingsTab]: Apariencia & Temas (paletas de color OLED, Aura dinámica de carátula, datos técnicos).
 * 2. [HeadphoneStatusCard]: Tarjeta de estado de auriculares en tiempo real.
 * 3. [headphonesAcousticsSection]: Acústica DSP Crossfeed C++20 (Bauer/Chu Moy) y Balance Estéreo L/R.
 * 4. [headphonesSecuritySection]: Protección contra desconexiones, Fade-In suave y Memoria de volumen.
 * 5. [headphonesGesturesSection]: Mapeo de botones físicos de audífonos con [HeadsetButtonActionDialog].
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
    // Configuración de Auriculares
    headphoneConfig: HeadphoneConfig = HeadphoneConfig(),
    onUpdateHeadphoneConfig: (HeadphoneConfig) -> Unit = {},
    onSetCrossfeedEnabled: (Boolean) -> Unit = {},
    onSetCrossfeedStrength: (Int) -> Unit = {},
    onSetBalanceControlEnabled: (Boolean) -> Unit = {},
    onSetStereoBalance: (Float) -> Unit = {},
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
    var mainSettingsTab by remember { mutableIntStateOf(0) }
    var headphoneSectionTab by remember { mutableIntStateOf(0) }

    // Diálogo para configurar acción de botones de auriculares
    var editingClickType by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Cabecera Principal
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
                text = "Personaliza la apariencia visual, acústica DSP y experiencia con audífonos.",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Selector de Pestaña Principal (Temas vs Auriculares vs Medios Guardados)
            TabRow(
                selectedTabIndex = mainSettingsTab,
                containerColor = SurfaceCard,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
            ) {
                Tab(
                    selected = mainSettingsTab == 0,
                    onClick = { mainSettingsTab = 0 },
                    text = { Text("Apariencia", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(19.dp)) },
                    modifier = Modifier.testTag("settings_tab_appearance")
                )
                Tab(
                    selected = mainSettingsTab == 1,
                    onClick = { mainSettingsTab = 1 },
                    text = { Text("Auriculares", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (headphoneConfig.isHeadphoneConnected) {
                                    Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                                        Text("ON", fontSize = 8.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(19.dp))
                        }
                    },
                    modifier = Modifier.testTag("settings_tab_headphones")
                )
                Tab(
                    selected = mainSettingsTab == 2,
                    onClick = { mainSettingsTab = 2 },
                    text = { Text("Medios", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                    icon = {
                        val mediaCount = allTracks.count { !it.albumArtPath.isNullOrBlank() || !it.videoUri.isNullOrBlank() }
                        BadgedBox(
                            badge = {
                                if (mediaCount > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                        Text("$mediaCount", fontSize = 8.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.PermMedia, contentDescription = null, modifier = Modifier.size(19.dp))
                        }
                    },
                    modifier = Modifier.testTag("settings_tab_stored_media")
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ==========================================
        // PESTAÑA 0: APARIENCIA & TEMAS
        // ==========================================
        if (mainSettingsTab == 0) {
            appearanceSettingsTab(
                currentTheme = currentTheme,
                onSelectTheme = onSelectTheme,
                isDynamicArtworkColorEnabled = isDynamicArtworkColorEnabled,
                onToggleDynamicArtworkColor = onToggleDynamicArtworkColor,
                isMiniPlayerVideoEnabled = isMiniPlayerVideoEnabled,
                onToggleMiniPlayerVideo = onToggleMiniPlayerVideo,
                onOpenOnboarding = onOpenOnboarding
            )
        }

        // ==========================================
        // PESTAÑA 1: AURICULARES & AUDÍFONOS
        // ==========================================
        if (mainSettingsTab == 1) {
            // Tarjeta de Estado de Audífonos en Vivo
            item {
                HeadphoneStatusCard(headphoneConfig = headphoneConfig)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Sub-Apartados de Auriculares
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
                        onClick = { headphoneSectionTab = 0 },
                        text = { Text("1. Acústica & DSP", style = MaterialTheme.typography.labelMedium) },
                        icon = { Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("headphone_subtab_dsp")
                    )
                    Tab(
                        selected = headphoneSectionTab == 1,
                        onClick = { headphoneSectionTab = 1 },
                        text = { Text("2. Seguridad & Conexión", style = MaterialTheme.typography.labelMedium) },
                        icon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("headphone_subtab_safety")
                    )
                    Tab(
                        selected = headphoneSectionTab == 2,
                        onClick = { headphoneSectionTab = 2 },
                        text = { Text("3. Botones y Gestos", style = MaterialTheme.typography.labelMedium) },
                        icon = { Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("headphone_subtab_gestures")
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Sub-Apartados modulares
            when (headphoneSectionTab) {
                0 -> headphonesAcousticsSection(
                    headphoneConfig = headphoneConfig,
                    onSetCrossfeedEnabled = onSetCrossfeedEnabled,
                    onSetCrossfeedStrength = onSetCrossfeedStrength,
                    onSetBalanceControlEnabled = onSetBalanceControlEnabled,
                    onSetStereoBalance = onSetStereoBalance
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
                    onOpenEditClickType = { editingClickType = it }
                )
            }
        }

        // ==========================================
        // PESTAÑA 2: MEDIOS Y ARCHIVOS GUARDADOS (TRANSPARENCIA)
        // ==========================================
        if (mainSettingsTab == 2) {
            storedMediaSettingsTab(
                allTracks = allTracks,
                onDeleteTrackArtwork = onDeleteTrackArtwork,
                onDeleteTrackVideo = onDeleteTrackVideo
            )
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
