package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.importer.IncomingMedia
import com.example.data.importer.IncomingMediaHandler
import com.example.data.local.AppDatabase
import com.example.data.repository.MusicRepository
import com.example.model.*
import com.example.playback.AudioEffectManager
import com.example.playback.AuraAudioPlayer
import com.example.ui.navigation.LibraryTab
import com.example.ui.navigation.NavScreen
import com.example.viewmodel.delegates.HeadphoneSettingsCoordinator
import com.example.viewmodel.delegates.IncomingMediaCoordinator
import com.example.viewmodel.delegates.LyricsCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel central de la aplicación Aura Music.
 * Conecta de forma reactiva la base de datos Room, el motor Media3 ExoPlayer,
 * los efectos de audio de hardware y los flujos de navegación de la interfaz de usuario.
 */
class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = MusicRepository(database)
    val effectManager = AudioEffectManager()
    val audioPlayer = AuraAudioPlayer(application, effectManager)

    // Controlador y gestor de hardware para Auriculares / Audífonos
    val headphoneController = com.example.playback.HeadphoneController(
        context = application,
        onPauseRequested = { audioPlayer.pause() },
        onPlayRequested = { audioPlayer.play() },
        onTogglePlayPauseRequested = { audioPlayer.togglePlayPause() },
        onNextRequested = { audioPlayer.playNext() },
        onPrevRequested = { audioPlayer.playPrevious() },
        onToggleFavoriteRequested = {
            audioPlayer.currentTrack.value?.let { track ->
                toggleFavorite(track)
            }
        },
        onSeekByOffset = { offsetMs ->
            val cur = audioPlayer.currentPosition.value
            val target = (cur + offsetMs).coerceAtLeast(0L)
            audioPlayer.seekTo(target)
        },
        onVolumeChangeRequested = { vol ->
            audioPlayer.setVolume(vol)
        },
        getCurrentVolume = { audioPlayer.getVolume() }
    )

    val headphoneCoordinator = HeadphoneSettingsCoordinator(
        headphoneController = headphoneController,
        audioPlayer = audioPlayer
    )

    val headphoneConfig: StateFlow<HeadphoneConfig> = headphoneCoordinator.headphoneConfig

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
    val eqBands = effectManager.bands
    val bassBoostLevel = effectManager.bassBoostLevel
    val currentPreset = effectManager.currentPreset
    val isEqEnabled = effectManager.isEnabled
    val spatial8DConfig = effectManager.spatial8DConfig
    val reverbConfig = effectManager.reverbConfig

    // Estados de velocidad, tono, crossfade, gapless y bucle A-B
    val playbackSpeed = audioPlayer.playbackSpeed
    val playbackPitch = audioPlayer.playbackPitch
    val crossfadeSeconds = audioPlayer.crossfadeSeconds
    val isGaplessEnabled = audioPlayer.isGaplessEnabled
    val abLoopState = audioPlayer.abLoopState

    // Estado del Temporizador de Apagado (Sleep Timer)
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private val _sleepTimerState = MutableStateFlow(com.example.model.SleepTimerState())
    val sleepTimerState: StateFlow<com.example.model.SleepTimerState> = _sleepTimerState.asStateFlow()

    // Estados de navegación y UI
    private val _currentScreen = MutableStateFlow<NavScreen>(NavScreen.Home)
    val currentScreen: StateFlow<NavScreen> = _currentScreen.asStateFlow()

    private val screenBackStack = mutableListOf<NavScreen>(NavScreen.Home)

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _selectedPlaylistTracks = MutableStateFlow<List<Track>>(emptyList())
    val selectedPlaylistTracks: StateFlow<List<Track>> = _selectedPlaylistTracks.asStateFlow()

    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded: StateFlow<Boolean> = _isNowPlayingExpanded.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedLibraryTab = MutableStateFlow(LibraryTab.SONGS)
    val selectedLibraryTab: StateFlow<LibraryTab> = _selectedLibraryTab.asStateFlow()

    private val appPrefs = getApplication<Application>().getSharedPreferences("aura_music_ui_prefs", android.content.Context.MODE_PRIVATE)

    private val _currentTheme = MutableStateFlow(
        run {
            val savedThemeName = appPrefs.getString("pref_aura_theme", AuraTheme.NEBULA_GLOW.name)
            try {
                AuraTheme.valueOf(savedThemeName ?: AuraTheme.NEBULA_GLOW.name)
            } catch (_: Exception) {
                AuraTheme.NEBULA_GLOW
            }
        }
    )
    val currentTheme: StateFlow<AuraTheme> = _currentTheme.asStateFlow()

    private var selectedCollectionJob: kotlinx.coroutines.Job? = null

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importStatusMessage = MutableStateFlow<String?>(null)
    val importStatusMessage: StateFlow<String?> = _importStatusMessage.asStateFlow()

    val storageManager = com.example.data.storage.AppStorageManager(application)

    // Bandas espectrales en tiempo real calculadas en C++20 (28 bandas)
    private val _visualizerBands = MutableStateFlow(FloatArray(28) { 0.15f })
    val visualizerBands: StateFlow<FloatArray> = _visualizerBands.asStateFlow()

    // Intensidad acústica RMS en tiempo real calculada directamente en el motor nativo C++20
    private val _audioIntensity = MutableStateFlow(0.15f)
    val audioIntensity: StateFlow<Float> = _audioIntensity.asStateFlow()

    // Coordinador Modular de Letras Sincronizadas (.LRC y Karaoke)
    val lyricsCoordinator = LyricsCoordinator(
        context = application,
        storageManager = storageManager,
        scope = viewModelScope,
        getCurrentTrack = { audioPlayer.currentTrack.value }
    )
    val lyricsState: StateFlow<LyricsState> = lyricsCoordinator.lyricsState
    val isSearchLyricsDialogOpen: StateFlow<Boolean> = lyricsCoordinator.isSearchLyricsDialogOpen
    val isSearchingLyrics: StateFlow<Boolean> = lyricsCoordinator.isSearchingLyrics
    val lyricsSearchResults: StateFlow<List<com.example.model.LyricSearchResult>> = lyricsCoordinator.lyricsSearchResults
    val searchLyricsError: StateFlow<String?> = lyricsCoordinator.searchLyricsError

    // Coordinador Modular de Medios Externos Entrantes ("Abrir con..." y "Compartir con...")
    val incomingMediaCoordinator = IncomingMediaCoordinator(
        context = application,
        repository = repository,
        scope = viewModelScope,
        onPlayTrack = { track, list -> playTrack(track, list) },
        onExpandNowPlaying = { _isNowPlayingExpanded.value = true },
        onFetchOnlineLyrics = { lyricsCoordinator.fetchOnlineLyrics() },
        setImportingStatus = { isImporting, message ->
            _isImporting.value = isImporting
            _importStatusMessage.value = message
        }
    )
    val pendingIncomingAudioUris: StateFlow<List<Uri>> = incomingMediaCoordinator.pendingIncomingAudioUris
    val pendingIncomingVideoUri: StateFlow<Uri?> = incomingMediaCoordinator.pendingIncomingVideoUri
    val pendingIncomingWebLink: StateFlow<String?> = incomingMediaCoordinator.pendingIncomingWebLink

    // Estado en tiempo real del progreso de descarga de video/audio web (bytes, total, velocidad)
    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    init {
        // Al iniciar por primera vez, si la biblioteca está vacía, no forzamos escaneo global,
        // pero sugerimos al usuario en la vista de importación o le permitimos generar demos

        // Ciclo de alta frecuencia en hilo secundario para alimentar el visualizador C++20 y la intensidad acústica
        viewModelScope.launch(Dispatchers.Default) {
            val buffer = FloatArray(28)
            while (true) {
                if (audioPlayer.isPlaying.value) {
                    com.example.playback.NativeAudioEngine.getVisualizerBands(buffer)
                    _visualizerBands.value = buffer.copyOf()
                    val intensity = com.example.playback.NativeAudioEngine.getAudioIntensity()
                    _audioIntensity.value = intensity
                    kotlinx.coroutines.delay(25L)
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
                        kotlinx.coroutines.delay(35L)
                    } else {
                        kotlinx.coroutines.delay(120L)
                    }
                }
            }
        }

        // Carga automática de letras sincronizadas al cambiar de canción
        viewModelScope.launch {
            audioPlayer.currentTrack.collect { track ->
                loadLyrics(track)
            }
        }

        // Sincronización reactiva del reproductor con la base de datos Room (favoritos, metadatos, video)
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

    // Acciones de Navegación
    fun navigateTo(screen: NavScreen) {
        if (_currentScreen.value != screen) {
            screenBackStack.add(screen)
            _currentScreen.value = screen
        }
    }

    fun handleBackPress(): Boolean {
        if (_isNowPlayingExpanded.value) {
            _isNowPlayingExpanded.value = false
            return true
        }
        if (screenBackStack.size > 1) {
            screenBackStack.removeAt(screenBackStack.size - 1)
            _currentScreen.value = screenBackStack.last()
            return true
        }
        return false
    }

    fun openPlaylist(playlist: Playlist) {
        _selectedPlaylist.value = playlist
        selectedCollectionJob?.cancel()
        selectedCollectionJob = viewModelScope.launch {
            when (playlist.id) {
                -1L -> {
                    repository.favoriteTracks.collect { tracks ->
                        _selectedPlaylistTracks.value = tracks
                    }
                }
                -2L -> {
                    repository.allTracks.collect { tracks ->
                        _selectedPlaylistTracks.value = tracks.filter { it.album == playlist.name }
                    }
                }
                -3L -> {
                    repository.allTracks.collect { tracks ->
                        _selectedPlaylistTracks.value = tracks.filter { it.artist == playlist.name }
                    }
                }
                else -> {
                    repository.getTracksForPlaylist(playlist.id).collect { tracks ->
                        _selectedPlaylistTracks.value = tracks
                        val cur = _selectedPlaylist.value
                        if (cur != null && cur.id == playlist.id) {
                            _selectedPlaylist.value = cur.copy(
                                trackCount = tracks.size,
                                previewTracks = tracks.take(4)
                            )
                        }
                    }
                }
            }
        }
        navigateTo(NavScreen.PlaylistDetail)
    }

    fun openAlbum(albumName: String) {
        val albumTracks = allTracks.value.filter { it.album == albumName }
        val artistSubtitle = albumTracks.map { it.artist }.distinct().let { artists ->
            if (artists.size == 1) "Álbum de ${artists.first()}"
            else if (artists.isNotEmpty()) "Álbum • ${artists.size} artistas"
            else "Álbum musical"
        }
        openPlaylist(
            Playlist(
                id = -2L,
                name = albumName,
                description = artistSubtitle,
                trackCount = albumTracks.size,
                previewTracks = albumTracks.take(4)
            )
        )
    }

    fun openArtist(artistName: String) {
        val artistTracks = allTracks.value.filter { it.artist == artistName }
        val albumCount = artistTracks.map { it.album }.distinct().size
        openPlaylist(
            Playlist(
                id = -3L,
                name = artistName,
                description = "Artista • $albumCount álbum(es) en tu biblioteca",
                trackCount = artistTracks.size,
                previewTracks = artistTracks.take(4)
            )
        )
    }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _isNowPlayingExpanded.value = expanded
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setLibraryTab(tab: LibraryTab) {
        _selectedLibraryTab.value = tab
    }

    fun setTheme(theme: AuraTheme) {
        _currentTheme.value = theme
        appPrefs.edit().putString("pref_aura_theme", theme.name).apply()
    }

    // Acciones de Reproducción con Cola Contextual Fiel
    fun playTrack(track: Track, fromList: List<Track>? = null) {
        viewModelScope.launch {
            repository.incrementPlayCount(track.id)
            val fullList = allTracks.value
            val list = if (!fromList.isNullOrEmpty()) {
                // Respeta estrictamente la lista contextual seleccionada (playlist, álbum, artista o búsqueda)
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

    fun seekTo(positionMs: Long) = audioPlayer.seekTo(positionMs)

    fun playNext() = audioPlayer.playNext()

    fun playPrevious() = audioPlayer.playPrevious()

    fun toggleShuffle() = audioPlayer.toggleShuffle()

    fun cycleRepeatMode() = audioPlayer.cycleRepeatMode()

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            val newFav = !track.isFavorite
            repository.toggleFavorite(track.id, track.isFavorite)
            audioPlayer.updateTrackFavorite(track.id, newFav)
        }
    }

    fun deleteTrack(trackId: Long) {
        viewModelScope.launch {
            repository.deleteTrack(getApplication(), trackId)
            if (currentTrack.value?.id == trackId) {
                audioPlayer.playNext()
            }
        }
    }

    // Modo de visualización de video de fondo:
    // FULLSCREEN_BACKGROUND: video a pantalla completa con carátula flotando al frente
    // CARD_CANVAS: video dentro del recuadro de la carátula
    // OFF: solo carátula estática
    private val _videoDisplayMode = MutableStateFlow(
        run {
            val savedMode = appPrefs.getString("pref_video_display_mode", VideoDisplayMode.FULLSCREEN_BACKGROUND.name)
            try {
                VideoDisplayMode.valueOf(savedMode ?: VideoDisplayMode.FULLSCREEN_BACKGROUND.name)
            } catch (_: Exception) {
                VideoDisplayMode.FULLSCREEN_BACKGROUND
            }
        }
    )
    val videoDisplayMode: StateFlow<VideoDisplayMode> = _videoDisplayMode.asStateFlow()

    private val _isVideoCanvasActive = MutableStateFlow(_videoDisplayMode.value != VideoDisplayMode.OFF)
    val isVideoCanvasActive: StateFlow<Boolean> = _isVideoCanvasActive.asStateFlow()

    private val _isDynamicArtworkColorEnabled = MutableStateFlow(
        appPrefs.getBoolean("pref_dynamic_artwork_color_enabled", true)
    )
    val isDynamicArtworkColorEnabled: StateFlow<Boolean> = _isDynamicArtworkColorEnabled.asStateFlow()

    private val _isMiniPlayerVideoEnabled = MutableStateFlow(
        appPrefs.getBoolean("pref_mini_player_video_enabled", true)
    )
    val isMiniPlayerVideoEnabled: StateFlow<Boolean> = _isMiniPlayerVideoEnabled.asStateFlow()

    fun setMiniPlayerVideoEnabled(enabled: Boolean) {
        _isMiniPlayerVideoEnabled.value = enabled
        appPrefs.edit().putBoolean("pref_mini_player_video_enabled", enabled).apply()
    }

    fun toggleMiniPlayerVideoEnabled() {
        setMiniPlayerVideoEnabled(!_isMiniPlayerVideoEnabled.value)
    }

    fun setVideoDisplayMode(mode: VideoDisplayMode) {
        _videoDisplayMode.value = mode
        _isVideoCanvasActive.value = (mode != VideoDisplayMode.OFF)
        appPrefs.edit().putString("pref_video_display_mode", mode.name).apply()
    }

    fun cycleVideoDisplayMode() {
        val nextMode = _videoDisplayMode.value.next()
        setVideoDisplayMode(nextMode)
    }

    fun toggleVideoCanvas() {
        cycleVideoDisplayMode()
    }

    fun setVideoCanvasActive(active: Boolean) {
        if (!active) {
            setVideoDisplayMode(VideoDisplayMode.OFF)
        } else if (_videoDisplayMode.value == VideoDisplayMode.OFF) {
            setVideoDisplayMode(VideoDisplayMode.FULLSCREEN_BACKGROUND)
        }
    }

    fun toggleDynamicArtworkColor(enabled: Boolean) {
        _isDynamicArtworkColorEnabled.value = enabled
        appPrefs.edit().putBoolean("pref_dynamic_artwork_color_enabled", enabled).apply()
    }

    fun updateTrackInfo(trackId: Long, newTitle: String, newArtist: String, newAlbum: String) {
        updateTrackDetails(trackId, newTitle, newArtist, newAlbum, null, false, null, false)
    }

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
        loopStyle: com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle = com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE
    ) {
        viewModelScope.launch {
            val updated = repository.updateTrackDetails(
                getApplication(),
                trackId,
                newTitle,
                newArtist,
                newAlbum,
                customArtUri,
                removeArtwork,
                customVideoUri,
                removeVideo,
                forceVideoLoop,
                loopStyle
            )
            audioPlayer.updateTrackMetadata(
                trackId,
                newTitle,
                newArtist,
                newAlbum,
                updated?.albumArtPath,
                updateArt = (customArtUri != null || removeArtwork),
                videoUri = updated?.videoUri,
                isVideoLoop = updated?.isVideoLoop ?: false,
                updateVideo = (customVideoUri != null || removeVideo || loopStyle == com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle.BOOMERANG)
            )
        }
    }

    /**
     * Elimina físicamente la carátula guardada de una canción del almacenamiento y actualiza Room.
     */
    fun deleteTrackArtwork(track: Track) {
        updateTrackDetails(
            trackId = track.id,
            newTitle = track.title,
            newArtist = track.artist,
            newAlbum = track.album,
            removeArtwork = true
        )
    }

    /**
     * Elimina físicamente el Video Canvas guardado de una canción del almacenamiento y actualiza Room.
     */
    fun deleteTrackVideo(track: Track) {
        updateTrackDetails(
            trackId = track.id,
            newTitle = track.title,
            newArtist = track.artist,
            newAlbum = track.album,
            removeVideo = true
        )
    }

    fun updatePlaylist(
        playlistId: Long,
        newName: String,
        newDescription: String = "",
        customArtUri: Uri? = null,
        removeArtwork: Boolean = false
    ) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val savedArtPath = repository.updatePlaylist(
                context = getApplication(),
                playlistId = playlistId,
                name = newName,
                description = newDescription,
                customArtUri = customArtUri,
                removeArtwork = removeArtwork
            )
            val current = _selectedPlaylist.value
            if (current != null && current.id == playlistId) {
                _selectedPlaylist.value = current.copy(
                    name = newName,
                    description = newDescription,
                    customArtPath = savedArtPath
                )
            }
        }
    }

    fun clearAllTracks() {
        viewModelScope.launch {
            repository.clearAllTracks()
            _importStatusMessage.value = "Biblioteca de música reiniciada."
        }
    }

    // Acciones de Importación
    fun importUris(uris: List<Uri>, trimSilence: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            _importStatusMessage.value = if (trimSilence) {
                "Importando y eliminando silencios al inicio y final..."
            } else {
                "Importando canciones seleccionadas..."
            }
            val count = repository.importUris(getApplication(), uris, trimSilence = trimSilence)
            _isImporting.value = false
            _importStatusMessage.value = if (count > 0) {
                if (trimSilence) {
                    "¡Éxito! Se importaron $count canción(es) sin silencios en los extremos."
                } else {
                    "¡Éxito! Se importaron $count canción(es) a tu biblioteca."
                }
            } else {
                "Las canciones seleccionadas ya estaban en tu biblioteca."
            }
        }
    }

    fun importFolder(treeUri: Uri, trimSilence: Boolean = false) {
        viewModelScope.launch {
            _isImporting.value = true
            _importStatusMessage.value = if (trimSilence) {
                "Analizando carpeta y eliminando silencios al inicio y final..."
            } else {
                "Analizando carpeta seleccionada..."
            }
            val count = repository.importTreeUri(getApplication(), treeUri, trimSilence = trimSilence)
            _isImporting.value = false
            _importStatusMessage.value = if (count > 0) {
                if (trimSilence) {
                    "¡Éxito! Se importaron $count canción(es) de la carpeta sin silencios."
                } else {
                    "¡Éxito! Se importaron $count canción(es) desde la carpeta."
                }
            } else {
                "No se encontraron canciones nuevas en la carpeta seleccionada."
            }
        }
    }

    fun seedDemoTracks() {
        viewModelScope.launch {
            _isImporting.value = true
            _importStatusMessage.value = "Generando pistas de audio demostrativas..."
            val count = repository.seedDemoTracks(getApplication())
            _isImporting.value = false
            _importStatusMessage.value = "Se crearon $count pistas de prueba Synthwave con audio real."
        }
    }

    /**
     * Importa y convierte un video de la galería en canción musical con carátula WebP
     * y Video Canvas sincronizado en Now Playing.
     */
    fun importVideoAsTrack(
        videoUri: Uri,
        title: String,
        artist: String,
        album: String,
        attachAsCanvas: Boolean,
        forceLoop: Boolean?,
        trimSilence: Boolean = false,
        loopStyle: com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle = com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE,
        onTrackCreated: ((Track) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isImporting.value = true
            _importStatusMessage.value = if (trimSilence) {
                "Convirtiendo video a música, recortando silencios y generando carátula..."
            } else {
                "Convirtiendo video a música, extrayendo carátula y Video Canvas..."
            }
            val track = repository.importVideoAsTrack(
                context = getApplication(),
                videoUri = videoUri,
                title = title,
                artist = artist,
                album = album,
                attachAsCanvas = attachAsCanvas,
                forceLoop = forceLoop,
                trimSilence = trimSilence,
                loopStyle = loopStyle
            )
            _isImporting.value = false
            if (track != null) {
                _importStatusMessage.value = "¡Éxito! Se añadió \"${track.title}\" con carátula y Video Canvas."
                onTrackCreated?.invoke(track)
            } else {
                _importStatusMessage.value = "No se pudo procesar el video seleccionado."
            }
        }
    }

    /**
     * Descarga e importa una canción desde un enlace web (TikTok u online),
     * extrayendo el audio, carátula en WebP y vinculando el Video Canvas.
     */
    fun importFromWebVideoLink(
        resolvedInfo: com.example.data.importer.OnlineVideoAudioImporter.ResolvedMediaInfo,
        customTitle: String,
        customArtist: String,
        attachAsCanvas: Boolean,
        trimSilence: Boolean = false,
        loopStyle: com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle = com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE,
        onSuccess: (Track) -> Unit
    ) {
        viewModelScope.launch {
            _isImporting.value = true
            _downloadProgress.value = DownloadProgress(
                isDownloading = true,
                phase = "Iniciando descarga...",
                bytesDownloaded = 0L,
                totalBytes = -1L,
                bytesPerSecond = 0L,
                progressFraction = 0f
            )
            _importStatusMessage.value = "Iniciando descarga de video y audio..."
            val storageManager = com.example.data.storage.AppStorageManager(getApplication())
            val result = com.example.data.importer.OnlineVideoAudioImporter.downloadAndImport(
                context = getApplication(),
                storageManager = storageManager,
                resolvedInfo = resolvedInfo,
                customTitle = customTitle,
                customArtist = customArtist,
                attachAsCanvas = attachAsCanvas,
                trimSilence = trimSilence,
                loopStyle = loopStyle,
                onProgressUpdate = { progress ->
                    _downloadProgress.value = progress
                    _importStatusMessage.value = "${progress.phase} • ${progress.formattedProgress} • ${progress.formattedSpeed}"
                }
            )

            _isImporting.value = false
            _downloadProgress.value = DownloadProgress(isDownloading = false)
            result.onSuccess { track ->
                val saved = repository.insertCustomTrack(getApplication(), track)
                _importStatusMessage.value = "¡Éxito! Se descargó \"${saved.title}\" con carátula y Video Canvas."
                onSuccess(saved)
            }.onFailure { error ->
                _importStatusMessage.value = "Error al descargar: ${error.message ?: "Verifica tu conexión y el enlace"}"
            }
        }
    }

    fun dismissImportStatus() {
        _importStatusMessage.value = null
    }

    // Acciones de Listas de Reproducción
    fun createPlaylist(name: String, description: String = "", customArtUri: Uri? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(getApplication(), name, description, customArtUri)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(getApplication(), playlistId)
            if (_selectedPlaylist.value?.id == playlistId) {
                handleBackPress()
            }
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, trackId)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    // Acciones de Ecualizador
    fun setEqEnabled(enabled: Boolean) = effectManager.setEnabled(enabled)

    fun setBandLevel(bandIndex: Int, levelMb: Int) = effectManager.setBandLevel(bandIndex, levelMb)

    fun setBassBoost(level: Int) = effectManager.setBassBoost(level)

    fun applyPreset(preset: EqualizerPreset) = effectManager.applyPreset(preset)

    // Acciones de Audio Espacial 8D C++20
    fun set8DEnabled(enabled: Boolean) = effectManager.set8DEnabled(enabled)

    fun set8DOrbitSpeed(speedSeconds: Float) = effectManager.set8DOrbitSpeed(speedSeconds)

    fun set8DSpatialIntensity(intensity: Float) = effectManager.set8DSpatialIntensity(intensity)

    fun set8DRoomDepth(depth: Float) = effectManager.set8DRoomDepth(depth)

    // Acciones de Suite Reverb Acústica (Presets + Personalización Libre)
    fun setReverbEnabled(enabled: Boolean) = effectManager.setReverbEnabled(enabled)
    fun setReverbPreset(preset: com.example.model.ReverbPreset) = effectManager.setReverbPreset(preset)
    fun setReverbCustomParameters(roomSize: Float, decayMs: Int, levelDb: Float) =
        effectManager.setReverbCustomParameters(roomSize, decayMs, levelDb)

    // Acciones de Velocidad y Tono (Playback Parameters)
    fun setPlaybackSpeed(speed: Float) = audioPlayer.setPlaybackSpeed(speed)

    fun setPlaybackPitch(pitch: Float) = audioPlayer.setPlaybackPitch(pitch)

    fun resetSpeedAndPitch() = audioPlayer.resetSpeedAndPitch()

    // Acciones de Transición de Pistas (Crossfade, Gapless y Repetidor A-B)
    fun setCrossfadeSeconds(seconds: Int) = audioPlayer.setCrossfadeSeconds(seconds)

    fun setGaplessEnabled(enabled: Boolean) = audioPlayer.setGaplessEnabled(enabled)

    fun markABPointA(positionMs: Long = audioPlayer.currentPosition.value) = audioPlayer.markABPointA(positionMs)

    fun markABPointB(positionMs: Long = audioPlayer.currentPosition.value) = audioPlayer.markABPointB(positionMs)

    fun toggleABLoopEnabled(enabled: Boolean) = audioPlayer.toggleABLoopEnabled(enabled)

    fun adjustABPointA(deltaMs: Long) = audioPlayer.adjustABPointA(deltaMs)

    fun adjustABPointB(deltaMs: Long) = audioPlayer.adjustABPointB(deltaMs)

    fun clearABLoop() = audioPlayer.clearABLoop()

    // Acciones del Temporizador de Apagado (Sleep Timer con Fade-Out de 10s)
    fun startSleepTimer(minutes: Int) {
        if (minutes <= 0) return
        sleepTimerJob?.cancel()
        val totalSec = minutes * 60
        _sleepTimerState.value = com.example.model.SleepTimerState(
            isActive = true,
            totalSeconds = totalSec,
            remainingSeconds = totalSec,
            isFadingOut = false
        )
        audioPlayer.setVolume(1.0f)

        sleepTimerJob = viewModelScope.launch(Dispatchers.Main) {
            var currentRemaining = totalSec
            while (currentRemaining > 0) {
                kotlinx.coroutines.delay(1000)
                currentRemaining--
                val isFading = currentRemaining in 1..10
                _sleepTimerState.value = _sleepTimerState.value.copy(
                    remainingSeconds = currentRemaining,
                    isFadingOut = isFading
                )

                if (isFading) {
                    val fadeFactor = (currentRemaining / 10.0f).coerceIn(0.0f, 1.0f)
                    audioPlayer.setVolume(fadeFactor)
                }
            }

            // Al cumplirse el tiempo, pausar reproducción y restaurar volumen para futuras reproducciones
            if (audioPlayer.isPlaying.value) {
                audioPlayer.togglePlayPause()
            }
            audioPlayer.setVolume(1.0f)
            _sleepTimerState.value = com.example.model.SleepTimerState()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        audioPlayer.setVolume(1.0f)
        _sleepTimerState.value = com.example.model.SleepTimerState()
    }

    fun addSleepTimerMinutes(extraMinutes: Int = 5) {
        val current = _sleepTimerState.value
        if (!current.isActive) {
            startSleepTimer(extraMinutes)
            return
        }
        val addedSec = extraMinutes * 60
        val newRemaining = current.remainingSeconds + addedSec
        val newTotal = current.totalSeconds + addedSec
        _sleepTimerState.value = current.copy(
            totalSeconds = newTotal,
            remainingSeconds = newRemaining,
            isFadingOut = false
        )
        audioPlayer.setVolume(1.0f)
    }

    // --- Métodos de Control para Auriculares / Audífonos (Delegados en HeadphoneSettingsCoordinator) ---

    fun updateHeadphoneConfig(newConfig: HeadphoneConfig) = headphoneCoordinator.updateHeadphoneConfig(newConfig)
    fun setCrossfeedEnabled(enabled: Boolean) = headphoneCoordinator.setCrossfeedEnabled(enabled)
    fun setCrossfeedStrength(strengthMode: Int) = headphoneCoordinator.setCrossfeedStrength(strengthMode)
    fun setBalanceControlEnabled(enabled: Boolean) = headphoneCoordinator.setBalanceControlEnabled(enabled)
    fun setStereoBalance(balance: Float) = headphoneCoordinator.setStereoBalance(balance)
    fun setBecomingNoisyGuardEnabled(enabled: Boolean) = headphoneCoordinator.setBecomingNoisyGuardEnabled(enabled)
    fun setFadeInOnResumeEnabled(enabled: Boolean) = headphoneCoordinator.setFadeInOnResumeEnabled(enabled)
    fun setDedicatedVolumeMemoryEnabled(enabled: Boolean) = headphoneCoordinator.setDedicatedVolumeMemoryEnabled(enabled)
    fun setHeadsetControlsEnabled(enabled: Boolean) = headphoneCoordinator.setHeadsetControlsEnabled(enabled)
    fun setHeadsetSingleClickAction(action: HeadsetButtonAction) = headphoneCoordinator.setHeadsetSingleClickAction(action)
    fun setHeadsetDoubleClickAction(action: HeadsetButtonAction) = headphoneCoordinator.setHeadsetDoubleClickAction(action)
    fun setHeadsetTripleClickAction(action: HeadsetButtonAction) = headphoneCoordinator.setHeadsetTripleClickAction(action)
    fun setHeadsetLongClickAction(action: HeadsetButtonAction) = headphoneCoordinator.setHeadsetLongClickAction(action)
    fun setHeadsetAction(type: Int, action: HeadsetButtonAction) = headphoneCoordinator.setHeadsetAction(type, action)

    // --- Métodos de Gestión de Letras Sincronizadas (Delegados en LyricsCoordinator) ---

    fun loadLyrics(track: Track?) = lyricsCoordinator.loadLyrics(track)
    fun importLyricsFromUri(uri: Uri) = lyricsCoordinator.importLyricsFromUri(uri)
    fun fetchOnlineLyrics(track: Track? = null) = lyricsCoordinator.fetchOnlineLyrics(track)
    fun saveCustomLyrics(content: String, track: Track? = null) = lyricsCoordinator.saveCustomLyrics(content, track)
    fun openSearchLyricsDialog() = lyricsCoordinator.openSearchLyricsDialog()
    fun closeSearchLyricsDialog() = lyricsCoordinator.closeSearchLyricsDialog()
    fun searchLyricsOptions(title: String, artist: String = "") = lyricsCoordinator.searchLyricsOptions(title, artist)
    fun selectLyricSearchResult(result: com.example.model.LyricSearchResult) = lyricsCoordinator.selectLyricSearchResult(result)

    // --- Métodos de Medios Externos Entrantes (Delegados en IncomingMediaCoordinator) ---

    fun onIncomingIntent(intent: Intent?) = incomingMediaCoordinator.onIncomingIntent(intent)
    fun clearPendingIncomingAudio() = incomingMediaCoordinator.clearPendingIncomingAudio()
    fun confirmIncomingAudioImport(trimSilence: Boolean) = incomingMediaCoordinator.confirmIncomingAudioImport(trimSilence)
    fun clearPendingIncomingVideo() = incomingMediaCoordinator.clearPendingIncomingVideo()
    fun clearPendingIncomingWebLink() = incomingMediaCoordinator.clearPendingIncomingWebLink()
    fun handleIncomingAudioUri(uri: Uri, trimSilence: Boolean = false) = incomingMediaCoordinator.handleIncomingAudioUri(uri, trimSilence)
    fun handleIncomingMultipleAudioUris(uris: List<Uri>, trimSilence: Boolean = false) = incomingMediaCoordinator.handleIncomingMultipleAudioUris(uris, trimSilence)

    override fun onCleared() {
        super.onCleared()
        cancelSleepTimer()
        headphoneCoordinator.release()
        audioPlayer.release()
    }
}
