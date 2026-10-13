package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.example.core.data.importer.FFmpegNativeEngine
import com.example.core.data.importer.OnlineVideoAudioImporter
import com.example.core.data.importer.YtDlpAutoUpdater
import com.example.core.model.*
import com.example.core.playback.AuraAudioPlayer
import com.example.ui.navigation.LibraryTab
import com.example.ui.navigation.NavScreen
import com.example.viewmodel.delegates.AudioEffectsCoordinator
import com.example.viewmodel.delegates.HeadphoneSettingsCoordinator
import com.example.viewmodel.delegates.IncomingMediaCoordinator
import com.example.viewmodel.delegates.LyricsCoordinator
import com.example.viewmodel.delegates.MixtapeCoordinator
import com.example.viewmodel.delegates.NavigationCoordinator
import com.example.viewmodel.delegates.PlaylistImportCoordinator
import com.example.viewmodel.delegates.StreamingModeCoordinator
import com.example.viewmodel.delegates.TrackLibraryCoordinator
import com.example.viewmodel.delegates.WidgetSyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Fachadas delegantes de MusicViewModel como interfaces con métodos por defecto.
 * Rol arquitectónico: concentra todas las funciones públicas de un solo reenvío
 * hacia coordinadores y `audioPlayer`, preservando el 100% de la API pública
 * sin engordar la fachada y sin exigir imports nuevos en las pantallas
 * (los métodos por defecto se heredan como miembros de [MusicViewModel]).
 * Proveedores abstractos mínimos: [MusicViewModelScopeProvider] para
 * corrutinas y [MusicViewModelAppProvider] para el contexto de aplicación.
 */

internal interface MusicViewModelScopeProvider {
    val facadeScope: CoroutineScope
}

internal interface MusicViewModelAppProvider {
    val facadeApp: Application
}

// --- Navegación ---
internal interface MusicViewModelNavigationFacade :
    MusicViewModelScopeProvider, MusicViewModelAppProvider {
    val navigationCoordinator: NavigationCoordinator
    val appWallpaperConfig: StateFlow<AppWallpaperConfig>
    val videoDisplayMode: StateFlow<VideoDisplayMode>
    val nowPlayingDesignMode: StateFlow<NowPlayingDesignMode>
    val isMiniPlayerVideoEnabled: StateFlow<Boolean>

    fun navigateTo(screen: NavScreen) = navigationCoordinator.navigateTo(screen)
    fun markSettingsVisited() = navigationCoordinator.markSettingsVisited()
    fun completeOnboarding() = navigationCoordinator.completeOnboarding()
    fun reopenOnboarding() = navigationCoordinator.reopenOnboarding()
    fun handleBackPress(): Boolean = navigationCoordinator.handleBackPress()
    fun openPlaylist(playlist: Playlist) = navigationCoordinator.openPlaylist(playlist)
    fun openAlbum(albumName: String) = navigationCoordinator.openAlbum(albumName)
    fun openArtist(artistName: String) = navigationCoordinator.openArtist(artistName)
    fun setNowPlayingExpanded(expanded: Boolean) =
        navigationCoordinator.setNowPlayingExpanded(expanded)
    fun setSearchQuery(query: String) = navigationCoordinator.setSearchQuery(query)
    fun setLibraryTab(tab: LibraryTab) = navigationCoordinator.setLibraryTab(tab)
    fun setTheme(theme: AuraTheme) = navigationCoordinator.setTheme(theme)
    fun setDynamicArtworkColorEnabled(enabled: Boolean) =
        navigationCoordinator.setDynamicArtworkColorEnabled(enabled)
    fun toggleDynamicArtworkColor(enabled: Boolean) =
        navigationCoordinator.setDynamicArtworkColorEnabled(enabled)
    fun setMiniPlayerVideoEnabled(enabled: Boolean) =
        navigationCoordinator.setMiniPlayerVideoEnabled(enabled)
    fun toggleMiniPlayerVideoEnabled() =
        navigationCoordinator.setMiniPlayerVideoEnabled(!isMiniPlayerVideoEnabled.value)
    fun setBackgroundGameModeEnabled(enabled: Boolean) =
        navigationCoordinator.setBackgroundGameModeEnabled(enabled)
    fun toggleBackgroundGameMode(enabled: Boolean) =
        navigationCoordinator.setBackgroundGameModeEnabled(enabled)
    fun setAppWallpaperEnabled(enabled: Boolean) =
        navigationCoordinator.setAppWallpaperEnabled(enabled)
    fun setAppWallpaperScope(scope: WallpaperScreenScope) =
        navigationCoordinator.setAppWallpaperScope(scope)
    fun setAppWallpaperDimAlpha(alpha: Float) =
        navigationCoordinator.setAppWallpaperDimAlpha(alpha)
    fun setAppWallpaperBlurDp(blurDp: Int) =
        navigationCoordinator.setAppWallpaperBlurDp(blurDp)

    fun setCustomWallpaperImage(uri: Uri) {
        facadeScope.launch {
            val oldPath = appWallpaperConfig.value.mediaPath
            val savedPath = MusicViewModelWiring.importWallpaperImage(facadeApp, uri, oldPath)
            if (!savedPath.isNullOrBlank()) {
                navigationCoordinator.updateAppWallpaperMedia(
                    type = WallpaperMediaType.IMAGE,
                    path = savedPath,
                    enabled = true
                )
            }
        }
    }

    fun setCustomWallpaperVideo(uri: Uri) {
        facadeScope.launch {
            val oldPath = appWallpaperConfig.value.mediaPath
            val savedPath = MusicViewModelWiring.importWallpaperVideo(facadeApp, uri, oldPath)
            if (!savedPath.isNullOrBlank()) {
                navigationCoordinator.updateAppWallpaperMedia(
                    type = WallpaperMediaType.VIDEO,
                    path = savedPath,
                    enabled = true
                )
            }
        }
    }

    fun clearCustomWallpaper() {
        facadeScope.launch {
            val oldPath = appWallpaperConfig.value.mediaPath
            MusicViewModelWiring.deleteWallpaperFile(facadeApp, oldPath)
            navigationCoordinator.updateAppWallpaperMedia(
                type = WallpaperMediaType.NONE,
                path = "",
                enabled = false
            )
        }
    }

    fun setVideoDisplayMode(mode: VideoDisplayMode) =
        navigationCoordinator.setVideoDisplayMode(mode)
    fun cycleVideoDisplayMode() =
        navigationCoordinator.setVideoDisplayMode(videoDisplayMode.value.next())
    fun toggleVideoCanvas() =
        navigationCoordinator.setVideoDisplayMode(videoDisplayMode.value.next())
    fun setVideoCanvasActive(active: Boolean) {
        if (!active) navigationCoordinator.setVideoDisplayMode(VideoDisplayMode.OFF)
        else if (videoDisplayMode.value == VideoDisplayMode.OFF) {
            navigationCoordinator.setVideoDisplayMode(VideoDisplayMode.FULLSCREEN_BACKGROUND)
        }
    }
    fun setNowPlayingDesignMode(mode: NowPlayingDesignMode) =
        navigationCoordinator.setNowPlayingDesignMode(mode)
    fun cycleNowPlayingDesignMode() =
        navigationCoordinator.setNowPlayingDesignMode(nowPlayingDesignMode.value.next())
}

// --- Reproducción ---
internal interface MusicViewModelPlaybackFacade {
    val audioPlayer: AuraAudioPlayer

    fun togglePlayPause() = audioPlayer.togglePlayPause()
    fun playNext() = audioPlayer.playNext()
    fun playPrevious() = audioPlayer.playPrevious()
    fun seekTo(positionMs: Long) = audioPlayer.seekTo(positionMs)
    fun toggleShuffle() = audioPlayer.toggleShuffle()
    fun cycleRepeatMode() = audioPlayer.cycleRepeatMode()
    fun setPlaybackSpeed(speed: Float) = audioPlayer.setPlaybackSpeed(speed)
    fun setPlaybackPitch(pitch: Float) = audioPlayer.setPlaybackPitch(pitch)
    fun resetSpeedAndPitch() = audioPlayer.resetSpeedAndPitch()
    fun setCrossfadeSeconds(seconds: Int) = audioPlayer.setCrossfadeSeconds(seconds)
    fun setGaplessEnabled(enabled: Boolean) = audioPlayer.setGaplessEnabled(enabled)
    fun markABPointA(positionMs: Long = audioPlayer.currentPosition.value) =
        audioPlayer.markABPointA(positionMs)
    fun markABPointB(positionMs: Long = audioPlayer.currentPosition.value) =
        audioPlayer.markABPointB(positionMs)
    fun toggleABLoopEnabled(enabled: Boolean) = audioPlayer.toggleABLoopEnabled(enabled)
    fun adjustABPointA(deltaMs: Long) = audioPlayer.adjustABPointA(deltaMs)
    fun adjustABPointB(deltaMs: Long) = audioPlayer.adjustABPointB(deltaMs)
    fun clearABLoop() = audioPlayer.clearABLoop()
}

// --- Pistas, Playlists e Importación (TrackLibraryCoordinator) ---
internal interface MusicViewModelLibraryFacade :
    MusicViewModelScopeProvider {
    val trackLibraryCoordinator: TrackLibraryCoordinator
    val playlistImportCoordinator: PlaylistImportCoordinator
    val navigationCoordinator: NavigationCoordinator
    val allTracks: StateFlow<List<Track>>
    val playlists: StateFlow<List<Playlist>>

    fun playTrack(track: Track, fromList: List<Track>? = null)
    fun deleteTrack(track: Track) = trackLibraryCoordinator.deleteTrack(track)
    fun updateTrackInfo(trackId: Long, newTitle: String, newArtist: String, newAlbum: String) =
        trackLibraryCoordinator.updateTrackDetails(trackId, newTitle, newArtist, newAlbum)
    fun updateTrackDetails(
        trackId: Long,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        customArtUri: Uri? = null,
        removeArtwork: Boolean = false,
        customVideoUri: Uri? = null,
        removeVideo: Boolean = false,
        forceVideoLoop: Boolean? = null,
        loopStyle: FFmpegNativeEngine.CanvasLoopStyle = FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE
    ) = trackLibraryCoordinator.updateTrackDetails(
        trackId, newTitle, newArtist, newAlbum, customArtUri, removeArtwork, customVideoUri, removeVideo, forceVideoLoop, loopStyle
    )
    fun deleteTrackArtwork(track: Track) =
        trackLibraryCoordinator.updateTrackDetails(track.id, track.title, track.artist, track.album, removeArtwork = true)
    fun deleteTrackVideo(track: Track) =
        trackLibraryCoordinator.updateTrackDetails(track.id, track.title, track.artist, track.album, removeVideo = true)
    fun createPlaylist(name: String, description: String = "", customArtUri: Uri? = null) =
        trackLibraryCoordinator.createPlaylist(name, description, customArtUri)
    fun updatePlaylist(playlistId: Long, newName: String, newDescription: String = "", customArtUri: Uri? = null, removeArtwork: Boolean = false) =
        trackLibraryCoordinator.updatePlaylist(playlistId, newName, newDescription, customArtUri, removeArtwork)
    fun deletePlaylist(playlistId: Long) = trackLibraryCoordinator.deletePlaylist(playlistId)
    fun addTrackToPlaylist(playlistId: Long, trackId: Long) =
        trackLibraryCoordinator.addTrackToPlaylist(playlistId, trackId)
    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) =
        trackLibraryCoordinator.removeTrackFromPlaylist(playlistId, trackId)
    fun clearAllTracks() = trackLibraryCoordinator.clearAllTracks()
    fun importUris(uris: List<Uri>, trimSilence: Boolean = false) =
        trackLibraryCoordinator.importUris(uris, trimSilence)
    fun importFolder(treeUri: Uri, trimSilence: Boolean = false) =
        trackLibraryCoordinator.importFolder(treeUri, trimSilence)
    fun seedDemoTracks() = trackLibraryCoordinator.seedDemoTracks()
    fun importVideoAsTrack(
        videoUri: Uri,
        title: String,
        artist: String,
        album: String,
        attachAsCanvas: Boolean,
        forceLoop: Boolean?,
        trimSilence: Boolean = false,
        loopStyle: FFmpegNativeEngine.CanvasLoopStyle = FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE,
        onTrackCreated: ((Track) -> Unit)? = null
    ) = trackLibraryCoordinator.importVideoAsTrack(videoUri, title, artist, album, attachAsCanvas, forceLoop, trimSilence, loopStyle, onTrackCreated)
    fun importFromWebVideoLink(
        resolvedInfo: OnlineVideoAudioImporter.ResolvedMediaInfo,
        customTitle: String,
        customArtist: String,
        attachAsCanvas: Boolean,
        trimSilence: Boolean = false,
        loopStyle: FFmpegNativeEngine.CanvasLoopStyle = FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE,
        onSuccess: (Track) -> Unit
    ) = trackLibraryCoordinator.importFromWebVideoLink(resolvedInfo, customTitle, customArtist, attachAsCanvas, trimSilence, loopStyle, onSuccess)

    fun playDownloadedTrackFromNotification(trackId: Long) {
        if (trackId <= 0L) return
        facadeScope.launch {
            var target = allTracks.value.find { it.id == trackId }
            if (target == null) {
                delay(250L)
                target = allTracks.value.find { it.id == trackId }
            }
            if (target != null) {
                playTrack(target)
                navigationCoordinator.setNowPlayingExpanded(true)
            }
        }
    }

    fun openPlaylistImportDialog(prefilledUrl: String = "") {
        playlistImportCoordinator.openDialog(prefilledUrl)
    }
    fun dismissImportStatus() = trackLibraryCoordinator.dismissImportStatus()
}

// --- Mixtapes Continuos y Auto-Remix ---
internal interface MusicViewModelMixtapeFacade {
    val mixtapeCoordinator: MixtapeCoordinator

    fun createMixtape(
        tracks: List<Track>,
        title: String,
        crossfadeSeconds: Int = 5,
        onCreated: ((Track) -> Unit)? = null
    ) = mixtapeCoordinator.createMixtape(tracks, title, crossfadeSeconds, onCreated)
    fun createAutoRemix(
        trackA: Track,
        trackB: Track,
        title: String,
        style: AutoRemixStyle,
        crossfadeSeconds: Int = 6,
        onCreated: ((Track) -> Unit)? = null
    ) = mixtapeCoordinator.createAutoRemix(trackA, trackB, title, style, crossfadeSeconds, onCreated)
    fun dismissMixtapeStatus() = mixtapeCoordinator.dismissStatus()
    fun getActiveChapterIndex(): Int = mixtapeCoordinator.getActiveChapterIndex()
    fun getActiveTotalChapters(): Int = mixtapeCoordinator.getActiveTotalChapters()
}

// --- Efectos de Audio ---
internal interface MusicViewModelEffectsFacade {
    val effectsCoordinator: AudioEffectsCoordinator

    fun setEqEnabled(enabled: Boolean) = effectsCoordinator.setEqEnabled(enabled)
    fun setEqScopeMode(mode: EqualizerScopeMode) = effectsCoordinator.setEqScopeMode(mode)
    fun setBandLevel(bandIndex: Int, levelMb: Int) = effectsCoordinator.setBandLevel(bandIndex, levelMb)
    fun setBassBoost(level: Int) = effectsCoordinator.setBassBoost(level)
    fun applyPreset(preset: EqualizerPreset) = effectsCoordinator.applyPreset(preset)
    fun set8DEnabled(enabled: Boolean) = effectsCoordinator.set8DEnabled(enabled)
    fun set8DMode16D(is16DMode: Boolean) = effectsCoordinator.set8DMode16D(is16DMode)
    fun set8DOrbitSpeed(speedSeconds: Float) = effectsCoordinator.set8DOrbitSpeed(speedSeconds)
    fun set8DSpatialIntensity(intensity: Float) = effectsCoordinator.set8DSpatialIntensity(intensity)
    fun set8DRoomDepth(depth: Float) = effectsCoordinator.set8DRoomDepth(depth)
    fun setVocalClarityEnabled(enabled: Boolean) = effectsCoordinator.setVocalClarityEnabled(enabled)
    fun setVocalClarityStrength(strength: Float) = effectsCoordinator.setVocalClarityStrength(strength)
    fun setReverbEnabled(enabled: Boolean) = effectsCoordinator.setReverbEnabled(enabled)
    fun setReverbPreset(preset: ReverbPreset) = effectsCoordinator.setReverbPreset(preset)
    fun setReverbCustomParameters(roomSize: Float, decayMs: Int, levelDb: Float) =
        effectsCoordinator.setReverbCustomParameters(roomSize, decayMs, levelDb)
    fun startSleepTimer(minutes: Int) = effectsCoordinator.startSleepTimer(minutes)
    fun cancelSleepTimer() = effectsCoordinator.cancelSleepTimer()
    fun addSleepTimerMinutes(extraMinutes: Int = 5) =
        effectsCoordinator.addSleepTimerMinutes(extraMinutes)
    fun setVolumeNormalizationEnabled(enabled: Boolean) =
        effectsCoordinator.setVolumeNormalizationEnabled(enabled)
    fun setVolumeNormalizationMode(mode: Int) =
        effectsCoordinator.setVolumeNormalizationMode(mode)
    fun setDjAutomixEnabled(enabled: Boolean) = effectsCoordinator.setDjAutomixEnabled(enabled)
    fun setDjEqCurveEnabled(enabled: Boolean) = effectsCoordinator.setDjEqCurveEnabled(enabled)
}

// --- Paquetes yt-dlp ---
internal interface MusicViewModelPackageUpdateFacade : MusicViewModelAppProvider {
    fun applyPendingPackageUpdateAndRestart() =
        YtDlpAutoUpdater.applyPendingUpdateAndRestart(facadeApp)
}

// --- Auriculares ---
internal interface MusicViewModelHeadphoneFacade {
    val headphoneCoordinator: HeadphoneSettingsCoordinator

    fun updateHeadphoneConfig(newConfig: HeadphoneConfig) =
        headphoneCoordinator.updateHeadphoneConfig(newConfig)
    fun setCrossfeedEnabled(enabled: Boolean) = headphoneCoordinator.setCrossfeedEnabled(enabled)
    fun setCrossfeedStrength(strengthMode: Int) = headphoneCoordinator.setCrossfeedStrength(strengthMode)
    fun setBalanceControlEnabled(enabled: Boolean) =
        headphoneCoordinator.setBalanceControlEnabled(enabled)
    fun setStereoBalance(balance: Float) = headphoneCoordinator.setStereoBalance(balance)
    fun setBitPerfectEnabled(enabled: Boolean) = headphoneCoordinator.setBitPerfectEnabled(enabled)
    fun setLowLatencyAAudioEnabled(enabled: Boolean) =
        headphoneCoordinator.setLowLatencyAAudioEnabled(enabled)
    fun setUsbDacExclusiveEnabled(enabled: Boolean) =
        headphoneCoordinator.setUsbDacExclusiveEnabled(enabled)
    fun setHiResTargetMode(mode: Int) = headphoneCoordinator.setHiResTargetMode(mode)
    fun setBecomingNoisyGuardEnabled(enabled: Boolean) =
        headphoneCoordinator.setBecomingNoisyGuardEnabled(enabled)
    fun setFadeInOnResumeEnabled(enabled: Boolean) =
        headphoneCoordinator.setFadeInOnResumeEnabled(enabled)
    fun setDedicatedVolumeMemoryEnabled(enabled: Boolean) =
        headphoneCoordinator.setDedicatedVolumeMemoryEnabled(enabled)
    fun setHeadsetControlsEnabled(enabled: Boolean) =
        headphoneCoordinator.setHeadsetControlsEnabled(enabled)
    fun setHeadsetSingleClickAction(action: HeadsetButtonAction) =
        headphoneCoordinator.setHeadsetSingleClickAction(action)
    fun setHeadsetDoubleClickAction(action: HeadsetButtonAction) =
        headphoneCoordinator.setHeadsetDoubleClickAction(action)
    fun setHeadsetTripleClickAction(action: HeadsetButtonAction) =
        headphoneCoordinator.setHeadsetTripleClickAction(action)
    fun setHeadsetLongClickAction(action: HeadsetButtonAction) =
        headphoneCoordinator.setHeadsetLongClickAction(action)
    fun setHeadsetAction(type: Int, action: HeadsetButtonAction) =
        headphoneCoordinator.setHeadsetAction(type, action)
}

// --- Letras Sincronizadas ---
internal interface MusicViewModelLyricsFacade {
    val lyricsCoordinator: LyricsCoordinator

    fun loadLyrics(track: Track?) = lyricsCoordinator.loadLyrics(track)
    fun importLyricsFromUri(uri: Uri) = lyricsCoordinator.importLyricsFromUri(uri)
    fun fetchOnlineLyrics(track: Track? = null) = lyricsCoordinator.fetchOnlineLyrics(track)
    fun saveCustomLyrics(content: String, track: Track? = null) =
        lyricsCoordinator.saveCustomLyrics(content, track)
    fun openSearchLyricsDialog() = lyricsCoordinator.openSearchLyricsDialog()
    fun closeSearchLyricsDialog() = lyricsCoordinator.closeSearchLyricsDialog()
    fun searchLyricsOptions(title: String, artist: String = "") =
        lyricsCoordinator.searchLyricsOptions(title, artist)
    fun selectLyricSearchResult(result: LyricSearchResult) =
        lyricsCoordinator.selectLyricSearchResult(result)
}

// --- Medios Entrantes ---
internal interface MusicViewModelIncomingFacade {
    val incomingMediaCoordinator: IncomingMediaCoordinator

    fun onIncomingIntent(intent: Intent?) = incomingMediaCoordinator.onIncomingIntent(intent)
    fun clearPendingIncomingAudio() = incomingMediaCoordinator.clearPendingIncomingAudio()
    fun confirmIncomingAudioImport(trimSilence: Boolean) =
        incomingMediaCoordinator.confirmIncomingAudioImport(trimSilence)
    fun clearPendingIncomingVideo() = incomingMediaCoordinator.clearPendingIncomingVideo()
    fun clearPendingIncomingWebLink() = incomingMediaCoordinator.clearPendingIncomingWebLink()
    fun handleIncomingAudioUri(uri: Uri, trimSilence: Boolean = false) =
        incomingMediaCoordinator.handleIncomingAudioUri(uri, trimSilence)
    fun handleIncomingMultipleAudioUris(uris: List<Uri>, trimSilence: Boolean = false) =
        incomingMediaCoordinator.handleIncomingMultipleAudioUris(uris, trimSilence)
}

// --- Modo Streaming ---
internal interface MusicViewModelStreamingFacade {
    val streamingCoordinator: StreamingModeCoordinator
    val navigationCoordinator: NavigationCoordinator
    val streamingSearchQuery: StateFlow<String>

    fun updateStreamingSearchQuery(query: String) = streamingCoordinator.updateSearchQuery(query)
    fun performStreamingSearch(query: String = streamingSearchQuery.value) =
        streamingCoordinator.performSearch(query)
    fun setStreamingSearchFilter(filter: StreamingSearchFilter) =
        streamingCoordinator.setSearchFilter(filter)
    fun playStreamingItem(item: StreamingSearchItem) {
        streamingCoordinator.playStreamingItem(item)
        navigationCoordinator.setNowPlayingExpanded(true)
    }
    fun downloadStreamingItemPermanently(item: StreamingSearchItem) =
        streamingCoordinator.downloadStreamingItemPermanently(item, includeVideoCanvas = true)
    fun downloadCurrentStreamingTrackPermanently(track: Track) =
        streamingCoordinator.downloadCurrentStreamingTrackPermanently(track)
    fun setStreamingVideoNetworkPolicy(policy: StreamingVideoNetworkPolicy) =
        streamingCoordinator.setStreamingVideoNetworkPolicy(policy)
    fun setStreamingCacheMaxSizeMb(sizeMb: Int) =
        streamingCoordinator.setStreamingCacheMaxSizeMb(sizeMb)
    fun clearStreamingCache() = streamingCoordinator.clearStreamingCache()
}

// --- Widgets de Escritorio ---
internal interface MusicViewModelWidgetFacade {
    val widgetSyncCoordinator: WidgetSyncCoordinator

    fun setWidgetDynamicColorEnabled(enabled: Boolean) =
        widgetSyncCoordinator.setWidgetDynamicColorEnabled(enabled)
    fun setWidgetGridContentMode(mode: WidgetGridContentMode) =
        widgetSyncCoordinator.setWidgetGridContentMode(mode)
    fun setWidgetShowProgress(enabled: Boolean) =
        widgetSyncCoordinator.setWidgetShowProgress(enabled)
    fun setWidgetColorIntensityPercent(percent: Int) =
        widgetSyncCoordinator.setWidgetColorIntensityPercent(percent)
    fun forceSyncAllWidgets() = widgetSyncCoordinator.forceSyncAllWidgets()
}
