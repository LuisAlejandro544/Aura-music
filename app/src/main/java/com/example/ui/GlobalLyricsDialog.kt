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
 * Búsqueda y edición de letras LRCLIB.
 * Rol arquitectónico: anfitrión de diálogo global (fachada pública intacta).
 */
@Composable
fun GlobalLyricsDialog(
    viewModel: com.example.viewmodel.MusicViewModel,
    isOpen: Boolean,
    isSearching: Boolean,
    results: List<com.example.core.model.LyricSearchResult>,
    error: String?
) {
    // 1. Modal de Búsqueda y Edición de Letras (.LRC / LRCLIB)
    val currentTrackForLyrics = viewModel.currentTrack.collectAsState().value
    val currentTheme = viewModel.currentTheme.collectAsState().value
    val accentColor = currentTheme.primaryColor
    if (isOpen && currentTrackForLyrics != null) {
        SearchLyricsDialog(
            currentTrack = currentTrackForLyrics,
            isSearching = isSearching,
            searchResults = results,
            searchError = error,
            accentColor = accentColor,
            onDismissRequest = { viewModel.closeSearchLyricsDialog() },
            onSearch = { t, a -> viewModel.searchLyricsOptions(t, a) },
            onSelectResult = { viewModel.selectLyricSearchResult(it) }
        )
    }
}
