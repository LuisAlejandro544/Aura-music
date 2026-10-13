package com.example.feature.streaming

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
import androidx.compose.material.icons.filled.Check
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
import com.example.core.model.StreamingCacheConfig
import com.example.core.model.StreamingSearchFilter
import com.example.core.model.StreamingSearchItem
import com.example.core.model.StreamingSourcePlatform
import com.example.core.model.Track
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import androidx.compose.foundation.layout.ColumnScope
/**
 * Lista de resultados con carga y estados por tarjeta.
 * Rol arquitectónico: ocupa el peso restante de la columna.
 */
@Composable
fun ColumnScope.StreamingResultsList(
    searchResults: List<StreamingSearchItem>,
    isSearching: Boolean,
    currentTrack: Track?,
    isResolvingStream: Boolean,
    resolvingVideoId: String?,
    downloadingVideoIds: Set<String>,
    downloadedVideoIds: Set<String>,
    localLibraryTracks: List<Track>,
    onPlayStreamingItem: (StreamingSearchItem) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onDownloadStreamingItem: (StreamingSearchItem) -> Unit
) {
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
                        val isAlreadyDownloaded = downloadedVideoIds.contains(item.videoId) ||
                            localLibraryTracks.any { local ->
                                local.streamingVideoId == item.videoId ||
                                    (local.title.equals(item.title, ignoreCase = true) &&
                                        local.artist.equals(item.artist, ignoreCase = true))
                            }

                        StreamingResultCard(
                            item = item,
                            isCurrentlyPlaying = isCurrentlyPlaying,
                            isBeingResolved = isBeingResolved,
                            isDownloading = isDownloading,
                            isDownloaded = isAlreadyDownloaded,
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
