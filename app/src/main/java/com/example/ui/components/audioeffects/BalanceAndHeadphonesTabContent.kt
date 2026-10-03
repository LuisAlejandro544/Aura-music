package com.example.ui.components.audioeffects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HeadphoneConfig
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pestaña 5 de Efectos de Audio: Balance Estéreo Fino (L/R) y Filtro Crossfeed C++20 para Audífonos.
 * Arquitectura: Componente modular de UI que presenta:
 * - Tarjeta de Balance Estéreo Fino L/R con switch, indicador y botón de centrado (0%).
 * - Tarjeta del Filtro Crossfeed acústico nativo (Bauer / Chu Moy en C++20) con indicador de auriculares conectados y chips de intensidad (Sutil, Moderado, Intenso).
 */
@Composable
fun BalanceAndHeadphonesTabContent(
    headphoneConfig: HeadphoneConfig,
    onSetCrossfeedEnabled: (Boolean) -> Unit,
    onSetCrossfeedStrength: (Int) -> Unit,
    onSetBalanceControlEnabled: (Boolean) -> Unit,
    onSetStereoBalance: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
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
