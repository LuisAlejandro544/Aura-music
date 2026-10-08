package com.example.ui

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
import com.example.data.updater.AppReleaseUpdater
import com.example.model.AppUpdateState
import com.example.model.DownloadProgress
import com.example.model.HeadphoneConfig
import com.example.model.SleepTimerState
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
    lyricsSearchResults: List<com.example.model.LyricSearchResult>,
    searchLyricsError: String?,
    eqBands: List<com.example.model.EqualizerBand>,
    bassBoostLevel: Int,
    currentPreset: com.example.model.EqualizerPreset,
    isEqEnabled: Boolean,
    spatial8DConfig: com.example.model.Spatial8DConfig,
    vocalClarityConfig: com.example.model.VocalClarityConfig,
    reverbConfig: com.example.model.ReverbConfig,
    sleepTimerState: SleepTimerState,
    playbackSpeed: Float,
    playbackPitch: Float,
    crossfadeSeconds: Int,
    isGaplessEnabled: Boolean,
    abLoopState: com.example.model.ABLoopState,
    headphoneConfig: HeadphoneConfig
) {
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

    // 1. Modal de Búsqueda y Edición de Letras (.LRC / LRCLIB)
    val currentTrackForLyrics = viewModel.currentTrack.collectAsState().value
    val currentTheme = viewModel.currentTheme.collectAsState().value
    val accentColor = currentTheme.primaryColor
    if (isSearchLyricsDialogOpen && currentTrackForLyrics != null) {
        SearchLyricsDialog(
            currentTrack = currentTrackForLyrics,
            isSearching = isSearchingLyrics,
            searchResults = lyricsSearchResults,
            searchError = searchLyricsError,
            accentColor = accentColor,
            onDismissRequest = { viewModel.closeSearchLyricsDialog() },
            onSearch = { t, a -> viewModel.searchLyricsOptions(t, a) },
            onSelectResult = { viewModel.selectLyricSearchResult(it) }
        )
    }

    // 2. Hoja modal de efectos de audio global (Ecualizador, 8D/16D, Reverb, etc.)
    if (showGlobalAudioEffectsSheet) {
        val eqScopeMode by viewModel.eqScopeMode.collectAsState()
        AudioEffectsBottomSheet(
            onDismissRequest = onDismissAudioEffectsSheet,
            isEqEnabled = isEqEnabled,
            eqScopeMode = eqScopeMode,
            onSetEqScopeMode = { viewModel.setEqScopeMode(it) },
            eqBands = eqBands,
            bassBoostLevel = bassBoostLevel,
            currentPreset = currentPreset,
            onToggleEqEnabled = { viewModel.setEqEnabled(it) },
            onBandLevelChange = { bandIndex, levelMb -> viewModel.setBandLevel(bandIndex, levelMb) },
            onBassBoostChange = { viewModel.setBassBoost(it) },
            onPresetSelect = { viewModel.applyPreset(it) },
            vocalClarityConfig = vocalClarityConfig,
            onSetVocalClarityEnabled = { viewModel.setVocalClarityEnabled(it) },
            onSetVocalClarityStrength = { viewModel.setVocalClarityStrength(it) },
            spatial8DConfig = spatial8DConfig,
            onSet8DEnabled = { viewModel.set8DEnabled(it) },
            onSet8DMode16D = { viewModel.set8DMode16D(it) },
            onSet8DOrbitSpeed = { viewModel.set8DOrbitSpeed(it) },
            onSet8DSpatialIntensity = { viewModel.set8DSpatialIntensity(it) },
            onSet8DRoomDepth = { viewModel.set8DRoomDepth(it) },
            reverbConfig = reverbConfig,
            onSetReverbEnabled = { viewModel.setReverbEnabled(it) },
            onSetReverbPreset = { viewModel.setReverbPreset(it) },
            onSetReverbCustomParameters = { roomSize, decayMs, levelDb ->
                viewModel.setReverbCustomParameters(roomSize, decayMs, levelDb)
            },
            sleepTimerState = sleepTimerState,
            onStartSleepTimer = { viewModel.startSleepTimer(it) },
            onCancelSleepTimer = { viewModel.cancelSleepTimer() },
            onAddSleepTimerMinutes = { viewModel.addSleepTimerMinutes(it) },
            playbackSpeed = playbackSpeed,
            onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
            playbackPitch = playbackPitch,
            onSetPlaybackPitch = { viewModel.setPlaybackPitch(it) },
            onResetSpeedAndPitch = { viewModel.resetSpeedAndPitch() },
            crossfadeSeconds = crossfadeSeconds,
            onSetCrossfadeSeconds = { viewModel.setCrossfadeSeconds(it) },
            isGaplessEnabled = isGaplessEnabled,
            onSetGaplessEnabled = { viewModel.setGaplessEnabled(it) },
            isDjAutomixEnabled = viewModel.isDjAutomixEnabled.collectAsState().value,
            onSetDjAutomixEnabled = { viewModel.setDjAutomixEnabled(it) },
            isDjEqCurveEnabled = viewModel.isDjEqCurveEnabled.collectAsState().value,
            onSetDjEqCurveEnabled = { viewModel.setDjEqCurveEnabled(it) },
            volumeNormalizationConfig = viewModel.volumeNormalizationConfig.collectAsState().value,
            onSetVolumeNormalizationEnabled = { viewModel.setVolumeNormalizationEnabled(it) },
            onSetVolumeNormalizationMode = { viewModel.setVolumeNormalizationMode(it) },
            abLoopState = abLoopState,
            onMarkABPointA = { viewModel.markABPointA() },
            onMarkABPointB = { viewModel.markABPointB() },
            onToggleABLoopEnabled = { viewModel.toggleABLoopEnabled(it) },
            onAdjustABPointA = { viewModel.adjustABPointA(it) },
            onAdjustABPointB = { viewModel.adjustABPointB(it) },
            onClearABLoop = { viewModel.clearABLoop() },
            headphoneConfig = headphoneConfig,
            onSetCrossfeedEnabled = { viewModel.setCrossfeedEnabled(it) },
            onSetCrossfeedStrength = { viewModel.setCrossfeedStrength(it) },
            onSetBalanceControlEnabled = { viewModel.setBalanceControlEnabled(it) },
            onSetStereoBalance = { viewModel.setStereoBalance(it) },
            initialTab = initialAudioEffectsTab
        )
    }

    // 3. Diálogo de audio entrante ("Abrir con..." / "Compartir con...")
    if (!pendingIncomingAudioUris.isNullOrEmpty()) {
        val incomingCount = pendingIncomingAudioUris.size
        var incomingTrimSilence by remember(pendingIncomingAudioUris) { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { viewModel.clearPendingIncomingAudio() },
            title = {
                Text(
                    text = if (incomingCount == 1) "Importar y Reproducir Audio" else "Importar $incomingCount Pistas de Audio",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Se guardará una copia en el almacenamiento privado de Aura Music y comenzará la reproducción.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Eliminar silencios al inicio y final",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Recorta automáticamente espacios silenciosos antes y después de la canción.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = incomingTrimSilence,
                                onCheckedChange = { incomingTrimSilence = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF10B981)
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmIncomingAudioImport(incomingTrimSilence) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Importar y Reproducir", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearPendingIncomingAudio() }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // 4. Diálogo de video entrante ("Video a Música")
    if (pendingIncomingVideoUri != null) {
        VideoToMusicDialog(
            videoUri = pendingIncomingVideoUri,
            onDismiss = { viewModel.clearPendingIncomingVideo() },
            onConfirm = { title, artist, album, attachAsCanvas, forceLoop, trimSilence ->
                viewModel.importVideoAsTrack(
                    videoUri = pendingIncomingVideoUri,
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
                    videoUri = pendingIncomingVideoUri,
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

    // 5. Diálogo de enlace web entrante ("DownloadFromLinkDialog")
    var wasDownloadingIncomingWebLink by remember { mutableStateOf(false) }
    LaunchedEffect(downloadProgress.isDownloading) {
        if (downloadProgress.isDownloading && pendingIncomingWebLink != null) {
            wasDownloadingIncomingWebLink = true
        } else if (!downloadProgress.isDownloading && wasDownloadingIncomingWebLink) {
            wasDownloadingIncomingWebLink = false
            viewModel.clearPendingIncomingWebLink()
        }
    }

    if (pendingIncomingWebLink != null) {
        DownloadFromLinkDialog(
            initialUrl = pendingIncomingWebLink,
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

    // 6. Tarjeta flotante de progreso de descarga en segundo plano
    if (downloadProgress.isDownloading && pendingIncomingWebLink == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 84.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            progress = { if (downloadProgress.totalBytes > 0) downloadProgress.progressFraction else 0f },
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = CardBorder,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = downloadProgress.phase.ifBlank { "Descargando..." },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            ),
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = downloadProgress.formattedSpeed,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { if (downloadProgress.totalBytes > 0) downloadProgress.progressFraction else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = CardBorder
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = downloadProgress.formattedProgress,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                }
            }
        }
    }
}
