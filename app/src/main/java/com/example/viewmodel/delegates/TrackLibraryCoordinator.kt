package com.example.viewmodel.delegates

import android.content.Context
import android.net.Uri
import com.example.data.importer.FFmpegNativeEngine
import com.example.data.importer.OnlineVideoAudioImporter
import com.example.data.repository.MusicRepository
import com.example.model.DownloadProgress
import com.example.model.Track
import com.example.playback.AuraAudioPlayer
import com.example.playback.AuraDownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Aura Music - Coordinador Modular de Biblioteca, Playlists e Importación de Medios
 *
 * Responsabilidades:
 * - Modificación de metadatos de pistas (título, artista, álbum, carátulas WebP, Video Canvas).
 * - Eliminación de canciones y limpieza de cola.
 * - Creación, actualización y eliminación de listas de reproducción.
 * - Orquestación de importación desde SAF (archivos y carpetas) con recorte inteligente de silencios.
 * - Extracción y conversión de Video a Música y descargas en segundo plano con AuraDownloadService.
 */
class TrackLibraryCoordinator(
    private val context: Context,
    private val repository: MusicRepository,
    private val audioPlayer: AuraAudioPlayer,
    private val coroutineScope: CoroutineScope,
    private val onUpdatePlaylistNav: (Long, String, String, String?) -> Unit,
    private val onPlaylistDeleted: (Long) -> Unit
) {

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importStatusMessage = MutableStateFlow<String?>(null)
    val importStatusMessage: StateFlow<String?> = _importStatusMessage.asStateFlow()

    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    private var pendingDownloadSuccessCallback: ((Track) -> Unit)? = null

    init {
        // Sincronización reactiva con AuraDownloadService
        coroutineScope.launch {
            AuraDownloadService.downloadProgress.collect { progress ->
                _downloadProgress.value = progress
                _isImporting.value = progress.isDownloading
            }
        }

        coroutineScope.launch {
            AuraDownloadService.statusMessages.collect { msg ->
                _importStatusMessage.value = msg
            }
        }

        coroutineScope.launch {
            AuraDownloadService.completedEvents.collect { event ->
                _isImporting.value = false
                val callback = pendingDownloadSuccessCallback
                pendingDownloadSuccessCallback = null
                if (event.autoPlayImmediately) {
                    if (callback != null) {
                        callback.invoke(event.track)
                    } else {
                        audioPlayer.playTrack(event.track)
                    }
                }
            }
        }
    }

    fun setStatusMessage(msg: String?) {
        _importStatusMessage.value = msg
    }

    fun dismissImportStatus() {
        _importStatusMessage.value = null
    }

    fun setImporting(importing: Boolean, message: String? = null) {
        _isImporting.value = importing
        _importStatusMessage.value = message
    }

    fun deleteTrack(track: Track) {
        coroutineScope.launch {
            if (audioPlayer.currentTrack.value?.id == track.id) {
                audioPlayer.playNext()
            }
            audioPlayer.removeTrackFromQueue(track.id)
            repository.deleteTrack(context, track.id)
        }
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
        loopStyle: FFmpegNativeEngine.CanvasLoopStyle = FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE
    ) {
        coroutineScope.launch {
            val updated = repository.updateTrackDetails(
                context,
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
                updateVideo = (customVideoUri != null || removeVideo || loopStyle == FFmpegNativeEngine.CanvasLoopStyle.BOOMERANG)
            )
        }
    }

    fun createPlaylist(name: String, description: String = "", customArtUri: Uri? = null) {
        if (name.isBlank()) return
        coroutineScope.launch {
            repository.createPlaylist(context, name, description, customArtUri)
        }
    }

    fun updatePlaylist(
        playlistId: Long,
        newName: String,
        newDescription: String = "",
        customArtUri: Uri? = null,
        removeArtwork: Boolean = false
    ) {
        if (newName.isBlank()) return
        coroutineScope.launch {
            val savedArtPath = repository.updatePlaylist(context, playlistId, newName, newDescription, customArtUri, removeArtwork)
            onUpdatePlaylistNav(playlistId, newName, newDescription, savedArtPath)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        coroutineScope.launch {
            repository.deletePlaylist(context, playlistId)
            onPlaylistDeleted(playlistId)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        coroutineScope.launch { repository.addTrackToPlaylist(playlistId, trackId) }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        coroutineScope.launch { repository.removeTrackFromPlaylist(playlistId, trackId) }
    }

    fun clearAllTracks() {
        coroutineScope.launch {
            repository.clearAllTracks()
            _importStatusMessage.value = "Biblioteca de música reiniciada."
        }
    }

    fun importUris(uris: List<Uri>, trimSilence: Boolean = false) {
        if (uris.isEmpty()) return
        coroutineScope.launch {
            _isImporting.value = true
            _importStatusMessage.value = if (trimSilence) "Importando y eliminando silencios al inicio y final..." else "Importando canciones seleccionadas..."
            val count = repository.importUris(context, uris, trimSilence = trimSilence)
            _isImporting.value = false
            _importStatusMessage.value = if (count > 0) "¡Éxito! Se importaron $count canción(es) a tu biblioteca." else "Las canciones seleccionadas ya estaban en tu biblioteca."
        }
    }

    fun importFolder(treeUri: Uri, trimSilence: Boolean = false) {
        coroutineScope.launch {
            _isImporting.value = true
            _importStatusMessage.value = if (trimSilence) "Analizando carpeta y eliminando silencios..." else "Analizando carpeta seleccionada..."
            val count = repository.importTreeUri(context, treeUri, trimSilence = trimSilence)
            _isImporting.value = false
            _importStatusMessage.value = if (count > 0) "¡Éxito! Se importaron $count canción(es) desde la carpeta." else "No se encontraron canciones nuevas en la carpeta."
        }
    }

    fun seedDemoTracks() {
        coroutineScope.launch {
            _isImporting.value = true
            _importStatusMessage.value = "Generando pistas demostrativas Synthwave..."
            val count = repository.seedDemoTracks(context)
            _isImporting.value = false
            _importStatusMessage.value = "Se crearon $count pistas de prueba Synthwave con audio real."
        }
    }

    fun importVideoAsTrack(
        videoUri: Uri,
        title: String,
        artist: String,
        album: String,
        attachAsCanvas: Boolean,
        forceLoop: Boolean?,
        trimSilence: Boolean = false,
        loopStyle: FFmpegNativeEngine.CanvasLoopStyle = FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE,
        onTrackCreated: ((Track) -> Unit)? = null
    ) {
        coroutineScope.launch {
            _isImporting.value = true
            _importStatusMessage.value = if (trimSilence) "Convirtiendo video a música y recortando silencios..." else "Convirtiendo video a música con Video Canvas..."
            val track = repository.importVideoAsTrack(context, videoUri, title, artist, album, attachAsCanvas, forceLoop, trimSilence, loopStyle)
            _isImporting.value = false
            if (track != null) {
                _importStatusMessage.value = "¡Éxito! Se añadió \"${track.title}\" con Video Canvas."
                onTrackCreated?.invoke(track)
            } else {
                _importStatusMessage.value = "No se pudo procesar el video seleccionado."
            }
        }
    }

    fun importFromWebVideoLink(
        resolvedInfo: OnlineVideoAudioImporter.ResolvedMediaInfo,
        customTitle: String,
        customArtist: String,
        attachAsCanvas: Boolean,
        trimSilence: Boolean = false,
        loopStyle: FFmpegNativeEngine.CanvasLoopStyle = FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE,
        onSuccess: (Track) -> Unit
    ) {
        pendingDownloadSuccessCallback = onSuccess
        _isImporting.value = true
        _downloadProgress.value = DownloadProgress(isDownloading = true, phase = "Iniciando descarga...")
        _importStatusMessage.value = "Descargando en segundo plano..."
        AuraDownloadService.startDownload(context, resolvedInfo, customTitle, customArtist, attachAsCanvas, trimSilence, loopStyle)
    }
}
