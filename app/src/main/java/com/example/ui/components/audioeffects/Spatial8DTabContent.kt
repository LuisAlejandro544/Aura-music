package com.example.ui.components.audioeffects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Spatial8DConfig
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Pestaña 1 de Efectos de Audio: Motor de Audio Espacial 8D Binaural nativo en C++20.
 * Arquitectura: Componente modular de UI que presenta:
 * - Switch de activación del efecto 8D binaural para audífonos.
 * - Control de velocidad de rotación orbital continua (4s a 30s por vuelta).
 * - Control de intensidad de paneo tridimensional (20% a 100%).
 * - Control de reverberación y profundidad de sala acústica (0% a 100%).
 */
@Composable
fun Spatial8DTabContent(
    config: Spatial8DConfig,
    onToggle: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onDepthChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Audio 8D Espacial (C++20)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                    Text(
                        text = "Efecto binaural orbital 360° para auriculares.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }

                Switch(
                    checked = config.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.testTag("eight_d_switch")
                )
            }

            AnimatedVisibility(visible = config.enabled) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Velocidad de Rotación", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                        Text(
                            text = "${config.orbitSpeedSeconds.roundToInt()}s por vuelta",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                    Slider(
                        value = config.orbitSpeedSeconds,
                        onValueChange = onSpeedChange,
                        valueRange = 4.0f..30.0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("eight_d_speed_slider")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Intensidad Espacial (Paneo)", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                        Text(
                            text = "${(config.spatialIntensity * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                    Slider(
                        value = config.spatialIntensity,
                        onValueChange = onIntensityChange,
                        valueRange = 0.2f..1.0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("eight_d_intensity_slider")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Reverberación de Sala", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                        Text(
                            text = "${(config.roomDepth * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                    Slider(
                        value = config.roomDepth,
                        onValueChange = onDepthChange,
                        valueRange = 0.0f..1.0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("eight_d_depth_slider")
                    )
                }
            }
        }
    }
}
