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

/**
 * Estado reactivo compartido del importador de playlists.
 * Rol arquitectónico: pieza de PlaylistImportCoordinator (fachada pública intacta).
 */
internal class PlaylistImportState {
    val showDialog = kotlinx.coroutines.flow.MutableStateFlow(false)
    val inputUrl = kotlinx.coroutines.flow.MutableStateFlow("")
    val isAnalyzing = kotlinx.coroutines.flow.MutableStateFlow(false)
    val playlistPreview = kotlinx.coroutines.flow.MutableStateFlow<com.example.core.model.ExternalPlaylistPreview?>(null)
    val customPlaylistName = kotlinx.coroutines.flow.MutableStateFlow("")
    val selectedSaveMode = kotlinx.coroutines.flow.MutableStateFlow(com.example.core.model.PlaylistImportSaveMode.STREAMING_INSTANT)
    val includeVideoCanvasOffline = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isImporting = kotlinx.coroutines.flow.MutableStateFlow(false)
    val importProgressText = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val importProgressFraction = kotlinx.coroutines.flow.MutableStateFlow(0f)
    val errorMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    var analyzeJob: kotlinx.coroutines.Job? = null
    var importJob: kotlinx.coroutines.Job? = null

    // Mapa de videoIds encolados para descarga offline -> playlistId destino en Room
    val pendingMap = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // === Fase A: orquestador serie 1-en-1 (un lote a la vez) ===
    // Job del lote offline en curso; permite botón "Cancelar lote" sin matar descargas sueltas.
    var currentBatchJob: kotlinx.coroutines.Job? = null
    // Progreso del lote visible aunque el diálogo ya se haya cerrado.
    val batchTotal = kotlinx.coroutines.flow.MutableStateFlow(0)
    val batchDone = kotlinx.coroutines.flow.MutableStateFlow(0)
    val batchFailed = kotlinx.coroutines.flow.MutableStateFlow(0)
    val batchActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    val batchPlaylistName = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    // === Fase B: cola global persistente + pausa sin red ===
    // Playlist Room destino del lote activo (para filtrar su cancelación sin tocar descargas sueltas).
    var batchPlaylistId: Long = -1L
    // true mientras el lote espera recuperar la red (pausa cooperativa, no fallo).
    val batchWaitingForNetwork = kotlinx.coroutines.flow.MutableStateFlow(false)
}
