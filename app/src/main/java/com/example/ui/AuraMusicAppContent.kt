package com.example.ui
import com.example.core.model.LyricSearchResult
import com.example.core.model.NowPlayingDesignMode
import com.example.core.model.RepeatMode

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.VideoDisplayMode
import com.example.ui.components.BottomNavBar
import com.example.ui.components.MiniPlayer
import com.example.ui.components.PackageUpdateBanner
import com.example.ui.components.SettingsDiscoveryBanner
import com.example.ui.navigation.NavScreen
import com.example.feature.home.HomeScreen
import com.example.feature.importmusic.ImportMusicScreen
import com.example.feature.library.LibraryScreen
import com.example.feature.nowplaying.NowPlayingScreen
import com.example.feature.onboarding.OnboardingScreen
import com.example.feature.playlist.PlaylistDetailScreen
import com.example.feature.settings.SettingsScreen
import com.example.ui.theme.ArtworkColorExtractor
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.ExtractedArtworkColors
import com.example.viewmodel.MusicViewModel

/**
 * Contenido principal y host de navegación de Aura Music.
 * Desacopla de MainActivity la estructura visual de Scaffold, transiciones de pantalla,
 * superposición fluida de NowPlayingScreen y mini reproductor flotante.
 */
@Composable
fun AuraMusicAppContent(viewModel: MusicViewModel) {

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val hasVisitedSettings by viewModel.hasVisitedSettings.collectAsStateWithLifecycle()
    val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsStateWithLifecycle()

    // Estados de reproducción Media3 (currentPosition se pasa como StateFlow para evitar recomponer toda la app 5 veces/s)
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    // Estados de Room

    // Estados de UI y Ecualizador

    val eqBands by viewModel.eqBands.collectAsStateWithLifecycle()
    val bassBoostLevel by viewModel.bassBoostLevel.collectAsStateWithLifecycle()
    val currentPreset by viewModel.currentPreset.collectAsStateWithLifecycle()
    val isEqEnabled by viewModel.isEqEnabled.collectAsStateWithLifecycle()

    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val spatial8DConfig by viewModel.spatial8DConfig.collectAsStateWithLifecycle()
    val vocalClarityConfig by viewModel.vocalClarityConfig.collectAsStateWithLifecycle()
    val packageUpdateState by viewModel.packageUpdateState.collectAsStateWithLifecycle()
    val reverbConfig by viewModel.reverbConfig.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val playbackPitch by viewModel.playbackPitch.collectAsStateWithLifecycle()
    val crossfadeSeconds by viewModel.crossfadeSeconds.collectAsStateWithLifecycle()
    val isGaplessEnabled by viewModel.isGaplessEnabled.collectAsStateWithLifecycle()
    val abLoopState by viewModel.abLoopState.collectAsStateWithLifecycle()
    val isBackgroundGameModeEnabled by viewModel.isBackgroundGameModeEnabled.collectAsStateWithLifecycle()
    val appWallpaperConfig by viewModel.appWallpaperConfig.collectAsStateWithLifecycle()
    val headphoneConfig by viewModel.headphoneConfig.collectAsStateWithLifecycle()
    val isSearchLyricsDialogOpen by viewModel.isSearchLyricsDialogOpen.collectAsStateWithLifecycle()
    val isSearchingLyrics by viewModel.isSearchingLyrics.collectAsStateWithLifecycle()
    val lyricsSearchResults by viewModel.lyricsSearchResults.collectAsStateWithLifecycle()
    val searchLyricsError by viewModel.searchLyricsError.collectAsStateWithLifecycle()
    val pendingIncomingAudioUris by viewModel.pendingIncomingAudioUris.collectAsStateWithLifecycle()
    val pendingIncomingVideoUri by viewModel.pendingIncomingVideoUri.collectAsStateWithLifecycle()
    val pendingIncomingWebLink by viewModel.pendingIncomingWebLink.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()

    // Estados de Mixtape Continuo con Capítulos Reactivos
    val activeMixtapeChapter by viewModel.activeMixtapeChapter.collectAsStateWithLifecycle()

    // Si la pista actual es un Mixtape continuo, virtualizamos la pista activa con el capítulo en curso
    val effectiveTrack = remember(currentTrack, activeMixtapeChapter) {
        val track = currentTrack
        val chapter = activeMixtapeChapter
        if (track != null && chapter != null) {
            chapter.toVirtualTrack(track)
        } else {
            track
        }
    }

    var showGlobalAudioEffectsSheet by remember { mutableStateOf(false) }
    var initialAudioEffectsTab by remember { mutableIntStateOf(0) }

    val miniColors = rememberMiniPlayerColors(
        viewModel = viewModel,
        effectiveTrack = effectiveTrack,
        isPlaying = isPlaying,
        isNowPlayingExpanded = isNowPlayingExpanded
    )
    val animatedMiniPrimary = miniColors.animatedPrimary
    val animatedMiniSecondary = miniColors.animatedSecondary

    val canGoBack = isNowPlayingExpanded || (currentScreen !is NavScreen.Home && currentScreen !is NavScreen.Onboarding)
    BackHandler(enabled = canGoBack) {
        viewModel.handleBackPress()
    }

    val isCustomWallpaperVisible = remember(appWallpaperConfig, currentScreen, isNowPlayingExpanded) {
        if (!appWallpaperConfig.hasValidMedia || isNowPlayingExpanded || currentScreen is NavScreen.Onboarding) {
            false
        } else {
            when (appWallpaperConfig.screenScope) {
                com.example.core.model.WallpaperScreenScope.LIBRARY_ONLY ->
                    currentScreen is NavScreen.Library || currentScreen is NavScreen.PlaylistDetail
                com.example.core.model.WallpaperScreenScope.HOME_AND_LIBRARY ->
                    currentScreen is NavScreen.Home || currentScreen is NavScreen.Library ||
                        currentScreen is NavScreen.PlaylistDetail || currentScreen is NavScreen.Streaming
                com.example.core.model.WallpaperScreenScope.ALL_SCREENS ->
                    true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Capa de fondo de pantalla personalizado de galería (Imagen WebP o Video corto MP4)
        com.example.ui.components.AppWallpaperBackground(
            config = appWallpaperConfig,
            currentScreen = currentScreen,
            isNowPlayingExpanded = isNowPlayingExpanded,
            isBackgroundGameModeEnabled = isBackgroundGameModeEnabled
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = if (isCustomWallpaperVisible) androidx.compose.ui.graphics.Color.Transparent else BackgroundDark,
            contentWindowInsets = WindowInsets.statusBars,
            bottomBar = {
                AppBottomBarHost(
                    viewModel = viewModel,
                    effectiveTrack = effectiveTrack,
                    animatedMiniPrimary = animatedMiniPrimary,
                    animatedMiniSecondary = animatedMiniSecondary
                )
            }        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (currentScreen !is NavScreen.Onboarding) {
                    PackageUpdateBanner(
                        state = packageUpdateState,
                        onApplyAndRestart = { viewModel.applyPendingPackageUpdateAndRestart() }
                    )
                    SettingsDiscoveryBanner(
                        visible = !hasVisitedSettings && currentScreen !is NavScreen.Settings,
                        onExploreSettings = { viewModel.navigateTo(NavScreen.Settings) },
                        onDismiss = { viewModel.markSettingsVisited() }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AppScreensHost(
                        viewModel = viewModel,
                        currentScreen = currentScreen,
                        effectiveTrack = effectiveTrack,
                        animatedMiniPrimary = animatedMiniPrimary,
                        animatedMiniSecondary = animatedMiniSecondary
                    )
                }
            }
        }

        // Pantalla Now Playing con deslizamiento suave sin capas negras residuales
        AppNowPlayingHost(
            viewModel = viewModel,
            effectiveTrack = effectiveTrack,
            isNowPlayingExpanded = isNowPlayingExpanded,
            onOpenGlobalEffects = {
                initialAudioEffectsTab = it
                showGlobalAudioEffectsSheet = true
            }
        )

        // Hospedador de diálogos emergentes y hojas modulares
        GlobalDialogsHost(
            viewModel = viewModel,
            showGlobalAudioEffectsSheet = showGlobalAudioEffectsSheet,
            initialAudioEffectsTab = initialAudioEffectsTab,
            onDismissAudioEffectsSheet = { showGlobalAudioEffectsSheet = false },
            pendingIncomingAudioUris = pendingIncomingAudioUris,
            pendingIncomingVideoUri = pendingIncomingVideoUri,
            pendingIncomingWebLink = pendingIncomingWebLink,
            downloadProgress = downloadProgress,
            isSearchLyricsDialogOpen = isSearchLyricsDialogOpen,
            isSearchingLyrics = isSearchingLyrics,
            lyricsSearchResults = lyricsSearchResults,
            searchLyricsError = searchLyricsError,
            eqBands = eqBands,
            bassBoostLevel = bassBoostLevel,
            currentPreset = currentPreset,
            isEqEnabled = isEqEnabled,
            spatial8DConfig = spatial8DConfig,
            vocalClarityConfig = vocalClarityConfig,
            reverbConfig = reverbConfig,
            sleepTimerState = sleepTimerState,
            playbackSpeed = playbackSpeed,
            playbackPitch = playbackPitch,
            crossfadeSeconds = crossfadeSeconds,
            isGaplessEnabled = isGaplessEnabled,
            abLoopState = abLoopState,
            headphoneConfig = headphoneConfig
        )
    }
}