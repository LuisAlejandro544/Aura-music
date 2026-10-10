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
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PermMedia
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
import com.example.model.AuraTheme
import com.example.model.NowPlayingDesignMode
import com.example.model.WidgetConfig
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
 * 2. [visualCustomizationSettingsContent]: Pantalla dedicada de Aura Dinámica y Video en Mini Reproductor.
 * 3. [themePaletteSettingsContent]: Pantalla dedicada de Paleta Base Predeterminada y Material You.
 * 4. [appearanceSettingsTab]: Compatibilidad de renderizado para pruebas y vistas integradas.
 */

fun LazyListScope.mainSettingsMenuContent(
    currentTheme: AuraTheme,
    isDynamicArtworkColorEnabled: Boolean,
    isMiniPlayerVideoEnabled: Boolean,
    nowPlayingDesignMode: NowPlayingDesignMode,
    isHeadphoneConnected: Boolean,
    connectedDeviceName: String,
    mediaCount: Int,
    widgetConfig: WidgetConfig = WidgetConfig(),
    onOpenPlayerDesign: () -> Unit,
    onOpenVisualBehavior: () -> Unit,
    onOpenThemePalettes: () -> Unit,
    onOpenWidgets: () -> Unit = {},
    onOpenHeadphones: () -> Unit,
    onOpenStoredMedia: () -> Unit,
    onOpenEnginesAndDiagnostics: () -> Unit,
    onOpenBehindTheProject: () -> Unit = {},
    onOpenOnboarding: () -> Unit
) {
    // 1. Diseño del Reproductor
    item {
        SettingsNavigationCard(
            icon = Icons.Default.DashboardCustomize,
            title = "Diseño del Reproductor",
            subtitle = "${nowPlayingDesignMode.label} • ${nowPlayingDesignMode.description}",
            iconTint = MaterialTheme.colorScheme.primary,
            onClick = onOpenPlayerDesign,
            testTag = "now_playing_design_card"
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    // 2. Aura Dinámica y Video en Mini Reproductor
    item {
        val auraStatus = if (isDynamicArtworkColorEnabled) "Aura Dinámica activa" else "Aura Dinámica desactivada"
        val miniVideoStatus = if (isMiniPlayerVideoEnabled) "Video en Mini Reproductor activo" else "Solo carátula en Mini Reproductor"
        SettingsNavigationCard(
            icon = Icons.Default.Videocam,
            title = "Aura Dinámica & Video en Mini Reproductor",
            subtitle = "$auraStatus • $miniVideoStatus",
            iconTint = MaterialTheme.colorScheme.secondary,
            onClick = onOpenVisualBehavior,
            testTag = "settings_nav_visual_behavior"
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    // 3. Paleta Base Predeterminada & Material You
    item {
        SettingsNavigationCard(
            icon = Icons.Default.Palette,
            title = "Paleta Base Predeterminada",
            subtitle = "${currentTheme.title} • ${currentTheme.description}",
            iconTint = MaterialTheme.colorScheme.primary,
            onClick = onOpenThemePalettes,
            testTag = "settings_nav_theme_palettes"
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    // 4. Nuevo Apartado: Widget (Color dinámico de imagen, tamaño adaptativo y Top 4 Canciones / 4 Playlists)
    item {
        val colorSummary = if (widgetConfig.isDynamicColorEnabled) "Color de imagen activo" else "Color fijo"
        SettingsNavigationCard(
            icon = Icons.Default.Widgets,
            title = "Widget",
            subtitle = "${widgetConfig.gridContentMode.label} • $colorSummary • Reanudación y tamaño adaptativo",
            iconTint = MaterialTheme.colorScheme.secondary,
            badgeText = "2 WIDGETS",
            badgeColor = MaterialTheme.colorScheme.primary,
            onClick = onOpenWidgets,
            testTag = "settings_nav_widgets"
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    // 5. Auriculares, Acústica DSP & Gestos
    item {
        SettingsNavigationCard(
            icon = Icons.Default.Headphones,
            title = "Auriculares & Acústica DSP",
            subtitle = if (isHeadphoneConnected) {
                "Conectado ($connectedDeviceName) • Crossfeed C++20, Balance L/R y Gestos"
            } else {
                "Crossfeed Bauer/Chu Moy en C++20, Balance Estéreo L/R, Seguridad y Botones"
            },
            iconTint = MaterialTheme.colorScheme.secondary,
            badgeText = if (isHeadphoneConnected) "ON" else null,
            badgeColor = MaterialTheme.colorScheme.secondary,
            onClick = onOpenHeadphones,
            testTag = "settings_tab_headphones"
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    // 6. Medios y Almacenamiento Guardado
    item {
        SettingsNavigationCard(
            icon = Icons.Default.PermMedia,
            title = "Medios & Almacenamiento",
            subtitle = "Inspecciona y libera espacio de carátulas WebP y videos Canvas MP4 guardados en tu teléfono",
            iconTint = MaterialTheme.colorScheme.primary,
            badgeText = if (mediaCount > 0) "$mediaCount" else null,
            badgeColor = MaterialTheme.colorScheme.primary,
            onClick = onOpenStoredMedia,
            testTag = "settings_tab_stored_media"
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    // 7. Motores Nativos, yt-dlp OTA & Diagnóstico (Aura Monitor solo en Debug)
    item {
        val enginesTitle = if (com.example.BuildConfig.ENABLE_DEBUG_MONITOR) {
            "Motores Nativos, Actualizador APK & Diagnóstico"
        } else {
            "Motores Nativos & Actualizador OTA (APK / yt-dlp)"
        }
        val enginesSubtitle = if (com.example.BuildConfig.ENABLE_DEBUG_MONITOR) {
            "Actualizador automático de APK (${com.example.BuildConfig.VERSION_NAME} • ${com.example.BuildConfig.APP_CODENAME}), yt-dlp OTA y Aura Monitor"
        } else {
            "Actualizador de versiones APK (${com.example.BuildConfig.VERSION_NAME} • ${com.example.BuildConfig.APP_CODENAME}), yt-dlp OTA y C++20/FFmpeg"
        }
        SettingsNavigationCard(
            icon = Icons.Default.Build,
            title = enginesTitle,
            subtitle = enginesSubtitle,
            iconTint = MaterialTheme.colorScheme.secondary,
            onClick = onOpenEnginesAndDiagnostics,
            testTag = "settings_nav_engines_diagnostics"
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    // 8. Detrás del Proyecto (Creador & Contribuidores en GitHub)
    item {
        SettingsNavigationCard(
            icon = Icons.Default.Groups,
            title = "Detrás del Proyecto",
            subtitle = "Creador: Luis Alejandro Sosa Camacho (@LuisAlejandro544) • Contribuidores: @thelandy03-boop",
            iconTint = MaterialTheme.colorScheme.primary,
            badgeText = "GITHUB",
            badgeColor = MaterialTheme.colorScheme.primary,
            onClick = onOpenBehindTheProject,
            testTag = "settings_nav_behind_project"
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    // 9. Bienvenida & Guía de la Aplicación
    item {
        SettingsNavigationCard(
            icon = Icons.Default.Info,
            title = "Bienvenida & Guía de la Aplicación",
            subtitle = "Consulta la guía interactiva de funciones y el aviso sobre almacenamiento de Video Canvas",
            iconTint = MaterialTheme.colorScheme.secondary,
            onClick = onOpenOnboarding,
            testTag = "open_onboarding_btn"
        )
    }
}

fun LazyListScope.visualCustomizationSettingsContent(
    isDynamicArtworkColorEnabled: Boolean,
    onToggleDynamicArtworkColor: (Boolean) -> Unit,
    isMiniPlayerVideoEnabled: Boolean,
    onToggleMiniPlayerVideo: (Boolean) -> Unit
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

        Spacer(modifier = Modifier.height(14.dp))
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
    }
}

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

fun LazyListScope.appearanceSettingsTab(
    currentTheme: AuraTheme,
    onSelectTheme: (AuraTheme) -> Unit,
    isDynamicArtworkColorEnabled: Boolean,
    onToggleDynamicArtworkColor: (Boolean) -> Unit,
    isMiniPlayerVideoEnabled: Boolean = true,
    onToggleMiniPlayerVideo: (Boolean) -> Unit = {},
    nowPlayingDesignMode: NowPlayingDesignMode = NowPlayingDesignMode.AUTO,
    onSetNowPlayingDesignMode: (NowPlayingDesignMode) -> Unit = {},
    onOpenOnboarding: () -> Unit = {}
) {
    playerDesignSettingsContent(
        currentDesignMode = nowPlayingDesignMode,
        onSetDesignMode = onSetNowPlayingDesignMode
    )
    visualCustomizationSettingsContent(
        isDynamicArtworkColorEnabled = isDynamicArtworkColorEnabled,
        onToggleDynamicArtworkColor = onToggleDynamicArtworkColor,
        isMiniPlayerVideoEnabled = isMiniPlayerVideoEnabled,
        onToggleMiniPlayerVideo = onToggleMiniPlayerVideo
    )
    themePaletteSettingsContent(
        currentTheme = currentTheme,
        onSelectTheme = onSelectTheme
    )
    enginesAndDiagnosticsSettingsContent(onOpenOnboarding = onOpenOnboarding)
}
