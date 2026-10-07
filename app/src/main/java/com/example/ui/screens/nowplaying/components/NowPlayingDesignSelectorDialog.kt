package com.example.ui.screens.nowplaying.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NowPlayingDesignMode
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Diálogo modal para elegir el diseño de pantalla completa de Now Playing:
 * - AUTO: Inteligente (Cinemático si hay Video Canvas, Clásico si es solo Carátula).
 * - CINEMATIC_CANVAS: Estilo Spotify Canvas con video completo, minicarátula y controles ergonómicos.
 * - CLASSIC: Carátula central grande 1:1, visualizador de 28 bandas y controles tradicionales.
 */
@Composable
fun NowPlayingDesignSelectorDialog(
    currentDesignMode: NowPlayingDesignMode,
    animatedPrimary: Color,
    onSetDesignMode: (NowPlayingDesignMode) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = SurfaceElevatedDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DashboardCustomize,
                    contentDescription = null,
                    tint = animatedPrimary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Diseño del Reproductor",
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
                    text = "Selecciona el estilo visual para la pantalla completa Now Playing:",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(4.dp))
                NowPlayingDesignMode.values().forEach { mode ->
                    val isSelected = (currentDesignMode == mode)
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
                                onSetDesignMode(mode)
                                onDismissRequest()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (mode) {
                                    NowPlayingDesignMode.AUTO -> Icons.Default.AutoAwesome
                                    NowPlayingDesignMode.CINEMATIC_CANVAS -> Icons.Default.Smartphone
                                    NowPlayingDesignMode.CLASSIC -> Icons.Default.PlayCircleOutline
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
                                    onSetDesignMode(mode)
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
