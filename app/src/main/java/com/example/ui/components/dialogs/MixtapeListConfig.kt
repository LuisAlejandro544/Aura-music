package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.data.importer.mixtape.SmartAutoRemixEngine
import com.example.core.model.AutoRemixStyle
import com.example.core.model.Track
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt



/**
 * Configuración del Mixtape Continuo (nombre, crossfade e incluye).
 * Rol arquitectónico: formulario de mezcla de lista completa.
 */
@Composable
fun MixtapeListConfig(
    mixtapeTitle: String,
    onTitleChange: (String) -> Unit,
    crossfadeSec: Float,
    onCrossfadeChange: (Float) -> Unit,
    localTrackCount: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (title: String, crossfade: Float) -> Unit
) {
                        OutlinedTextField(
                            value = mixtapeTitle,
                            onValueChange = { onTitleChange(it) },
                            label = { Text("Nombre del Mixtape") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mixtape_title_input")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Duración del Crossfade (Fundido):",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "${crossfadeSec.roundToInt()} seg",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        Slider(
                            value = crossfadeSec,
                            onValueChange = { onCrossfadeChange(it) },
                            valueRange = 2f..12f,
                            steps = 9,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceCard,
                            border = BorderStroke(0.8.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "✨ Incluye automáticamente:",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• Fusión real de audio en 1 sola pista con curva DJ sin silencios.\n" +
                                        "• Portada en Collage WebP con hasta 4 carátulas de la lista.\n" +
                                        "• Cambio reactivo de carátula y Video Canvas en vivo según el minuto.\n" +
                                        "• Letras .LRC combinadas automáticamente para modo Karaoke.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

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
                                onClick = {
                                    onConfirm(mixtapeTitle, crossfadeSec)
                                },
                                enabled = localTrackCount >= 2,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .testTag("confirm_create_mixtape_btn")
                            ) {
                                Icon(Icons.Default.AllInclusive, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Fusionar Pistas", fontWeight = FontWeight.Bold)
                            }
                        }
}
