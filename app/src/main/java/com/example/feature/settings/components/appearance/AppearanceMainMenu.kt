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
 * Menú principal de categorías de Ajustes.
 * Rol arquitectónico: sección del menú de apariencia.
 */
fun LazyListScope.mainSettingsMenuContent(
    currentTheme: AuraTheme,
    isDynamicArtworkColorEnabled: Boolean,
    isMiniPlayerVideoEnabled: Boolean,
    isBackgroundGameModeEnabled: Boolean = true,
    appWallpaperConfig: AppWallpaperConfig = AppWallpaperConfig(),
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
    onOpenDownloadsAndCookies: () -> Unit = {},
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

    // 2. Aura Dinámica, Streaming & Modo Juego (Menú de 4 Sub-Apartados)
    item {
        val auraStatus = if (isDynamicArtworkColorEnabled) "Aura Dinámica (1s)" else "Aura Dinámica OFF"
        val wallpaperStatus = if (appWallpaperConfig.hasValidMedia) "Fondo Galería activo" else "Fondo OLED"
        val gameModeStatus = if (isBackgroundGameModeEnabled) "Modo Juego ON" else "Modo Juego OFF"
        SettingsNavigationCard(
            icon = Icons.Default.Videocam,
            title = "Aura Dinámica, Streaming & Modo Juego",
            subtitle = "Streaming y Caché • $auraStatus • $gameModeStatus • $wallpaperStatus",
            iconTint = MaterialTheme.colorScheme.secondary,
            badgeText = "4 OPCIONES",
            badgeColor = MaterialTheme.colorScheme.secondary,
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

    // 8. Descargas & Cookies (Estilo YTDLnis), PO Token Web y yt-dlp OTA
    item {
        val context = androidx.compose.ui.platform.LocalContext.current
        val cookiesActive = com.example.core.data.importer.YtDlpCookieManager.isCookiesEnabled(context)
        val cookiesBadge = if (cookiesActive) "COOKIES ON" else "YTDLnis"
        SettingsNavigationCard(
            icon = Icons.Default.CloudDownload,
            title = "Descargas & Cookies (Estilo YTDLnis)",
            subtitle = "Estrategia yt-dlp natural (player_client=default), cookies.txt opcionales, PO Token Web y OTA",
            iconTint = MaterialTheme.colorScheme.secondary,
            badgeText = cookiesBadge,
            badgeColor = MaterialTheme.colorScheme.secondary,
            onClick = onOpenDownloadsAndCookies,
            testTag = "settings_nav_downloads_and_cookies"
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    // 9. Detrás del Proyecto (Creador & Contribuidores en GitHub)
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

/**
 * Menú intermedio con 4 tarjetas navegables para el apartado "Aura Dinámica, Streaming & Modo Juego",
 * evitando tener todas las opciones y controles apretados en una sola pantalla.
 */
