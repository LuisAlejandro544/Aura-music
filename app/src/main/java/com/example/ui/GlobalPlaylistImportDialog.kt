package com.example.ui
import com.example.core.model.LyricSearchResult

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.data.updater.AppReleaseUpdater
import com.example.core.model.AppUpdateState
import com.example.core.model.DownloadProgress
import com.example.core.model.HeadphoneConfig
import com.example.core.model.SleepTimerState
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.AudioEffectsBottomSheet
import com.example.ui.components.DownloadFromLinkDialog
import com.example.ui.components.SearchLyricsDialog
import com.example.ui.components.VideoToMusicDialog
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.MusicViewModel

/**
 * Importador universal de playlists Spotify/YouTube.
 * Rol arquitectónico: anfitrión de diálogo global (fachada pública intacta).
 */
@Composable
fun GlobalPlaylistImportDialog(
    viewModel: com.example.viewmodel.MusicViewModel
) {
    // 7. Modal del Importador Universal de Playlists (Spotify y YouTube Music / YouTube)
    val showPlaylistImportDialog by viewModel.playlistImportCoordinator.showDialog.collectAsState()
    if (showPlaylistImportDialog) {
        val inputUrl by viewModel.playlistImportCoordinator.inputUrl.collectAsState()
        val isAnalyzing by viewModel.playlistImportCoordinator.isAnalyzing.collectAsState()
        val playlistPreview by viewModel.playlistImportCoordinator.playlistPreview.collectAsState()
        val customPlaylistName by viewModel.playlistImportCoordinator.customPlaylistName.collectAsState()
        val selectedSaveMode by viewModel.playlistImportCoordinator.selectedSaveMode.collectAsState()
        val includeVideoCanvasOffline by viewModel.playlistImportCoordinator.includeVideoCanvasOffline.collectAsState()
        val isImportingPlaylist by viewModel.playlistImportCoordinator.isImporting.collectAsState()
        val importProgressText by viewModel.playlistImportCoordinator.importProgressText.collectAsState()
        val importProgressFraction by viewModel.playlistImportCoordinator.importProgressFraction.collectAsState()
        val errorMessage by viewModel.playlistImportCoordinator.errorMessage.collectAsState()

        com.example.ui.components.dialogs.ImportExternalPlaylistDialog(
            inputUrl = inputUrl,
            isAnalyzing = isAnalyzing,
            playlistPreview = playlistPreview,
            customPlaylistName = customPlaylistName,
            selectedSaveMode = selectedSaveMode,
            includeVideoCanvasOffline = includeVideoCanvasOffline,
            isImporting = isImportingPlaylist,
            importProgressText = importProgressText,
            importProgressFraction = importProgressFraction,
            errorMessage = errorMessage,
            onUrlChange = { viewModel.playlistImportCoordinator.updateInputUrl(it) },
            onCustomNameChange = { viewModel.playlistImportCoordinator.updateCustomPlaylistName(it) },
            onAnalyzeClick = { viewModel.playlistImportCoordinator.analyzePlaylistUrl() },
            onSaveModeChange = { viewModel.playlistImportCoordinator.setSaveMode(it) },
            onIncludeVideoCanvasChange = { viewModel.playlistImportCoordinator.setIncludeVideoCanvasOffline(it) },
            onConfirmImportClick = { viewModel.playlistImportCoordinator.confirmImportPlaylist() },
            onDismiss = { viewModel.playlistImportCoordinator.dismissDialog() }
        )
    }

    // Fase B: tarjeta global del lote 1-en-1 (visible aunque el diálogo ya se cerró),
    // con posición de cola, pausa sin red y botón Cancelar.
    val batchActive by viewModel.playlistImportCoordinator.batchActive.collectAsState()
    if (batchActive && !showPlaylistImportDialog) {
        val batchTotal by viewModel.playlistImportCoordinator.batchTotal.collectAsState()
        val batchDone by viewModel.playlistImportCoordinator.batchDone.collectAsState()
        val batchFailed by viewModel.playlistImportCoordinator.batchFailed.collectAsState()
        val batchName by viewModel.playlistImportCoordinator.batchPlaylistName.collectAsState()
        val batchWaiting by viewModel.playlistImportCoordinator.batchWaitingForNetwork.collectAsState()
        val queuedGlobal by viewModel.playlistImportCoordinator.downloadQueueDepth.collectAsState()
        PlaylistBatchProgressCard(
            playlistName = batchName,
            done = batchDone,
            total = batchTotal,
            failed = batchFailed,
            queuedGlobal = queuedGlobal,
            waitingForNetwork = batchWaiting,
            onCancel = { viewModel.playlistImportCoordinator.cancelBatchDownload() }
        )
    }
}
