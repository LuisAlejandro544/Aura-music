package com.example.viewmodel.delegates

import android.content.SharedPreferences
import com.example.data.repository.MusicRepository
import com.example.model.AppWallpaperConfig
import com.example.model.AuraTheme
import com.example.model.NowPlayingDesignMode
import com.example.model.Playlist
import com.example.model.Track
import com.example.model.VideoDisplayMode
import com.example.model.WallpaperMediaType
import com.example.model.WallpaperScreenScope
import com.example.model.WidgetConfig
import com.example.model.WidgetGridContentMode
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
 * paletas temáticas, fondo personalizado de galería y modos de visualización de video.
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

    private val _hasVisitedSettings = MutableStateFlow(
        appPrefs.getBoolean("pref_has_visited_settings", false)
    )
    val hasVisitedSettings: StateFlow<Boolean> = _hasVisitedSettings.asStateFlow()

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

    private val _isBackgroundGameModeEnabled = MutableStateFlow(
        appPrefs.getBoolean("pref_background_game_mode_enabled", true).also {
            com.example.AuraApplication.isBackgroundGameModeEnabled = it
        }
    )
    val isBackgroundGameModeEnabled: StateFlow<Boolean> = _isBackgroundGameModeEnabled.asStateFlow()

    private val _appWallpaperConfig = MutableStateFlow(loadWallpaperConfigFromPrefs(appPrefs))
    val appWallpaperConfig: StateFlow<AppWallpaperConfig> = _appWallpaperConfig.asStateFlow()

    private fun loadWallpaperConfigFromPrefs(prefs: SharedPreferences): AppWallpaperConfig {
        val enabled = prefs.getBoolean("pref_app_wallpaper_enabled", false)
        val mediaType = WallpaperMediaType.fromId(prefs.getInt("pref_app_wallpaper_type", 0))
        val mediaPath = prefs.getString("pref_app_wallpaper_path", "") ?: ""
        val scope = WallpaperScreenScope.fromId(prefs.getInt("pref_app_wallpaper_scope", 1))
        val dimAlpha = prefs.getFloat("pref_app_wallpaper_dim", 0.62f).coerceIn(0.25f, 0.92f)
        val blurDp = prefs.getInt("pref_app_wallpaper_blur", 0).coerceIn(0, 25)
        return AppWallpaperConfig(
            isEnabled = enabled,
            mediaType = mediaType,
            mediaPath = mediaPath,
            screenScope = scope,
            dimOverlayAlpha = dimAlpha,
            blurRadiusDp = blurDp
        )
    }

    private val _videoDisplayMode = MutableStateFlow(
        try {
            val savedMode = appPrefs.getString("pref_video_display_mode", VideoDisplayMode.FULLSCREEN_BACKGROUND.name)
            VideoDisplayMode.valueOf(savedMode ?: VideoDisplayMode.FULLSCREEN_BACKGROUND.name)
        } catch (_: Exception) {
            VideoDisplayMode.FULLSCREEN_BACKGROUND
        }
    )
    val videoDisplayMode: StateFlow<VideoDisplayMode> = _videoDisplayMode.asStateFlow()

    private val _nowPlayingDesignMode = MutableStateFlow(
        try {
            val savedDesign = appPrefs.getString("pref_now_playing_design_mode", NowPlayingDesignMode.AUTO.name)
            NowPlayingDesignMode.valueOf(savedDesign ?: NowPlayingDesignMode.AUTO.name)
        } catch (_: Exception) {
            NowPlayingDesignMode.AUTO
        }
    )
    val nowPlayingDesignMode: StateFlow<NowPlayingDesignMode> = _nowPlayingDesignMode.asStateFlow()

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
            "pref_background_game_mode_enabled" -> {
                val enabled = prefs.getBoolean(key, true)
                _isBackgroundGameModeEnabled.value = enabled
                com.example.AuraApplication.isBackgroundGameModeEnabled = enabled
            }
            "pref_app_wallpaper_enabled",
            "pref_app_wallpaper_type",
            "pref_app_wallpaper_path",
            "pref_app_wallpaper_scope",
            "pref_app_wallpaper_dim",
            "pref_app_wallpaper_blur" -> {
                _appWallpaperConfig.value = loadWallpaperConfigFromPrefs(prefs)
            }
            "pref_now_playing_design_mode" -> {
                val design = prefs.getString(key, NowPlayingDesignMode.AUTO.name)
                try {
                    _nowPlayingDesignMode.value = NowPlayingDesignMode.valueOf(design ?: NowPlayingDesignMode.AUTO.name)
                } catch (_: Exception) {}
            }
            "pref_has_visited_settings" -> {
                _hasVisitedSettings.value = prefs.getBoolean(key, false)
            }
        }
    }

    init {
        appPrefs.registerOnSharedPreferenceChangeListener(prefsChangeListener)
    }

    fun navigateTo(screen: NavScreen) {
        if (screen is NavScreen.Settings && !_hasVisitedSettings.value) {
            markSettingsVisited()
        }
        if (_currentScreen.value != screen) {
            screenBackStack.add(screen)
            _currentScreen.value = screen
        }
    }

    fun markSettingsVisited() {
        _hasVisitedSettings.value = true
        appPrefs.edit().putBoolean("pref_has_visited_settings", true).apply()
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

    fun setBackgroundGameModeEnabled(enabled: Boolean) {
        _isBackgroundGameModeEnabled.value = enabled
        com.example.AuraApplication.isBackgroundGameModeEnabled = enabled
        appPrefs.edit().putBoolean("pref_background_game_mode_enabled", enabled).apply()
    }

    fun updateAppWallpaperMedia(type: WallpaperMediaType, path: String, enabled: Boolean = true) {
        val updated = _appWallpaperConfig.value.copy(
            isEnabled = enabled && type != WallpaperMediaType.NONE && path.isNotBlank(),
            mediaType = type,
            mediaPath = path
        )
        _appWallpaperConfig.value = updated
        appPrefs.edit()
            .putBoolean("pref_app_wallpaper_enabled", updated.isEnabled)
            .putInt("pref_app_wallpaper_type", updated.mediaType.id)
            .putString("pref_app_wallpaper_path", updated.mediaPath)
            .apply()
    }

    fun setAppWallpaperEnabled(enabled: Boolean) {
        val current = _appWallpaperConfig.value
        val validEnabled = enabled && current.mediaType != WallpaperMediaType.NONE && current.mediaPath.isNotBlank()
        _appWallpaperConfig.value = current.copy(isEnabled = validEnabled)
        appPrefs.edit().putBoolean("pref_app_wallpaper_enabled", validEnabled).apply()
    }

    fun setAppWallpaperScope(scope: WallpaperScreenScope) {
        _appWallpaperConfig.value = _appWallpaperConfig.value.copy(screenScope = scope)
        appPrefs.edit().putInt("pref_app_wallpaper_scope", scope.id).apply()
    }

    fun setAppWallpaperDimAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0.25f, 0.92f)
        _appWallpaperConfig.value = _appWallpaperConfig.value.copy(dimOverlayAlpha = clamped)
        appPrefs.edit().putFloat("pref_app_wallpaper_dim", clamped).apply()
    }

    fun setAppWallpaperBlurDp(blurDp: Int) {
        val clamped = blurDp.coerceIn(0, 25)
        _appWallpaperConfig.value = _appWallpaperConfig.value.copy(blurRadiusDp = clamped)
        appPrefs.edit().putInt("pref_app_wallpaper_blur", clamped).apply()
    }

    fun setVideoDisplayMode(mode: VideoDisplayMode) {
        _videoDisplayMode.value = mode
        appPrefs.edit().putString("pref_video_display_mode", mode.name).apply()
    }

    fun setNowPlayingDesignMode(mode: NowPlayingDesignMode) {
        _nowPlayingDesignMode.value = mode
        appPrefs.edit().putString("pref_now_playing_design_mode", mode.name).apply()
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
