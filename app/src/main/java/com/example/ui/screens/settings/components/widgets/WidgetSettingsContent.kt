package com.example.ui.screens.settings.components.widgets

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
import com.example.model.Playlist
import com.example.model.Track
import com.example.model.WidgetConfig
import com.example.model.WidgetGridContentMode
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
    item {
        Text(
            text = "CONTENIDO DEL WIDGET DE ACCESO RÁPIDO (2x2)",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.1.sp
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Elige qué mostrará el segundo Widget de buen tamaño en tu pantalla de inicio:",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )
        Spacer(modifier = Modifier.height(10.dp))

        WidgetGridContentMode.values().forEach { mode ->
            val isSelected = widgetConfig.gridContentMode == mode
            val modeIcon = if (mode == WidgetGridContentMode.TOP_SONGS) {
                Icons.Default.Whatshot
            } else {
                Icons.Default.QueueMusic
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = if (isSelected) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else {
                    BorderStroke(1.dp, CardBorder)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSetGridContentMode(mode) }
                    .testTag("widget_mode_card_${mode.name}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                else Color.White.copy(alpha = 0.06f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = modeIcon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = mode.subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }

                    if (isSelected) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Seleccionado",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // 3. Color Dinámico desde la Imagen de Carátula e Intensidad
    item {
        Text(
            text = "COLOR DINÁMICO Y ADAPTACIÓN AL ESTIRAR",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                letterSpacing = 1.1.sp
            )
        )
        Spacer(modifier = Modifier.height(10.dp))

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("widget_dynamic_color_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reaccionar a los Colores de la Imagen",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Extrae los tonos vibrantes de la carátula de cada canción para pintar el fondo Neo-Glass, el botón Play y la barra de progreso del Widget.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Switch(
                        checked = widgetConfig.isDynamicColorEnabled,
                        onCheckedChange = onSetDynamicColorEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("widget_dynamic_color_switch")
                    )
                }

                if (widgetConfig.isDynamicColorEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = CardBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Intensidad del color de la imagen",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "${widgetConfig.colorIntensityPercent}%",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Slider(
                        value = widgetConfig.colorIntensityPercent.toFloat(),
                        onValueChange = { onSetColorIntensityPercent(it.toInt()) },
                        valueRange = 35f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                        ),
                        modifier = Modifier.testTag("widget_color_intensity_slider")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }

    // 4. Barra de Progreso al Estirar el Widget + Botón de Sincronización
    item {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("widget_progress_toggle_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Barra de Progreso al Estirar el Widget",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Muestra la barra de tiempo con bolita clásica y minutos (mm:ss) cuando estiras el Widget a tamaño mediano (4x2) o grande (4x3).",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Switch(
                    checked = widgetConfig.showProgressInWidget,
                    onCheckedChange = onSetShowProgressInWidget,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.testTag("widget_show_progress_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onForceSyncWidgets,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("widget_force_sync_btn")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Actualizar Widgets en Pantalla de Inicio Ahora",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
        }
    }
}

@Composable
private fun WidgetLivePreviewSection(
    widgetConfig: WidgetConfig,
    currentTrack: Track?,
    topTracks: List<Track>,
    playlists: List<Playlist>,
    dynamicPrimary: Color,
    dynamicSecondary: Color
) {
    val intensity = (widgetConfig.colorIntensityPercent / 100f).coerceIn(0.35f, 1f)
    val activePrimary = if (widgetConfig.isDynamicColorEnabled) dynamicPrimary else Color(0xFF8B5CF6)
    val activeSecondary = if (widgetConfig.isDynamicColorEnabled) dynamicSecondary else Color(0xFF06B6D4)

    val previewGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF131B2E).copy(alpha = 1f),
            activePrimary.copy(alpha = 0.36f * intensity),
            activeSecondary.copy(alpha = 0.28f * intensity)
        )
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VISTA PREVIA EN VIVO",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.1.sp
                )
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = activePrimary.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, activePrimary.copy(alpha = 0.45f))
            ) {
                Text(
                    text = if (widgetConfig.isDynamicColorEnabled) "COLOR DE IMAGEN ACTIVO" else "COLOR BASE FIJO",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Vista previa 1: Widget Reproductor Adaptativo (Estirado Mediano con Progreso y Favorito)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF0A0E17))
                .background(previewGradient)
                .border(1.dp, activePrimary.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtworkImage(
                        track = currentTrack,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${currentTrack?.formatBadge() ?: "AURA"} • C++20 DSP",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0),
                                fontSize = 9.sp
                            )
                        )
                        Text(
                            text = currentTrack?.title ?: "Aura Music",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentTrack?.artist ?: "Reanudación directa en 2do plano",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = if (currentTrack?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (currentTrack?.isFavorite == true) Color(0xFFEF4444) else Color(0xFFCBD5E1),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(activePrimary, activeSecondary))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                if (widgetConfig.showProgressInWidget) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1:24",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFE2E8F0), fontSize = 10.sp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.22f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.45f)
                                    .fillMaxHeight()
                                    .background(Brush.horizontalGradient(listOf(activePrimary, activeSecondary)))
                            )
                        }
                        Text(
                            text = "3:42",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8), fontSize = 10.sp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Vista previa 2: Segundo Widget Independiente (4 Canciones más escuchadas o 4 Playlists)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF0A0E17))
                .background(previewGradient)
                .border(1.dp, activeSecondary.copy(alpha = 0.40f), RoundedCornerShape(22.dp))
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (widgetConfig.gridContentMode == WidgetGridContentMode.TOP_SONGS) {
                        "🔥 WIDGET 2: TUS 4 CANCIONES MÁS ESCUCHADAS"
                    } else {
                        "💿 WIDGET 2: TUS 4 PLAYLISTS PRINCIPALES"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E8F0),
                        fontSize = 10.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                val previewLabels: List<Pair<String, String>> = if (widgetConfig.gridContentMode == WidgetGridContentMode.TOP_SONGS) {
                    val list = topTracks.take(4).map { it.title to it.artist }
                    if (list.size == 4) list else (list + listOf(
                        "Canción Top 1" to "Artista",
                        "Canción Top 2" to "Artista",
                        "Canción Top 3" to "Artista",
                        "Canción Top 4" to "Artista"
                    )).take(4)
                } else {
                    val list = playlists.take(4).map { it.name to "${it.trackCount} canciones" }
                    if (list.size == 4) list else (list + listOf(
                        "Tus Me Gusta" to "Favoritos",
                        "Playlist #2" to "Biblioteca",
                        "Playlist #3" to "Biblioteca",
                        "Playlist #4" to "Biblioteca"
                    )).take(4)
                }

                for (row in 0..1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (col in 0..1) {
                            val idx = row * 2 + col
                            val itemPair = previewLabels[idx]
                            val sampleTrack = topTracks.getOrNull(idx)
                            val samplePlaylist = playlists.getOrNull(idx)

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                                    .padding(7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (widgetConfig.gridContentMode == WidgetGridContentMode.PLAYLISTS && samplePlaylist != null) {
                                    PlaylistCoverCollage(
                                        playlist = samplePlaylist,
                                        modifier = Modifier.size(34.dp),
                                        cornerRadius = 8.dp
                                    )
                                } else {
                                    ArtworkImage(
                                        track = sampleTrack ?: currentTrack,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = itemPair.first,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = itemPair.second,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 9.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
