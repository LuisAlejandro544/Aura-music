package com.example.viewmodel.delegates

import android.content.Context
import com.example.core.model.Playlist
import com.example.core.model.Track
import com.example.core.model.WidgetConfig
import com.example.core.model.WidgetGridContentMode
import com.example.widget.AuraLibraryWidgetProvider
import com.example.widget.AuraMusicWidgetProvider
import com.example.widget.WidgetStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Coordinador de Widgets de pantalla de inicio para MusicViewModel.
 * Rol arquitectónico: concentra la configuración persistente del widget
 * (color dinámico, contenido 4x, progreso, intensidad), la sincronización
 * reactiva de top-4/playlists/favoritos con AuraLibraryWidgetProvider y
 * los callbacks de favorito/reproducción del widget principal.
 */
class WidgetSyncCoordinator(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val topPlayedTracks: StateFlow<List<Track>>,
    private val allTracks: StateFlow<List<Track>>,
    private val playlists: StateFlow<List<Playlist>>,
    private val favoriteTracks: StateFlow<List<Track>>,
    private val onPlayTrack: (Track, List<Track>?) -> Unit,
    private val getCurrentTrack: () -> Track?,
    private val onToggleFavorite: (Track) -> Unit
) {

    private val _widgetConfig = MutableStateFlow(WidgetStateStore.getConfig(appContext))
    val widgetConfig: StateFlow<WidgetConfig> = _widgetConfig.asStateFlow()

    fun setWidgetDynamicColorEnabled(enabled: Boolean) {
        updateConfig(_widgetConfig.value.copy(isDynamicColorEnabled = enabled)) {
            AuraMusicWidgetProvider.refreshAllWidgets(appContext)
            AuraLibraryWidgetProvider.refreshAllWidgets(appContext)
        }
    }

    fun setWidgetGridContentMode(mode: WidgetGridContentMode) {
        updateConfig(_widgetConfig.value.copy(gridContentMode = mode)) {
            AuraLibraryWidgetProvider.refreshAllWidgets(appContext)
        }
    }

    fun setWidgetShowProgress(enabled: Boolean) {
        updateConfig(_widgetConfig.value.copy(showProgressInWidget = enabled)) {
            AuraMusicWidgetProvider.refreshAllWidgets(appContext)
        }
    }

    fun setWidgetColorIntensityPercent(percent: Int) {
        updateConfig(_widgetConfig.value.copy(colorIntensityPercent = percent.coerceIn(30, 100))) {
            AuraMusicWidgetProvider.refreshAllWidgets(appContext)
            AuraLibraryWidgetProvider.refreshAllWidgets(appContext)
        }
    }

    fun forceSyncAllWidgets() {
        scope.launch(Dispatchers.IO) {
            val top = topPlayedTracks.value.ifEmpty { allTracks.value.sortedByDescending { it.playCount }.take(4) }
            WidgetStateStore.saveQuickGridItems(
                context = appContext,
                topTracks = top,
                playlists = playlists.value,
                favoriteTracks = favoriteTracks.value
            )
            AuraMusicWidgetProvider.refreshAllWidgets(appContext)
            AuraLibraryWidgetProvider.refreshAllWidgets(appContext)
        }
    }

    /** Vincula los callbacks en memoria de AuraMusicWidgetProvider hacia el ViewModel. */
    fun registerWidgetCallbacks() {
        AuraMusicWidgetProvider.onToggleFavoriteCallback = {
            val current = getCurrentTrack()
            if (current != null) {
                onToggleFavorite(current)
                true
            } else {
                false
            }
        }
        AuraMusicWidgetProvider.onPlaySpecificTrackCallback = { track, queueList ->
            onPlayTrack(track, queueList)
            true
        }
    }

    /** Sincroniza en caliente top-4, playlists y favoritos con el widget de biblioteca. */
    fun startLibraryWidgetSync() {
        scope.launch(Dispatchers.IO) {
            combine(topPlayedTracks, allTracks, playlists, favoriteTracks) { top, all, pls, favs ->
                val effectiveTop = if (top.isNotEmpty()) top else all.sortedByDescending { it.playCount }.take(4)
                Triple(effectiveTop, pls, favs)
            }.collect { (top4, pls, favs) ->
                WidgetStateStore.saveQuickGridItems(
                    context = appContext,
                    topTracks = top4,
                    playlists = pls,
                    favoriteTracks = favs
                )
                AuraLibraryWidgetProvider.refreshAllWidgets(appContext)
            }
        }
    }

    /** Refleja un cambio de favorito en ambos widgets tras conmutar en Room (hilo del llamador). */
    fun syncFavoriteToggled(trackId: Long, newFavorite: Boolean) {
        WidgetStateStore.updateFavoriteOnly(appContext, trackId, newFavorite)
        AuraMusicWidgetProvider.refreshAllWidgets(appContext)
        AuraLibraryWidgetProvider.refreshAllWidgets(appContext)
    }

    private fun updateConfig(updated: WidgetConfig, onSaved: () -> Unit) {
        _widgetConfig.value = updated
        scope.launch(Dispatchers.IO) {
            WidgetStateStore.saveConfig(appContext, updated)
            onSaved()
        }
    }
}
