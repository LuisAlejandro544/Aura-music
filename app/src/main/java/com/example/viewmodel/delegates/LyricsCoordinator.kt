package com.example.viewmodel.delegates

import android.content.Context
import android.net.Uri
import com.example.data.importer.LyricsManager
import com.example.data.storage.AppStorageManager
import com.example.model.LyricSearchResult
import com.example.model.LyricsState
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Aura Music - Coordinador Modular de Letras Sincronizadas (.LRC y Karaoke)
 *
 * Responsabilidades:
 * - Carga reactiva de letras locales y auto-detección (.lrc/.txt hermanos en almacenamiento).
 * - Consulta y descarga automática de letras en línea libres desde LRCLIB.
 * - Importación de archivos .lrc / .txt seleccionados por el usuario desde su dispositivo.
 * - Búsqueda interactiva de letras con selección de versiones y recomendación oficial.
 * - Almacenamiento persistente en la subcarpeta estructurada `lyrics/`.
 */
class LyricsCoordinator(
    private val context: Context,
    private val storageManager: AppStorageManager,
    private val scope: CoroutineScope,
    private val getCurrentTrack: () -> Track?
) {

    // Estado reactivo de Letras Sincronizadas (.LRC)
    private val _lyricsState = MutableStateFlow(LyricsState())
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()

    // Búsqueda interactiva de letras con selección de versiones y recomendación oficial
    private val _isSearchLyricsDialogOpen = MutableStateFlow(false)
    val isSearchLyricsDialogOpen: StateFlow<Boolean> = _isSearchLyricsDialogOpen.asStateFlow()

    private val _isSearchingLyrics = MutableStateFlow(false)
    val isSearchingLyrics: StateFlow<Boolean> = _isSearchingLyrics.asStateFlow()

    private val _lyricsSearchResults = MutableStateFlow<List<LyricSearchResult>>(emptyList())
    val lyricsSearchResults: StateFlow<List<LyricSearchResult>> = _lyricsSearchResults.asStateFlow()

    private val _searchLyricsError = MutableStateFlow<String?>(null)
    val searchLyricsError: StateFlow<String?> = _searchLyricsError.asStateFlow()

    fun loadLyrics(track: Track?) {
        if (track == null) {
            _lyricsState.value = LyricsState()
            return
        }
        scope.launch(Dispatchers.IO) {
            val local = LyricsManager.loadLocalLyrics(track, storageManager)
            if (local != null) {
                _lyricsState.value = local
            } else {
                // 1. Detección automática en el celular (.lrc o .txt hermano en misma carpeta o tags)
                val autoDetected = LyricsManager.autoDetectAndAssociateLyrics(
                    context,
                    track,
                    storageManager
                )
                if (autoDetected != null) {
                    _lyricsState.value = autoDetected
                } else {
                    _lyricsState.value = LyricsState(trackId = track.id)
                    // 2. Intento de descarga en línea desde LRCLIB para canciones sin letras
                    fetchOnlineLyrics(track)
                }
            }
        }
    }

    fun importLyricsFromUri(uri: Uri) {
        val target = getCurrentTrack() ?: return
        scope.launch(Dispatchers.IO) {
            val imported = LyricsManager.importLyricsFromUri(
                context,
                target,
                uri,
                storageManager
            )
            if (imported != null) {
                _lyricsState.value = imported
            }
        }
    }

    fun fetchOnlineLyrics(track: Track? = null) {
        val target = track ?: getCurrentTrack() ?: return
        scope.launch(Dispatchers.IO) {
            _lyricsState.value = _lyricsState.value.copy(isLoading = true, error = null)
            val result = LyricsManager.fetchLyricsOnline(target, storageManager)
            result.onSuccess { state ->
                _lyricsState.value = state
            }.onFailure { err ->
                _lyricsState.value = _lyricsState.value.copy(isLoading = false, error = err.message)
            }
        }
    }

    fun saveCustomLyrics(content: String, track: Track? = null) {
        val target = track ?: getCurrentTrack() ?: return
        scope.launch(Dispatchers.IO) {
            val state = LyricsManager.saveLyrics(target, storageManager, content)
            _lyricsState.value = state
        }
    }

    /**
     * Abre el diálogo interactivo de búsqueda de letras con el nombre y artista de la pista actual.
     */
    fun openSearchLyricsDialog() {
        val target = getCurrentTrack()
        _lyricsSearchResults.value = emptyList()
        _searchLyricsError.value = null
        _isSearchLyricsDialogOpen.value = true
        if (target != null) {
            searchLyricsOptions(target.title, target.artist)
        }
    }

    fun closeSearchLyricsDialog() {
        _isSearchLyricsDialogOpen.value = false
    }

    /**
     * Realiza la búsqueda de letras en LRCLIB permitiendo que el usuario personalice el nombre de la canción.
     */
    fun searchLyricsOptions(title: String, artist: String = "") {
        scope.launch(Dispatchers.IO) {
            _isSearchingLyrics.value = true
            _searchLyricsError.value = null
            try {
                // Al buscar interactivamente por nombre, durationSec se fija en 0L
                // para que el catálogo de letras no descarte canciones si el audio local está recortado
                val results = LyricsManager.searchLyricsOptions(
                    trackTitle = title,
                    artistName = artist,
                    durationSec = 0L
                )
                _lyricsSearchResults.value = results
                if (results.isEmpty()) {
                    _searchLyricsError.value = "No se encontraron letras para \"$title\". Prueba simplificando el nombre de la canción o borrando el artista."
                }
            } catch (e: Throwable) {
                _searchLyricsError.value = "Error al consultar catálogo de letras: ${e.message}"
            } finally {
                _isSearchingLyrics.value = false
            }
        }
    }

    /**
     * Aplica la opción de letra seleccionada por el usuario (oficial o alternativa) a la pista activa.
     */
    fun selectLyricSearchResult(result: LyricSearchResult) {
        val target = getCurrentTrack() ?: return
        scope.launch(Dispatchers.IO) {
            val state = LyricsManager.applySearchResult(target, storageManager, result)
            _lyricsState.value = state
            _isSearchLyricsDialogOpen.value = false
        }
    }
}
