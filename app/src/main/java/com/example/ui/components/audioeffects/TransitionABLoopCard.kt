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
 * Tarjeta del Repetidor de Segmento A-B (fijar, ajuste fino y limpiar).
 * Rol arquitectónico: bucle continuo de fragmentos con estado en vivo.
 */
@Composable
fun TransitionABLoopCard(
    abLoopState: ABLoopState,
    onMarkABPointA: () -> Unit,
    onMarkABPointB: () -> Unit,
    onToggleABLoopEnabled: (Boolean) -> Unit,
    onAdjustABPointA: (Long) -> Unit,
    onAdjustABPointB: (Long) -> Unit,
    onClearABLoop: () -> Unit
) {
            // Tarjeta 1: Repetidor de Segmento A-B (A-B Loop)
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
                                imageVector = Icons.Default.Repeat,
                                contentDescription = null,
                                tint = if (abLoopState.isLoopingActive) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Repetidor de Segmento A-B",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = if (abLoopState.isLoopingActive) {
                                        "Bucle activo: ${formatPlaybackTime(abLoopState.pointAMs ?: 0L)} → ${formatPlaybackTime(abLoopState.pointBMs ?: 0L)}"
                                    } else {
                                        "Repite cualquier fragmento de la canción en bucle continuo."
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (abLoopState.isLoopingActive) Color(0xFF10B981) else TextSecondary
                                    )
                                )
                            }
                        }

                        if (abLoopState.pointAMs != null && abLoopState.pointBMs != null) {
                            Switch(
                                checked = abLoopState.enabled,
                                onCheckedChange = onToggleABLoopEnabled,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF10B981)
                                ),
                                modifier = Modifier.testTag("ab_loop_sheet_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Botones principales: Fijar A, Fijar B y Limpiar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onMarkABPointA,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("sheet_ab_set_a_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (abLoopState.pointAMs != null) {
                                    "A: ${formatPlaybackTime(abLoopState.pointAMs)}"
                                } else {
                                    "Marcar A"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onMarkABPointB,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("sheet_ab_set_b_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (abLoopState.pointBMs != null) {
                                    "B: ${formatPlaybackTime(abLoopState.pointBMs)}"
                                } else {
                                    "Marcar B"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (abLoopState.pointAMs != null || abLoopState.pointBMs != null) {
                            IconButton(
                                onClick = onClearABLoop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("sheet_ab_clear_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpiar bucle A-B",
                                    tint = TextPrimary
                                )
                            }
                        }
                    }

                    // Controles de ajuste fino (-1s / +1s) cuando A o B están fijados
                    if (abLoopState.pointAMs != null || abLoopState.pointBMs != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (abLoopState.pointAMs != null) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { onAdjustABPointA(-1000L) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 40.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Text("A -1s", style = MaterialTheme.typography.labelSmall)
                                    }
                                    FilledTonalButton(
                                        onClick = { onAdjustABPointA(1000L) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 40.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Text("A +1s", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            if (abLoopState.pointBMs != null) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { onAdjustABPointB(-1000L) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 40.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Text("B -1s", style = MaterialTheme.typography.labelSmall)
                                    }
                                    FilledTonalButton(
                                        onClick = { onAdjustABPointB(1000L) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 40.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Text("B +1s", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
}
