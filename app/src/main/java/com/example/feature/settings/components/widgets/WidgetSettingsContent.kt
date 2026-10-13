package com.example.feature.settings.components.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.Playlist
import com.example.core.model.Track
import com.example.core.model.WidgetConfig
import com.example.core.model.WidgetGridContentMode
import com.example.ui.components.ArtworkImage
import com.example.ui.components.PlaylistCoverCollage
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pantalla independiente dentro de Ajustes ("Widget") para personalizar el comportamiento,
 * colores dinámicos desde la imagen y contenido (4 Canciones más escuchadas vs 4 Playlists)
 * de los Widgets de escritorio de Aura Music, incluyendo una Vista Previa en Vivo interactiva.
 */
fun LazyListScope.widgetSettingsContent(
    widgetConfig: WidgetConfig,
    currentTrack: Track?,
    topTracks: List<Track>,
    playlists: List<Playlist>,
    dynamicPrimaryColor: Color,
    dynamicSecondaryColor: Color,
    onSetDynamicColorEnabled: (Boolean) -> Unit,
    onSetGridContentMode: (WidgetGridContentMode) -> Unit,
    onSetShowProgressInWidget: (Boolean) -> Unit,
    onSetColorIntensityPercent: (Int) -> Unit,
    onForceSyncWidgets: () -> Unit
) {
    // 1. Vista Previa Interactiva en Vivo de ambos Widgets
    item {
        WidgetLivePreviewSection(
            widgetConfig = widgetConfig,
            currentTrack = currentTrack,
            topTracks = topTracks,
            playlists = playlists,
            dynamicPrimary = dynamicPrimaryColor,
            dynamicSecondary = dynamicSecondaryColor
        )
        Spacer(modifier = Modifier.height(18.dp))
    }

    // 2. Selector de Modo del Widget Grande (4 Canciones más escuchadas vs 4 Playlists)
    widgetGridModeSection(
        widgetConfig = widgetConfig,
        onSetGridContentMode = onSetGridContentMode
    )

    // 3. Color Dinámico desde la Imagen de Carátula e Intensidad
    widgetDynamicColorSection(
        widgetConfig = widgetConfig,
        onSetDynamicColorEnabled = onSetDynamicColorEnabled,
        onSetColorIntensityPercent = onSetColorIntensityPercent
    )

    // 4. Barra de Progreso al Estirar el Widget + Botón de Sincronización
    widgetProgressSection(
        widgetConfig = widgetConfig,
        onSetShowProgressInWidget = onSetShowProgressInWidget,
        onForceSyncWidgets = onForceSyncWidgets
    )
}

