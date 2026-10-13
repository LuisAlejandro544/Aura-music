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
 * Video entrante hacia Video a Música.
 * Rol arquitectónico: anfitrión de diálogo global (fachada pública intacta).
 */
@Composable
fun GlobalIncomingVideoDialog(
    viewModel: com.example.viewmodel.MusicViewModel,
    videoUri: android.net.Uri?
) {
    // 4. Diálogo de video entrante ("Video a Música")
    if (videoUri != null) {
        VideoToMusicDialog(
            videoUri = videoUri,
            onDismiss = { viewModel.clearPendingIncomingVideo() },
            onConfirm = { title, artist, album, attachAsCanvas, forceLoop, trimSilence ->
                viewModel.importVideoAsTrack(
                    videoUri = videoUri,
                    title = title,
                    artist = artist,
                    album = album,
                    attachAsCanvas = attachAsCanvas,
                    forceLoop = forceLoop,
                    trimSilence = trimSilence
                ) { track ->
                    viewModel.playTrack(track)
                    viewModel.setNowPlayingExpanded(true)
                }
                viewModel.clearPendingIncomingVideo()
            },
            onConfirmWithLoopStyle = { title, artist, album, attachAsCanvas, forceLoop, trimSilence, loopStyle ->
                viewModel.importVideoAsTrack(
                    videoUri = videoUri,
                    title = title,
                    artist = artist,
                    album = album,
                    attachAsCanvas = attachAsCanvas,
                    forceLoop = forceLoop,
                    trimSilence = trimSilence,
                    loopStyle = loopStyle
                ) { track ->
                    viewModel.playTrack(track)
                    viewModel.setNowPlayingExpanded(true)
                }
                viewModel.clearPendingIncomingVideo()
            }
        )
    }
}
