package com.example.viewmodel
import com.example.core.model.LyricsState
import com.example.core.model.LyricSearchResult
import com.example.core.model.VideoDisplayMode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.importer.YtDlpAutoUpdater
import com.example.core.data.local.AppDatabase
import com.example.core.data.repository.MusicRepository
import com.example.core.data.storage.AppStorageManager
import com.example.core.model.*
import com.example.core.playback.AudioEffectManager
import com.example.core.playback.AuraAudioPlayer
import com.example.core.playback.HeadphoneController
import com.example.ui.navigation.LibraryTab
import com.example.ui.navigation.NavScreen
import com.example.viewmodel.delegates.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel central de Aura Music.
 * Fachada modular MVVM (< 500 líneas): expone el estado público reactivo y
 * delega la lógica en coordinadores modulares (efectos, navegación, biblioteca,
 * mixtape, letras, medios entrantes, streaming, widgets) más el bucle de
 * espectro FFT ([VisualizerLoopController]) y el cableado reactivo
 * ([MusicViewModelWiring]). Las funciones de reenvío viven como métodos por
 * defecto en `MusicViewModelFacades.kt` con el 100% de la API pública.
 */
class MusicViewModel(application: Application) : AndroidViewModel(application),
    MusicViewModelNavigationFacade,
    MusicViewModelPlaybackFacade,
    MusicViewModelLibraryFacade,
    MusicViewModelMixtapeFacade,
    MusicViewModelEffectsFacade,
    MusicViewModelPackageUpdateFacade,
    MusicViewModelHeadphoneFacade,
    MusicViewModelLyricsFacade,
    MusicViewModelIncomingFacade,
    MusicViewModelStreamingFacade,
    MusicViewModelWidgetFacade {

    override val facadeScope: CoroutineScope get() = viewModelScope
    override val facadeApp: Application get() = getApplication()

    private val database = AppDatabase.getInstance(application)
    private val repository = MusicRepository(database)
    val effectManager = AudioEffectManager()
    override val audioPlayer = AuraAudioPlayer(application, effectManager)

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

    override val headphoneCoordinator = HeadphoneSettingsCoordinator(
        headphoneController = headphoneController,
        audioPlayer = audioPlayer
    )
    val headphoneConfig: StateFlow<HeadphoneConfig> = headphoneCoordinator.headphoneConfig

    // Coordinador modular de Efectos de Audio y Sleep Timer
    internal override val effectsCoordinator = AudioEffectsCoordinator(
        effectManager = effectManager,
        audioPlayer = audioPlayer,
        coroutineScope = viewModelScope
    )

    // Coordinador modular de Navegación y Preferencias de UI
    private val appPrefs = getApplication<Application>().getSharedPreferences("aura_music_ui_prefs", android.content.Context.MODE_PRIVATE)
    internal override val navigationCoordinator = NavigationCoordinator(
        appPrefs = appPrefs,
        repository = repository,
        coroutineScope = viewModelScope,
        getAllTracks = { allTracks.value }
    )

    // Coordinador modular de Biblioteca, Playlists e Importaciones
    override val trackLibraryCoordinator = TrackLibraryCoordinator(
        context = application,
        repository = repository,
        audioPlayer = audioPlayer,
        coroutineScope = viewModelScope,
        onUpdatePlaylistNav = { id, name, desc, art ->
            navigationCoordinator.updateSelectedPlaylistMetadata(id, name, desc, art)
        },
        onPlaylistDeleted = { id ->
            if (selectedPlaylist.value?.id == id) navigationCoordinator.handleBackPress()
        }
    )

    // Coordinador modular de Mixtape y Capítulos Continuos
    override val mixtapeCoordinator = MixtapeCoordinator(application, viewModelScope)
    val isCreatingMixtape: StateFlow<Boolean> = mixtapeCoordinator.isCreatingMixtape
    val mixtapeProgress: StateFlow<Float> = mixtapeCoordinator.mixtapeProgress
    val mixtapeStatusMessage: StateFlow<String?> = mixtapeCoordinator.mixtapeStatusMessage
    val activeMixtapeChapter = mixtapeCoordinator.activeMixtapeChapter
    val activeMixtapeMetadata = mixtapeCoordinator.activeMixtapeMetadata

    // Datos reactivos de Room
    override val allTracks: StateFlow<List<Track>> = repository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteTracks: StateFlow<List<Track>> = repository.favoriteTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyAddedTracks: StateFlow<List<Track>> = repository.recentlyAddedTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topPlayedTracks: StateFlow<List<Track>> = repository.topPlayedTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    override val playlists: StateFlow<List<Playlist>> = repository.playlists
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
    val isAudioBuffering = audioPlayer.isBuffering
    val isNetworkStalled = audioPlayer.isNetworkStalled

    // Estado de ecualizador y efectos
    val eqBands = effectsCoordinator.eqBands
    val bassBoostLevel = effectsCoordinator.bassBoostLevel
    val currentPreset = effectsCoordinator.currentPreset
    val isEqEnabled = effectsCoordinator.isEqEnabled
    val eqScopeMode = effectsCoordinator.eqScopeMode
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
    val hasVisitedSettings = navigationCoordinator.hasVisitedSettings
    val currentScreen = navigationCoordinator.currentScreen
    val selectedPlaylist = navigationCoordinator.selectedPlaylist
    val selectedPlaylistTracks = navigationCoordinator.selectedPlaylistTracks
    val isNowPlayingExpanded = navigationCoordinator.isNowPlayingExpanded
    val searchQuery = navigationCoordinator.searchQuery
    val selectedLibraryTab = navigationCoordinator.selectedLibraryTab
    val currentTheme = navigationCoordinator.currentTheme
    val isDynamicArtworkColorEnabled = navigationCoordinator.isDynamicArtworkColorEnabled
    override val isMiniPlayerVideoEnabled = navigationCoordinator.isMiniPlayerVideoEnabled
    val isBackgroundGameModeEnabled = navigationCoordinator.isBackgroundGameModeEnabled
    override val appWallpaperConfig = navigationCoordinator.appWallpaperConfig
    override val videoDisplayMode = navigationCoordinator.videoDisplayMode
    val isVideoCanvasActive: StateFlow<Boolean> = videoDisplayMode.map { it != VideoDisplayMode.OFF }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    override val nowPlayingDesignMode = navigationCoordinator.nowPlayingDesignMode

    // Estados de importación y progreso de descargas
    val isImporting = trackLibraryCoordinator.isImporting
    val importStatusMessage = trackLibraryCoordinator.importStatusMessage
    val downloadProgress = trackLibraryCoordinator.downloadProgress

    // Espectro de audio para visualizador C++20
    private val _visualizerBands = MutableStateFlow(FloatArray(28))
    val visualizerBands: StateFlow<FloatArray> = _visualizerBands.asStateFlow()

    private val _audioIntensity = MutableStateFlow(0f)
    val audioIntensity: StateFlow<Float> = _audioIntensity.asStateFlow()

    private val visualizerController = VisualizerLoopController(_visualizerBands, _audioIntensity)

    // Coordinador de Letras y Karaoke
    override val lyricsCoordinator = LyricsCoordinator(
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

    // Coordinador Universal de Importación de Playlists (Spotify y YouTube Music / YouTube)
    override val playlistImportCoordinator = com.example.viewmodel.delegates.PlaylistImportCoordinator(
        context = application,
        trackDao = database.trackDao(),
        playlistDao = database.playlistDao(),
        storageManager = AppStorageManager(application),
        scope = viewModelScope,
        onStatusMessage = { msg -> trackLibraryCoordinator.setStatusMessage(msg) },
        onOpenCreatedPlaylist = { createdPlaylistId ->
            viewModelScope.launch {
                val created = playlists.value.find { it.id == createdPlaylistId }
                if (created != null) {
                    navigationCoordinator.openPlaylist(created)
                } else {
                    navigationCoordinator.navigateTo(NavScreen.Library)
                    navigationCoordinator.setLibraryTab(LibraryTab.PLAYLISTS)
                }
            }
        }
    )

    // Coordinador de Medios Entrantes
    override val incomingMediaCoordinator = IncomingMediaCoordinator(
        context = application,
        repository = repository,
        scope = viewModelScope,
        onPlayTrack = { track, list -> playTrack(track, list) },
        onExpandNowPlaying = { navigationCoordinator.setNowPlayingExpanded(true) },
        onFetchOnlineLyrics = { lyricsCoordinator.fetchOnlineLyrics() },
        setImportingStatus = { isImp, msg ->
            trackLibraryCoordinator.setImporting(isImp, msg)
        },
        onOpenPlaylistImportUrl = { playlistUrl ->
            playlistImportCoordinator.openDialog(playlistUrl)
        }
    )
    val pendingIncomingAudioUris = incomingMediaCoordinator.pendingIncomingAudioUris
    val pendingIncomingVideoUri = incomingMediaCoordinator.pendingIncomingVideoUri
    val pendingIncomingWebLink = incomingMediaCoordinator.pendingIncomingWebLink

    // Coordinador del Modo Streaming Híbrido (Búsqueda API tri-plataforma, yt-dlp para YouTube/SoundCloud, Caché 30 min y Radio)
    override val streamingCoordinator = StreamingModeCoordinator(
        context = application,
        audioPlayer = audioPlayer,
        scope = viewModelScope,
        onLoadLyricsForTrack = { track -> lyricsCoordinator.loadLyrics(track) },
        onStatusMessage = { msg -> trackLibraryCoordinator.setStatusMessage(msg) }
    )
    override val streamingSearchQuery: StateFlow<String> = streamingCoordinator.searchQuery
    val streamingSelectedFilter: StateFlow<StreamingSearchFilter> = streamingCoordinator.selectedFilter
    val streamingSearchResults: StateFlow<List<StreamingSearchItem>> = streamingCoordinator.searchResults
    val isStreamingSearching: StateFlow<Boolean> = streamingCoordinator.isSearching
    val isResolvingStream: StateFlow<Boolean> = streamingCoordinator.isResolvingStream
    val resolvingVideoId: StateFlow<String?> = streamingCoordinator.resolvingVideoId
    val nextSimilarTrackPreview: StateFlow<StreamingSearchItem?> = streamingCoordinator.nextSimilarTrackPreview
    val streamingCacheConfig: StateFlow<StreamingCacheConfig> = streamingCoordinator.cacheConfig
    val downloadingStreamingIds: StateFlow<Set<String>> = streamingCoordinator.downloadingVideoIds
    val downloadedStreamingIds: StateFlow<Set<String>> = streamingCoordinator.downloadedVideoIds

    // Coordinador de Widgets de Escritorio (configuración, sincronización y callbacks)
    override val widgetSyncCoordinator = WidgetSyncCoordinator(
        appContext = application,
        scope = viewModelScope,
        topPlayedTracks = topPlayedTracks,
        allTracks = allTracks,
        playlists = playlists,
        favoriteTracks = favoriteTracks,
        onPlayTrack = { track, list -> playTrack(track, list) },
        getCurrentTrack = { audioPlayer.currentTrack.value },
        onToggleFavorite = { track -> toggleFavorite(track) }
    )
    val widgetConfig: StateFlow<WidgetConfig> = widgetSyncCoordinator.widgetConfig

    init {
        visualizerController.start(
            scope = viewModelScope,
            isPlaying = { audioPlayer.isPlaying.value },
            isExpanded = { isNowPlayingExpanded.value },
            getCurrentTrack = { audioPlayer.currentTrack.value },
            getPositionMs = { audioPlayer.currentPosition.value },
            getVideoDisplayMode = { videoDisplayMode.value },
            getDesignMode = { nowPlayingDesignMode.value }
        )

        MusicViewModelWiring.wireReactiveSyncs(
            scope = viewModelScope,
            audioPlayer = audioPlayer,
            repository = repository,
            lyricsCoordinator = lyricsCoordinator,
            mixtapeCoordinator = mixtapeCoordinator
        )

        widgetSyncCoordinator.registerWidgetCallbacks()
        widgetSyncCoordinator.startLibraryWidgetSync()
    }

    // --- Reproducción (núcleo conservado en la fachada) ---
    override fun playTrack(track: Track, fromList: List<Track>? = null) {
        viewModelScope.launch {
            repository.incrementPlayCount(track.id)
            val (list, index) = MusicViewModelWiring.resolvePlaybackList(track, fromList, allTracks.value)
            audioPlayer.playTrackList(list, index)
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            val newFavorite = !track.isFavorite
            repository.toggleFavorite(track.id, track.isFavorite)
            audioPlayer.updateTrackFavorite(track.id, newFavorite)
            widgetSyncCoordinator.syncFavoriteToggled(track.id, newFavorite)
        }
    }

    override fun onCleared() {
        super.onCleared()
        com.example.ui.theme.ArtworkColorExtractor.releaseRetriever()
        navigationCoordinator.release()
        effectsCoordinator.release()
        headphoneCoordinator.release()
        audioPlayer.release()
    }
}
