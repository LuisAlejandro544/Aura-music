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
 * Modo juego y ahorro en segundo plano.
 * Rol arquitectónico: sección del menú de apariencia.
 */
fun LazyListScope.backgroundGameModeSettingsSection(
    isBackgroundGameModeEnabled: Boolean,
    onToggleBackgroundGameMode: (Boolean) -> Unit
) {
    item {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("background_game_mode_card")
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
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Modo Juego / Ahorro en 2do Plano",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Al salir de la app mientras juegas, apaga automáticamente lo que no se ve (decodificador de video, muestreo de colores, visualizador espectral y cachés gráficas) dejando toda tu GPU y RAM para el juego, mientras mantiene 100% activos la música y los efectos que estés usando.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Switch(
                    checked = isBackgroundGameModeEnabled,
                    onCheckedChange = onToggleBackgroundGameMode,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("background_game_mode_switch")
                )
            }
        }
    }
}
