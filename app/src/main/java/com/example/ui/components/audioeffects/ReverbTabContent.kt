package com.example.ui.components.audioeffects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ReverbConfig
import com.example.model.ReverbPreset
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Pestaña de Suite Reverb & Filtros Acústicos Ambientales.
 *
 * Características:
 * - Selección rápida de Presets Acústicos: Estudio, Sala Mediana, Club en Vivo, Gran Hall, Catedral y Eco Espacial.
 * - Personalización libre para usuarios avanzados:
 *   * Tamaño de Espacio / Sala (Room Size).
 *   * Tiempo de Decaimiento / Resonancia (Decay Time).
 *   * Nivel de Reverberación / Mezcla Húmeda (Wet Level en dB).
 */
@Composable
fun ReverbTabContent(
    config: ReverbConfig,
    onToggle: (Boolean) -> Unit,
    onSelectPreset: (ReverbPreset) -> Unit,
    onCustomParametersChange: (roomSize: Float, decayMs: Int, levelDb: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Cabecera con Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SurroundSound,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reverb & Espacios Acústicos",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                    Text(
                        text = "Reflexiones y acústica ambiental de sala, teatro y catedral.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }

                Switch(
                    checked = config.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("reverb_switch")
                )
            }

            AnimatedVisibility(visible = config.isEnabled) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = "Presets Acústicos",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Fila horizontal con selector de presets
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(ReverbPreset.values()) { preset ->
                            val isSelected = config.preset == preset
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectPreset(preset) },
                                label = {
                                    Text(
                                        text = preset.title,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White,
                                    containerColor = Color.White.copy(alpha = 0.05f),
                                    labelColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("reverb_preset_${preset.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Personalización Libre",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. Control de Tamaño de Sala (Room Size)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Tamaño de Sala / Espacio",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                        Text(
                            text = String.format("%.2fx", config.roomSize),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Slider(
                        value = config.roomSize,
                        onValueChange = {
                            onCustomParametersChange(it, config.decayMs, config.reverbLevelDb)
                        },
                        valueRange = 0.1f..2.0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reverb_room_size_slider")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Control de Tiempo de Decaimiento (Decay Time)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Tiempo de Decaimiento (Resonancia)",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                        Text(
                            text = "${config.decayMs} ms",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Slider(
                        value = config.decayMs.toFloat(),
                        onValueChange = {
                            onCustomParametersChange(config.roomSize, it.roundToInt(), config.reverbLevelDb)
                        },
                        valueRange = 200f..6000f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reverb_decay_slider")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Nivel de Mezcla Reverb (Wet Level)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Nivel de Reverb (Mezcla)",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                        Text(
                            text = String.format("%+.1f dB", config.reverbLevelDb),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Slider(
                        value = config.reverbLevelDb,
                        onValueChange = {
                            onCustomParametersChange(config.roomSize, config.decayMs, it)
                        },
                        valueRange = -24.0f..6.0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reverb_level_slider")
                    )
                }
            }
        }
    }
}
