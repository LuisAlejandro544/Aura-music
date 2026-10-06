package com.example.playback.controllers

import com.example.model.RepeatMode
import com.example.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Aura Music - Controlador Modular de Cola y Navegación de Reproducción
 *
 * Responsabilidades:
 * - Gestión atómica de la lista de reproducción activa (queue) y posición actual (currentIndex).
 * - Modos de reproducción: Aleatorio (Shuffle) y Repetición (OFF, ALL, ONE).
 * - Cálculo determinista del siguiente y anterior elemento evitando colisiones.
 * - Sincronización reactiva de metadatos y favoritos dentro de la cola sin recrear listas innecesarias.
 */
class PlayerQueueController {

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    /**
     * Establece una nueva cola de canciones y selecciona el índice inicial seguro.
     * Retorna la pista seleccionada o null si la lista está vacía.
     */
    fun setQueue(tracks: List<Track>, startIndex: Int = 0): Track? {
        if (tracks.isEmpty()) return null
        _queue.value = tracks
        val safeIndex = startIndex.coerceIn(0, tracks.size - 1)
        _currentIndex.value = safeIndex
        return tracks[safeIndex]
    }

    /**
     * Obtiene el siguiente índice según el modo de repetición y aleatorio activo.
     */
    fun getNextIndex(): Int? {
        val q = _queue.value
        if (q.isEmpty()) return null

        if (_repeatMode.value == RepeatMode.ONE) {
            return _currentIndex.value
        }

        val nextIndex = if (_shuffleEnabled.value) {
            if (q.size > 1) {
                (q.indices - _currentIndex.value).randomOrNull() ?: 0
            } else 0
        } else {
            (_currentIndex.value + 1) % q.size
        }
        _currentIndex.value = nextIndex
        return nextIndex
    }

    /**
     * Obtiene el índice anterior respetando los límites de la cola.
     */
    fun getPreviousIndex(): Int? {
        val q = _queue.value
        if (q.isEmpty()) return null

        val prevIndex = if (_currentIndex.value > 0) {
            _currentIndex.value - 1
        } else {
            q.size - 1
        }
        _currentIndex.value = prevIndex
        return prevIndex
    }

    /**
     * Alterna el modo aleatorio (Shuffle).
     */
    fun toggleShuffle(): Boolean {
        val newState = !_shuffleEnabled.value
        _shuffleEnabled.value = newState
        return newState
    }

    /**
     * Cicla secuencialmente entre los modos de repetición: OFF -> ALL -> ONE -> OFF.
     */
    fun cycleRepeatMode(): RepeatMode {
        val next = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _repeatMode.value = next
        return next
    }

    /**
     * Actualiza el estado de favorito de una pista en la cola y opcionalmente en la pista activa.
     */
    fun updateTrackFavorite(trackId: Long, isFavorite: Boolean, currentTrack: Track?): Pair<Track?, List<Track>> {
        val updatedCurrent = if (currentTrack != null && currentTrack.id == trackId) {
            currentTrack.copy(isFavorite = isFavorite)
        } else currentTrack

        val updatedQueue = _queue.value.map {
            if (it.id == trackId) it.copy(isFavorite = isFavorite) else it
        }
        _queue.value = updatedQueue
        return updatedCurrent to updatedQueue
    }

    /**
     * Actualiza los metadatos editados de una pista en la cola y en la pista activa.
     */
    fun updateTrackMetadata(
        trackId: Long,
        title: String,
        artist: String,
        album: String,
        albumArtPath: String?,
        updateArt: Boolean,
        videoUri: String?,
        isVideoLoop: Boolean,
        updateVideo: Boolean,
        currentTrack: Track?
    ): Pair<Track?, List<Track>> {
        val updatedCurrent = if (currentTrack != null && currentTrack.id == trackId) {
            currentTrack.copy(
                title = title,
                artist = artist,
                album = album,
                albumArtPath = if (updateArt) albumArtPath else currentTrack.albumArtPath,
                videoUri = if (updateVideo) videoUri else currentTrack.videoUri,
                isVideoLoop = if (updateVideo) isVideoLoop else currentTrack.isVideoLoop
            )
        } else currentTrack

        val updatedQueue = _queue.value.map {
            if (it.id == trackId) {
                it.copy(
                    title = title,
                    artist = artist,
                    album = album,
                    albumArtPath = if (updateArt) albumArtPath else it.albumArtPath,
                    videoUri = if (updateVideo) videoUri else it.videoUri,
                    isVideoLoop = if (updateVideo) isVideoLoop else it.isVideoLoop
                )
            } else it
        }
        _queue.value = updatedQueue
        return updatedCurrent to updatedQueue
    }

    /**
     * Remueve una pista de la cola activa por su ID.
     */
    fun removeTrack(trackId: Long) {
        val currentQ = _queue.value
        val index = currentQ.indexOfFirst { it.id == trackId }
        if (index != -1) {
            val newQ = currentQ.toMutableList().apply { removeAt(index) }
            _queue.value = newQ
            if (_currentIndex.value >= newQ.size && newQ.isNotEmpty()) {
                _currentIndex.value = newQ.size - 1
            }
        }
    }
}
