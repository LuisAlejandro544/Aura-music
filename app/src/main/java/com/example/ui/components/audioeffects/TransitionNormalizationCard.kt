package com.example.ui.components.audioeffects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.ABLoopState
import com.example.feature.nowplaying.components.formatPlaybackTime
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Tarjeta de Normalización de Volumen Inteligente (EBU R128).
 * Rol arquitectónico: modos Sutil / Estándar / Alto por LUFS.
 */
@Composable
fun TransitionNormalizationCard(
    isVolumeNormalizationEnabled: Boolean,
    onVolumeNormalizationToggle: (Boolean) -> Unit,
    volumeNormalizationMode: Int,
    onVolumeNormalizationModeChange: (Int) -> Unit
) {
            // Tarjeta 3: Normalización de Volumen Inteligente (Loudness Normalizer EBU R128)
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = if (isVolumeNormalizationEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Normalización de Volumen Inteligente",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = "EBU R128 en C++20. Elimina saltos bruscos entre canciones de distintas fuentes.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                        }

                        Switch(
                            checked = isVolumeNormalizationEnabled,
                            onCheckedChange = onVolumeNormalizationToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981)
                            ),
                            modifier = Modifier.testTag("volume_normalization_switch")
                        )
                    }

                    if (isVolumeNormalizationEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Nivel Objetivo de Sonoridad (Target LUFS)",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontWeight = FontWeight.Medium)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Modo 0: Sutil (-18 LUFS)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (volumeNormalizationMode == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color(0xFF161A26),
                                border = BorderStroke(
                                    1.dp,
                                    if (volumeNormalizationMode == 0) MaterialTheme.colorScheme.primary else Color(0xFF282D3D)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onVolumeNormalizationModeChange(0) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Sutil",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (volumeNormalizationMode == 0) MaterialTheme.colorScheme.primary else TextPrimary
                                        )
                                    )
                                    Text(
                                        text = "-18 LUFS",
                                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                    )
                                }
                            }

                            // Modo 1: Estándar (-14 LUFS)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (volumeNormalizationMode == 1) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF161A26),
                                border = BorderStroke(
                                    1.dp,
                                    if (volumeNormalizationMode == 1) Color(0xFF10B981) else Color(0xFF282D3D)
                                ),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .clickable { onVolumeNormalizationModeChange(1) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = if (volumeNormalizationMode == 1) Color(0xFF10B981) else TextSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Estándar",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (volumeNormalizationMode == 1) Color(0xFF10B981) else TextPrimary
                                            )
                                        )
                                    }
                                    Text(
                                        text = "-14 LUFS (Recomendado)",
                                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                    )
                                }
                            }

                            // Modo 2: Alto (-11 LUFS)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (volumeNormalizationMode == 2) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color(0xFF161A26),
                                border = BorderStroke(
                                    1.dp,
                                    if (volumeNormalizationMode == 2) MaterialTheme.colorScheme.primary else Color(0xFF282D3D)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onVolumeNormalizationModeChange(2) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Alto",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (volumeNormalizationMode == 2) MaterialTheme.colorScheme.primary else TextPrimary
                                        )
                                    )
                                    Text(
                                        text = "-11 LUFS",
                                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = when (volumeNormalizationMode) {
                                0 -> "Máximo rango dinámico para grabaciones acústicas o de alta fidelidad sin compresión."
                                2 -> "Volumen constante y potente, ideal para entornos ruidosos o altavoces portátiles."
                                else -> "Nivel estándar de estudio (-14 LUFS): balance ideal entre dinámica y volumen constante."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                }
            }
}
