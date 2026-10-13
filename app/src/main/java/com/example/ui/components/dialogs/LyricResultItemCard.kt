package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.model.LyricSearchResult
import com.example.core.model.Track
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
/**
 * Tarjeta individual de resultado con insignia oficial y previsualización.
 * Rol arquitectónico: átomo de la lista de resultados de letras.
 */
/**
 * Tarjeta individual para un resultado de letra en la lista.
 * Si es oficial/recomendada, resalta con borde de neón e insignia estelar destacada.
 */
@Composable
internal fun LyricResultItemCard(
    result: LyricSearchResult,
    accentColor: Color,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOfficial = result.isOfficialRecommended

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOfficial) accentColor.copy(alpha = 0.12f) else SurfaceCard.copy(alpha = 0.7f)
        ),
        border = BorderStroke(
            width = if (isOfficial) 1.6.dp else 1.dp,
            color = if (isOfficial) accentColor else CardBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect() }
            .testTag(if (isOfficial) "lyric_official_card" else "lyric_option_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Fila superior: Insignia Oficial y Tipo de Formato (diseño compacto anti-desbordamiento)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isOfficial) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentColor,
                        modifier = Modifier.height(22.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "OFICIAL",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp,
                                    color = Color.Black,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.height(20.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            Text(
                                text = "VERSIÓN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    color = TextSecondary
                                )
                            )
                        }
                    }
                }

                // Chip de formato: Sincronizada (LRC) o Texto plano
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (result.isSynced) Color(0xFF10B981).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(
                        0.8.dp,
                        if (result.isSynced) Color(0xFF10B981).copy(alpha = 0.5f) else Color.Transparent
                    ),
                    modifier = Modifier.height(22.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (result.isSynced) Icons.Default.Sync else Icons.Default.Notes,
                            contentDescription = null,
                            tint = if (result.isSynced) Color(0xFF10B981) else TextSecondary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (result.isSynced) "Karaoke LRC" else "Texto",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (result.isSynced) Color(0xFF10B981) else TextSecondary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Título de la pista
            Text(
                text = result.trackName.ifBlank { "Sin título" },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isOfficial) accentColor else TextPrimary,
                    fontSize = 15.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Artista, álbum y duración con restricción armónica
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = result.artistName.ifBlank { "Artista desconocido" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary.copy(alpha = 0.85f),
                        fontSize = 12.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (result.albumName.isNotBlank()) {
                    Text(
                        text = " • ${result.albumName}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                if (result.durationSeconds > 0) {
                    val m = result.durationSeconds / 60
                    val s = result.durationSeconds % 60
                    Text(
                        text = "  (${String.format("%02d:%02d", m, s)})",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 11.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            // Extracto de versos para previsualización
            if (result.previewSnippet.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = result.previewSnippet,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary.copy(alpha = 0.9f),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            ),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Botón de selección táctil
            Button(
                onClick = onSelect,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOfficial) accentColor else Color.White.copy(alpha = 0.12f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Text(
                    text = if (isOfficial) "Elegir Lírica Oficial" else "Elegir esta versión",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isOfficial) Color.Black else TextPrimary
                    )
                )
            }
        }
    }
}
