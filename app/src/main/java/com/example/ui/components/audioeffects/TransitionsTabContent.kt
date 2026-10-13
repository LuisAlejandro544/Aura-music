package com.example.ui.components.audioeffects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.ABLoopState
import com.example.feature.nowplaying.components.formatPlaybackTime
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Pestaña 6 de Efectos de Audio: Transiciones Suaves (Crossfade), Reproducción Gapless y Repetidor de Segmento A-B.
 * Arquitectura: Componente modular de UI que presenta:
 * - Panel completo del Repetidor de Segmento A-B con fijación en vivo, ajuste fino (-1s / +1s) y limpieza.
 * - Deslizador de duración de fundido cruzado (0 a 12 segundos) entre canciones continuas.
 * - Conmutador de reproducción Gapless para eliminar silencios en conciertos y álbumes continuos.
 */
@Composable
fun TransitionsTabContent(
    crossfadeSeconds: Int,
    onCrossfadeChange: (Int) -> Unit,
    isGapless: Boolean,
    onGaplessToggle: (Boolean) -> Unit,
    abLoopState: ABLoopState = ABLoopState(),
    onMarkABPointA: () -> Unit = {},
    onMarkABPointB: () -> Unit = {},
    onToggleABLoopEnabled: (Boolean) -> Unit = {},
    onAdjustABPointA: (Long) -> Unit = {},
    onAdjustABPointB: (Long) -> Unit = {},
    onClearABLoop: () -> Unit = {},
    isDjAutomixEnabled: Boolean = false,
    onDjAutomixToggle: (Boolean) -> Unit = {},
    isDjEqCurveEnabled: Boolean = true,
    onDjEqCurveToggle: (Boolean) -> Unit = {},
    isVolumeNormalizationEnabled: Boolean = false,
    onVolumeNormalizationToggle: (Boolean) -> Unit = {},
    volumeNormalizationMode: Int = 1,
    onVolumeNormalizationModeChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tarjeta 1: Repetidor de Segmento A-B (A-B Loop)
        TransitionABLoopCard(
            abLoopState = abLoopState,
            onMarkABPointA = onMarkABPointA,
            onMarkABPointB = onMarkABPointB,
            onToggleABLoopEnabled = onToggleABLoopEnabled,
            onAdjustABPointA = onAdjustABPointA,
            onAdjustABPointB = onAdjustABPointB,
            onClearABLoop = onClearABLoop
        )

        // Tarjeta 2: Smart AutoMix DJ (Música Local) & Crossfade Dual-Deck con Curva Armónica
        TransitionAutomixCard(
            crossfadeSeconds = crossfadeSeconds,
            onCrossfadeChange = onCrossfadeChange,
            isGapless = isGapless,
            onGaplessToggle = onGaplessToggle,
            isDjAutomixEnabled = isDjAutomixEnabled,
            onDjAutomixToggle = onDjAutomixToggle,
            isDjEqCurveEnabled = isDjEqCurveEnabled,
            onDjEqCurveToggle = onDjEqCurveToggle
        )

        // Tarjeta 3: Normalización de Volumen Inteligente (Loudness Normalizer EBU R128)
        TransitionNormalizationCard(
            isVolumeNormalizationEnabled = isVolumeNormalizationEnabled,
            onVolumeNormalizationToggle = onVolumeNormalizationToggle,
            volumeNormalizationMode = volumeNormalizationMode,
            onVolumeNormalizationModeChange = onVolumeNormalizationModeChange
        )
    }
}
