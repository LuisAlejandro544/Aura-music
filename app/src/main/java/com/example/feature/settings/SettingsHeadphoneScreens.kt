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
 * Menú Auriculares & Acústica DSP (3 tarjetas).
 * Rol arquitectónico: pantalla intermedia hacia DSP, seguridad y gestos.
 */
@Composable
fun SettingsHeadphonesMenuScreen(
    headphoneConfig: HeadphoneConfig,
    onNavigate: (SettingsSubScreen) -> Unit,
    onBack: () -> Unit
) {
                    SettingsSubMenuScreen(
                        title = "Auriculares & Acústica DSP",
                        subtitle = "Crossfeed C++20, Balance Estéreo L/R, Seguridad y Gestos",
                        icon = Icons.Default.Headphones,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onBack = onBack,
                        testTag = "subscreen_headphones"
                    ) {
                        headphonesMenuSection(
                            headphoneConfig = headphoneConfig,
                            onOpenAcousticsDsp = { onNavigate(SettingsSubScreen.HEADPHONES_DSP) },
                            onOpenSecurity = { onNavigate(SettingsSubScreen.HEADPHONES_SECURITY) },
                            onOpenGestures = { onNavigate(SettingsSubScreen.HEADPHONES_GESTURES) }
                        )
                    }
}

/**
 * Sub-pantalla Acústica & DSP.
 * Rol arquitectónico: pantalla independiente con retroceso.
 */
@Composable
fun SettingsHeadphoneDspScreen(
    headphoneConfig: HeadphoneConfig,
    onSetCrossfeedEnabled: (Boolean) -> Unit,
    onSetCrossfeedStrength: (Int) -> Unit,
    onSetBalanceControlEnabled: (Boolean) -> Unit,
    onSetStereoBalance: (Float) -> Unit,
    onSetBitPerfectEnabled: (Boolean) -> Unit,
    onSetLowLatencyEnabled: (Boolean) -> Unit,
    onSetUsbDacExclusiveEnabled: (Boolean) -> Unit,
    onSetHiResTargetMode: (Int) -> Unit,
    onBack: () -> Unit
) {
                    SettingsSubMenuScreen(
                        title = "Acústica & DSP",
                        subtitle = "Modo Bit-Perfect 1:1, Crossfeed C++20 y Balance Estéreo L/R",
                        icon = Icons.Default.GraphicEq,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onBack = onBack,
                        testTag = "subscreen_headphones_dsp"
                    ) {
                        headphonesAcousticsSection(
                            headphoneConfig = headphoneConfig,
                            onSetCrossfeedEnabled = onSetCrossfeedEnabled,
                            onSetCrossfeedStrength = onSetCrossfeedStrength,
                            onSetBalanceControlEnabled = onSetBalanceControlEnabled,
                            onSetStereoBalance = onSetStereoBalance,
                            onSetBitPerfectEnabled = onSetBitPerfectEnabled,
                            onSetLowLatencyEnabled = onSetLowLatencyEnabled,
                            onSetUsbDacExclusiveEnabled = onSetUsbDacExclusiveEnabled,
                            onSetHiResTargetMode = onSetHiResTargetMode
                        )
                    }
}

/**
 * Sub-pantalla Seguridad & Conexión.
 * Rol arquitectónico: pantalla independiente con retroceso.
 */
@Composable
fun SettingsHeadphoneSecurityScreen(
    headphoneConfig: HeadphoneConfig,
    onSetBecomingNoisyGuardEnabled: (Boolean) -> Unit,
    onSetFadeInOnResumeEnabled: (Boolean) -> Unit,
    onSetDedicatedVolumeMemoryEnabled: (Boolean) -> Unit,
    onBack: () -> Unit
) {
                    SettingsSubMenuScreen(
                        title = "Seguridad & Conexión",
                        subtitle = "Protección contra desconexiones, Fade-In y Memoria de Volumen",
                        icon = Icons.Default.Security,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onBack = onBack,
                        testTag = "subscreen_headphones_security"
                    ) {
                        headphonesSecuritySection(
                            headphoneConfig = headphoneConfig,
                            onSetBecomingNoisyGuardEnabled = onSetBecomingNoisyGuardEnabled,
                            onSetFadeInOnResumeEnabled = onSetFadeInOnResumeEnabled,
                            onSetDedicatedVolumeMemoryEnabled = onSetDedicatedVolumeMemoryEnabled
                        )
                    }
}

/**
 * Sub-pantalla Botones y Gestos.
 * Rol arquitectónico: pantalla independiente con retroceso.
 */
@Composable
fun SettingsHeadphoneGesturesScreen(
    headphoneConfig: HeadphoneConfig,
    onSetHeadsetControlsEnabled: (Boolean) -> Unit,
    onEditClickType: (Int) -> Unit,
    onBack: () -> Unit
) {
                    SettingsSubMenuScreen(
                        title = "Botones y Gestos",
                        subtitle = "Personaliza las pulsaciones simples, dobles, triples y prolongadas",
                        icon = Icons.Default.TouchApp,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onBack = onBack,
                        testTag = "subscreen_headphones_gestures"
                    ) {
                        headphonesGesturesSection(
                            headphoneConfig = headphoneConfig,
                            onSetHeadsetControlsEnabled = onSetHeadsetControlsEnabled,
                            onOpenEditClickType = { onEditClickType(it) }
                        )
                    }
}

/**
 * Secciones del menú de auriculares (DSP, seguridad y gestos).
 * Rol arquitectónico: tarjetas de navegación con estado resumido.
 */
internal fun androidx.compose.foundation.lazy.LazyListScope.headphonesMenuSection(
    headphoneConfig: HeadphoneConfig,
    onOpenAcousticsDsp: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenGestures: () -> Unit
) {
    item {
        HeadphoneStatusCard(headphoneConfig = headphoneConfig)
        Spacer(modifier = Modifier.height(16.dp))
    }

    item {
        val dspStatus = buildList {
            if (headphoneConfig.isBitPerfectEnabled) add("Bit-Perfect 1:1")
            if (headphoneConfig.isCrossfeedEnabled) add("Crossfeed activo")
            if (headphoneConfig.isBalanceControlEnabled) add("Balance ${headphoneConfig.formattedBalance}")
        }.joinToString(" • ").ifEmpty { "Modo Bit-Perfect, Crossfeed Bauer/Chu Moy C++20 y Balance Estéreo L/R" }

        SettingsNavigationCard(
            icon = Icons.Default.GraphicEq,
            title = "Acústica & DSP",
            subtitle = dspStatus,
            iconTint = MaterialTheme.colorScheme.primary,
            badgeText = if (headphoneConfig.isBitPerfectEnabled || headphoneConfig.isCrossfeedEnabled) "ACTIVO" else null,
            badgeColor = MaterialTheme.colorScheme.primary,
            onClick = onOpenAcousticsDsp,
            testTag = "headphone_subtab_dsp"
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    item {
        val activeGuards = listOf(
            headphoneConfig.isBecomingNoisyGuardEnabled,
            headphoneConfig.isFadeInOnResumeEnabled,
            headphoneConfig.isDedicatedVolumeMemoryEnabled
        ).count { it }

        SettingsNavigationCard(
            icon = Icons.Default.Security,
            title = "Seguridad & Conexión",
            subtitle = "Protección contra desconexiones, Fade-In suave al reanudar y Memoria de Volumen",
            iconTint = MaterialTheme.colorScheme.secondary,
            badgeText = if (activeGuards > 0) "$activeGuards/3" else null,
            badgeColor = MaterialTheme.colorScheme.secondary,
            onClick = onOpenSecurity,
            testTag = "headphone_subtab_safety"
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    item {
        SettingsNavigationCard(
            icon = Icons.Default.TouchApp,
            title = "Botones y Gestos",
            subtitle = if (headphoneConfig.isHeadsetControlsEnabled) {
                "Controles físicos activos • 1x: ${headphoneConfig.singleClickAction.label}, 2x: ${headphoneConfig.doubleClickAction.label}"
            } else {
                "Personaliza las pulsaciones simples, dobles, triples y prolongadas de tus audífonos"
            },
            iconTint = MaterialTheme.colorScheme.primary,
            badgeText = if (headphoneConfig.isHeadsetControlsEnabled) "ON" else null,
            badgeColor = MaterialTheme.colorScheme.primary,
            onClick = onOpenGestures,
            testTag = "headphone_subtab_gestures"
        )
    }
}
