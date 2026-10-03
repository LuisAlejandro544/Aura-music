package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EqualizerBand
import com.example.model.EqualizerPreset
import com.example.model.SleepTimerState
import com.example.model.Spatial8DConfig
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Hoja modal unificada de Efectos de Audio, Ecualizador y Herramientas para Aura Music.
 * Integra:
 * - Ecualizador Paramétrico de 10 Bandas ISO en C++20 con Presets acústicos y Bass Boost.
 * - Motor de Audio Espacial 8D Binaural nativo en C++20 con órbita, intensidad y reverberación.
 * - Temporizador de Apagado personalizable con fade-out progresivo de 10s.
 * - Control de Velocidad (Speed) y Tono (Pitch) en tiempo real protegido contra saturación.
 * - Transición Suave entre Canciones (Crossfade configurable) y Reproducción Gapless.
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
    // Temporizador de Apagado
    sleepTimerState: SleepTimerState,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onAddSleepTimerMinutes: (Int) -> Unit,
    // Audio 8D
    spatial8DConfig: Spatial8DConfig,
    onSet8DEnabled: (Boolean) -> Unit,
    onSet8DOrbitSpeed: (Float) -> Unit,
    onSet8DSpatialIntensity: (Float) -> Unit,
    onSet8DRoomDepth: (Float) -> Unit,
    // Velocidad y Tono
    playbackSpeed: Float,
    onSetPlaybackSpeed: (Float) -> Unit,
    playbackPitch: Float,
    onSetPlaybackPitch: (Float) -> Unit,
    onResetSpeedAndPitch: () -> Unit,
    // Transiciones
    crossfadeSeconds: Int,
    onSetCrossfadeSeconds: (Int) -> Unit,
    isGaplessEnabled: Boolean,
    onSetGaplessEnabled: (Boolean) -> Unit,
    // Auriculares y Balance L/R
    headphoneConfig: com.example.model.HeadphoneConfig = com.example.model.HeadphoneConfig(),
    onSetCrossfeedEnabled: (Boolean) -> Unit = {},
    onSetCrossfeedStrength: (Int) -> Unit = {},
    onSetBalanceControlEnabled: (Boolean) -> Unit = {},
    onSetStereoBalance: (Float) -> Unit = {},
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 5)) }
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
                    text = { Text("Audio 8D", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Temporizador", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Velocidad/Voz", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("Transiciones", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 5,
                    onClick = { selectedTab = 5 },
                    text = { Text("Balance L/R", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.Balance, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Contenido según pestaña seleccionada
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
                        onPresetSelect = onPresetSelect
                    )
                    1 -> Spatial8DSection(
                        config = spatial8DConfig,
                        onToggle = onSet8DEnabled,
                        onSpeedChange = onSet8DOrbitSpeed,
                        onIntensityChange = onSet8DSpatialIntensity,
                        onDepthChange = onSet8DRoomDepth
                    )
                    2 -> SleepTimerSection(
                        timerState = sleepTimerState,
                        onStartTimer = {
                            keyboardController?.hide()
                            onStartSleepTimer(it)
                        },
                        onCancelTimer = onCancelSleepTimer,
                        onAddMinutes = onAddSleepTimerMinutes
                    )
                    3 -> SpeedAndPitchSection(
                        speed = playbackSpeed,
                        onSpeedChange = onSetPlaybackSpeed,
                        pitch = playbackPitch,
                        onPitchChange = onSetPlaybackPitch,
                        onReset = onResetSpeedAndPitch
                    )
                    4 -> CrossfadeAndGaplessSection(
                        crossfadeSeconds = crossfadeSeconds,
                        onCrossfadeChange = onSetCrossfadeSeconds,
                        isGapless = isGaplessEnabled,
                        onGaplessToggle = onSetGaplessEnabled
                    )
                    5 -> BalanceAndHeadphonesContent(
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

/**
 * Sección 0: Ecualizador Paramétrico de 10 Bandas ISO en C++20 con Presets y Bass Boost.
 */
@Composable
private fun EqualizerTabContent(
    isEnabled: Boolean,
    bands: List<EqualizerBand>,
    bassBoostLevel: Int,
    currentPreset: EqualizerPreset,
    onToggleEnabled: (Boolean) -> Unit,
    onBandLevelChange: (Int, Int) -> Unit,
    onBassBoostChange: (Int) -> Unit,
    onPresetSelect: (EqualizerPreset) -> Unit
) {
    val displayBands = if (bands.isNotEmpty()) bands else EqualizerPreset.DEFAULT_10_BANDS

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Switch Maestro e Indicador
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceCard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Ecualizador C++20 (10 Bandas)",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = if (isEnabled) "Procesamiento biquad 64-bit activo" else "Ecualizador desactivado",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isEnabled) MaterialTheme.colorScheme.primary else TextSecondary
                        )
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("eq_sheet_master_switch")
                )
            }
        }

        // Presets Acústicos
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceCard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Perfiles Acústicos (Presets)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(EqualizerPreset.PRESETS) { preset ->
                        val isSelected = preset.name == currentPreset.name
                        FilterChip(
                            selected = isSelected,
                            onClick = { onPresetSelect(preset) },
                            label = { Text(preset.name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White,
                                containerColor = BackgroundDark,
                                labelColor = TextSecondary
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }
        }

        // Control de Refuerzo de Bajos (Bass Boost)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceCard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Refuerzo de Bajos (Bass Boost)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                    Text(
                        text = "${(bassBoostLevel / 10)}%",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    )
                }

                Slider(
                    value = bassBoostLevel.toFloat(),
                    onValueChange = { onBassBoostChange(it.toInt()) },
                    valueRange = 0f..1000f,
                    enabled = isEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("sheet_bass_boost_slider")
                )
            }
        }

        // Bandas de Frecuencia (10 Bandas ISO)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceCard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Bandas Paramétricas ISO (-15 dB a +15 dB)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                displayBands.forEach { band ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${band.displayFreq()}Hz",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            ),
                            modifier = Modifier.width(64.dp)
                        )

                        Slider(
                            value = band.levelMb / 100f,
                            onValueChange = { onBandLevelChange(band.index, (it * 100).toInt()) },
                            valueRange = -15f..15f,
                            enabled = isEnabled,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_eq_band_${band.index}")
                        )

                        Text(
                            text = band.gainDbFormatted(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (band.levelMb != 0) MaterialTheme.colorScheme.primary else TextMuted
                            ),
                            modifier = Modifier.width(58.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sección 1: Temporizador de Apagado (Sleep Timer con Fade-Out de 10s).
 */
@Composable
private fun SleepTimerSection(
    timerState: SleepTimerState,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onAddMinutes: (Int) -> Unit
) {
    var customMinutesInput by remember { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Temporizador de Apagado",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Text(
                text = "Detiene la música suavemente con una atenuación progresiva de 10s para no interrumpir tu descanso.",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (timerState.isActive) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (timerState.isFadingOut) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = BorderStroke(
                        1.dp,
                        if (timerState.isFadingOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = timerState.formattedRemaining,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = if (timerState.isFadingOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = if (timerState.isFadingOut) "Atenuando volumen (Fade-out)..." else "Tiempo restante para pausar",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { onAddMinutes(5) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+5 min")
                            }

                            Button(
                                onClick = onCancelTimer,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancelar", color = Color.White)
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Selecciona una duración:",
                    style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BackgroundDark,
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onStartTimer(mins) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$mins m",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customMinutesInput,
                        onValueChange = { customMinutesInput = it.filter { char -> char.isDigit() }.take(3) },
                        label = { Text("Minutos libres") },
                        placeholder = { Text("Ej: 20") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val mins = customMinutesInput.toIntOrNull() ?: 0
                                if (mins > 0) {
                                    onStartTimer(mins)
                                    customMinutesInput = ""
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sleep_timer_custom_input")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val mins = customMinutesInput.toIntOrNull() ?: 0
                            if (mins > 0) {
                                onStartTimer(mins)
                                customMinutesInput = ""
                            }
                        },
                        enabled = (customMinutesInput.toIntOrNull() ?: 0) > 0,
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("Iniciar")
                    }
                }
            }
        }
    }
}

/**
 * Sección 2: Motor de Audio Espacial 8D Binaural nativo en C++20.
 */
@Composable
private fun Spatial8DSection(
    config: Spatial8DConfig,
    onToggle: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onDepthChange: (Float) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Audio 8D Espacial (C++20)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                    Text(
                        text = "Efecto binaural orbital 360° para auriculares.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }

                Switch(
                    checked = config.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.testTag("eight_d_switch")
                )
            }

            AnimatedVisibility(visible = config.enabled) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Velocidad de Rotación", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                        Text(
                            text = "${config.orbitSpeedSeconds.roundToInt()}s por vuelta",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                    Slider(
                        value = config.orbitSpeedSeconds,
                        onValueChange = onSpeedChange,
                        valueRange = 4.0f..30.0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("eight_d_speed_slider")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Intensidad Espacial (Paneo)", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                        Text(
                            text = "${(config.spatialIntensity * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                    Slider(
                        value = config.spatialIntensity,
                        onValueChange = onIntensityChange,
                        valueRange = 0.2f..1.0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("eight_d_intensity_slider")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Reverberación de Sala", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                        Text(
                            text = "${(config.roomDepth * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                    Slider(
                        value = config.roomDepth,
                        onValueChange = onDepthChange,
                        valueRange = 0.0f..1.0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("eight_d_depth_slider")
                    )
                }
            }
        }
    }
}

/**
 * Sección 3: Control de Velocidad y Tono (Playback Parameters).
 * Protegido contra saturación de llamadas y pérdida de reproducción.
 */
@Composable
private fun SpeedAndPitchSection(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    pitch: Float,
    onPitchChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    var localSpeed by remember(speed) { mutableFloatStateOf(speed) }
    var localPitch by remember(pitch) { mutableFloatStateOf(pitch) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Velocidad y Tono Musical",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                TextButton(
                    onClick = onReset,
                    modifier = Modifier.testTag("reset_speed_pitch_btn")
                ) {
                    Text("Restablecer 1.0x", color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Slider de Velocidad
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Velocidad de Reproducción", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                Text(
                    text = "%.2fx".format(localSpeed),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
            Slider(
                value = localSpeed,
                onValueChange = {
                    localSpeed = it
                    onSpeedChange(it)
                },
                onValueChangeFinished = {
                    onSpeedChange(localSpeed)
                },
                valueRange = 0.5f..2.0f,
                steps = 29, // Incrementos de 0.05
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playback_speed_slider")
            )

            // Chips rápidos de velocidad
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0.8f, 1.0f, 1.25f, 1.5f).forEach { s ->
                    val isSelected = (localSpeed - s).let { it > -0.02f && it < 0.02f }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                localSpeed = s
                                onSpeedChange(s)
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${s}x",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else TextPrimary
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Slider de Tono (Pitch)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tono (Pitch Shift)", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                Text(
                    text = "%.2fx".format(localPitch),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                )
            }
            Slider(
                value = localPitch,
                onValueChange = {
                    localPitch = it
                    onPitchChange(it)
                },
                onValueChangeFinished = {
                    onPitchChange(localPitch)
                },
                valueRange = 0.5f..1.5f,
                steps = 19,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playback_pitch_slider")
            )
        }
    }
}

/**
 * Sección 4: Transición Suave entre Canciones (Crossfade) y Reproducción Gapless.
 */
@Composable
private fun CrossfadeAndGaplessSection(
    crossfadeSeconds: Int,
    onCrossfadeChange: (Int) -> Unit,
    isGapless: Boolean,
    onGaplessToggle: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Transiciones Suaves (Crossfade)",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Text(
                text = "Elimina cortes abruptos fundiendo la pista actual con la siguiente al cambiar o terminar.",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Duración del Crossfade", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                Text(
                    text = if (crossfadeSeconds == 0) "Desactivado" else "${crossfadeSeconds}s",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (crossfadeSeconds > 0) MaterialTheme.colorScheme.primary else TextMuted
                    )
                )
            }
            Slider(
                value = crossfadeSeconds.toFloat(),
                onValueChange = { onCrossfadeChange(it.roundToInt()) },
                valueRange = 0f..12f,
                steps = 11,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("crossfade_slider")
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = Color(0xFF232736))

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reproducción Gapless",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "Reproducción continua sin silencios entre pistas continuas (álbumes en vivo y conciertos).",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }

                Switch(
                    checked = isGapless,
                    onCheckedChange = onGaplessToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("gapless_switch")
                )
            }
        }
    }
}

/**
 * Sección 5: Balance Estéreo Fino (L/R) y Filtro Crossfeed C++20 para Audífonos.
 */
@Composable
private fun BalanceAndHeadphonesContent(
    headphoneConfig: com.example.model.HeadphoneConfig,
    onSetCrossfeedEnabled: (Boolean) -> Unit,
    onSetCrossfeedStrength: (Int) -> Unit,
    onSetBalanceControlEnabled: (Boolean) -> Unit,
    onSetStereoBalance: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .padding(bottom = 16.dp)
    ) {
        // Balance Estéreo Fino L/R
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("modal_balance_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Balance Estéreo Fino (L / R)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                        )
                        Text(
                            text = "Ajusta la ganancia Izquierda / Derecha en tiempo real",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                        )
                    }

                    Switch(
                        checked = headphoneConfig.isBalanceControlEnabled,
                        onCheckedChange = onSetBalanceControlEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.testTag("modal_balance_switch")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "L",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (headphoneConfig.stereoBalance < -0.05f) MaterialTheme.colorScheme.secondary else TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = headphoneConfig.formattedBalance,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    )
                    Text(
                        text = "R",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (headphoneConfig.stereoBalance > 0.05f) MaterialTheme.colorScheme.secondary else TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Slider(
                    value = headphoneConfig.stereoBalance,
                    onValueChange = onSetStereoBalance,
                    enabled = headphoneConfig.isBalanceControlEnabled,
                    valueRange = -1.0f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary,
                        inactiveTrackColor = Color(0xFF232736)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("modal_balance_slider")
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    OutlinedButton(
                        onClick = { onSetStereoBalance(0.0f) },
                        enabled = headphoneConfig.isBalanceControlEnabled,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Centrar (0%)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Estado del Filtro Crossfeed C++20
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("modal_crossfeed_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Filtro Crossfeed (Bauer / Chu Moy)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                        )
                        Text(
                            text = if (headphoneConfig.isHeadphoneConnected) "🎧 Auriculares detectados: Filtro C++20 activo" else "🔈 Altavoz del teléfono: Crossfeed en pausa",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (headphoneConfig.isHeadphoneConnected) MaterialTheme.colorScheme.primary else TextMuted,
                                fontSize = 11.sp
                            )
                        )
                    }

                    Switch(
                        checked = headphoneConfig.isCrossfeedEnabled,
                        onCheckedChange = onSetCrossfeedEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("modal_crossfeed_switch")
                    )
                }

                if (headphoneConfig.isCrossfeedEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Intensidad Acústica:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            0 to "Sutil",
                            1 to "Moderado",
                            2 to "Intenso"
                        ).forEach { (mode, label) ->
                            val isSelected = headphoneConfig.crossfeedStrength == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSetCrossfeedStrength(mode) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
