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
 * Comportamiento visual y navegación.
 * Rol arquitectónico: sección del menú de apariencia.
 */
fun LazyListScope.visualBehaviorMenuSection(
    isDynamicArtworkColorEnabled: Boolean,
    isMiniPlayerVideoEnabled: Boolean,
    isBackgroundGameModeEnabled: Boolean,
    appWallpaperConfig: AppWallpaperConfig,
    streamingCacheConfig: com.example.core.model.StreamingCacheConfig,
    onOpenStreamingAndCache: () -> Unit,
    onOpenAuraAndMiniPlayer: () -> Unit,
    onOpenGameMode: () -> Unit,
    onOpenGalleryWallpaper: () -> Unit
) {
    // 1. Streaming & Caché Inteligente
    item {
        SettingsNavigationCard(
            icon = Icons.Default.Videocam,
            title = "Streaming & Caché Inteligente",
            subtitle = "${streamingCacheConfig.videoNetworkPolicy.title} • Caché máx: ${streamingCacheConfig.maxCacheSizeMb} MB (${streamingCacheConfig.usedMbFormatted} en uso)",
            iconTint = MaterialTheme.colorScheme.primary,
            badgeText = "${streamingCacheConfig.maxCacheSizeMb} MB",
            badgeColor = MaterialTheme.colorScheme.primary,
            onClick = onOpenStreamingAndCache,
            testTag = "visual_subtab_streaming_cache"
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    // 2. Aura Dinámica (1s) & Video en Mini Reproductor
    item {
        val activeCount = listOf(isDynamicArtworkColorEnabled, isMiniPlayerVideoEnabled).count { it }
        val auraDesc = if (isDynamicArtworkColorEnabled) "Color reactivo cada 1s activo" else "Color reactivo OFF"
        val miniDesc = if (isMiniPlayerVideoEnabled) "Video en MiniPlayer ON" else "Video en MiniPlayer OFF"
        SettingsNavigationCard(
            icon = Icons.Default.Palette,
            title = "Aura Dinámica (1s) & Mini Reproductor",
            subtitle = "$auraDesc • $miniDesc",
            iconTint = MaterialTheme.colorScheme.secondary,
            badgeText = if (activeCount > 0) "$activeCount/2 ON" else "OFF",
            badgeColor = MaterialTheme.colorScheme.secondary,
            onClick = onOpenAuraAndMiniPlayer,
            testTag = "visual_subtab_aura_miniplayer"
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    // 3. Modo Juego / Ahorro en 2do Plano
    item {
        SettingsNavigationCard(
            icon = Icons.Default.SportsEsports,
            title = "Modo Juego / Ahorro en 2do Plano",
            subtitle = if (isBackgroundGameModeEnabled) {
                "Activo • Libera GPU, decodificador de video y visualizador al minimizar la app para jugar"
            } else {
                "Desactivado • Los recursos visuales permanecen activos en segundo plano"
            },
            iconTint = MaterialTheme.colorScheme.primary,
            badgeText = if (isBackgroundGameModeEnabled) "ACTIVO" else "OFF",
            badgeColor = MaterialTheme.colorScheme.primary,
            onClick = onOpenGameMode,
            testTag = "visual_subtab_game_mode"
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    // 4. Fondo de Pantalla de Galería (Imagen WebP o Video corto MP4)
    item {
        val wallpaperSummary = if (appWallpaperConfig.hasValidMedia) {
            "Activo: ${appWallpaperConfig.mediaType.label} • ${appWallpaperConfig.screenScope.label} • Blur ${appWallpaperConfig.blurRadiusDp}dp"
        } else {
            "Negro OLED Puro • Elige una foto (WebP) o video corto (MP4) de tu galería"
        }
        SettingsNavigationCard(
            icon = Icons.Default.PermMedia,
            title = "Fondo de Pantalla de Galería",
            subtitle = wallpaperSummary,
            iconTint = MaterialTheme.colorScheme.secondary,
            badgeText = if (appWallpaperConfig.hasValidMedia) "PERSONALIZADO" else "OLED",
            badgeColor = MaterialTheme.colorScheme.secondary,
            onClick = onOpenGalleryWallpaper,
            testTag = "visual_subtab_gallery_wallpaper"
        )
    }
}

/**
 * Sub-apartado 2: Aura Dinámica de Carátula y Video (1s) + Video en Mini Reproductor.
 */
