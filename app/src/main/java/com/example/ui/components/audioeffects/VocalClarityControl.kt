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
 * Clarificador de voces HD Mid-Side en C++20.
 * Rol arquitectónico: sección de EqualizerTabContent (fachada pública intacta).
 */
@Composable
fun VocalClarityControl(
    config: VocalClarityConfig,
    onEnabledChange: (Boolean) -> Unit,
    onStrengthChange: (Float) -> Unit
) {
        // Control Estratégico: Clarificador de Voces HD (C++20 Mid-Side)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Clarificar Voces HD (C++20 Mid-Side)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Aísla el canal vocal central, atenúa resonancias opacas (260 Hz) y realza presencia (2.8–5.2 kHz)",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = onEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("eq_vocal_clarity_switch")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Intensidad de Claridad Vocal",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                    Text(
                        text = "${(config.strength * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Slider(
                    value = config.strength,
                    onValueChange = {
                        if (!config.enabled) onEnabledChange(true)
                        onStrengthChange(it)
                    },
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("eq_vocal_clarity_slider")
                )
            }
        }
}
