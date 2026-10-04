package com.example.ui.components.audioeffects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ABLoopState
import com.example.ui.screens.nowplaying.components.formatPlaybackTime
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Pestaña 6 de Efectos de Audio: Transiciones Suaves (Crossfade), Reproducción Gapless y Repetidor de Segmento A-B.
 * Arquitectura: Componente modular de UI que presenta:
 * - Panel completo del Repetidor de Segmento A-B con fijación en vivo, ajuste fino (-1s / +1s) y limpieza.
 * - Deslizador de duración de fundido cruzado (0 a 12 segundos) entre canciones continuas.
 * - Conmutador de reproducción Gapless para eliminar silencios en conciertos y álbumes continuos.
 */
@Composable
fun TransitionsTabContent(
    crossfadeSeconds: Int,
    onCrossfadeChange: (Int) -> Unit,
    isGapless: Boolean,
    onGaplessToggle: (Boolean) -> Unit,
    abLoopState: ABLoopState = ABLoopState(),
    onMarkABPointA: () -> Unit = {},
    onMarkABPointB: () -> Unit = {},
    onToggleABLoopEnabled: (Boolean) -> Unit = {},
    onAdjustABPointA: (Long) -> Unit = {},
    onAdjustABPointB: (Long) -> Unit = {},
    onClearABLoop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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

        // Tarjeta 2: Crossfade y Gapless
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceCard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Transiciones Suaves (Crossfade)",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "Elimina cortes abruptos fundiendo la pista actual con la siguiente al cambiar o terminar.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Duración del Crossfade", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                    Text(
                        text = if (crossfadeSeconds == 0) "Desactivado" else "${crossfadeSeconds}s",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (crossfadeSeconds > 0) MaterialTheme.colorScheme.primary else TextMuted
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

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = Color(0xFF232736))

                Spacer(modifier = Modifier.height(16.dp))

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
                            text = "Reproducción continua sin silencios entre pistas continuas (álbumes en vivo y conciertos).",
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
}
