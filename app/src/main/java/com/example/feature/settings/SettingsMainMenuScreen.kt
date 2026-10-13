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
 * Menú principal de Ajustes con cabecera y tarjetas de navegación.
 * Rol arquitectónico: puerta de entrada a las pantallas independientes.
 */
@Composable
fun SettingsMainMenuScreen(
    currentTheme: AuraTheme,
    isDynamicArtworkColorEnabled: Boolean,
    isMiniPlayerVideoEnabled: Boolean,
    isBackgroundGameModeEnabled: Boolean,
    appWallpaperConfig: AppWallpaperConfig,
    nowPlayingDesignMode: NowPlayingDesignMode,
    headphoneConfig: HeadphoneConfig,
    widgetConfig: WidgetConfig,
    allTracks: List<Track>,
    onNavigate: (SettingsSubScreen) -> Unit,
    onOpenOnboarding: () -> Unit
) {
    val mediaCount = remember(allTracks) {
        allTracks.count { !it.albumArtPath.isNullOrBlank() || !it.videoUri.isNullOrBlank() }
    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        // Cabecera Principal Directa (Sin barra de pestañas superior redundante)
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Configuración & Ajustes",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Personaliza el reproductor, temas visuales, acústica DSP y medios guardados.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        // Menú Principal de Navegación por Pantallas Independientes
                        mainSettingsMenuContent(
                            currentTheme = currentTheme,
                            isDynamicArtworkColorEnabled = isDynamicArtworkColorEnabled,
                            isMiniPlayerVideoEnabled = isMiniPlayerVideoEnabled,
                            isBackgroundGameModeEnabled = isBackgroundGameModeEnabled,
                            appWallpaperConfig = appWallpaperConfig,
                            nowPlayingDesignMode = nowPlayingDesignMode,
                            isHeadphoneConnected = headphoneConfig.isHeadphoneConnected,
                            connectedDeviceName = headphoneConfig.connectedDeviceName,
                            mediaCount = mediaCount,
                            widgetConfig = widgetConfig,
                            onOpenPlayerDesign = { onNavigate(SettingsSubScreen.PLAYER_DESIGN) },
                            onOpenVisualBehavior = { onNavigate(SettingsSubScreen.VISUAL_BEHAVIOR) },
                            onOpenThemePalettes = { onNavigate(SettingsSubScreen.THEME_PALETTES) },
                            onOpenWidgets = { onNavigate(SettingsSubScreen.WIDGETS) },
                            onOpenHeadphones = { onNavigate(SettingsSubScreen.HEADPHONES) },
                            onOpenStoredMedia = { onNavigate(SettingsSubScreen.STORED_MEDIA) },
                            onOpenEnginesAndDiagnostics = { onNavigate(SettingsSubScreen.ENGINES_AND_DIAGNOSTICS) },
                            onOpenDownloadsAndCookies = { onNavigate(SettingsSubScreen.DOWNLOADS_AND_COOKIES) },
                            onOpenBehindTheProject = { onNavigate(SettingsSubScreen.BEHIND_THE_PROJECT) },
                            onOpenOnboarding = onOpenOnboarding
                        )
                    }
}
