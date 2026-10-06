package com.example.ui.components.audioeffects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Spatial8DConfig
import com.example.ui.theme.CardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pestaña modular de controles directos de estudio para Audio Espacial 8D y 16D en C++20.
 *
 * Rol arquitectónico:
 * - Permite activar el motor espacial binaural y alternar entre:
 *   1. Audio 8D (Órbita 360° de plano único con retardo interaural ITD y Head Shadow).
 *   2. Audio 16D (Doble Órbita Multi-Capa con separación espectral <320 Hz y >320 Hz en contrarrotación).
 * - Diseño limpio y directo centrado exclusivamente en controles acústicos funcionales,
 *   sin logotipos ni animaciones decorativas innecesarias.
 */
@Composable
fun Spatial8DTabContent(
    config: Spatial8DConfig,
    onToggle: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onDepthChange: (Float) -> Unit,
    onMode16DChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeAccent = if (config.is16DMode) Color(0xFF8B5CF6) else Color(0xFF00E5FF)

    Column(modifier = modifier.fillMaxWidth()) {
        // Tarjeta de activación principal
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (config.enabled) {
                    activeAccent.copy(alpha = 0.10f)
                } else {
                    Color(0xFF141414)
                }
            ),
            border = BorderStroke(
                1.dp,
                if (config.enabled) activeAccent.copy(alpha = 0.45f) else CardBorder
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (config.is16DMode) {
                                "Motor Espacial 16D Multi-Órbita (C++20)"
                            } else {
                                "Motor Espacial 8D Binaural (C++20)"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = if (config.is16DMode) {
                                "Separa graves y voces en dos órbitas independientes de contrarrotación"
                            } else {
                                "Rotación acústica 360° con retardo interaural ITD y sombra de cabeza"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = activeAccent
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("spatial_8d_switch")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selector estratégico de arquitectura espacial: 8D vs 16D
                Text(
                    text = "Arquitectura de Espacialización:",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = !config.is16DMode,
                        onClick = {
                            onMode16DChange(false)
                            if (!config.enabled) onToggle(true)
                        },
                        label = {
                            Text(
                                text = "Audio 8D (Órbita 360°)",
                                fontWeight = if (!config.is16DMode) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.SurroundSound,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.20f),
                            selectedLabelColor = Color(0xFF00E5FF),
                            selectedLeadingIconColor = Color(0xFF00E5FF)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("spatial_mode_8d_chip")
                    )

                    FilterChip(
                        selected = config.is16DMode,
                        onClick = {
                            onMode16DChange(true)
                            if (!config.enabled) onToggle(true)
                        },
                        label = {
                            Text(
                                text = "Audio 16D (Doble Capa)",
                                fontWeight = if (config.is16DMode) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF8B5CF6).copy(alpha = 0.24f),
                            selectedLabelColor = Color(0xFFC4B5FD),
                            selectedLeadingIconColor = Color(0xFFC4B5FD)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("spatial_mode_16d_chip")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.04f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = activeAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (config.is16DMode) {
                                "16D divide el espectro en 320 Hz: los subgraves orbitan con fase estable mientras las voces y armónicos trazan una lemniscata envolvente."
                            } else {
                                "8D desplaza la mezcla completa alrededor de una esfera acústica de 360°."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Control 1: Velocidad de Rotación
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Velocidad de Rotación",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                text = "${config.orbitSpeedSeconds.toInt()}s / vuelta",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = activeAccent,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Text(
            text = "Menor tiempo equivale a un giro más rápido alrededor de tus oídos",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )
        Slider(
            value = config.orbitSpeedSeconds,
            onValueChange = {
                if (!config.enabled) onToggle(true)
                onSpeedChange(it)
            },
            valueRange = 4f..30f,
            colors = SliderDefaults.colors(
                thumbColor = activeAccent,
                activeTrackColor = activeAccent,
                inactiveTrackColor = CardBorder
            ),
            modifier = Modifier.testTag("spatial_8d_speed_slider")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Control 2: Intensidad Espacial
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Amplitud e Intensidad Espacial",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                text = "${(config.spatialIntensity * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = activeAccent,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Slider(
            value = config.spatialIntensity,
            onValueChange = {
                if (!config.enabled) onToggle(true)
                onIntensityChange(it)
            },
            valueRange = 0.2f..1.0f,
            colors = SliderDefaults.colors(
                thumbColor = activeAccent,
                activeTrackColor = activeAccent,
                inactiveTrackColor = CardBorder
            ),
            modifier = Modifier.testTag("spatial_8d_intensity_slider")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Control 3: Profundidad de Sala
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Profundidad de Sala (Early Reflections)",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                text = "${(config.roomDepth * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = activeAccent,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Slider(
            value = config.roomDepth,
            onValueChange = {
                if (!config.enabled) onToggle(true)
                onDepthChange(it)
            },
            valueRange = 0.0f..1.0f,
            colors = SliderDefaults.colors(
                thumbColor = activeAccent,
                activeTrackColor = activeAccent,
                inactiveTrackColor = CardBorder
            ),
            modifier = Modifier.testTag("spatial_8d_depth_slider")
        )
    }
}
