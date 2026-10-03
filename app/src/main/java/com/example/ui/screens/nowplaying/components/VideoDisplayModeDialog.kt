package com.example.ui.screens.nowplaying.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VideoDisplayMode
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Diálogo modal para alternar entre los 3 modos de visualización de video de fondo:
 * - FULLSCREEN_BACKGROUND: Video cubriendo toda la pantalla detrás con carátula flotando.
 * - CARD_CANVAS: Video dentro del marco central de carátula 1:1.
 * - OFF: Desactivado.
 */
@Composable
fun VideoDisplayModeDialog(
    videoDisplayMode: VideoDisplayMode,
    animatedPrimary: Color,
    onSetVideoDisplayMode: (VideoDisplayMode) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = SurfaceElevatedDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = animatedPrimary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Modo de Video",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Elige cómo visualizar el video asociado a esta canción:",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(4.dp))
                VideoDisplayMode.values().forEach { mode ->
                    val isSelected = (videoDisplayMode == mode)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) animatedPrimary.copy(alpha = 0.16f) else SurfaceCard,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) animatedPrimary else CardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onSetVideoDisplayMode(mode)
                                onDismissRequest()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (mode) {
                                    VideoDisplayMode.FULLSCREEN_BACKGROUND -> Icons.Default.Wallpaper
                                    VideoDisplayMode.CARD_CANVAS -> Icons.Default.CropSquare
                                    VideoDisplayMode.OFF -> Icons.Default.VideocamOff
                                },
                                contentDescription = null,
                                tint = if (isSelected) animatedPrimary else TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) animatedPrimary else TextPrimary
                                    )
                                )
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSetVideoDisplayMode(mode)
                                    onDismissRequest()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = animatedPrimary,
                                    unselectedColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Listo", color = animatedPrimary, fontWeight = FontWeight.Bold)
            }
        }
    )
}
