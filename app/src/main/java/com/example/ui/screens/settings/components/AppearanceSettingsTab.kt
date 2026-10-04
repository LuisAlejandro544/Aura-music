package com.example.ui.screens.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AuraTheme
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pestaña de Apariencia & Temas de Aura Music.
 * Arquitectura: Módulo de UI que renderiza dentro del LazyColumn:
 * - Tarjeta del Aura Dinámica de Carátula.
 * - Tarjetas de selección para cada tema visual (Nebula Violet, Cyber Mint, Sunset Ember, Ocean Abyss).
 * - Tarjeta de Ficha Técnica, Arquitectura y Privacidad.
 */
fun LazyListScope.appearanceSettingsTab(
    currentTheme: AuraTheme,
    onSelectTheme: (AuraTheme) -> Unit,
    isDynamicArtworkColorEnabled: Boolean,
    onToggleDynamicArtworkColor: (Boolean) -> Unit,
    isMiniPlayerVideoEnabled: Boolean = true,
    onToggleMiniPlayerVideo: (Boolean) -> Unit = {}
) {
    // Opción Avanzada: Aura Dinámica de Carátula
    item {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dynamic_artwork_color_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Aura Dinámica de Carátula",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Adapta el resplandor de neón, la barra de progreso y los acentos visuales a los tonos de cada portada o video.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Switch(
                    checked = isDynamicArtworkColorEnabled,
                    onCheckedChange = onToggleDynamicArtworkColor,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("dynamic_color_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    // Opción: Video en Mini Reproductor
    item {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mini_player_video_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Videocam,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Video en Mini Reproductor",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Reproduce el Video Canvas miniatura en el mini reproductor flotante cuando la canción tenga video.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Switch(
                    checked = isMiniPlayerVideoEnabled,
                    onCheckedChange = onToggleMiniPlayerVideo,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.testTag("mini_player_video_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    // Selector de Temas Visuales
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Paleta Base Predeterminada",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Se utiliza en la biblioteca, menús y cuando una pista no tiene colores específicos.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    items(AuraTheme.values().size) { index ->
        val theme = AuraTheme.values()[index]
        val isSelected = theme == currentTheme
        val context = androidx.compose.ui.platform.LocalContext.current
        val (displayPrimary, displaySecondary) = if (theme.isDynamic && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val dyn = androidx.compose.material3.dynamicDarkColorScheme(context)
            Pair(dyn.primary, dyn.secondary)
        } else {
            Pair(theme.primaryColor, theme.secondaryColor)
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = if (isSelected) {
                BorderStroke(2.dp, displayPrimary)
            } else {
                BorderStroke(1.dp, CardBorder)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onSelectTheme(theme) }
                .testTag("theme_card_${theme.name}")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Círculos de color del tema
                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(displayPrimary)
                            .border(2.dp, SurfaceCard, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(displaySecondary)
                            .border(2.dp, SurfaceCard, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = theme.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = theme.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }

                if (isSelected) {
                    Surface(
                        shape = CircleShape,
                        color = displayPrimary,
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

    // Información de Privacidad y Arquitectura Local
    item {
        val context = androidx.compose.ui.platform.LocalContext.current
        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Arquitectura y Privacidad",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingDetailRow("Motor de Reproducción", "Jetpack Media3 ExoPlayer")
                SettingDetailRow("Motor DSP Nativo", "ISO C++20 con filtros Biquad 64-bit")
                SettingDetailRow("Persistencia Local", "Room Database SQLite")
                SettingDetailRow("Acceso a Archivos", "SAF (Storage Access Framework)")
                SettingDetailRow("Escaneo Automático", "Desactivado (100% bajo control del usuario)")
                SettingDetailRow("Versión", "1.1 (Aura Music Edition)")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Acceso directo a Aura Monitor (Diagnóstico & Telemetría)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Aura Monitor (Telemetría & Diagnóstico)",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Inspecciona el consumo de RAM en tiempo real (Java, C++ Nativo, PSS), la carga de procesador, los hilos de ejecución activos y el registro detallado de incidentes.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        val intent = android.content.Intent(context, com.example.debug.DebugMonitorActivity::class.java)
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_aura_monitor_btn")
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Abrir Aura Monitor", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
