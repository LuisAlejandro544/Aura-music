package com.example.ui.components.audioeffects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EqualizerBand
import com.example.model.EqualizerPreset
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pestaña 0 de Efectos de Audio: Ecualizador Paramétrico de 10 Bandas ISO en C++20.
 * Arquitectura: Componente modular de UI que presenta:
 * - Switch maestro de activación del motor DSP C++20.
 * - Selector horizontal de perfiles acústicos (Presets: Rock, Pop, Jazz, etc.).
 * - Control deslizante de refuerzo dinámico de graves (Bass Boost a 60 Hz).
 * - 10 deslizadores paramétricos ISO (-15 dB a +15 dB) con filtros IIR Bi-cuadráticos de doble precisión.
 */
@Composable
fun EqualizerTabContent(
    isEnabled: Boolean,
    bands: List<EqualizerBand>,
    bassBoostLevel: Int,
    currentPreset: EqualizerPreset,
    onToggleEnabled: (Boolean) -> Unit,
    onBandLevelChange: (Int, Int) -> Unit,
    onBassBoostChange: (Int) -> Unit,
    onPresetSelect: (EqualizerPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayBands = if (bands.isNotEmpty()) bands else EqualizerPreset.DEFAULT_10_BANDS

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                            label = {
                                Text(
                                    text = preset.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sheet_bass_boost_slider")
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
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.width(84.dp)
                        ) {
                            Text(
                                text = getBandAcousticLabel(band.centerFreqHz),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isEnabled && band.levelMb != 0) MaterialTheme.colorScheme.primary else TextPrimary
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = "${band.displayFreq()}Hz",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }

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
                            modifier = Modifier.width(52.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

/**
 * Traduce frecuencias en hercios a descriptores acústicos comprensibles para cualquier usuario.
 */
private fun getBandAcousticLabel(freqHz: Int): String {
    return when {
        freqHz <= 35 -> "Subgraves"
        freqHz <= 70 -> "Bajos"
        freqHz <= 140 -> "Graves"
        freqHz <= 280 -> "Cuerpo"
        freqHz <= 600 -> "Medios Bajos"
        freqHz <= 1200 -> "Voces"
        freqHz <= 2500 -> "Claridad"
        freqHz <= 5000 -> "Presencia"
        freqHz <= 10000 -> "Brillo"
        else -> "Aire / Agudos"
    }
}
