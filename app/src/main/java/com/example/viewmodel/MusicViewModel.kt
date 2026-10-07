package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.importer.FFmpegNativeEngine
import com.example.data.importer.OnlineVideoAudioImporter
import com.example.data.importer.YtDlpAutoUpdater
import com.example.data.local.AppDatabase
import com.example.data.repository.MusicRepository
import com.example.data.storage.AppStorageManager
import com.example.model.*
import com.example.playback.AudioEffectManager
import com.example.playback.AuraAudioPlayer
import com.example.playback.HeadphoneController
import com.example.playback.NativeAudioEngine
import com.example.ui.navigation.LibraryTab
import com.example.ui.navigation.NavScreen
import com.example.viewmodel.delegates.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel central de Aura Music.
 * Arquitectura Modular MVVM (< 500 líneas):
 * Delega responsabilidades en coordinadores modulares:
 * - [AudioEffectsCoordinator]: EQ 10 bandas C++20, 8D/16D, Reverb, Vocal Clarity y Sleep Timer.
 * - [NavigationCoordinator]: Rutas de navegación, temas visuales y selección de colecciones.
 * - [HeadphoneSettingsCoordinator]: Acústica DSP, seguridad y control de audífonos.
 * - [LyricsCoordinator]: Karaoke sincronizado LRCLIB, parsing LRC y búsqueda interactiva.
 * - [IncomingMediaCoordinator]: Recepción de enlaces e intents externos ("Abrir con..." / "Compartir con...").
 * - [TrackLibraryCoordinator]: Gestión de pistas, metadatos, playlists e importaciones SAF / Video a Música.
 * - [MixtapeCoordinator]: Fusión continua de pistas con FFmpeg y capítulos reactivos en tiempo real.
 */
class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = MusicRepository(database)
    val effectManager = AudioEffectManager()
    val audioPlayer = AuraAudioPlayer(application, effectManager)

    // Controlador y gestor de hardware para Auriculares / Audífonos
    val headphoneController = HeadphoneController(
        context = application,
        onPauseRequested = { audioPlayer.pause() },
        onPlayRequested = { audioPlayer.play() },
        onTogglePlayPauseRequested = { audioPlayer.togglePlayPause() },
        onNextRequested = { audioPlayer.playNext() },
        onPrevRequested = { audioPlayer.playPrevious() },
        onToggleFavoriteRequested = {
            audioPlayer.currentTrack.value?.let { track -> toggleFavorite(track) }
        },
        onSeekByOffset = { offsetMs ->
            val cur = audioPlayer.currentPosition.value
            val target = (cur + offsetMs).coerceAtLeast(0L)
            audioPlayer.seekTo(target)
        },
        onVolumeChangeRequested = { vol -> audioPlayer.setVolume(vol) },
        getCurrentVolume = { audioPlayer.getVolume() }
    )

    val headphoneCoordinator = HeadphoneSettingsCoordinator(
        headphoneController = headphoneController,
        audioPlayer = audioPlayer
    )
    val headphoneConfig: StateFlow<HeadphoneConfig> = headphoneCoordinator.headphoneConfig

    // Coordinador modular de Efectos de Audio y Sleep Timer
    private val effectsCoordinator = AudioEffectsCoordinator(
        effectManager = effectManager,
        audioPlayer = audioPlayer,
        coroutineScope = viewModelScope
    )

    // Coordinador modular de Navegación y Preferencias de UI
    private val appPrefs = getApplication<Application>().getSharedPreferences("aura_music_ui_prefs", android.content.Context.MODE_PRIVATE)
    private val navigationCoordinator = NavigationCoordinator(
        appPrefs = appPrefs,
        repository = repository,
        coroutineScope = viewModelScope,
        getAllTracks = { allTracks.value }
    )

    // Coordinador modular de Biblioteca, Playlists e Importaciones
    val trackLibraryCoordinator = TrackLibraryCoordinator(
        context = application,
        repository = repository,
        audioPlayer = audioPlayer,
        coroutineScope = viewModelScope,
        onUpdatePlaylistNav = { id, name, desc, art ->
            navigationCoordinator.updateSelectedPlaylistMetadata(id, name, desc, art)
        },
        onPlaylistDeleted = { id ->
            if (selectedPlaylist.value?.id == id) handleBackPress()
        }
    )

    // Coordinador modular de Mixtape y Capítulos Continuos
    val mixtapeCoordinator = MixtapeCoordinator(application, viewModelScope)
    val isCreatingMixtape: StateFlow<Boolean> = mixtapeCoordinator.isCreatingMixtape
    val mixtapeProgress: StateFlow<Float> = mixtapeCoordinator.mixtapeProgress
    val mixtapeStatusMessage: StateFlow<String?> = mixtapeCoordinator.mixtapeStatusMessage
    val activeMixtapeChapter = mixtapeCoordinator.activeMixtapeChapter
    val activeMixtapeMetadata = mixtapeCoordinator.activeMixtapeMetadata

    // Datos reactivos de Room
    val allTracks: StateFlow<List<Track>> = repository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteTracks: StateFlow<List<Track>> = repository.favoriteTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyAddedTracks: StateFlow<List<Track>> = repository.recentlyAddedTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topPlayedTracks: StateFlow<List<Track>> = repository.topPlayedTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Estado del reproductor Media3
    val currentTrack = audioPlayer.currentTrack
    val isPlaying = audioPlayer.isPlaying
    val currentPosition = audioPlayer.currentPosition
    val duration = audioPlayer.duration
    val shuffleEnabled = audioPlayer.shuffleEnabled
    val repeatMode = audioPlayer.repeatMode
    val queue = audioPlayer.queue
    val currentIndex = audioPlayer.currentIndex
    val playbackError = audioPlayer.playbackError

    // Estado de ecualizador y efectos
    val eqBands = effectsCoordinator.eqBands
    val bassBoostLevel = effectsCoordinator.bassBoostLevel
    val currentPreset = effectsCoordinator.currentPreset
    val isEqEnabled = effectsCoordinator.isEqEnabled
    val spatial8DConfig = effectsCoordinator.spatial8DConfig
    val vocalClarityConfig = effectsCoordinator.vocalClarityConfig
    val reverbConfig = effectsCoordinator.reverbConfig
    val sleepTimerState = effectsCoordinator.sleepTimerState
    val volumeNormalizationConfig = effectsCoordinator.volumeNormalizationConfig
    val isDjAutomixEnabled = effectsCoordinator.isDjAutomixEnabled
    val isDjEqCurveEnabled = effectsCoordinator.isDjEqCurveEnabled

    // Estado del actualizador de paquetes yt-dlp
    val packageUpdateState = YtDlpAutoUpdater.packageUpdateState

    // Parámetros de reproducción
    val playbackSpeed = audioPlayer.playbackSpeed
    val playbackPitch = audioPlayer.playbackPitch
    val crossfadeSeconds = audioPlayer.crossfadeSeconds
    val isGaplessEnabled = audioPlayer.isGaplessEnabled
    val abLoopState = audioPlayer.abLoopState

    // Estados de navegación y UI
    val isOnboardingCompleted = navigationCoordinator.isOnboardingCompleted
    val currentScreen = navigationCoordinator.currentScreen
    val selectedPlaylist = navigationCoordinator.selectedPlaylist
    val selectedPlaylistTracks = navigationCoordinator.selectedPlaylistTracks
    val isNowPlayingExpanded = navigationCoordinator.isNowPlayingExpanded
    val searchQuery = navigationCoordinator.searchQuery
    val selectedLibraryTab = navigationCoordinator.selectedLibraryTab
    val currentTheme = navigationCoordinator.currentTheme
    val isDynamicArtworkColorEnabled = navigationCoordinator.isDynamicArtworkColorEnabled
    val isMiniPlayerVideoEnabled = navigationCoordinator.isMiniPlayerVideoEnabled
    val videoDisplayMode = navigationCoordinator.videoDisplayMode
    val isVideoCanvasActive: StateFlow<Boolean> = videoDisplayMode.map { it != VideoDisplayMode.OFF }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val nowPlayingDesignMode = navigationCoordinator.nowPlayingDesignMode

    // Estados de importación y progreso de descargas
    val isImporting = trackLibraryCoordinator.isImporting
    val importStatusMessage = trackLibraryCoordinator.importStatusMessage
    val downloadProgress = trackLibraryCoordinator.downloadProgress

    // Espectro de audio para visualizador C++20
    private val _visualizerBands = MutableStateFlow(FloatArray(28))
    val visualizerBands: StateFlow<FloatArray> = _visualizerBands.asStateFlow()

    private val _audioIntensity = MutableStateFlow(0f)
    val audioIntensity: StateFlow<Float> = _audioIntensity.asStateFlow()

    // Coordinador de Letras y Karaoke
    val lyricsCoordinator = LyricsCoordinator(
        context = application,
        storageManager = AppStorageManager(application),
        scope = viewModelScope,
        getCurrentTrack = { audioPlayer.currentTrack.value }
    )
    val lyricsState: StateFlow<LyricsState> = lyricsCoordinator.lyricsState
    val isSearchLyricsDialogOpen: StateFlow<Boolean> = lyricsCoordinator.isSearchLyricsDialogOpen
    val isSearchingLyrics: StateFlow<Boolean> = lyricsCoordinator.isSearchingLyrics
    val lyricsSearchResults: StateFlow<List<LyricSearchResult>> = lyricsCoordinator.lyricsSearchResults
    val searchLyricsError: StateFlow<String?> = lyricsCoordinator.searchLyricsError

    // Coordinador de Medios Entrantes
    val incomingMediaCoordinator = IncomingMediaCoordinator(
        context = application,
        repository = repository,
        scope = viewModelScope,
        onPlayTrack = { track, list -> playTrack(track, list) },
        onExpandNowPlaying = { setNowPlayingExpanded(true) },
        onFetchOnlineLyrics = { lyricsCoordinator.fetchOnlineLyrics() },
        setImportingStatus = { isImp, msg ->
            trackLibraryCoordinator.setImporting(isImp, msg)
        }
    )
    val pendingIncomingAudioUris: StateFlow<List<Uri>> = incomingMediaCoordinator.pendingIncomingAudioUris
    val pendingIncomingVideoUri: StateFlow<Uri?> = incomingMediaCoordinator.pendingIncomingVideoUri
    val pendingIncomingWebLink: StateFlow<String?> = incomingMediaCoordinator.pendingIncomingWebLink

    init {
        // Hilo de renderizado de espectro FFT C++20
        viewModelScope.launch(Dispatchers.Default) {
            val buffer = FloatArray(28)
            while (true) {
                if (audioPlayer.isPlaying.value) {
                    NativeAudioEngine.getVisualizerBands(buffer)
                    _visualizerBands.value = buffer.copyOf()
                    _audioIntensity.value = NativeAudioEngine.getAudioIntensity()
                    delay(25L)
                } else {
                    var needsDecay = false
                    for (i in buffer.indices) {
                        if (buffer[i] > 0.08f) {
                            buffer[i] = buffer[i] * 0.82f
                            needsDecay = true
                        }
                    }
                    if (_audioIntensity.value > 0.06f) {
                        _audioIntensity.value = _audioIntensity.value * 0.80f
                        needsDecay = true
                    }
                    if (needsDecay) {
                        _visualizerBands.value = buffer.copyOf()
                        delay(35L)
                    } else {
                        delay(120L)
                    }
                }
            }
        }

        // Carga automática de letras sincronizadas
        viewModelScope.launch {
            audioPlayer.currentTrack.collect { track -> loadLyrics(track) }
        }

        // Seguimiento reactivo del capítulo activo para Mixtapes Continuos
        viewModelScope.launch {
            combine(audioPlayer.currentTrack, audioPlayer.currentPosition) { trk, pos ->
                trk to pos
            }.collect { (trk, pos) ->
                mixtapeCoordinator.updateActiveChapter(trk, pos)
            }
        }

        // Sincronización reactiva del reproductor con Room
        viewModelScope.launch {
            repository.allTracks.collect { tracks ->
                val current = audioPlayer.currentTrack.value
                if (current != null) {
                    val updated = tracks.find { it.id == current.id }
                    if (updated != null && (updated.isFavorite != current.isFavorite || updated.title != current.title || updated.artist != current.artist || updated.album != current.album || updated.albumArtPath != current.albumArtPath || updated.videoUri != current.videoUri || updated.isVideoLoop != current.isVideoLoop)) {
                        audioPlayer.updateTrackFavorite(current.id, updated.isFavorite)
                        audioPlayer.updateTrackMetadata(
                            trackId = updated.id,
                            title = updated.title,
                            artist = updated.artist,
                            album = updated.album,
                            albumArtPath = updated.albumArtPath,
                            updateArt = true,
                            videoUri = updated.videoUri,
                            isVideoLoop = updated.isVideoLoop,
                            updateVideo = true
                        )
                    }
                }
            }
        }
    }

    // --- Navegación ---
    fun navigateTo(screen: NavScreen) = navigationCoordinator.navigateTo(screen)
    fun completeOnboarding() = navigationCoordinator.completeOnboarding()
    fun reopenOnboarding() = navigationCoordinator.reopenOnboarding()
    fun handleBackPress(): Boolean = navigationCoordinator.handleBackPress()
    fun openPlaylist(playlist: Playlist) = navigationCoordinator.openPlaylist(playlist)
    fun openAlbum(albumName: String) = navigationCoordinator.openAlbum(albumName)
    fun openArtist(artistName: String) = navigationCoordinator.openArtist(artistName)
    fun setNowPlayingExpanded(expanded: Boolean) = navigationCoordinator.setNowPlayingExpanded(expanded)
    fun setSearchQuery(query: String) = navigationCoordinator.setSearchQuery(query)
    fun setLibraryTab(tab: LibraryTab) = navigationCoordinator.setLibraryTab(tab)
    fun setTheme(theme: AuraTheme) = navigationCoordinator.setTheme(theme)
    fun setDynamicArtworkColorEnabled(enabled: Boolean) = navigationCoordinator.setDynamicArtworkColorEnabled(enabled)
    fun toggleDynamicArtworkColor(enabled: Boolean) = setDynamicArtworkColorEnabled(enabled)
    fun setMiniPlayerVideoEnabled(enabled: Boolean) = navigationCoordinator.setMiniPlayerVideoEnabled(enabled)
    fun toggleMiniPlayerVideoEnabled() = setMiniPlayerVideoEnabled(!isMiniPlayerVideoEnabled.value)
    fun setVideoDisplayMode(mode: VideoDisplayMode) = navigationCoordinator.setVideoDisplayMode(mode)
    fun cycleVideoDisplayMode() = setVideoDisplayMode(videoDisplayMode.value.next())
    fun toggleVideoCanvas() = cycleVideoDisplayMode()
    fun setVideoCanvasActive(active: Boolean) {
        if (!active) setVideoDisplayMode(VideoDisplayMode.OFF)
        else if (videoDisplayMode.value == VideoDisplayMode.OFF) setVideoDisplayMode(VideoDisplayMode.FULLSCREEN_BACKGROUND)
    }
    fun setNowPlayingDesignMode(mode: NowPlayingDesignMode) = navigationCoordinator.setNowPlayingDesignMode(mode)
    fun cycleNowPlayingDesignMode() = setNowPlayingDesignMode(nowPlayingDesignMode.value.next())

    // --- Reproducción ---
    fun playTrack(track: Track, fromList: List<Track>? = null) {
        viewModelScope.launch {
            repository.incrementPlayCount(track.id)
            val fullList = allTracks.value
            val list = if (!fromList.isNullOrEmpty()) {
                fromList
            } else if (fullList.isNotEmpty()) {
                if (fullList.any { it.id == track.id }) fullList
                else (listOf(track) + fullList).distinctBy { it.id }
            } else {
                listOf(track)
            }
            val index = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            audioPlayer.playTrackList(list, index)
        }
    }

    fun togglePlayPause() = audioPlayer.togglePlayPause()
    fun playNext() = audioPlayer.playNext()
    fun playPrevious() = audioPlayer.playPrevious()
    fun seekTo(positionMs: Long) = audioPlayer.seekTo(positionMs)
    fun toggleShuffle() = audioPlayer.toggleShuffle()
    fun cycleRepeatMode() = audioPlayer.cycleRepeatMode()

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            repository.toggleFavorite(track.id, track.isFavorite)
            audioPlayer.updateTrackFavorite(track.id, !track.isFavorite)
        }
    }

    // --- Pistas y Playlists (Delegadas en TrackLibraryCoordinator) ---
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
    fun deleteTrackArtwork(track: Track) = updateTrackDetails(track.id, track.title, track.artist, track.album, removeArtwork = true)
    fun deleteTrackVideo(track: Track) = updateTrackDetails(track.id, track.title, track.artist, track.album, removeVideo = true)

    fun createPlaylist(name: String, description: String = "", customArtUri: Uri? = null) =
        trackLibraryCoordinator.createPlaylist(name, description, customArtUri)
    fun updatePlaylist(playlistId: Long, newName: String, newDescription: String = "", customArtUri: Uri? = null, removeArtwork: Boolean = false) =
        trackLibraryCoordinator.updatePlaylist(playlistId, newName, newDescription, customArtUri, removeArtwork)
    fun deletePlaylist(playlistId: Long) = trackLibraryCoordinator.deletePlaylist(playlistId)
    fun addTrackToPlaylist(playlistId: Long, trackId: Long) = trackLibraryCoordinator.addTrackToPlaylist(playlistId, trackId)
    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) = trackLibraryCoordinator.removeTrackFromPlaylist(playlistId, trackId)
    fun clearAllTracks() = trackLibraryCoordinator.clearAllTracks()

    // --- Importación de Medios ---
    fun importUris(uris: List<Uri>, trimSilence: Boolean = false) = trackLibraryCoordinator.importUris(uris, trimSilence)
    fun importFolder(treeUri: Uri, trimSilence: Boolean = false) = trackLibraryCoordinator.importFolder(treeUri, trimSilence)
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
        viewModelScope.launch {
            var target = allTracks.value.find { it.id == trackId }
            if (target == null) {
                delay(250L)
                target = allTracks.value.find { it.id == trackId }
            }
            if (target != null) {
                playTrack(target)
                setNowPlayingExpanded(true)
            }
        }
    }

    // --- Mixtapes Continuos (FFmpeg) ---
    fun createMixtape(
        tracks: List<Track>,
        title: String,
        crossfadeSeconds: Int = 5,
        onCreated: ((Track) -> Unit)? = null
    ) = mixtapeCoordinator.createMixtape(tracks, title, crossfadeSeconds, onCreated)

    fun dismissMixtapeStatus() = mixtapeCoordinator.dismissStatus()
    fun getActiveChapterIndex(): Int = mixtapeCoordinator.getActiveChapterIndex()
    fun getActiveTotalChapters(): Int = mixtapeCoordinator.getActiveTotalChapters()

    fun dismissImportStatus() = trackLibraryCoordinator.dismissImportStatus()

    // --- Efectos de Audio ---
    fun setEqEnabled(enabled: Boolean) = effectsCoordinator.setEqEnabled(enabled)
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
    fun setReverbCustomParameters(roomSize: Float, decayMs: Int, levelDb: Float) = effectsCoordinator.setReverbCustomParameters(roomSize, decayMs, levelDb)
    fun startSleepTimer(minutes: Int) = effectsCoordinator.startSleepTimer(minutes)
    fun cancelSleepTimer() = effectsCoordinator.cancelSleepTimer()
    fun addSleepTimerMinutes(extraMinutes: Int = 5) = effectsCoordinator.addSleepTimerMinutes(extraMinutes)
    fun setVolumeNormalizationEnabled(enabled: Boolean) = effectsCoordinator.setVolumeNormalizationEnabled(enabled)
    fun setVolumeNormalizationMode(mode: Int) = effectsCoordinator.setVolumeNormalizationMode(mode)
    fun setDjAutomixEnabled(enabled: Boolean) = effectsCoordinator.setDjAutomixEnabled(enabled)
    fun setDjEqCurveEnabled(enabled: Boolean) = effectsCoordinator.setDjEqCurveEnabled(enabled)

    // Parámetros de velocidad y bucle A-B
    fun setPlaybackSpeed(speed: Float) = audioPlayer.setPlaybackSpeed(speed)
    fun setPlaybackPitch(pitch: Float) = audioPlayer.setPlaybackPitch(pitch)
    fun resetSpeedAndPitch() = audioPlayer.resetSpeedAndPitch()
    fun setCrossfadeSeconds(seconds: Int) = audioPlayer.setCrossfadeSeconds(seconds)
    fun setGaplessEnabled(enabled: Boolean) = audioPlayer.setGaplessEnabled(enabled)
    fun markABPointA(positionMs: Long = audioPlayer.currentPosition.value) = audioPlayer.markABPointA(positionMs)
    fun markABPointB(positionMs: Long = audioPlayer.currentPosition.value) = audioPlayer.markABPointB(positionMs)
    fun toggleABLoopEnabled(enabled: Boolean) = audioPlayer.toggleABLoopEnabled(enabled)
    fun adjustABPointA(deltaMs: Long) = audioPlayer.adjustABPointA(deltaMs)
    fun adjustABPointB(deltaMs: Long) = audioPlayer.adjustABPointB(deltaMs)
    fun clearABLoop() = audioPlayer.clearABLoop()

    fun applyPendingPackageUpdateAndRestart() = YtDlpAutoUpdater.applyPendingUpdateAndRestart(getApplication())

    // --- Auriculares ---
    fun updateHeadphoneConfig(newConfig: HeadphoneConfig) = headphoneCoordinator.updateHeadphoneConfig(newConfig)
    fun setCrossfeedEnabled(enabled: Boolean) = headphoneCoordinator.setCrossfeedEnabled(enabled)
    fun setCrossfeedStrength(strengthMode: Int) = headphoneCoordinator.setCrossfeedStrength(strengthMode)
    fun setBalanceControlEnabled(enabled: Boolean) = headphoneCoordinator.setBalanceControlEnabled(enabled)
    fun setStereoBalance(balance: Float) = headphoneCoordinator.setStereoBalance(balance)
    fun setBitPerfectEnabled(enabled: Boolean) = headphoneCoordinator.setBitPerfectEnabled(enabled)
    fun setLowLatencyAAudioEnabled(enabled: Boolean) = headphoneCoordinator.setLowLatencyAAudioEnabled(enabled)
    fun setUsbDacExclusiveEnabled(enabled: Boolean) = headphoneCoordinator.setUsbDacExclusiveEnabled(enabled)
    fun setHiResTargetMode(mode: Int) = headphoneCoordinator.setHiResTargetMode(mode)
    fun setBecomingNoisyGuardEnabled(enabled: Boolean) = headphoneCoordinator.setBecomingNoisyGuardEnabled(enabled)
    fun setFadeInOnResumeEnabled(enabled: Boolean) = headphoneCoordinator.setFadeInOnResumeEnabled(enabled)
    fun setDedicatedVolumeMemoryEnabled(enabled: Boolean) = headphoneCoordinator.setDedicatedVolumeMemoryEnabled(enabled)
    fun setHeadsetControlsEnabled(enabled: Boolean) = headphoneCoordinator.setHeadsetControlsEnabled(enabled)
    fun setHeadsetSingleClickAction(action: HeadsetButtonAction) = headphoneCoordinator.setHeadsetSingleClickAction(action)
    fun setHeadsetDoubleClickAction(action: HeadsetButtonAction) = headphoneCoordinator.setHeadsetDoubleClickAction(action)
    fun setHeadsetTripleClickAction(action: HeadsetButtonAction) = headphoneCoordinator.setHeadsetTripleClickAction(action)
    fun setHeadsetLongClickAction(action: HeadsetButtonAction) = headphoneCoordinator.setHeadsetLongClickAction(action)
    fun setHeadsetAction(type: Int, action: HeadsetButtonAction) = headphoneCoordinator.setHeadsetAction(type, action)

    // --- Letras Sincronizadas ---
    fun loadLyrics(track: Track?) = lyricsCoordinator.loadLyrics(track)
    fun importLyricsFromUri(uri: Uri) = lyricsCoordinator.importLyricsFromUri(uri)
    fun fetchOnlineLyrics(track: Track? = null) = lyricsCoordinator.fetchOnlineLyrics(track)
    fun saveCustomLyrics(content: String, track: Track? = null) = lyricsCoordinator.saveCustomLyrics(content, track)
    fun openSearchLyricsDialog() = lyricsCoordinator.openSearchLyricsDialog()
    fun closeSearchLyricsDialog() = lyricsCoordinator.closeSearchLyricsDialog()
    fun searchLyricsOptions(title: String, artist: String = "") = lyricsCoordinator.searchLyricsOptions(title, artist)
    fun selectLyricSearchResult(result: LyricSearchResult) = lyricsCoordinator.selectLyricSearchResult(result)

    // --- Medios Entrantes ---
    fun onIncomingIntent(intent: Intent?) = incomingMediaCoordinator.onIncomingIntent(intent)
    fun clearPendingIncomingAudio() = incomingMediaCoordinator.clearPendingIncomingAudio()
    fun confirmIncomingAudioImport(trimSilence: Boolean) = incomingMediaCoordinator.confirmIncomingAudioImport(trimSilence)
    fun clearPendingIncomingVideo() = incomingMediaCoordinator.clearPendingIncomingVideo()
    fun clearPendingIncomingWebLink() = incomingMediaCoordinator.clearPendingIncomingWebLink()
    fun handleIncomingAudioUri(uri: Uri, trimSilence: Boolean = false) = incomingMediaCoordinator.handleIncomingAudioUri(uri, trimSilence)
    fun handleIncomingMultipleAudioUris(uris: List<Uri>, trimSilence: Boolean = false) = incomingMediaCoordinator.handleIncomingMultipleAudioUris(uris, trimSilence)

    override fun onCleared() {
        super.onCleared()
        com.example.ui.theme.ArtworkColorExtractor.releaseRetriever()
        navigationCoordinator.release()
        effectsCoordinator.release()
        headphoneCoordinator.release()
        audioPlayer.release()
    }
}
