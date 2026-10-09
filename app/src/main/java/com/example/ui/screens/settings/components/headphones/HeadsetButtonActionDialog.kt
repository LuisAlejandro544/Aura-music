package com.example.ui.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.HeadphoneConfig
import com.example.model.HeadsetButtonAction
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Diálogo modal para configurar la acción asignada a pulsaciones de botones físicos de audífonos.
 */
@Composable
fun HeadsetButtonActionDialog(
    clickType: Int,
    headphoneConfig: HeadphoneConfig,
    onSetHeadsetAction: (Int, HeadsetButtonAction) -> Unit,
    onDismissRequest: () -> Unit
) {
    val typeTitle = when (clickType) {
        1 -> "1 Pulsación (Simple)"
        2 -> "2 Pulsaciones (Doble)"
        3 -> "3 Pulsaciones (Triple)"
        else -> "Pulsación Prolongada (Hold)"
    }
    val currentSelection = when (clickType) {
        1 -> headphoneConfig.singleClickAction
        2 -> headphoneConfig.doubleClickAction
        3 -> headphoneConfig.tripleClickAction
        else -> headphoneConfig.longClickAction
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Configurar $typeTitle",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Elige qué acción debe ejecutarse:",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                HeadsetButtonAction.values().forEach { action ->
                    val isSelected = action == currentSelection
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSetHeadsetAction(clickType, action)
                                onDismissRequest()
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSetHeadsetAction(clickType, action)
                                    onDismissRequest()
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = action.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = action.description,
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cerrar", color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = SurfaceCard,
        shape = RoundedCornerShape(18.dp)
    )
}
