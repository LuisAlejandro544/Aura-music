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

    // Estado de ecualizador
    val eqBands = effectManager.bands
    val bassBoostLevel = effectManager.bassBoostLevel
    val currentPreset = effectManager.currentPreset
    val isEqEnabled = effectManager.isEnabled

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
            repository.getTracksForPlaylist(playlist.id).collect { tracks ->
                _selectedPlaylistTracks.value = tracks
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
            repository.toggleFavorite(track.id, track.isFavorite)
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

    fun updateTrackInfo(trackId: Long, newTitle: String, newArtist: String, newAlbum: String) {
        viewModelScope.launch {
            repository.updateTrackInfo(getApplication(), trackId, newTitle, newArtist, newAlbum)
            audioPlayer.updateTrackMetadata(trackId, newTitle, newArtist, newAlbum)
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

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
