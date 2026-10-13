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
 * Tarjeta Smart AutoMix DJ, crossfade Dual-Deck y gapless.
 * Rol arquitectónico: transiciones suaves entre canciones continuas.
 */
@Composable
fun TransitionAutomixCard(
    crossfadeSeconds: Int,
    onCrossfadeChange: (Int) -> Unit,
    isGapless: Boolean,
    onGaplessToggle: (Boolean) -> Unit,
    isDjAutomixEnabled: Boolean,
    onDjAutomixToggle: (Boolean) -> Unit,
    isDjEqCurveEnabled: Boolean,
    onDjEqCurveToggle: (Boolean) -> Unit,
    // Fase 1 activa en streaming (crossfade manual + gapless); DJ es Fase 4.
    isStreamingTrack: Boolean = false
) {
            // Tarjeta 2: Smart AutoMix DJ (Música Local) & Crossfade Dual-Deck con Curva Armónica
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StreamingComingSoonBlock(
                        title = "Smart AutoMix DJ en streaming disponible próximamente",
                        isStreaming = isStreamingTrack
                    ) {
                    Column {
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
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = if (isDjAutomixEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Smart AutoMix DJ",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = "LOCAL",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF10B981)
                                            ),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Detecta el outro acústico de la canción local y aplica un cruce Dual-Deck 100% simultáneo (Canción A de 100% a 0% mientras Canción B sube de 0% a 100%).",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                        }

                        Switch(
                            checked = isDjAutomixEnabled,
                            onCheckedChange = onDjAutomixToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981)
                            ),
                            modifier = Modifier.testTag("dj_automix_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Curva Armónica de Ecualización DJ en X (Bajos + Bolsillo Vocal C++20)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Curva Armónica en X (Bajos & Bolsillo Vocal)",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Atenúa subgraves (<120 Hz) y abre un bolsillo vocal en 2.6 kHz durante el cruce para que las voces y el ritmo entrante no choquen.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }

                        Switch(
                            checked = isDjEqCurveEnabled,
                            onCheckedChange = onDjEqCurveToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("dj_eq_curve_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Duración del Crossfade Dual-Deck
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Superposición Dual-Deck (Crossfade)",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                        Text(
                            text = when {
                                crossfadeSeconds > 0 -> "${crossfadeSeconds}s (Superpuesto)"
                                isDjAutomixEnabled -> "6s Auto (Smart AutoMix DJ)"
                                else -> "Desactivado (Directo)"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (crossfadeSeconds > 0 || isDjAutomixEnabled) Color(0xFF10B981) else TextMuted
                            )
                        )
                    }
                    Slider(
                        value = crossfadeSeconds.toFloat(),
                        onValueChange = { onCrossfadeChange(it.roundToInt()) },
                        valueRange = 0f..12f,
                        steps = 11,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("crossfade_slider")
                    )

                    // Botones rápidos de Crossfade Profesional
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0 to "Off", 3 to "3s", 5 to "5s DJ", 8 to "8s", 12 to "12s").forEach { (secs, label) ->
                            val isSelected = crossfadeSeconds == secs
                            FilledTonalButton(
                                onClick = { onCrossfadeChange(secs) },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 36.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f) else Color(0xFF161A26),
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.primary else TextSecondary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFF232736))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Reproducción Gapless
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reproducción Gapless",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Sin silencios entre pistas continuas (álbumes en vivo y conciertos).",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }

                        Switch(
                            checked = isGapless,
                            onCheckedChange = onGaplessToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("gapless_switch")
                        )
                    }
                }
            }
}
