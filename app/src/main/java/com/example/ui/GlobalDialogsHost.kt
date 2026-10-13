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
 * Hospedador modular de diálogos globales y hojas modulares para Aura Music.
 * Desacopla de MainActivity la gestión de AudioEffectsBottomSheet, VideoToMusicDialog,
 * DownloadFromLinkDialog, SearchLyricsDialog y los modales interactivos de medios entrantes.
 */
@Composable
fun GlobalDialogsHost(
    viewModel: MusicViewModel,
    showGlobalAudioEffectsSheet: Boolean,
    initialAudioEffectsTab: Int,
    onDismissAudioEffectsSheet: () -> Unit,
    pendingIncomingAudioUris: List<Uri>?,
    pendingIncomingVideoUri: Uri?,
    pendingIncomingWebLink: String?,
    downloadProgress: DownloadProgress,
    isSearchLyricsDialogOpen: Boolean,
    isSearchingLyrics: Boolean,
    lyricsSearchResults: List<com.example.core.model.LyricSearchResult>,
    searchLyricsError: String?,
    eqBands: List<com.example.core.model.EqualizerBand>,
    bassBoostLevel: Int,
    currentPreset: com.example.core.model.EqualizerPreset,
    isEqEnabled: Boolean,
    spatial8DConfig: com.example.core.model.Spatial8DConfig,
    vocalClarityConfig: com.example.core.model.VocalClarityConfig,
    reverbConfig: com.example.core.model.ReverbConfig,
    sleepTimerState: SleepTimerState,
    playbackSpeed: Float,
    playbackPitch: Float,
    crossfadeSeconds: Int,
    isGaplessEnabled: Boolean,
    abLoopState: com.example.core.model.ABLoopState,
    headphoneConfig: HeadphoneConfig
) {
    // 0. Modal de Actualización Automática de APK (GitHub Pre-Releases -beta / Releases)
    GlobalAppUpdateDialog()

    // 1. Modal de Búsqueda y Edición de Letras (.LRC / LRCLIB)
    GlobalLyricsDialog(
        viewModel = viewModel,
        isOpen = isSearchLyricsDialogOpen,
        isSearching = isSearchingLyrics,
        results = lyricsSearchResults,
        error = searchLyricsError
    )

    // 2. Hoja modal de efectos de audio global (Ecualizador, 8D/16D, Reverb, etc.)
    GlobalAudioEffectsSheet(
        viewModel = viewModel,
        show = showGlobalAudioEffectsSheet,
        initialTab = initialAudioEffectsTab,
        onDismiss = onDismissAudioEffectsSheet,
        isEqEnabled = isEqEnabled,
        eqBands = eqBands,
        bassBoostLevel = bassBoostLevel,
        currentPreset = currentPreset,
        vocalClarityConfig = vocalClarityConfig,
        spatial8DConfig = spatial8DConfig,
        reverbConfig = reverbConfig,
        sleepTimerState = sleepTimerState,
        playbackSpeed = playbackSpeed,
        playbackPitch = playbackPitch,
        crossfadeSeconds = crossfadeSeconds,
        isGaplessEnabled = isGaplessEnabled,
        abLoopState = abLoopState,
        headphoneConfig = headphoneConfig
    )

    // 3. Diálogo de audio entrante ("Abrir con..." / "Compartir con...")
    GlobalIncomingAudioDialog(
        viewModel = viewModel,
        uris = pendingIncomingAudioUris
    )

    // 4. Diálogo de video entrante ("Video a Música")
    GlobalIncomingVideoDialog(
        viewModel = viewModel,
        videoUri = pendingIncomingVideoUri
    )

    // 5. Diálogo de enlace web entrante ("DownloadFromLinkDialog")
    GlobalWebLinkDialog(
        viewModel = viewModel,
        webLink = pendingIncomingWebLink,
        downloadProgress = downloadProgress
    )

    // 6. Tarjeta flotante de progreso de descarga en segundo plano
    GlobalDownloadProgressCard(
        downloadProgress = downloadProgress,
        isWebLinkActive = pendingIncomingWebLink != null
    )

    // 7. Modal del Importador Universal de Playlists (Spotify y YouTube Music / YouTube)
    GlobalPlaylistImportDialog(viewModel = viewModel)
}
