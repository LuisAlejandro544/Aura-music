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
 * Enlace web entrante con seguimiento de descarga.
 * Rol arquitectónico: anfitrión de diálogo global (fachada pública intacta).
 */
@Composable
fun GlobalWebLinkDialog(
    viewModel: com.example.viewmodel.MusicViewModel,
    webLink: String?,
    downloadProgress: com.example.core.model.DownloadProgress
) {
    // 5. Diálogo de enlace web entrante ("DownloadFromLinkDialog")
    var wasDownloadingIncomingWebLink by remember { mutableStateOf(false) }
    LaunchedEffect(downloadProgress.isDownloading) {
        if (downloadProgress.isDownloading && webLink != null) {
            wasDownloadingIncomingWebLink = true
        } else if (!downloadProgress.isDownloading && wasDownloadingIncomingWebLink) {
            wasDownloadingIncomingWebLink = false
            viewModel.clearPendingIncomingWebLink()
        }
    }

    if (webLink != null) {
        DownloadFromLinkDialog(
            initialUrl = webLink,
            downloadProgress = downloadProgress,
            onDismiss = {
                wasDownloadingIncomingWebLink = false
                viewModel.clearPendingIncomingWebLink()
            },
            onConfirmDownload = { resolvedInfo, title, artist, attachAsCanvas, trimSilence ->
                wasDownloadingIncomingWebLink = true
                viewModel.importFromWebVideoLink(
                    resolvedInfo = resolvedInfo,
                    customTitle = title,
                    customArtist = artist,
                    attachAsCanvas = attachAsCanvas,
                    trimSilence = trimSilence
                ) { track ->
                    viewModel.playTrack(track)
                    viewModel.setNowPlayingExpanded(true)
                }
            },
            onConfirmDownloadWithLoopStyle = { resolvedInfo, title, artist, attachAsCanvas, trimSilence, loopStyle ->
                wasDownloadingIncomingWebLink = true
                viewModel.importFromWebVideoLink(
                    resolvedInfo = resolvedInfo,
                    customTitle = title,
                    customArtist = artist,
                    attachAsCanvas = attachAsCanvas,
                    trimSilence = trimSilence,
                    loopStyle = loopStyle
                ) { track ->
                    viewModel.playTrack(track)
                    viewModel.setNowPlayingExpanded(true)
                }
            }
        )
    }
}
