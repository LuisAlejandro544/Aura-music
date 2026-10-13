package com.example.feature.settings.components.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.Playlist
import com.example.core.model.Track
import com.example.core.model.WidgetConfig
import com.example.core.model.WidgetGridContentMode
import com.example.ui.components.ArtworkImage
import com.example.ui.components.PlaylistCoverCollage
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary


/**
 * Selector de contenido del widget grande (top canciones vs playlists).
 * Rol arquitectónico: tarjetas de modo 2x2 con estado de selección.
 */
fun LazyListScope.widgetGridModeSection(
    widgetConfig: WidgetConfig,
    onSetGridContentMode: (WidgetGridContentMode) -> Unit
) {
        item {
            Text(
                text = "CONTENIDO DEL WIDGET DE ACCESO RÁPIDO (2x2)",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.1.sp
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Elige qué mostrará el segundo Widget de buen tamaño en tu pantalla de inicio:",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
            )
            Spacer(modifier = Modifier.height(10.dp))

            WidgetGridContentMode.values().forEach { mode ->
                val isSelected = widgetConfig.gridContentMode == mode
                val modeIcon = if (mode == WidgetGridContentMode.TOP_SONGS) {
                    Icons.Default.Whatshot
                } else {
                    Icons.Default.QueueMusic
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    border = if (isSelected) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        BorderStroke(1.dp, CardBorder)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSetGridContentMode(mode) }
                        .testTag("widget_mode_card_${mode.name}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                    else Color.White.copy(alpha = 0.06f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = modeIcon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = mode.subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }

                        if (isSelected) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Seleccionado",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
}
