package com.example.ui.screens.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CrossfeedStrengthPreset
import com.example.model.HeadphoneConfig
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Sub-Apartado 0 de Auriculares: Acústica & DSP (Crossfeed C++20 y Balance Estéreo L/R).
 * Arquitectura: Módulo de UI que renderiza dentro del LazyColumn:
 * - Tarjeta del Filtro Crossfeed Acústico Bauer / Chu Moy en C++20 con selector de 3 presets.
 * - Tarjeta de Balance Estéreo Fino (L / R) con deslizador de precisión y botón de centrado.
 */
fun LazyListScope.headphonesAcousticsSection(
    headphoneConfig: HeadphoneConfig,
    onSetCrossfeedEnabled: (Boolean) -> Unit,
    onSetCrossfeedStrength: (Int) -> Unit,
    onSetBalanceControlEnabled: (Boolean) -> Unit,
    onSetStereoBalance: (Float) -> Unit
) {
    // Tarjeta Filtro Crossfeed C++20
    item {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("crossfeed_card")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Filtro Crossfeed Acústico",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Algoritmo Bauer / Chu Moy en C++20",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    Switch(
                        checked = headphoneConfig.isCrossfeedEnabled,
                        onCheckedChange = { onSetCrossfeedEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("crossfeed_switch")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Elimina la fatiga auditiva mezclando sutilmente una porción de audio con filtro paso-bajos (~700 Hz) y retardo de microsegundos en el oído opuesto, emulando la escucha natural de altavoces en una sala.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "🔒 Se activará en la música EXCLUSIVAMENTE cuando se detecte un auricular conectado, para no alterar la respuesta de altavoces.",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                )

                AnimatedVisibility(visible = headphoneConfig.isCrossfeedEnabled) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = "Intensidad Acústica del Crossfeed:",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        CrossfeedStrengthPreset.values().forEach { preset ->
                            val isPresetSelected = headphoneConfig.crossfeedStrength == preset.mode
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isPresetSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (isPresetSelected) MaterialTheme.colorScheme.primary else CardBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSetCrossfeedStrength(preset.mode) }
                                    .testTag("crossfeed_preset_${preset.mode}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isPresetSelected,
                                        onClick = { onSetCrossfeedStrength(preset.mode) },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = preset.title,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                        )
                                        Text(
                                            text = preset.subtitle,
                                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Tarjeta Balance Estéreo Fino (L/R)
    item {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("stereo_balance_card")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Balance Estéreo Fino (L / R)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Control Izquierda / Derecha en tiempo real",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    Switch(
                        checked = headphoneConfig.isBalanceControlEnabled,
                        onCheckedChange = { onSetBalanceControlEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.testTag("balance_control_switch")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Permite compensar asimetrías auditivas o audífonos con diferente presión. Al activarse, podrás ajustarlo aquí y directamente en el reproductor completo Now Playing en tiempo real.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )

                AnimatedVisibility(visible = headphoneConfig.isBalanceControlEnabled) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Canal Izquierdo (L)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (headphoneConfig.stereoBalance < -0.05f) MaterialTheme.colorScheme.secondary else TextSecondary,
                                    fontWeight = if (headphoneConfig.stereoBalance < -0.05f) FontWeight.Bold else FontWeight.Normal
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
                                text = "Canal Derecho (R)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (headphoneConfig.stereoBalance > 0.05f) MaterialTheme.colorScheme.secondary else TextSecondary,
                                    fontWeight = if (headphoneConfig.stereoBalance > 0.05f) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Slider(
                            value = headphoneConfig.stereoBalance,
                            onValueChange = { onSetStereoBalance(it) },
                            valueRange = -1.0f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.secondary,
                                activeTrackColor = MaterialTheme.colorScheme.secondary,
                                inactiveTrackColor = SurfaceCard
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_stereo_balance_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            OutlinedButton(
                                onClick = { onSetStereoBalance(0.0f) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.testTag("reset_balance_btn")
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restablecer al Centro (0%)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
