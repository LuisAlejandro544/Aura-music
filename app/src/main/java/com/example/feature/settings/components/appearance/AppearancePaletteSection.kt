package com.example.feature.settings.components

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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import com.example.core.model.AppWallpaperConfig
import com.example.core.model.AuraTheme
import com.example.core.model.NowPlayingDesignMode
import com.example.core.model.WallpaperScreenScope
import com.example.core.model.WidgetConfig
import com.example.feature.settings.components.appearance.appWallpaperSettingsSection
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import androidx.compose.material.icons.filled.Widgets

/**
 * Contenido modular de la pantalla de Ajustes de Aura Music (< 500 líneas).
 * Proporciona:
 * 1. [mainSettingsMenuContent]: Menú principal de navegación de Ajustes con tarjetas estilo "Diseño del Reproductor"
 *    que abren pantallas independientes a pantalla completa (sin modales).
 * 2. [visualCustomizationSettingsContent]: Pantalla dedicada de Aura Dinámica (1s), Video en Mini Reproductor, Fondo de Galería y Modo Juego en 2do Plano.
 * 3. [themePaletteSettingsContent]: Pantalla dedicada de Paleta Base Predeterminada y Material You.
 * 4. [appearanceSettingsTab]: Compatibilidad de renderizado para pruebas y vistas integradas.
 */

/**
 * Paleta de colores y tema Material You.
 * Rol arquitectónico: sección del menú de apariencia.
 */
fun LazyListScope.themePaletteSettingsContent(
    currentTheme: AuraTheme,
    onSelectTheme: (AuraTheme) -> Unit
) {
    item {
        Text(
            text = "Se utiliza en la biblioteca, menús y cuando una pista no tiene colores específicos.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    items(AuraTheme.values().size) { index ->
        val theme = AuraTheme.values()[index]
        val isSelected = theme == currentTheme
        val context = LocalContext.current
        val (displayPrimary, displaySecondary) = if (theme.isDynamic && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val dyn = dynamicDarkColorScheme(context)
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
}
