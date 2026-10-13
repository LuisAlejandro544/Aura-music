package com.example.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import com.example.core.model.AppWallpaperConfig
import com.example.core.model.AuraTheme
import com.example.core.model.HeadphoneConfig
import com.example.core.model.HeadsetButtonAction
import com.example.core.model.NowPlayingDesignMode
import com.example.core.model.Playlist
import com.example.core.model.Track
import com.example.core.model.WallpaperScreenScope
import com.example.core.model.WidgetConfig
import com.example.core.model.WidgetGridContentMode
import com.example.feature.settings.components.*
import com.example.feature.settings.components.about.behindTheProjectSettingsContent
import com.example.feature.settings.components.appearance.appWallpaperSettingsSection
import com.example.feature.settings.components.widgets.widgetSettingsContent
import com.example.feature.settings.components.ytdlp.ytDlpCookiesAndPoTokenSettingsContent
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import androidx.compose.ui.graphics.Color

/**
 * Pantalla de Diseño del Reproductor.
 * Rol arquitectónico: pantalla independiente con retroceso.
 */
@Composable
fun SettingsPlayerDesignScreen(
    nowPlayingDesignMode: NowPlayingDesignMode,
    onSetNowPlayingDesignMode: (NowPlayingDesignMode) -> Unit,
    onBack: () -> Unit
) {
                    SettingsSubMenuScreen(
                        title = "Diseño del Reproductor",
                        subtitle = "Elige el estilo visual de la pantalla completa Now Playing",
                        icon = Icons.Default.DashboardCustomize,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onBack = onBack,
                        testTag = "subscreen_player_design"
                    ) {
                        playerDesignSettingsContent(
                            currentDesignMode = nowPlayingDesignMode,
                            onSetDesignMode = onSetNowPlayingDesignMode
                        )
                    }
}

/**
 * Pantalla de Paleta Base y Temas.
 * Rol arquitectónico: pantalla independiente con retroceso.
 */
@Composable
fun SettingsThemePalettesScreen(
    currentTheme: AuraTheme,
    onSelectTheme: (AuraTheme) -> Unit,
    onBack: () -> Unit
) {
                    SettingsSubMenuScreen(
                        title = "Paleta Base Predeterminada",
                        subtitle = "Colores de acento OLED y sincronización con Material You",
                        icon = Icons.Default.Palette,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onBack = onBack,
                        testTag = "subscreen_theme_palettes"
                    ) {
                        themePaletteSettingsContent(
                            currentTheme = currentTheme,
                            onSelectTheme = onSelectTheme
                        )
                    }
}
