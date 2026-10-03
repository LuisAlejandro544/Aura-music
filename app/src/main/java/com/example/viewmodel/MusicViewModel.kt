package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.repository.MusicRepository
import com.example.model.*
import com.example.playback.AudioEffectManager
import com.example.playback.AuraAudioPlayer
import com.example.ui.navigation.LibraryTab
import com.example.ui.navigation.NavScreen
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

    // Estados de velocidad, tono, crossfade y gapless
    val playbackSpeed = audioPlayer.playbackSpeed
    val playbackPitch = audioPlayer.playbackPitch
    val crossfadeSeconds = audioPlayer.crossfadeSeconds
    val isGaplessEnabled = audioPlayer.isGaplessEnabled

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

    private val _currentTheme = MutableStateFlow(AuraTheme.NEBULA_GLOW)
    val currentTheme: StateFlow<AuraTheme> = _currentTheme.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importStatusMessage = MutableStateFlow<String?>(null)
    val importStatusMessage: StateFlow<String?> = _importStatusMessage.asStateFlow()

    init {
        // Al iniciar por primera vez, si la biblioteca está vacía, no forzamos escaneo global,
        // pero sugerimos al usuario en la vista de importación o le permitimos generar demos

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
        viewModelScope.launch {
            if (playlist.id == -1L) {
                repository.favoriteTracks.collect { tracks ->
                    _selectedPlaylistTracks.value = tracks
                }
            } else {
                repository.getTracksForPlaylist(playlist.id).collect { tracks ->
                    _selectedPlaylistTracks.value = tracks
                }
            }
        }
        navigateTo(NavScreen.PlaylistDetail)
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
    }

    // Acciones de Reproducción
    fun playTrack(track: Track, fromList: List<Track>? = null) {
        viewModelScope.launch {
            repository.incrementPlayCount(track.id)
            val list = fromList ?: allTracks.value
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
    private val _videoDisplayMode = MutableStateFlow(VideoDisplayMode.FULLSCREEN_BACKGROUND)
    val videoDisplayMode: StateFlow<VideoDisplayMode> = _videoDisplayMode.asStateFlow()

    private val _isVideoCanvasActive = MutableStateFlow(true)
    val isVideoCanvasActive: StateFlow<Boolean> = _isVideoCanvasActive.asStateFlow()

    private val _isDynamicArtworkColorEnabled = MutableStateFlow(true)
    val isDynamicArtworkColorEnabled: StateFlow<Boolean> = _isDynamicArtworkColorEnabled.asStateFlow()

    fun setVideoDisplayMode(mode: VideoDisplayMode) {
        _videoDisplayMode.value = mode
        _isVideoCanvasActive.value = (mode != VideoDisplayMode.OFF)
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
        forceVideoLoop: Boolean? = null
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
                forceVideoLoop
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
                updateVideo = (customVideoUri != null || removeVideo)
            )
        }
    }

    fun updatePlaylist(playlistId: Long, newName: String, newDescription: String = "") {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.updatePlaylist(playlistId, newName, newDescription)
            val current = _selectedPlaylist.value
            if (current != null && current.id == playlistId) {
                _selectedPlaylist.value = current.copy(name = newName, description = newDescription)
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
    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            _importStatusMessage.value = "Importando canciones seleccionadas..."
            val count = repository.importUris(getApplication(), uris)
            _isImporting.value = false
            _importStatusMessage.value = if (count > 0) {
                "¡Éxito! Se importaron $count canción(es) a tu biblioteca."
            } else {
                "Las canciones seleccionadas ya estaban en tu biblioteca."
            }
        }
    }

    fun importFolder(treeUri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importStatusMessage.value = "Analizando carpeta seleccionada..."
            val count = repository.importTreeUri(getApplication(), treeUri)
            _isImporting.value = false
            _importStatusMessage.value = if (count > 0) {
                "¡Éxito! Se importaron $count canción(es) desde la carpeta."
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
        onTrackCreated: ((Track) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isImporting.value = true
            _importStatusMessage.value = "Convirtiendo video a música, extrayendo carátula y Video Canvas..."
            val track = repository.importVideoAsTrack(
                context = getApplication(),
                videoUri = videoUri,
                title = title,
                artist = artist,
                album = album,
                attachAsCanvas = attachAsCanvas,
                forceLoop = forceLoop
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

    fun dismissImportStatus() {
        _importStatusMessage.value = null
    }

    // Acciones de Listas de Reproducción
    fun createPlaylist(name: String, description: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name, description)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
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

    // Acciones de Velocidad y Tono (Playback Parameters)
    fun setPlaybackSpeed(speed: Float) = audioPlayer.setPlaybackSpeed(speed)

    fun setPlaybackPitch(pitch: Float) = audioPlayer.setPlaybackPitch(pitch)

    fun resetSpeedAndPitch() = audioPlayer.resetSpeedAndPitch()

    // Acciones de Transición de Pistas (Crossfade y Gapless)
    fun setCrossfadeSeconds(seconds: Int) = audioPlayer.setCrossfadeSeconds(seconds)

    fun setGaplessEnabled(enabled: Boolean) = audioPlayer.setGaplessEnabled(enabled)

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

        sleepTimerJob = viewModelScope.launch(Dispatchers.Default) {
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

    override fun onCleared() {
        super.onCleared()
        cancelSleepTimer()
        audioPlayer.release()
    }
}
