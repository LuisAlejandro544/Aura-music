package com.example.viewmodel.delegates

import android.content.SharedPreferences
import com.example.data.repository.MusicRepository
import com.example.model.AuraTheme
import com.example.model.Playlist
import com.example.model.Track
import com.example.model.VideoDisplayMode
import com.example.ui.navigation.LibraryTab
import com.example.ui.navigation.NavScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coordinador modular de navegación, preferencias de interfaz y colecciones de usuario en Aura Music.
 * Desacopla la gestión de pantallas, pila de navegación hacia atrás, selección de listas/álbumes/artistas,
 * paletas temáticas y modos de visualización de video de Now Playing y Mini Reproductor.
 */
class NavigationCoordinator(
    private val appPrefs: SharedPreferences,
    private val repository: MusicRepository,
    private val coroutineScope: CoroutineScope,
    private val getAllTracks: () -> List<Track>
) {
    private val _isOnboardingCompleted = MutableStateFlow(
        appPrefs.getBoolean("pref_onboarding_completed", false)
    )
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    private val _currentScreen = MutableStateFlow<NavScreen>(
        if (appPrefs.getBoolean("pref_onboarding_completed", false)) NavScreen.Home else NavScreen.Onboarding
    )
    val currentScreen: StateFlow<NavScreen> = _currentScreen.asStateFlow()

    private val screenBackStack = mutableListOf<NavScreen>(
        if (appPrefs.getBoolean("pref_onboarding_completed", false)) NavScreen.Home else NavScreen.Onboarding
    )

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _selectedPlaylistTracks = MutableStateFlow<List<Track>>(emptyList())
    val selectedPlaylistTracks: StateFlow<List<Track>> = _selectedPlaylistTracks.asStateFlow()

    private var selectedCollectionJob: Job? = null

    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded: StateFlow<Boolean> = _isNowPlayingExpanded.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedLibraryTab = MutableStateFlow(LibraryTab.SONGS)
    val selectedLibraryTab: StateFlow<LibraryTab> = _selectedLibraryTab.asStateFlow()

    private val _currentTheme = MutableStateFlow(
        try {
            val saved = appPrefs.getString("pref_aura_theme", AuraTheme.MATERIAL_YOU.name)
            AuraTheme.valueOf(saved ?: AuraTheme.MATERIAL_YOU.name)
        } catch (_: Exception) {
            AuraTheme.MATERIAL_YOU
        }
    )
    val currentTheme: StateFlow<AuraTheme> = _currentTheme.asStateFlow()

    private val _isDynamicArtworkColorEnabled = MutableStateFlow(
        appPrefs.getBoolean("pref_dynamic_artwork_color_enabled", true)
    )
    val isDynamicArtworkColorEnabled: StateFlow<Boolean> = _isDynamicArtworkColorEnabled.asStateFlow()

    private val _isMiniPlayerVideoEnabled = MutableStateFlow(
        appPrefs.getBoolean("pref_mini_player_video_enabled", false)
    )
    val isMiniPlayerVideoEnabled: StateFlow<Boolean> = _isMiniPlayerVideoEnabled.asStateFlow()

    private val _videoDisplayMode = MutableStateFlow(
        try {
            val savedMode = appPrefs.getString("pref_video_display_mode", VideoDisplayMode.FULLSCREEN_BACKGROUND.name)
            VideoDisplayMode.valueOf(savedMode ?: VideoDisplayMode.FULLSCREEN_BACKGROUND.name)
        } catch (_: Exception) {
            VideoDisplayMode.FULLSCREEN_BACKGROUND
        }
    )
    val videoDisplayMode: StateFlow<VideoDisplayMode> = _videoDisplayMode.asStateFlow()

    private val prefsChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            "pref_aura_theme" -> {
                val name = prefs.getString(key, AuraTheme.MATERIAL_YOU.name)
                try {
                    _currentTheme.value = AuraTheme.valueOf(name ?: AuraTheme.MATERIAL_YOU.name)
                } catch (_: Exception) {}
            }
            "pref_dynamic_artwork_color_enabled" -> {
                _isDynamicArtworkColorEnabled.value = prefs.getBoolean(key, true)
            }
            "pref_video_display_mode" -> {
                val mode = prefs.getString(key, VideoDisplayMode.FULLSCREEN_BACKGROUND.name)
                try {
                    _videoDisplayMode.value = VideoDisplayMode.valueOf(mode ?: VideoDisplayMode.FULLSCREEN_BACKGROUND.name)
                } catch (_: Exception) {}
            }
            "pref_mini_player_video_enabled" -> {
                _isMiniPlayerVideoEnabled.value = prefs.getBoolean(key, false)
            }
        }
    }

    init {
        appPrefs.registerOnSharedPreferenceChangeListener(prefsChangeListener)
    }

    fun navigateTo(screen: NavScreen) {
        if (_currentScreen.value != screen) {
            screenBackStack.add(screen)
            _currentScreen.value = screen
        }
    }

    fun completeOnboarding() {
        appPrefs.edit().putBoolean("pref_onboarding_completed", true).apply()
        _isOnboardingCompleted.value = true
        screenBackStack.clear()
        screenBackStack.add(NavScreen.Home)
        _currentScreen.value = NavScreen.Home
    }

    fun reopenOnboarding() {
        navigateTo(NavScreen.Onboarding)
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
        selectedCollectionJob = coroutineScope.launch {
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
        val albumTracks = getAllTracks().filter { it.album == albumName }
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
        val artistTracks = getAllTracks().filter { it.artist == artistName }
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

    fun setDynamicArtworkColorEnabled(enabled: Boolean) {
        _isDynamicArtworkColorEnabled.value = enabled
        appPrefs.edit().putBoolean("pref_dynamic_artwork_color_enabled", enabled).apply()
    }

    fun setMiniPlayerVideoEnabled(enabled: Boolean) {
        _isMiniPlayerVideoEnabled.value = enabled
        appPrefs.edit().putBoolean("pref_mini_player_video_enabled", enabled).apply()
    }

    fun setVideoDisplayMode(mode: VideoDisplayMode) {
        _videoDisplayMode.value = mode
        appPrefs.edit().putString("pref_video_display_mode", mode.name).apply()
    }

    fun updateSelectedPlaylistMetadata(id: Long, name: String, description: String, artPath: String?) {
        val current = _selectedPlaylist.value
        if (current != null && current.id == id) {
            _selectedPlaylist.value = current.copy(
                name = name,
                description = description,
                customArtPath = artPath
            )
        }
    }

    fun release() {
        appPrefs.unregisterOnSharedPreferenceChangeListener(prefsChangeListener)
        selectedCollectionJob?.cancel()
    }
}
