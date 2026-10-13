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
 * Hoja global de efectos: EQ, 8D, reverb, sleep, velocidad, AB y auriculares.
 * Rol arquitectónico: anfitrión de diálogo global (fachada pública intacta).
 */
@Composable
fun GlobalAudioEffectsSheet(
    viewModel: com.example.viewmodel.MusicViewModel,
    show: Boolean,
    initialTab: Int,
    onDismiss: () -> Unit,
    isEqEnabled: Boolean,
    eqBands: List<com.example.core.model.EqualizerBand>,
    bassBoostLevel: Int,
    currentPreset: com.example.core.model.EqualizerPreset,
    vocalClarityConfig: com.example.core.model.VocalClarityConfig,
    spatial8DConfig: com.example.core.model.Spatial8DConfig,
    reverbConfig: com.example.core.model.ReverbConfig,
    sleepTimerState: com.example.core.model.SleepTimerState,
    playbackSpeed: Float,
    playbackPitch: Float,
    crossfadeSeconds: Int,
    isGaplessEnabled: Boolean,
    abLoopState: com.example.core.model.ABLoopState,
    headphoneConfig: com.example.core.model.HeadphoneConfig
) {
    // 2. Hoja modal de efectos de audio global (Ecualizador, 8D/16D, Reverb, etc.)
    if (show) {
        val eqScopeMode by viewModel.eqScopeMode.collectAsState()
        AudioEffectsBottomSheet(
            onDismissRequest = onDismiss,
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
            initialTab = initialTab
        )
    }
}
