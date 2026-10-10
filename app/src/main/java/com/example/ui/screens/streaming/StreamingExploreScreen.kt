package com.example.ui.screens.streaming

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.StreamingCacheConfig
import com.example.model.StreamingSearchFilter
import com.example.model.StreamingSearchItem
import com.example.model.StreamingSourcePlatform
import com.example.model.Track
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pantalla modular de Exploración y Modo Streaming Híbrido (Estilo Spotify).
 *
 * Rol Arquitectónico:
 * - Permite buscar canciones y videos musicales en tiempo real mediante API (sin usar yt-dlp para buscar)
 *   diferenciando claramente entre YouTube Music (`🎵 YT MUSIC`) y YouTube (`▶️ YOUTUBE`).
 * - Al tocar cualquier pista, la resuelve con `yt-dlp` / `InnerTube` para reproducirla en streaming
 *   con letras sincronizadas (.LRC), Video Canvas según la política de Wi-Fi/Datos y pre-carga no lineal
 *   en la caché temporal inteligente (50 MB - 500 MB, 30 min TTL).
 * - Muestra la siguiente canción similar que la API y `yt-dlp` están preparando automáticamente
 *   antes de que termine la canción actual.
 * - Incluye un botón directo de descarga permanente (`⬇️`) en cada tarjeta para guardar la canción
 *   en la biblioteca local (`songs/`, `images/`, `videos/`, `lyrics/`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StreamingExploreScreen(
    searchQuery: String,
    selectedFilter: StreamingSearchFilter,
    searchResults: List<StreamingSearchItem>,
    isSearching: Boolean,
    isResolvingStream: Boolean,
    resolvingVideoId: String?,
    currentTrack: Track?,
    nextSimilarTrackPreview: StreamingSearchItem?,
    cacheConfig: StreamingCacheConfig,
    downloadingVideoIds: Set<String>,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onFilterChange: (StreamingSearchFilter) -> Unit,
    onPlayStreamingItem: (StreamingSearchItem) -> Unit,
    onDownloadStreamingItem: (StreamingSearchItem) -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp)
            .testTag("streaming_explore_screen")
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Encabezado con estado de Caché Temporal y Política de Video Canvas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Explorar & Streaming",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Búsqueda API dual (YT Music & YouTube) + Radio + Caché 30 min",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Surface(
                color = SurfaceVariantDark,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${cacheConfig.usedMbFormatted} / ${cacheConfig.maxCacheSizeMb} MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Barra de Búsqueda Unificada
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = "Buscar canciones, artistas o videos en YT Music y YouTube...",
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Buscar",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = {
                                onSearchQueryChange("")
                                onSearchSubmit("")
                            },
                            modifier = Modifier.testTag("clear_streaming_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Limpiar búsqueda",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                    onSearchSubmit(searchQuery)
                }
            ),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceCard,
                unfocusedContainerColor = SurfaceCard,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("streaming_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filtros de Origen: Todo, Solo YT Music, Solo YouTube + Sugerencias rápidas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StreamingSearchFilter.values().forEach { filter ->
                val isSelected = selectedFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterChange(filter) },
                    label = {
                        Text(
                            text = filter.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        containerColor = SurfaceCard,
                        labelColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("streaming_filter_${filter.name.lowercase()}")
                )
            }
        }

        // Tarjeta de Radio / Siguiente canción similar pre-cargada por API + yt-dlp
        if (nextSimilarTrackPreview != null && currentTrack?.isStreamingTrack == true) {
            Spacer(modifier = Modifier.height(8.dp))
            SimilarTrackRadioBanner(
                similarItem = nextSimilarTrackPreview,
                onPlayNow = { onPlayStreamingItem(nextSimilarTrackPreview) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isSearching && searchResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Consultando catálogo de YouTube Music & YouTube...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("streaming_results_list"),
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = searchResults,
                    key = { "${it.platform.id}_${it.videoId}" }
                ) { item ->
                    val isCurrentlyPlaying = currentTrack?.streamingVideoId == item.videoId
                    val isBeingResolved = isResolvingStream && resolvingVideoId == item.videoId
                    val isDownloading = downloadingVideoIds.contains(item.videoId)

                    StreamingResultCard(
                        item = item,
                        isCurrentlyPlaying = isCurrentlyPlaying,
                        isBeingResolved = isBeingResolved,
                        isDownloading = isDownloading,
                        onPlayClick = {
                            if (isCurrentlyPlaying) {
                                onOpenNowPlaying()
                            } else {
                                onPlayStreamingItem(item)
                            }
                        },
                        onDownloadClick = { onDownloadStreamingItem(item) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SimilarTrackRadioBanner(
    similarItem: StreamingSearchItem,
    onPlayNow: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF15192B)),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onPlayNow() }
            .testTag("similar_track_radio_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Radio,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "SIGUIENTE SIMILAR LISTA EN CACHÉ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp
                    )
                    StreamingPlatformBadge(platform = similarItem.platform)
                }
                Text(
                    text = "${similarItem.title} • ${similarItem.artist}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Reproducir siguiente similar ahora",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StreamingResultCard(
    item: StreamingSearchItem,
    isCurrentlyPlaying: Boolean,
    isBeingResolved: Boolean,
    isDownloading: Boolean,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    val borderColor = if (isCurrentlyPlaying) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.White.copy(alpha = 0.07f)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyPlaying) Color(0xFF191D30) else SurfaceCard
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onPlayClick() }
            .testTag("streaming_item_${item.videoId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Miniatura con indicador de carga o reproducción
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceVariantDark),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (isBeingResolved) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else if (isCurrentlyPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = "Reproduciendo en streaming",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Información de la canción + Insignia de Plataforma (YT MUSIC vs YOUTUBE)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StreamingPlatformBadge(platform = item.platform)
                    Text(
                        text = item.formattedDuration(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )

                Text(
                    text = item.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Botón de Descarga Permanente en Biblioteca Local (mínimo 48dp de área táctil)
            IconButton(
                onClick = onDownloadClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDownloading) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else SurfaceVariantDark
                    )
                    .testTag("download_streaming_${item.videoId}")
            ) {
                Icon(
                    imageVector = if (isDownloading) Icons.Filled.CheckCircle else Icons.Filled.CloudDownload,
                    contentDescription = "Descargar canción para siempre",
                    tint = if (isDownloading) MaterialTheme.colorScheme.primary else TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Insignia distintiva para diferenciar visualmente entre YouTube Music (`🎵 YT MUSIC`) y YouTube (`▶️ YOUTUBE`).
 */
@Composable
fun StreamingPlatformBadge(
    platform: StreamingSourcePlatform,
    modifier: Modifier = Modifier
) {
    val isYtMusic = platform == StreamingSourcePlatform.YT_MUSIC
    val bgColor = if (isYtMusic) Color(0xFF9C27B0).copy(alpha = 0.24f) else Color(0xFFE53935).copy(alpha = 0.24f)
    val accentColor = if (isYtMusic) Color(0xFFEA80FC) else Color(0xFFFF8A80)
    val icon = if (isYtMusic) Icons.Filled.MusicNote else Icons.Filled.SmartDisplay

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.border(
            width = 0.8.dp,
            color = accentColor.copy(alpha = 0.6f),
            shape = RoundedCornerShape(6.dp)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = platform.label,
                tint = accentColor,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = platform.badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.5.sp
            )
        }
    }
}
