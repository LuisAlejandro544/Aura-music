package com.example.ui.components.audioeffects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.EqualizerBand
import com.example.core.model.EqualizerPreset
import com.example.core.model.EqualizerScopeMode
import com.example.core.model.VocalClarityConfig
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pestaña 0 de Efectos de Audio: Ecualizador Paramétrico de 10 Bandas ISO en C++20 y Clarificador Vocal HD.
 * Arquitectura: Componente modular de UI que presenta:
 * - Switch maestro de activación del motor DSP C++20 (desactivado por defecto).
 * - Selector de alcance de cambios: "Solo esta canción" vs "Para todas las siguientes".
 * - Selector horizontal de perfiles acústicos (Presets: Rock, Pop, Jazz, etc.).
 * - Control deslizante de refuerzo dinámico de graves (Bass Boost a 60 Hz).
 * - Clarificador de Voces HD en C++20 (aislamiento Mid-Side y realce de presencia vocal).
 * - 10 deslizadores paramétricos ISO (-15 dB a +15 dB) con filtros IIR Bi-cuadráticos de doble precisión.
 */
@Composable
fun EqualizerTabContent(
    isEnabled: Boolean,
    bands: List<EqualizerBand>,
    bassBoostLevel: Int,
    currentPreset: EqualizerPreset,
    onToggleEnabled: (Boolean) -> Unit,
    onBandLevelChange: (Int, Int) -> Unit,
    onBassBoostChange: (Int) -> Unit,
    onPresetSelect: (EqualizerPreset) -> Unit,
    modifier: Modifier = Modifier,
    eqScopeMode: EqualizerScopeMode = EqualizerScopeMode.GLOBAL_ALL_TRACKS,
    onEqScopeModeChange: (EqualizerScopeMode) -> Unit = {},
    vocalClarityConfig: VocalClarityConfig = VocalClarityConfig(),
    onVocalClarityEnabledChange: (Boolean) -> Unit = {},
    onVocalClarityStrengthChange: (Float) -> Unit = {}
) {
    val displayBands = if (bands.isNotEmpty()) bands else EqualizerPreset.DEFAULT_10_BANDS

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Switch Maestro e Indicador + Selector de Alcance ("Solo esta canción" vs "Para todas las siguientes")
        EqualizerMasterSwitch(
            isEnabled = isEnabled,
            onToggleEnabled = onToggleEnabled,
            eqScopeMode = eqScopeMode,
            onEqScopeModeChange = onEqScopeModeChange
        )

        // Presets Acústicos
        EqualizerPresetsRow(
            currentPreset = currentPreset,
            onPresetSelect = onPresetSelect
        )

        // Control de Refuerzo de Bajos (Bass Boost)
        EqualizerBassBoost(
            bassBoostLevel = bassBoostLevel,
            isEnabled = isEnabled,
            onBassBoostChange = onBassBoostChange
        )

        // Control Estratégico: Clarificador de Voces HD (C++20 Mid-Side)
        VocalClarityControl(
            config = vocalClarityConfig,
            onEnabledChange = onVocalClarityEnabledChange,
            onStrengthChange = onVocalClarityStrengthChange
        )

        // Bandas de Frecuencia (10 Bandas ISO)
        EqualizerBandsGrid(
            displayBands = displayBands,
            isEnabled = isEnabled,
            onBandLevelChange = onBandLevelChange
        )
    }
}

