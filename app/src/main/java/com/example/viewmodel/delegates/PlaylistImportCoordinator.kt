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

    private val st = PlaylistImportState()
    private val analyzer = PlaylistImportAnalyzer(context, scope, st)
    private val runner = PlaylistImportRunner(
        context, trackDao, playlistDao, storageManager, scope, st,
        onStatusMessage, onOpenCreatedPlaylist
    )

    val showDialog: StateFlow<Boolean> = st.showDialog.asStateFlow()
    val inputUrl: StateFlow<String> = st.inputUrl.asStateFlow()
    val isAnalyzing: StateFlow<Boolean> = st.isAnalyzing.asStateFlow()
    val playlistPreview: StateFlow<ExternalPlaylistPreview?> = st.playlistPreview.asStateFlow()
    val customPlaylistName: StateFlow<String> = st.customPlaylistName.asStateFlow()
    val selectedSaveMode: StateFlow<PlaylistImportSaveMode> = st.selectedSaveMode.asStateFlow()
    val includeVideoCanvasOffline: StateFlow<Boolean> = st.includeVideoCanvasOffline.asStateFlow()
    val isImporting: StateFlow<Boolean> = st.isImporting.asStateFlow()
    val importProgressText: StateFlow<String?> = st.importProgressText.asStateFlow()
    val importProgressFraction: StateFlow<Float> = st.importProgressFraction.asStateFlow()
    val errorMessage: StateFlow<String?> = st.errorMessage.asStateFlow()

    init {
        // Escuchar finalización de descargas en segundo plano para vincular cada pista descargada a su Playlist
        scope.launch(Dispatchers.IO) {
            AuraDownloadService.completedEvents.collect { event ->
                val vid = event.sourceVideoId
                if (!vid.isNullOrBlank()) {
                    val targetPlaylistId = st.pendingMap.remove(vid)
                    if (targetPlaylistId != null && event.track.id > 0L) {
                        runner.linkTrack(targetPlaylistId, event.track.id)
                    }
                }
            }
        }
        scope.launch(Dispatchers.IO) {
            AuraDownloadService.taskFinishedEvents.collect { event ->
                val vid = event.sourceVideoId
                if (!event.success && !vid.isNullOrBlank()) {
                    st.pendingMap.remove(vid)
                }
            }
        }
    }

    fun openDialog(prefilledUrl: String = "") {
        st.errorMessage.value = null
        st.showDialog.value = true
        if (prefilledUrl.isNotBlank()) {
            st.inputUrl.value = prefilledUrl.trim()
            analyzer.analyze(prefilledUrl.trim())
        }
    }

    fun dismissDialog() {
        if (st.isImporting.value) return
        st.analyzeJob?.cancel()
        st.showDialog.value = false
        st.isAnalyzing.value = false
        st.errorMessage.value = null
    }

    fun updateInputUrl(url: String) {
        st.inputUrl.value = url
        st.errorMessage.value = null
    }

    fun updateCustomPlaylistName(name: String) {
        st.customPlaylistName.value = name
    }

    fun setSaveMode(mode: PlaylistImportSaveMode) {
        st.selectedSaveMode.value = mode
    }

    fun setIncludeVideoCanvasOffline(enabled: Boolean) {
        st.includeVideoCanvasOffline.value = enabled
    }

    fun analyzePlaylistUrl(urlOverride: String? = null) =
        analyzer.analyze(urlOverride)

    fun confirmImportPlaylist() =
        runner.import()
}
