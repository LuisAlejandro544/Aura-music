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
 * Pantalla Medios & Almacenamiento.
 * Rol arquitectónico: pantalla independiente con retroceso.
 */
@Composable
fun SettingsStoredMediaScreen(
    allTracks: List<Track>,
    onDeleteTrackArtwork: (Track) -> Unit,
    onDeleteTrackVideo: (Track) -> Unit,
    onBack: () -> Unit
) {
                    SettingsSubMenuScreen(
                        title = "Medios & Almacenamiento",
                        subtitle = "Gestión transparente de carátulas WebP y videos MP4 guardados",
                        icon = Icons.Default.PermMedia,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onBack = onBack,
                        testTag = "subscreen_stored_media"
                    ) {
                        storedMediaSettingsTab(
                            allTracks = allTracks,
                            onDeleteTrackArtwork = onDeleteTrackArtwork,
                            onDeleteTrackVideo = onDeleteTrackVideo
                        )
                    }
}

/**
 * Pantalla Motores Nativos & Diagnóstico.
 * Rol arquitectónico: pantalla independiente con retroceso.
 */
@Composable
fun SettingsEnginesScreen(
    onOpenOnboarding: () -> Unit,
    onBack: () -> Unit
) {
                    SettingsSubMenuScreen(
                        title = "Motores Nativos & Diagnóstico",
                        subtitle = "Arquitectura C++20, FFmpeg puro, yt-dlp OTA y Aura Monitor",
                        icon = Icons.Default.Build,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onBack = onBack,
                        testTag = "subscreen_engines_diagnostics"
                    ) {
                        enginesAndDiagnosticsSettingsContent(onOpenOnboarding = onOpenOnboarding)
                    }
}

/**
 * Pantalla Descargas & Cookies (YTDLnis).
 * Rol arquitectónico: pantalla independiente con retroceso.
 */
@Composable
fun SettingsDownloadsScreen(
    onBack: () -> Unit
) {
                    SettingsSubMenuScreen(
                        title = "Descargas & Cookies (YTDLnis)",
                        subtitle = "Estrategia yt-dlp natural, Cookies.txt opcionales, PO Token Web y OTA",
                        icon = Icons.Default.CloudDownload,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onBack = onBack,
                        testTag = "subscreen_downloads_and_cookies"
                    ) {
                        ytDlpCookiesAndPoTokenSettingsContent()
                    }
}

/**
 * Pantalla Detrás del Proyecto.
 * Rol arquitectónico: pantalla independiente con retroceso.
 */
@Composable
fun SettingsBehindProjectScreen(
    onBack: () -> Unit
) {
                    SettingsSubMenuScreen(
                        title = "Detrás del Proyecto",
                        subtitle = "Creador oficial, contribuidores y perfiles de GitHub",
                        icon = Icons.Default.Groups,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onBack = onBack,
                        testTag = "subscreen_behind_project"
                    ) {
                        behindTheProjectSettingsContent()
                    }
}
