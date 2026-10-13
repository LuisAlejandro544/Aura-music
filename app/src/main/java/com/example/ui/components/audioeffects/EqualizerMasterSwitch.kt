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
 * Switch maestro DSP y selector de alcance por canción/global.
 * Rol arquitectónico: sección de EqualizerTabContent (fachada pública intacta).
 */
@Composable
fun EqualizerMasterSwitch(
    isEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    eqScopeMode: EqualizerScopeMode,
    onEqScopeModeChange: (EqualizerScopeMode) -> Unit
) {
        // Switch Maestro e Indicador + Selector de Alcance ("Solo esta canción" vs "Para todas las siguientes")
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceCard,
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
                            text = "Ecualizador C++20 (10 Bandas)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = if (isEnabled) {
                                if (eqScopeMode == EqualizerScopeMode.CURRENT_TRACK_ONLY) {
                                    "Activo • Aplicado solo en esta canción"
                                } else {
                                    "Procesamiento biquad 64-bit activo"
                                }
                            } else {
                                "Ecualizador desactivado (Sonido original)"
                            },
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

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Aplicar cambios del ecualizador:",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isCurrentOnly = eqScopeMode == EqualizerScopeMode.CURRENT_TRACK_ONLY
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrentOnly) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else BackgroundDark,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isCurrentOnly) MaterialTheme.colorScheme.primary else CardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onEqScopeModeChange(EqualizerScopeMode.CURRENT_TRACK_ONLY) }
                            .testTag("eq_scope_current_track_btn")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (isCurrentOnly) MaterialTheme.colorScheme.primary else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Solo esta canción",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isCurrentOnly) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrentOnly) MaterialTheme.colorScheme.primary else TextSecondary,
                                    fontSize = 11.5.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    val isGlobalAll = eqScopeMode == EqualizerScopeMode.GLOBAL_ALL_TRACKS
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isGlobalAll) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else BackgroundDark,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isGlobalAll) MaterialTheme.colorScheme.primary else CardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onEqScopeModeChange(EqualizerScopeMode.GLOBAL_ALL_TRACKS) }
                            .testTag("eq_scope_all_tracks_btn")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AllInclusive,
                                contentDescription = null,
                                tint = if (isGlobalAll) MaterialTheme.colorScheme.primary else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Las siguientes también",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isGlobalAll) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isGlobalAll) MaterialTheme.colorScheme.primary else TextSecondary,
                                    fontSize = 11.5.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = eqScopeMode.subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                )
            }
        }
}
