package com.example.ui.screens.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HeadphoneConfig
import com.example.model.HiResTargetPreset
import com.example.playback.NativeAudioEngine
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Sección Modular de Modo Bit-Perfect, Salida AAudio Ultra-Baja Latencia y DAC USB (Fase 7).
 *
 * Responsabilidades:
 * - Permitir activar/desactivar el Modo Bit-Perfect Direct 1:1 (omite filtros de coloración y AudioFlinger).
 * - Configurar la Salida AAudio de Ultra-Baja Latencia y el Modo Exclusivo para DACs USB-C externos.
 * - Seleccionar la resolución objetivo Hi-Res (Nativo 1:1, 96 kHz / 24-bit o 192 kHz / 32-bit).
 * - Mostrar telemetría en vivo de la ruta de hardware AAudio (tasa de muestreo del DAC, FramesPerBurst y latencia en ms).
 */
fun LazyListScope.bitPerfectSettingsSection(
    headphoneConfig: HeadphoneConfig,
    onSetBitPerfectEnabled: (Boolean) -> Unit,
    onSetLowLatencyEnabled: (Boolean) -> Unit,
    onSetUsbDacExclusiveEnabled: (Boolean) -> Unit,
    onSetHiResTargetMode: (Int) -> Unit
) {
    item {
        val latencyMs = remember(headphoneConfig.isLowLatencyAAudioEnabled, headphoneConfig.isBitPerfectEnabled) {
            NativeAudioEngine.getEstimatedLatencyMs()
        }
        val burstFrames = remember { NativeAudioEngine.getFramesPerBurst() }
        val hwSampleRate = remember { NativeAudioEngine.getHardwareSampleRate() }
        val activeBitDepth = remember(headphoneConfig.hiResTargetMode, headphoneConfig.isBitPerfectEnabled) {
            NativeAudioEngine.getActiveBitDepth()
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(
                1.dp,
                if (headphoneConfig.isBitPerfectEnabled) MaterialTheme.colorScheme.primary else CardBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bit_perfect_card")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Encabezado: Modo Bit-Perfect Direct 1:1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Modo Bit-Perfect Direct 1:1",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = "HI-RES",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 9.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Bypass de procesamiento para fidelidad pura de estudio",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    Switch(
                        checked = headphoneConfig.isBitPerfectEnabled,
                        onCheckedChange = onSetBitPerfectEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("bit_perfect_switch")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Transmite el flujo PCM bit a bit sin alteraciones de ecualización ni coloración hacia el DAC interno o DAC USB-C externo, conservando el rango dinámico intacto.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // Conmutador 2: Salida AAudio de Ultra-Baja Latencia
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Motor Nativo AAudio Ultra-Baja Latencia",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Ráfaga directa NDK: ${String.format("%.1f", latencyMs)} ms ($burstFrames frames @ ${hwSampleRate / 1000.0} kHz)",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                            )
                        }
                    }

                    Switch(
                        checked = headphoneConfig.isLowLatencyAAudioEnabled,
                        onCheckedChange = onSetLowLatencyEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.testTag("low_latency_aaudio_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Conmutador 3: Acceso Exclusivo DAC USB Externo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Prioridad Exclusiva para DAC USB-C",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Evita el remuestreo de AudioFlinger al conectar dongles o DACs externos",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                            )
                        }
                    }

                    Switch(
                        checked = headphoneConfig.isUsbDacExclusiveEnabled,
                        onCheckedChange = onSetUsbDacExclusiveEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("usb_dac_exclusive_switch")
                    )
                }

                // Selector de Resolución Hi-Res cuando no está bloqueado en 1:1 estricto
                AnimatedVisibility(visible = !headphoneConfig.isBitPerfectEnabled) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = "Resolución Objetivo del Motor de Salida ($activeBitDepth-bit):",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        HiResTargetPreset.values().forEach { preset ->
                            val isSelected = headphoneConfig.hiResTargetMode == preset.mode
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else CardBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSetHiResTargetMode(preset.mode) }
                                    .testTag("hires_preset_${preset.mode}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onSetHiResTargetMode(preset.mode) },
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
}
