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
import kotlinx.coroutines.launch

/**
 * Modal de actualización APK desde Pre-Releases.
 * Rol arquitectónico: anfitrión de diálogo global (fachada pública intacta).
 */
@Composable
fun GlobalAppUpdateDialog() {
    // 0. Modal de Actualización Automática de APK (GitHub Pre-Releases -beta / Releases)
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val appUpdateState by AppReleaseUpdater.updateState.collectAsState()
    val dialogScope = rememberCoroutineScope()
    if (appUpdateState is AppUpdateState.UpdateAvailable ||
        appUpdateState is AppUpdateState.Downloading ||
        appUpdateState is AppUpdateState.ReadyToInstall
    ) {
        val currentReleaseInfo = when (val st = appUpdateState) {
            is AppUpdateState.UpdateAvailable -> st.releaseInfo
            is AppUpdateState.Downloading -> st.releaseInfo
            is AppUpdateState.ReadyToInstall -> st.releaseInfo
            else -> null
        }
        AppUpdateDialog(
            updateState = appUpdateState,
            onStartDownload = {
                currentReleaseInfo?.let { info ->
                    dialogScope.launch {
                        AppReleaseUpdater.downloadAndInstallUpdate(appContext, info)
                    }
                }
            },
            onDismiss = { tagToSkip ->
                AppReleaseUpdater.dismissCurrentUpdateDialog(appContext, tagToSkip)
            }
        )
    }
}
