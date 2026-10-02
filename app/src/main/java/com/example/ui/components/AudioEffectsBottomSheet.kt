package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.model.SleepTimerState
import com.example.model.Spatial8DConfig
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Hoja modal inferior de Efectos de Audio y Temporizador para Aura Music.
 * Integra:
 * - Temporizador de Apagado personalizable con fade-out progresivo de 10s.
 * - Motor de Audio Espacial 8D Binaural nativo en C++20 con órbita, intensidad y reverberación.
 * - Control de Velocidad (Speed) y Tono (Pitch) en tiempo real.
 * - Transición Suave entre Canciones (Crossfade configurable) y Reproducción Gapless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEffectsBottomSheet(
    onDismissRequest: () -> Unit,
    sleepTimerState: SleepTimerState,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onAddSleepTimerMinutes: (Int) -> Unit,
    spatial8DConfig: Spatial8DConfig,
    onSet8DEnabled: (Boolean) -> Unit,
    onSet8DOrbitSpeed: (Float) -> Unit,
    onSet8DSpatialIntensity: (Float) -> Unit,
    onSet8DRoomDepth: (Float) -> Unit,
    playbackSpeed: Float,
    onSetPlaybackSpeed: (Float) -> Unit,
    playbackPitch: Float,
    onSetPlaybackPitch: (Float) -> Unit,
    onResetSpeedAndPitch: () -> Unit,
    crossfadeSeconds: Int,
    onSetCrossfadeSeconds: (Int) -> Unit,
    isGaplessEnabled: Boolean,
    onSetGaplessEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
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
                        text = "Efectos & Herramientas de Audio",
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

            // Pestañas / Selector de Módulo
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceCard,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Temporizador", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp)) }
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
                    text = { Text("Velocidad/Tono", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Crossfade", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Contenido según pestaña
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedTab) {
                    0 -> SleepTimerSection(
                        sleepTimerState = sleepTimerState,
                        onStartTimer = onStartSleepTimer,
                        onCancelTimer = onCancelSleepTimer,
                        onAddMinutes = onAddSleepTimerMinutes
                    )
                    1 -> Spatial8DSection(
                        config = spatial8DConfig,
                        onToggle = onSet8DEnabled,
                        onOrbitSpeedChange = onSet8DOrbitSpeed,
                        onIntensityChange = onSet8DSpatialIntensity,
                        onDepthChange = onSet8DRoomDepth
                    )
                    2 -> SpeedAndPitchSection(
                        speed = playbackSpeed,
                        onSpeedChange = onSetPlaybackSpeed,
                        pitch = playbackPitch,
                        onPitchChange = onSetPlaybackPitch,
                        onReset = onResetSpeedAndPitch
                    )
                    3 -> CrossfadeAndGaplessSection(
                        crossfadeSeconds = crossfadeSeconds,
                        onCrossfadeChange = onSetCrossfadeSeconds,
                        isGapless = isGaplessEnabled,
                        onGaplessToggle = onSetGaplessEnabled
                    )
                }
            }
        }
    }
}

/**
 * Sección 1: Temporizador de Apagado Personalizable con Fade-out de 10s.
 */
@Composable
private fun SleepTimerSection(
    sleepTimerState: SleepTimerState,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onAddMinutes: (Int) -> Unit
) {
    var customMinutesInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (sleepTimerState.isActive) {
                // Estado ACTIVO con cuenta regresiva
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "TEMPORIZADOR EN CURSO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = sleepTimerState.formattedRemaining,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (sleepTimerState.isFadingOut) MaterialTheme.colorScheme.secondary else TextPrimary
                        )
                    )

                    AnimatedVisibility(visible = sleepTimerState.isFadingOut) {
                        Text(
                            text = "Atenuando volumen suavemente (fade-out 10s)...",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onAddMinutes(5) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("timer_add_5m_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+5 min")
                        }

                        Button(
                            onClick = onCancelTimer,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("timer_cancel_btn")
                        ) {
                            Text("Cancelar")
                        }
                    }
                }
            } else {
                // Estado INACTIVO: selección de minutos
                Text(
                    text = "Detener música automáticamente",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "Aplica un desvanecimiento suave de 10 segundos antes de pausar.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                // Chips de presets rápidos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onStartTimer(mins) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${mins}m",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Entrada personalizada libre de minutos
                Text(
                    text = "O introduce minutos personalizados:",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = customMinutesInput,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 4) {
                                customMinutesInput = input
                            }
                        },
                        placeholder = { Text("Ej: 25", color = TextMuted) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                val mins = customMinutesInput.toIntOrNull()
                                if (mins != null && mins > 0) {
                                    onStartTimer(mins)
                                    customMinutesInput = ""
                                }
                            }
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("timer_custom_input")
                    )

                    Button(
                        onClick = {
                            keyboardController?.hide()
                            val mins = customMinutesInput.toIntOrNull()
                            if (mins != null && mins > 0) {
                                onStartTimer(mins)
                                customMinutesInput = ""
                            }
                        },
                        enabled = (customMinutesInput.toIntOrNull() ?: 0) > 0,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 56.dp)
                            .testTag("timer_start_custom_btn")
                    ) {
                        Text("Iniciar")
                    }
                }
            }
        }
    }
}

/**
 * Sección 2: Audio Espacial 8D Binaural en C++20.
 */
@Composable
private fun Spatial8DSection(
    config: Spatial8DConfig,
    onToggle: (Boolean) -> Unit,
    onOrbitSpeedChange: (Float) -> Unit,
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
                    Text(
                        text = "Efecto de Audio 8D Espacial",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "Requiere auriculares para experimentar la órbita acústica 360° en C++20.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Switch(
                    checked = config.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("eight_d_switch")
                )
            }

            AnimatedVisibility(visible = config.enabled) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Velocidad de Rotación Órbita
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Velocidad de Órbita", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                        Text(
                            text = "${config.orbitSpeedSeconds.roundToInt()}s por vuelta",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Slider(
                        value = config.orbitSpeedSeconds,
                        onValueChange = onOrbitSpeedChange,
                        valueRange = 4f..30f,
                        steps = 25,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("eight_d_orbit_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Intensidad Espacial Binaural
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Intensidad Espacial", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                        Text(
                            text = "${(config.spatialIntensity * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Profundidad de Sala
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Profundidad Acústica de Sala", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
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
 */
@Composable
private fun SpeedAndPitchSection(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    pitch: Float,
    onPitchChange: (Float) -> Unit,
    onReset: () -> Unit
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
                    text = "%.2fx".format(speed),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
            Slider(
                value = speed,
                onValueChange = onSpeedChange,
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
                    val isSelected = (speed - s).let { it > -0.02f && it < 0.02f }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSpeedChange(s) }
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
                    text = "%.2fx".format(pitch),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                )
            }
            Slider(
                value = pitch,
                onValueChange = onPitchChange,
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
            // Gapless
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
                        text = "Reproduce pistas consecutivas sin silencio intermedio (ideal para álbumes en vivo).",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Switch(
                    checked = isGapless,
                    onCheckedChange = onGaplessToggle,
                    modifier = Modifier.testTag("gapless_switch")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            // Crossfade
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Fundido Cruzado (Crossfade)",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "Solapa suavemente el volumen al cambiar de canción.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    text = if (crossfadeSeconds == 0) "Desactivado" else "${crossfadeSeconds}s",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
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

            // Chips rápidos de Crossfade
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0 to "Apagado", 3 to "3s", 6 to "6s", 10 to "10s").forEach { (sec, label) ->
                    val isSelected = crossfadeSeconds == sec
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onCrossfadeChange(sec) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else TextPrimary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
