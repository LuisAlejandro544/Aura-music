package com.example.ui.components.audioeffects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.EqualizerBand
import com.example.core.model.EqualizerPreset
import com.example.core.model.EqualizerScopeMode
import com.example.core.model.VocalClarityConfig
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Rejilla de 10 bandas ISO con etiquetas acústicas.
 * Rol arquitectónico: sección de EqualizerTabContent (fachada pública intacta).
 */
@Composable
fun EqualizerBandsGrid(
    displayBands: List<EqualizerBand>,
    isEnabled: Boolean,
    onBandLevelChange: (Int, Int) -> Unit
) {
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
                                text = acousticLabel(band.centerFreqHz),
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

private fun acousticLabel(freqHz: Int): String {
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
