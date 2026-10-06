package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ABLoopState
import com.example.model.EqualizerBand
import com.example.model.EqualizerPreset
import com.example.model.HeadphoneConfig
import com.example.model.ReverbConfig
import com.example.model.ReverbPreset
import com.example.model.SleepTimerState
import com.example.model.Spatial8DConfig
import com.example.model.VocalClarityConfig
import com.example.ui.components.audioeffects.*
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Hoja modal unificada de Efectos de Audio, Ecualizador y Herramientas para Aura Music.
 * Arquitectura Modular (MVVM):
 * Actúa como orquestador de pestañas y contenedor modal, delegando el renderizado de cada sección
 * en submódulos especializados en [com.example.ui.components.audioeffects]:
 * - [EqualizerTabContent]: Ecualizador Paramétrico de 10 Bandas ISO en C++20, Clarificador Vocal HD, Presets y Bass Boost.
 * - [Spatial8DTabContent]: Motor de Audio Espacial 8D (Órbita 360°) y 16D (Doble Órbita Multi-Capa) en C++20.
 * - [ReverbTabContent]: Suite Reverb Híbrida (Presets ambientales de sala/catedral/club + Ajuste libre de tamaño, decay y wet).
 * - [SleepTimerTabContent]: Temporizador de Apagado personalizable con fade-out progresivo de 10s.
 * - [PlaybackParametersTabContent]: Control de Velocidad, Tono (Pitch Shift) y Clarificador Vocal HD.
 * - [TransitionsTabContent]: Repetidor de Segmento A-B, Transiciones suaves (Crossfade configurable) y Reproducción Gapless.
 * - [BalanceAndHeadphonesTabContent]: Balance Estéreo Fino L/R y Filtro Crossfeed C++20 para audífonos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEffectsBottomSheet(
    onDismissRequest: () -> Unit,
    // Ecualizador de 10 bandas C++20
    isEqEnabled: Boolean = true,
    eqBands: List<EqualizerBand> = emptyList(),
    bassBoostLevel: Int = 0,
    currentPreset: EqualizerPreset = EqualizerPreset.PRESETS.first(),
    onToggleEqEnabled: (Boolean) -> Unit = {},
    onBandLevelChange: (Int, Int) -> Unit = { _, _ -> },
    onBassBoostChange: (Int) -> Unit = {},
    onPresetSelect: (EqualizerPreset) -> Unit = {},
    // Clarificador de Voces HD (C++20 Mid-Side)
    vocalClarityConfig: VocalClarityConfig = VocalClarityConfig(),
    onSetVocalClarityEnabled: (Boolean) -> Unit = {},
    onSetVocalClarityStrength: (Float) -> Unit = {},
    // Audio 8D / 16D
    spatial8DConfig: Spatial8DConfig = Spatial8DConfig(),
    onSet8DEnabled: (Boolean) -> Unit = {},
    onSet8DMode16D: (Boolean) -> Unit = {},
    onSet8DOrbitSpeed: (Float) -> Unit = {},
    onSet8DSpatialIntensity: (Float) -> Unit = {},
    onSet8DRoomDepth: (Float) -> Unit = {},
    // Suite Reverb & Filtros Acústicos
    reverbConfig: ReverbConfig = ReverbConfig(),
    onSetReverbEnabled: (Boolean) -> Unit = {},
    onSetReverbPreset: (ReverbPreset) -> Unit = {},
    onSetReverbCustomParameters: (roomSize: Float, decayMs: Int, levelDb: Float) -> Unit = { _, _, _ -> },
    // Temporizador de Apagado
    sleepTimerState: SleepTimerState = SleepTimerState(),
    onStartSleepTimer: (Int) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onAddSleepTimerMinutes: (Int) -> Unit = {},
    // Velocidad y Tono
    playbackSpeed: Float = 1.0f,
    onSetPlaybackSpeed: (Float) -> Unit = {},
    playbackPitch: Float = 1.0f,
    onSetPlaybackPitch: (Float) -> Unit = {},
    onResetSpeedAndPitch: () -> Unit = {},
    // Transiciones y Repetidor A-B
    crossfadeSeconds: Int = 0,
    onSetCrossfadeSeconds: (Int) -> Unit = {},
    isGaplessEnabled: Boolean = true,
    onSetGaplessEnabled: (Boolean) -> Unit = {},
    abLoopState: ABLoopState = ABLoopState(),
    onMarkABPointA: () -> Unit = {},
    onMarkABPointB: () -> Unit = {},
    onToggleABLoopEnabled: (Boolean) -> Unit = {},
    onAdjustABPointA: (Long) -> Unit = {},
    onAdjustABPointB: (Long) -> Unit = {},
    onClearABLoop: () -> Unit = {},
    isDjAutomixEnabled: Boolean = false,
    onSetDjAutomixEnabled: (Boolean) -> Unit = {},
    isDjEqCurveEnabled: Boolean = true,
    onSetDjEqCurveEnabled: (Boolean) -> Unit = {},
    volumeNormalizationConfig: com.example.model.VolumeNormalizationConfig = com.example.model.VolumeNormalizationConfig(),
    onSetVolumeNormalizationEnabled: (Boolean) -> Unit = {},
    onSetVolumeNormalizationMode: (Int) -> Unit = {},
    // Auriculares y Balance L/R
    headphoneConfig: HeadphoneConfig = HeadphoneConfig(),
    onSetCrossfeedEnabled: (Boolean) -> Unit = {},
    onSetCrossfeedStrength: (Int) -> Unit = {},
    onSetBalanceControlEnabled: (Boolean) -> Unit = {},
    onSetStereoBalance: (Float) -> Unit = {},
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 6)) }
    val keyboardController = LocalSoftwareKeyboardController.current

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = BackgroundDark,
        tonalElevation = 8.dp,
        modifier = modifier.testTag("audio_effects_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Cabecera
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Efectos & Ajustes de Audio",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextSecondary)
                }
            }

            // Pestañas / Selector de Módulo con Scroll
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceCard,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Ecualizador", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Audio 8D / 16D", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Reverb", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.SurroundSound, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Temporizador", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("Velocidad/Voz", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 5,
                    onClick = { selectedTab = 5 },
                    text = { Text("Bucle A-B / Trans.", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 6,
                    onClick = { selectedTab = 6 },
                    text = { Text("Balance L/R", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.Balance, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Contenido modularizado según pestaña seleccionada
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedTab) {
                    0 -> EqualizerTabContent(
                        isEnabled = isEqEnabled,
                        bands = eqBands,
                        bassBoostLevel = bassBoostLevel,
                        currentPreset = currentPreset,
                        onToggleEnabled = onToggleEqEnabled,
                        onBandLevelChange = onBandLevelChange,
                        onBassBoostChange = onBassBoostChange,
                        onPresetSelect = onPresetSelect,
                        vocalClarityConfig = vocalClarityConfig,
                        onVocalClarityEnabledChange = onSetVocalClarityEnabled,
                        onVocalClarityStrengthChange = onSetVocalClarityStrength
                    )
                    1 -> Spatial8DTabContent(
                        config = spatial8DConfig,
                        onToggle = onSet8DEnabled,
                        onSpeedChange = onSet8DOrbitSpeed,
                        onIntensityChange = onSet8DSpatialIntensity,
                        onDepthChange = onSet8DRoomDepth,
                        onMode16DChange = onSet8DMode16D
                    )
                    2 -> ReverbTabContent(
                        config = reverbConfig,
                        onToggle = onSetReverbEnabled,
                        onSelectPreset = onSetReverbPreset,
                        onCustomParametersChange = onSetReverbCustomParameters
                    )
                    3 -> SleepTimerTabContent(
                        timerState = sleepTimerState,
                        onStartTimer = {
                            keyboardController?.hide()
                            onStartSleepTimer(it)
                        },
                        onCancelTimer = onCancelSleepTimer,
                        onAddMinutes = onAddSleepTimerMinutes
                    )
                    4 -> PlaybackParametersTabContent(
                        speed = playbackSpeed,
                        onSpeedChange = onSetPlaybackSpeed,
                        pitch = playbackPitch,
                        onPitchChange = onSetPlaybackPitch,
                        onReset = onResetSpeedAndPitch,
                        vocalClarityConfig = vocalClarityConfig,
                        onVocalClarityEnabledChange = onSetVocalClarityEnabled,
                        onVocalClarityStrengthChange = onSetVocalClarityStrength
                    )
                    5 -> TransitionsTabContent(
                        crossfadeSeconds = crossfadeSeconds,
                        onCrossfadeChange = onSetCrossfadeSeconds,
                        isGapless = isGaplessEnabled,
                        onGaplessToggle = onSetGaplessEnabled,
                        abLoopState = abLoopState,
                        onMarkABPointA = onMarkABPointA,
                        onMarkABPointB = onMarkABPointB,
                        onToggleABLoopEnabled = onToggleABLoopEnabled,
                        onAdjustABPointA = onAdjustABPointA,
                        onAdjustABPointB = onAdjustABPointB,
                        onClearABLoop = onClearABLoop,
                        isDjAutomixEnabled = isDjAutomixEnabled,
                        onDjAutomixToggle = onSetDjAutomixEnabled,
                        isDjEqCurveEnabled = isDjEqCurveEnabled,
                        onDjEqCurveToggle = onSetDjEqCurveEnabled,
                        isVolumeNormalizationEnabled = volumeNormalizationConfig.enabled,
                        onVolumeNormalizationToggle = onSetVolumeNormalizationEnabled,
                        volumeNormalizationMode = volumeNormalizationConfig.mode,
                        onVolumeNormalizationModeChange = onSetVolumeNormalizationMode
                    )
                    6 -> BalanceAndHeadphonesTabContent(
                        headphoneConfig = headphoneConfig,
                        onSetCrossfeedEnabled = onSetCrossfeedEnabled,
                        onSetCrossfeedStrength = onSetCrossfeedStrength,
                        onSetBalanceControlEnabled = onSetBalanceControlEnabled,
                        onSetStereoBalance = onSetStereoBalance
                    )
                }
            }
        }
    }
}
