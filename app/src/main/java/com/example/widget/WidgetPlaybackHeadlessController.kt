package com.example.widget

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.model.Track
import com.example.playback.AudioEffectManager
import com.example.playback.AuraAudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Controlador de reproducción autónomo en segundo plano para los Widgets de escritorio.
 *
 * Responsabilidades:
 * - Si la aplicación está abierta y [AuraAudioPlayer] ya tiene callbacks registrados, delega
 *   la acción de inmediato en memoria.
 * - Si la aplicación fue cerrada por el usuario o por Android (arranque en frío), inicializa
 *   una instancia ligera de [AuraAudioPlayer] en el contexto de la aplicación/servicio, consulta
 *   [AppDatabase] en segundo plano (`Dispatchers.IO`) y reanuda la canción en el minuto guardado,
 *   cambia de pista, marca favoritos o reproduce cualquiera de las 4 canciones / 4 playlists
 *   **sin abrir la ventana de MainActivity**.
 */
object WidgetPlaybackHeadlessController {

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var headlessPlayer: AuraAudioPlayer? = null

    @Volatile
    private var headlessEffectManager: AudioEffectManager? = null

    private fun getOrCreateHeadlessPlayer(context: Context): AuraAudioPlayer {
        val existing = headlessPlayer
        if (existing != null) return existing
        synchronized(this) {
            val again = headlessPlayer
            if (again != null) return again
            val appCtx = context.applicationContext
            val fx = AudioEffectManager()
            val player = AuraAudioPlayer(appCtx, fx)
            headlessEffectManager = fx
            headlessPlayer = player
            return player
        }
    }

    /**
     * Si el ViewModel principal se inicializa con su propio AuraAudioPlayer, liberamos el reproductor
     * headless si fuera distinto para evitar dos instancias simultáneas.
     */
    fun releaseHeadlessIfSeparate(activePlayer: AuraAudioPlayer) {
        val current = headlessPlayer
        if (current != null && current !== activePlayer) {
            try {
                current.release()
            } catch (_: Throwable) {}
            headlessPlayer = null
            headlessEffectManager = null
        }
    }

    /**
     * Reanuda o pausa la música desde el Widget (incluso en frío tras cerrar la app).
     */
    fun handleTogglePlayPause(context: Context) {
        val appContext = context.applicationContext
        controllerScope.launch {
            val allTracks = loadAllTracksFromDb(appContext)
            val persisted = WidgetStateStore.getPersistedPlaybackState(appContext)
            val player = getOrCreateHeadlessPlayer(appContext)

            if (player.currentTrack.value != null) {
                player.togglePlayPause()
                return@launch
            }

            if (allTracks.isEmpty()) return@launch
            val targetIndex = allTracks.indexOfFirst { it.id == persisted.trackId }.coerceAtLeast(0)
            player.playTrackList(allTracks, targetIndex)
            if (persisted.positionMs > 1000L && allTracks[targetIndex].id == persisted.trackId) {
                player.seekTo(persisted.positionMs)
            }
        }
    }

    fun handleNext(context: Context) {
        val appContext = context.applicationContext
        controllerScope.launch {
            val allTracks = loadAllTracksFromDb(appContext)
            val persisted = WidgetStateStore.getPersistedPlaybackState(appContext)
            val player = getOrCreateHeadlessPlayer(appContext)

            if (player.currentTrack.value != null && player.queue.value.isNotEmpty()) {
                player.playNext()
                return@launch
            }

            if (allTracks.isEmpty()) return@launch
            val currentIdx = allTracks.indexOfFirst { it.id == persisted.trackId }.coerceAtLeast(0)
            val nextIdx = (currentIdx + 1) % allTracks.size
            player.playTrackList(allTracks, nextIdx)
        }
    }

    fun handlePrevious(context: Context) {
        val appContext = context.applicationContext
        controllerScope.launch {
            val allTracks = loadAllTracksFromDb(appContext)
            val persisted = WidgetStateStore.getPersistedPlaybackState(appContext)
            val player = getOrCreateHeadlessPlayer(appContext)

            if (player.currentTrack.value != null && player.queue.value.isNotEmpty()) {
                player.playPrevious()
                return@launch
            }

            if (allTracks.isEmpty()) return@launch
            val currentIdx = allTracks.indexOfFirst { it.id == persisted.trackId }.coerceAtLeast(0)
            val prevIdx = if (currentIdx - 1 >= 0) currentIdx - 1 else allTracks.lastIndex
            player.playTrackList(allTracks, prevIdx)
        }
    }

    fun handleToggleFavorite(context: Context) {
        val appContext = context.applicationContext
        controllerScope.launch {
            val persisted = WidgetStateStore.getPersistedPlaybackState(appContext)
            val targetId = persisted.trackId
            if (targetId <= 0L) return@launch

            val newFavorite = !persisted.isFavorite
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(appContext)
                db.trackDao().updateFavorite(targetId, newFavorite)
            }
            WidgetStateStore.updateFavoriteOnly(appContext, targetId, newFavorite)
            headlessPlayer?.updateTrackFavorite(targetId, newFavorite)
            AuraMusicWidgetProvider.refreshAllWidgets(appContext)
            AuraLibraryWidgetProvider.refreshAllWidgets(appContext)
        }
    }

    /**
     * Reproduce una canción específica al tocar una de las 4 tarjetas del segundo Widget.
     */
    fun handlePlaySpecificTrack(context: Context, trackId: Long) {
        if (trackId <= 0L) return
        val appContext = context.applicationContext
        controllerScope.launch {
            val db = AppDatabase.getInstance(appContext)
            withContext(Dispatchers.IO) {
                db.trackDao().incrementPlayCount(trackId)
            }
            val topOrAll = loadTopPlayedOrAllTracksFromDb(appContext)
            if (topOrAll.isEmpty()) return@launch
            val index = topOrAll.indexOfFirst { it.id == trackId }.coerceAtLeast(0)

            val handledByActive = AuraMusicWidgetProvider.onPlaySpecificTrackCallback?.invoke(
                topOrAll[index],
                topOrAll
            ) ?: false

            if (!handledByActive) {
                val player = getOrCreateHeadlessPlayer(appContext)
                player.playTrackList(topOrAll, index)
            }
            syncQuickGridFromDb(appContext)
        }
    }

    /**
     * Reproduce una Playlist completa al tocar una de las 4 tarjetas del segundo Widget en modo Playlists.
     */
    fun handlePlayPlaylist(context: Context, playlistId: Long) {
        val appContext = context.applicationContext
        controllerScope.launch {
            val tracks = loadPlaylistTracksFromDb(appContext, playlistId)
            if (tracks.isEmpty()) return@launch

            val firstTrack = tracks.first()
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(appContext).trackDao().incrementPlayCount(firstTrack.id)
            }

            val handledByActive = AuraMusicWidgetProvider.onPlaySpecificTrackCallback?.invoke(
                firstTrack,
                tracks
            ) ?: false

            if (!handledByActive) {
                val player = getOrCreateHeadlessPlayer(appContext)
                player.playTrackList(tracks, 0)
            }
            syncQuickGridFromDb(appContext)
        }
    }

    /**
     * Sincroniza desde Room las 4 canciones más escuchadas y las 4 playlists hacia [WidgetStateStore].
     */
    fun syncQuickGridFromDb(context: Context) {
        val appContext = context.applicationContext
        controllerScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(appContext)
                val topTracks = db.trackDao().getTopPlayedTracks().firstOrNull()?.map { it.toDomain() } ?: emptyList()
                val allTracks = db.trackDao().getAllTracks().firstOrNull()?.map { it.toDomain() } ?: emptyList()
                val favTracks = db.trackDao().getFavoriteTracks().firstOrNull()?.map { it.toDomain() } ?: emptyList()
                val playlistEntities = db.playlistDao().getAllPlaylists().firstOrNull() ?: emptyList()

                val domainPlaylists = playlistEntities.map { entity ->
                    val plTracks = db.playlistDao().getTracksForPlaylist(entity.id).firstOrNull()?.map { it.toDomain() } ?: emptyList()
                    com.example.model.Playlist(
                        id = entity.id,
                        name = entity.name,
                        description = entity.description,
                        trackCount = plTracks.size,
                        createdAt = entity.createdAt,
                        customArtPath = entity.customArtPath,
                        previewTracks = plTracks.take(4)
                    )
                }

                val effectiveTop = if (topTracks.isNotEmpty()) topTracks else allTracks
                WidgetStateStore.saveQuickGridItems(
                    context = appContext,
                    topTracks = effectiveTop,
                    playlists = domainPlaylists,
                    favoriteTracks = favTracks
                )
                withContext(Dispatchers.Main) {
                    AuraLibraryWidgetProvider.refreshAllWidgets(appContext)
                }
            } catch (_: Throwable) {}
        }
    }

    private suspend fun loadAllTracksFromDb(context: Context): List<Track> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            db.trackDao().getAllTracks().firstOrNull()?.map { it.toDomain() } ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private suspend fun loadTopPlayedOrAllTracksFromDb(context: Context): List<Track> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val top = db.trackDao().getTopPlayedTracks().firstOrNull()?.map { it.toDomain() } ?: emptyList()
            if (top.isNotEmpty()) top else (db.trackDao().getAllTracks().firstOrNull()?.map { it.toDomain() } ?: emptyList())
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private suspend fun loadPlaylistTracksFromDb(context: Context, playlistId: Long): List<Track> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            if (playlistId == -1L) {
                val favs = db.trackDao().getFavoriteTracks().firstOrNull()?.map { it.toDomain() } ?: emptyList()
                if (favs.isNotEmpty()) favs else (db.trackDao().getAllTracks().firstOrNull()?.map { it.toDomain() } ?: emptyList())
            } else {
                val plTracks = db.playlistDao().getTracksForPlaylist(playlistId).firstOrNull()?.map { it.toDomain() } ?: emptyList()
                if (plTracks.isNotEmpty()) plTracks else (db.trackDao().getAllTracks().firstOrNull()?.map { it.toDomain() } ?: emptyList())
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
