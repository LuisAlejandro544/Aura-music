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
 * Pantalla Widget (color, estiramiento y Top 4 / Playlists).
 * Rol arquitectónico: pantalla independiente con retroceso.
 */
@Composable
fun SettingsWidgetScreen(
    widgetConfig: WidgetConfig,
    currentTrack: Track?,
    topPlayedTracks: List<Track>,
    allTracks: List<Track>,
    playlists: List<Playlist>,
    dynamicPrimaryColor: Color,
    dynamicSecondaryColor: Color,
    onSetWidgetDynamicColorEnabled: (Boolean) -> Unit,
    onSetWidgetGridContentMode: (WidgetGridContentMode) -> Unit,
    onSetWidgetShowProgress: (Boolean) -> Unit,
    onSetWidgetColorIntensityPercent: (Int) -> Unit,
    onForceSyncWidgets: () -> Unit,
    onBack: () -> Unit
) {
                    SettingsSubMenuScreen(
                        title = "Widget",
                        subtitle = "Color dinámico de carátula, adaptación al estirar y 4 Canciones / 4 Playlists",
                        icon = Icons.Default.Widgets,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onBack = onBack,
                        testTag = "subscreen_widgets"
                    ) {
                        widgetSettingsContent(
                            widgetConfig = widgetConfig,
                            currentTrack = currentTrack,
                            topTracks = topPlayedTracks.ifEmpty { allTracks },
                            playlists = playlists,
                            dynamicPrimaryColor = dynamicPrimaryColor,
                            dynamicSecondaryColor = dynamicSecondaryColor,
                            onSetDynamicColorEnabled = onSetWidgetDynamicColorEnabled,
                            onSetGridContentMode = onSetWidgetGridContentMode,
                            onSetShowProgressInWidget = onSetWidgetShowProgress,
                            onSetColorIntensityPercent = onSetWidgetColorIntensityPercent,
                            onForceSyncWidgets = onForceSyncWidgets
                        )
                    }
}
