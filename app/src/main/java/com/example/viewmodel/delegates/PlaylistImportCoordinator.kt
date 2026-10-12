package com.example.viewmodel.delegates

import android.content.Context
import android.graphics.BitmapFactory
import com.example.core.data.importer.FFmpegNativeEngine
import com.example.core.data.importer.OnlineVideoAudioImporter
import com.example.core.data.importer.playlist.ExternalPlaylistImporter
import com.example.core.data.local.dao.PlaylistDao
import com.example.core.data.local.dao.TrackDao
import com.example.core.data.local.entity.PlaylistEntity
import com.example.core.data.local.entity.PlaylistTrackCrossRef
import com.example.core.data.local.entity.TrackEntity
import com.example.core.data.storage.AppStorageManager
import com.example.core.data.streaming.StreamingApiService
import com.example.debug.AuraDebugManager
import com.example.core.model.ExternalPlaylistPreview
import com.example.core.model.ExternalPlaylistSource
import com.example.core.model.ExternalPlaylistTrackItem
import com.example.core.model.PlaylistImportSaveMode
import com.example.core.model.StreamingSearchItem
import com.example.core.model.StreamingSourcePlatform
import com.example.core.playback.AuraDownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Coordinador MVVM para el Importador Universal de Playlists (Spotify y YouTube Music / YouTube).
 *
 * Responsabilidades:
 * 1.Gestionar el estado reactivo del modal `ImportExternalPlaylistDialog` (URL ingresada, análisis,
 *    previsualización de canciones, selección de modo de guardado y barra de progreso).
 * 2.Descargar y comprimir la portada oficial de la Playlist a WebP Lossless en `images/` (`Dispatchers.IO`).
 * 3.Ejecutar la importación en cualquiera de los 2 modos elegidos por el usuario:
 *    - `STREAMING_INSTANT`: Empareja las pistas de Spotify con el catálogo de streaming (si aplica),
 *      inserta las pistas como `TrackEntity` de Modo Streaming y crea la Playlist completa en Room
 *      en cuestión de segundos, abriéndola lista para reproducir.
 *    - `OFFLINE_DOWNLOAD`: Crea la Playlist en Room con su portada WebP y encola la descarga en segundo plano
 *      de sus pistas mediante `AuraDownloadService`, vinculando automáticamente cada canción descargada
 *      a la Playlist creada a medida que finaliza cada descarga.
 */
class PlaylistImportCoordinator(
    private val context: Context,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val storageManager: AppStorageManager,
    private val scope: CoroutineScope,
    private val onStatusMessage: (String?) -> Unit,
    private val onOpenCreatedPlaylist: (Long) -> Unit
) {

    companion object {
        private const val TAG = "PlaylistImportCoordinator"
    }

    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()

    private val _inputUrl = MutableStateFlow("")
    val inputUrl: StateFlow<String> = _inputUrl.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _playlistPreview = MutableStateFlow<ExternalPlaylistPreview?>(null)
    val playlistPreview: StateFlow<ExternalPlaylistPreview?> = _playlistPreview.asStateFlow()

    private val _customPlaylistName = MutableStateFlow("")
    val customPlaylistName: StateFlow<String> = _customPlaylistName.asStateFlow()

    private val _selectedSaveMode = MutableStateFlow(PlaylistImportSaveMode.STREAMING_INSTANT)
    val selectedSaveMode: StateFlow<PlaylistImportSaveMode> = _selectedSaveMode.asStateFlow()

    private val _includeVideoCanvasOffline = MutableStateFlow(true)
    val includeVideoCanvasOffline: StateFlow<Boolean> = _includeVideoCanvasOffline.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importProgressText = MutableStateFlow<String?>(null)
    val importProgressText: StateFlow<String?> = _importProgressText.asStateFlow()

    private val _importProgressFraction = MutableStateFlow(0f)
    val importProgressFraction: StateFlow<Float> = _importProgressFraction.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var analyzeJob: Job? = null
    private var importJob: Job? = null

    // Mapa de videoIds encolados para descarga offline -> playlistId destino en Room
    private val pendingOfflineVideoToPlaylist = java.util.concurrent.ConcurrentHashMap<String, Long>()

    init {
        // Escuchar finalización de descargas en segundo plano para vincular cada pista descargada a su Playlist
        scope.launch(Dispatchers.IO) {
            AuraDownloadService.completedEvents.collect { event ->
                val vid = event.sourceVideoId
                if (!vid.isNullOrBlank()) {
                    val targetPlaylistId = pendingOfflineVideoToPlaylist.remove(vid)
                    if (targetPlaylistId != null && event.track.id > 0L) {
                        linkNewlyDownloadedTrackToPlaylist(targetPlaylistId, event.track.id)
                    }
                }
            }
        }
        scope.launch(Dispatchers.IO) {
            AuraDownloadService.taskFinishedEvents.collect { event ->
                val vid = event.sourceVideoId
                if (!event.success && !vid.isNullOrBlank()) {
                    pendingOfflineVideoToPlaylist.remove(vid)
                }
            }
        }
    }

    /**
     * Abre el diálogo del Importador de Playlists. Si se recibe [prefilledUrl] (por ejemplo desde "Compartir con..."),
     * precarga la URL y lanza automáticamente el análisis.
     */
    fun openDialog(prefilledUrl: String = "") {
        _errorMessage.value = null
        _showDialog.value = true
        if (prefilledUrl.isNotBlank()) {
            _inputUrl.value = prefilledUrl.trim()
            analyzePlaylistUrl(prefilledUrl.trim())
        }
    }

    fun dismissDialog() {
        if (_isImporting.value) return
        analyzeJob?.cancel()
        _showDialog.value = false
        _isAnalyzing.value = false
        _errorMessage.value = null
    }

    fun updateInputUrl(url: String) {
        _inputUrl.value = url
        _errorMessage.value = null
    }

    fun updateCustomPlaylistName(name: String) {
        _customPlaylistName.value = name
    }

    fun setSaveMode(mode: PlaylistImportSaveMode) {
        _selectedSaveMode.value = mode
    }

    fun setIncludeVideoCanvasOffline(enabled: Boolean) {
        _includeVideoCanvasOffline.value = enabled
    }

    /**
     * Analiza el enlace de Spotify o YouTube Music / YouTube para extraer su portada, título y canciones.
     */
    fun analyzePlaylistUrl(urlOverride: String? = null) {
        val targetUrl = (urlOverride ?: _inputUrl.value).trim()
        if (targetUrl.isBlank()) {
            _errorMessage.value = "Pega un enlace de Playlist de Spotify o YouTube Music."
            return
        }

        analyzeJob?.cancel()
        analyzeJob = scope.launch {
            _isAnalyzing.value = true
            _errorMessage.value = null
            _playlistPreview.value = null
            try {
                val result = ExternalPlaylistImporter.fetchPlaylistPreview(context, targetUrl)
                val preview = result.getOrNull()
                if (preview != null && preview.tracks.isNotEmpty()) {
                    _playlistPreview.value = preview
                    _customPlaylistName.value = preview.title
                } else {
                    _errorMessage.value = result.exceptionOrNull()?.localizedMessage
                        ?: "No se encontraron canciones en la Playlist indicada."
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Error al analizar la Playlist."
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    /**
     * Ejecuta la importación de la Playlist analizada según el modo seleccionado:
     * - `STREAMING_INSTANT`: Crea la Playlist al instante con pistas en Modo Streaming listas para escuchar.
     * - `OFFLINE_DOWNLOAD`: Crea la Playlist y encola la descarga de las canciones con `AuraDownloadService`.
     */
    fun confirmImportPlaylist() {
        val preview = _playlistPreview.value ?: return
        if (_isImporting.value) return

        val finalPlaylistName = _customPlaylistName.value.trim().ifBlank { preview.title }
        val saveMode = _selectedSaveMode.value
        val includeCanvas = _includeVideoCanvasOffline.value

        importJob?.cancel()
        importJob = scope.launch(Dispatchers.IO) {
            _isImporting.value = true
            _errorMessage.value = null
            _importProgressFraction.value = 0.05f
            _importProgressText.value = "Descargando portada oficial en WebP sin pérdida..."

            try {
                // 1. Descargar y comprimir la portada oficial de la Playlist a WebP Lossless
                val savedCoverPath = downloadAndSavePlaylistCoverWebP(preview.coverUrl)

                // 2. Crear la entidad Playlist en Room
                val playlistId = playlistDao.insertPlaylist(
                    PlaylistEntity(
                        name = finalPlaylistName,
                        description = "Importada desde ${preview.source.badgeLabel} • ${preview.author}",
                        customArtPath = savedCoverPath
                    )
                )

                // 3. Si la Playlist proviene de Spotify, emparejar sus canciones con el catálogo de streaming
                val resolvedTracks: List<ExternalPlaylistTrackItem> = if (preview.source == ExternalPlaylistSource.SPOTIFY) {
                    _importProgressText.value = "Emparejando ${preview.tracks.size} canciones de Spotify con el catálogo de alta fidelidad..."
                    ExternalPlaylistImporter.matchSpotifyTracksBatch(preview.tracks) { done, total ->
                        val frac = 0.10f + (done.toFloat() / total.coerceAtLeast(1).toFloat()) * 0.65f
                        _importProgressFraction.value = frac.coerceIn(0.10f, 0.75f)
                        _importProgressText.value = "Emparejando pistas de Spotify ($done / $total)..."
                    }
                } else {
                    preview.tracks
                }

                if (saveMode == PlaylistImportSaveMode.STREAMING_INSTANT) {
                    // === MODO STREAMING RÁPIDO (INSTANTÁNEO) ===
                    _importProgressText.value = "Guardando ${resolvedTracks.size} canciones en la Playlist '$finalPlaylistName'..."
                    _importProgressFraction.value = 0.85f

                    val baseTime = System.currentTimeMillis()
                    var position = 0
                    for (item in resolvedTracks) {
                        val videoId = item.videoIdOrUrl.trim()
                        if (videoId.isBlank() || videoId.startsWith("spotify:")) continue

                        val watchUrl = if (videoId.startsWith("http", ignoreCase = true)) {
                            videoId
                        } else {
                            "https://music.youtube.com/watch?v=$videoId"
                        }

                        val existing = trackDao.getTrackByUri(watchUrl)
                        val trackId = if (existing != null) {
                            existing.id
                        } else {
                            val entity = TrackEntity(
                                title = item.title,
                                artist = item.artist,
                                album = finalPlaylistName,
                                durationMs = item.durationSeconds * 1000L,
                                uriString = watchUrl,
                                albumArtPath = item.thumbnailUrl.ifBlank { savedCoverPath },
                                videoUri = null,
                                isVideoLoop = false,
                                mimeType = "audio/mp4",
                                dateAdded = baseTime + position,
                                folderName = "STREAM:YT_MUSIC:$videoId",
                                fileSizeFormatted = "YT MUSIC",
                                isFavorite = false
                            )
                            val insertedId = trackDao.insertTrack(entity)
                            if (insertedId <= 0L) {
                                trackDao.getTrackByUri(watchUrl)?.id ?: -1L
                            } else {
                                insertedId
                            }
                        }

                        if (trackId > 0L) {
                            playlistDao.insertCrossRef(
                                PlaylistTrackCrossRef(
                                    playlistId = playlistId,
                                    trackId = trackId,
                                    addedAt = baseTime + position
                                )
                            )
                            position++
                        }
                    }

                    _importProgressFraction.value = 1.0f
                    _importProgressText.value = "¡Playlist '$finalPlaylistName' creada con éxito!"

                    withContext(Dispatchers.Main) {
                        _isImporting.value = false
                        _showDialog.value = false
                        _playlistPreview.value = null
                        _inputUrl.value = ""
                        onStatusMessage("Playlist \"$finalPlaylistName\" ($position canciones) importada en Modo Streaming.")
                        onOpenCreatedPlaylist(playlistId)
                    }
                } else {
                    // === MODO DESCARGAR TODO OFFLINE (EN 2DO PLANO) ===
                    _importProgressText.value = "Preparando descarga offline de ${resolvedTracks.size} canciones..."
                    _importProgressFraction.value = 0.80f

                    val validTracks = resolvedTracks.filter {
                        it.videoIdOrUrl.isNotBlank() && !it.videoIdOrUrl.startsWith("spotify:")
                    }

                    withContext(Dispatchers.Main) {
                        _isImporting.value = false
                        _showDialog.value = false
                        _playlistPreview.value = null
                        _inputUrl.value = ""
                        onStatusMessage("Descargando ${validTracks.size} canciones de \"$finalPlaylistName\" en segundo plano...")
                        onOpenCreatedPlaylist(playlistId)
                    }

                    // Encolar secuencialmente en segundo plano para no saturar la red y vincular cada pista a la Playlist
                    scope.launch(Dispatchers.IO) {
                        for (item in validTracks) {
                            val vid = item.videoIdOrUrl.trim()
                            val searchItem = StreamingSearchItem(
                                videoId = vid,
                                title = item.title,
                                artist = item.artist,
                                album = finalPlaylistName,
                                durationSeconds = item.durationSeconds,
                                thumbnailUrl = item.thumbnailUrl,
                                platform = StreamingSourcePlatform.YT_MUSIC
                            )
                            val resolved = StreamingApiService.resolvePlayableStreamWithYtDlp(context, searchItem).getOrNull()
                            val mediaInfo = resolved ?: OnlineVideoAudioImporter.ResolvedMediaInfo(
                                originalUrl = searchItem.canonicalWatchUrl,
                                suggestedTitle = item.title,
                                suggestedArtist = item.artist,
                                videoUrl = searchItem.canonicalWatchUrl,
                                audioUrl = null,
                                coverUrl = item.thumbnailUrl,
                                durationSeconds = item.durationSeconds
                            )

                            pendingOfflineVideoToPlaylist[vid] = playlistId
                            AuraDownloadService.startDownload(
                                context = context,
                                resolvedInfo = mediaInfo,
                                customTitle = item.title,
                                customArtist = item.artist,
                                attachAsCanvas = includeCanvas,
                                trimSilence = false,
                                loopStyle = FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE,
                                autoPlayOnComplete = false,
                                sourceVideoId = vid
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                AuraDebugManager.logWarning(TAG, "Error importando Playlist: ${e.message}")
                _errorMessage.value = e.localizedMessage ?: "Ocurrió un error al importar la Playlist."
                _isImporting.value = false
            } finally {
                _importProgressText.value = null
                _importProgressFraction.value = 0f
            }
        }
    }

    private suspend fun linkNewlyDownloadedTrackToPlaylist(playlistId: Long, trackId: Long) {
        try {
            playlistDao.insertCrossRef(
                PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = trackId,
                    addedAt = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {}
    }

    private suspend fun downloadAndSavePlaylistCoverWebP(coverUrl: String): String? {
        if (coverUrl.isBlank() || !coverUrl.startsWith("http", ignoreCase = true)) return null
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(coverUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            if (conn.responseCode !in 200..299) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val savedFile = storageManager.saveCoverAsWebp(
                key = "playlist_import_${System.currentTimeMillis()}",
                bitmap = bmp
            )
            bmp.recycle()
            savedFile.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
