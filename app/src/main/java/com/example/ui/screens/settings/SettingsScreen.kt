package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pantalla de Temas y Ajustes.
 * Permite cambiar la paleta estética de acentos luminosos de la app y consultar
 * información técnica de privacidad y motor de reproducción.
 */
@Composable
fun SettingsScreen(
    currentTheme: AuraTheme,
    onSelectTheme: (AuraTheme) -> Unit,
    isDynamicArtworkColorEnabled: Boolean = true,
    onToggleDynamicArtworkColor: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Temas y Ajustes",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Personaliza el estilo visual de tu reproductor.",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Opción Avanzada: Aura Dinámica de Carátula
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = CardBorder.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
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
                            text = "Adapta el resplandor de neón, la barra de progreso y los acentos visuales a los tonos de cada portada.",
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

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(2.dp, theme.primaryColor)
                } else {
                    CardBorder.let { androidx.compose.foundation.BorderStroke(1.dp, it) }
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
                                .background(theme.primaryColor)
                                .border(2.dp, SurfaceCard, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(theme.secondaryColor)
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
                            color = theme.primaryColor,
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

        // Información de Privacidad y Rendimiento Local
        item {
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
                border = CardBorder.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingDetailRow("Motor de Reproducción", "Jetpack Media3 ExoPlayer")
                    SettingDetailRow("Persistencia Local", "Room Database SQLite")
                    SettingDetailRow("Acceso a Archivos", "SAF (Storage Access Framework)")
                    SettingDetailRow("Ecualizador", "Hardware AudioFX (5 bandas + Bass Boost)")
                    SettingDetailRow("Escaneo Automático", "Desactivado (100% bajo control del usuario)")
                    SettingDetailRow("Versión", "1.0 (Aura Music Edition)")
                }
            }
        }
    }
}

@Composable
private fun SettingDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}
