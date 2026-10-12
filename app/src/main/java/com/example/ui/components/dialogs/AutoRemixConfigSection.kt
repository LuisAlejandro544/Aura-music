package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.model.AutoRemixStyle
import com.example.core.model.Track
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Sección modular de configuración del Generador de Auto-Remix / Mashup con 2 Canciones Locales.
 * Permite:
 * - Elegir manualmente la Canción A y la Canción B o pulsar "Auto-Match Inteligente" para que el algoritmo
 *   seleccione las 2 pistas locales más compatibles.
 * - Alternar entre el estilo Mashup (Voz A + Base B) y Remix Alternado DJ (Sincronización de Ritmo).
 * - Intercambiar el rol de Canción A y Canción B con un toque.
 */
@Composable
fun AutoRemixConfigSection(
    localTracks: List<Track>,
    selectedTrackA: Track?,
    selectedTrackB: Track?,
    autoMatchReason: String,
    selectedRemixStyle: AutoRemixStyle,
    remixTitle: String,
    crossfadeSec: Float,
    onRemixTitleChange: (String) -> Unit,
    onSelectStyle: (AutoRemixStyle) -> Unit,
    onCrossfadeChange: (Float) -> Unit,
    onPickTrackA: () -> Unit,
    onPickTrackB: () -> Unit,
    onSwapTracks: () -> Unit,
    onAutoMatchPair: () -> Unit,
    onDismissRequest: () -> Unit,
    onCreateAutoRemix: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Botón destacado de Auto-Match Inteligente
        FilledTonalButton(
            onClick = onAutoMatchPair,
            enabled = localTracks.size >= 2,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                contentColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auto_match_pair_btn")
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Elección Inteligente (Auto-Match 2 Pistas)",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = autoMatchReason,
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Selector de Canción A
        TrackSlotSelectorCard(
            badge = if (selectedRemixStyle == AutoRemixStyle.VOCAL_MASHUP) "CANCIÓN A • VOZ PRINCIPAL" else "CANCIÓN A • BLOQUE 1 & 3",
            track = selectedTrackA,
            onClick = onPickTrackA,
            testTag = "pick_track_a_card"
        )

        // Botón central para intercambiar Canción A y B
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onSwapTracks,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 34.dp)
                    .testTag("swap_remix_tracks_btn")
            ) {
                Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Intercambiar A ⇄ B", style = MaterialTheme.typography.labelSmall)
            }
        }

        // Selector de Canción B
        TrackSlotSelectorCard(
            badge = if (selectedRemixStyle == AutoRemixStyle.VOCAL_MASHUP) "CANCIÓN B • BASE RÍTMICA" else "CANCIÓN B • DROP 2 & CIERRE",
            track = selectedTrackB,
            onClick = onPickTrackB,
            testTag = "pick_track_b_card"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Selector de Estilo de Auto-Remix (Mashup vs Remix Alternado DJ)
        Text(
            text = "Estilo de Remix Automático:",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        AutoRemixStyle.values().forEach { style ->
            val isSelected = selectedRemixStyle == style
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else SurfaceCard,
                border = BorderStroke(
                    width = if (isSelected) 1.2.dp else 0.8.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else CardBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelectStyle(style) }
                    .testTag("remix_style_${style.name}")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectStyle(style) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = style.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = style.description,
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = remixTitle,
            onValueChange = onRemixTitleChange,
            label = { Text("Nombre del Auto-Remix") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("autoremix_title_input")
        )

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancelar", color = TextSecondary)
            }
            Button(
                onClick = onCreateAutoRemix,
                enabled = selectedTrackA != null && selectedTrackB != null && localTracks.size >= 2,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1.4f)
                    .testTag("confirm_create_autoremix_btn")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Crear Auto-Remix", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TrackSlotSelectorCard(
    badge: String,
    track: Track?,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track?.title ?: "Toca para elegir canción local",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track?.artist ?: "Sin seleccionar",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Cambiar canción",
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
