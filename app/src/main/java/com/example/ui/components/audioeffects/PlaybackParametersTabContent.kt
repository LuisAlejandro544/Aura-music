package com.example.ui.components.audioeffects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.VocalClarityConfig
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pestaña 3 de Efectos de Audio: Control de Velocidad de Música (Speed), Velocidad/Tono de Voz (Pitch)
 * y Clarificador de Voces HD en C++20 (Mid-Side).
 */
@Composable
fun PlaybackParametersTabContent(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    pitch: Float,
    onPitchChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    vocalClarityConfig: VocalClarityConfig = VocalClarityConfig(),
    onVocalClarityEnabledChange: (Boolean) -> Unit = {},
    onVocalClarityStrengthChange: (Float) -> Unit = {}
) {
    var localSpeed by remember(speed) { mutableFloatStateOf(speed) }
    var localPitch by remember(pitch) { mutableFloatStateOf(pitch) }
    val quickValues = remember { listOf(0.8f, 1.0f, 1.25f, 1.5f, 2.0f) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Velocidad de Música y Voz",
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

            // 1. Slider de Velocidad de la Música
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Velocidad de la Música", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
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

            // Chips rápidos de velocidad de música (incluye 2.0x)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickValues.forEach { s ->
                    val isSelected = (localSpeed - s).let { it > -0.02f && it < 0.02f }
                    val labelText = "${s}x"
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
                            .testTag("speed_chip_${s}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = labelText,
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

            // 2. Slider de Velocidad de Voz / Tono (Pitch Shift hasta 2.0x)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Velocidad de Voz / Tono (Pitch)", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
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
                valueRange = 0.5f..2.0f,
                steps = 29, // Incrementos de 0.05 hasta 2.00x
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playback_pitch_slider")
            )

            // Chips rápidos de velocidad de voz / tono (incluye 2.0x)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickValues.forEach { p ->
                    val isSelected = (localPitch - p).let { it > -0.02f && it < 0.02f }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                localPitch = p
                                onPitchChange(p)
                            }
                            .testTag("pitch_chip_${p}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${p}x",
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
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(12.dp))

            // 3. Clarificador de Voces HD en C++20 (Aislamiento Mid-Side y Presencia Vocal)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Clarificar Voces HD (C++20)",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "Realza la inteligibilidad de la voz y letra sobre la pista",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
                Switch(
                    checked = vocalClarityConfig.enabled,
                    onCheckedChange = onVocalClarityEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("voice_tab_vocal_clarity_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Nivel de Claridad Vocal", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                Text(
                    text = "${(vocalClarityConfig.strength * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Slider(
                value = vocalClarityConfig.strength,
                onValueChange = {
                    if (!vocalClarityConfig.enabled) onVocalClarityEnabledChange(true)
                    onVocalClarityStrengthChange(it)
                },
                valueRange = 0.1f..1.0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_tab_vocal_clarity_slider")
            )
        }
    }
}
