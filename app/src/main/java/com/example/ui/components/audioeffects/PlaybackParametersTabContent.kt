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
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pestaña 3 de Efectos de Audio: Control de Velocidad (Speed) y Tono (Pitch).
 * Arquitectura: Componente modular de UI que presenta:
 * - Botón de restablecimiento instantáneo a 1.0x.
 * - Deslizador fluido de velocidad de reproducción (0.50x a 2.00x) con throttling en ViewModel.
 * - Chips rápidos de velocidad (0.8x, 1.0x, 1.25x, 1.5x).
 * - Deslizador de modulación de tono musical (Pitch Shift de 0.50x a 1.50x).
 */
@Composable
fun PlaybackParametersTabContent(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    pitch: Float,
    onPitchChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var localSpeed by remember(speed) { mutableFloatStateOf(speed) }
    var localPitch by remember(pitch) { mutableFloatStateOf(pitch) }

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
