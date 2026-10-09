package com.example.ui.screens.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.HeadphoneConfig
import com.example.model.HeadsetButtonAction
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Sub-Apartado 2 de Auriculares: Botones Físicos y Gestos (Headset Controls).
 * Arquitectura: Módulo de UI que renderiza dentro del LazyColumn:
 * - Tarjeta Maestra con switch para activar/desactivar la intercepción de botones físicos.
 * - Filas interactivas para 1 Pulsación (Simple), 2 Pulsaciones (Doble clic), 3 Pulsaciones (Triple clic) y Pulsación Prolongada (Hold).
 */
fun LazyListScope.headphonesGesturesSection(
    headphoneConfig: HeadphoneConfig,
    onSetHeadsetControlsEnabled: (Boolean) -> Unit,
    onOpenEditClickType: (Int) -> Unit
) {
    item {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("headset_controls_master_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Controles Físicos de Auriculares",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Intercepta y personaliza las pulsaciones del botón físico o panel táctil de tus audífonos.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }

                Switch(
                    checked = headphoneConfig.isHeadsetControlsEnabled,
                    onCheckedChange = { onSetHeadsetControlsEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("headset_controls_switch")
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (headphoneConfig.isHeadsetControlsEnabled) {
        item {
            Text(
                text = "Acciones Configurables por Pulsación:",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 1 Clic
        item {
            HeadsetGestureRow(
                title = "1 Pulsación (Simple)",
                description = "Pulsar una vez el botón de los audífonos",
                currentAction = headphoneConfig.singleClickAction,
                onClick = { onOpenEditClickType(1) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 2 Clics
        item {
            HeadsetGestureRow(
                title = "2 Pulsaciones (Doble clic)",
                description = "Pulsar dos veces seguidas",
                currentAction = headphoneConfig.doubleClickAction,
                onClick = { onOpenEditClickType(2) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 3 Clics
        item {
            HeadsetGestureRow(
                title = "3 Pulsaciones (Triple clic)",
                description = "Pulsar tres veces seguidas",
                currentAction = headphoneConfig.tripleClickAction,
                onClick = { onOpenEditClickType(3) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Clic Largo
        item {
            HeadsetGestureRow(
                title = "Pulsación Prolongada (Hold)",
                description = "Mantener presionado más de 0.7 segundos",
                currentAction = headphoneConfig.longClickAction,
                onClick = { onOpenEditClickType(4) }
            )
        }
    }
}

@Composable
fun HeadsetGestureRow(
    title: String,
    description: String,
    currentAction: HeadsetButtonAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentAction.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
