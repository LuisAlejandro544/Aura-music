package com.example.ui.components.audioeffects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Pestaña 4 de Efectos de Audio: Transiciones Suaves (Crossfade) y Reproducción Gapless.
 * Arquitectura: Componente modular de UI que presenta:
 * - Deslizador de duración de fundido cruzado (0 a 12 segundos) entre canciones continuas.
 * - Conmutador de reproducción Gapless para eliminar silencios en conciertos y álbumes continuos.
 */
@Composable
fun TransitionsTabContent(
    crossfadeSeconds: Int,
    onCrossfadeChange: (Int) -> Unit,
    isGapless: Boolean,
    onGaplessToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        modifier = modifier.fillMaxWidth()
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
