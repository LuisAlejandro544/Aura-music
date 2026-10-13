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
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pantalla modular de Exploración y Modo Streaming Híbrido.
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
    downloadedVideoIds: Set<String> = emptySet(),
    localLibraryTracks: List<Track> = emptyList(),
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onFilterChange: (StreamingSearchFilter) -> Unit,
    onPlayStreamingItem: (StreamingSearchItem) -> Unit,
    onDownloadStreamingItem: (StreamingSearchItem) -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp)
            .testTag("streaming_explore_screen")
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Encabezado con estado de Caché Temporal y Política de Video Canvas
        StreamingExploreHeader(cacheConfig = cacheConfig)

        // Barra de Búsqueda Unificada
        StreamingSearchBar(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onSearchSubmit = onSearchSubmit
        )

        // Filtros de Origen: Todo, Solo YT Music, Solo YouTube + Sugerencias rápidas
        StreamingFilterRow(
            selectedFilter = selectedFilter,
            onFilterChange = onFilterChange
        )

        // Tarjeta de Radio / Siguiente canción similar pre-cargada por API + yt-dlp
        StreamingRadioSection(
            preview = nextSimilarTrackPreview,
            isStreamingTrack = currentTrack?.isStreamingTrack == true,
            onPlayNow = {
                val preview = nextSimilarTrackPreview
                if (preview != null) onPlayStreamingItem(preview)
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        StreamingResultsList(
            searchResults = searchResults,
            isSearching = isSearching,
            currentTrack = currentTrack,
            isResolvingStream = isResolvingStream,
            resolvingVideoId = resolvingVideoId,
            downloadingVideoIds = downloadingVideoIds,
            downloadedVideoIds = downloadedVideoIds,
            localLibraryTracks = localLibraryTracks,
            onPlayStreamingItem = onPlayStreamingItem,
            onOpenNowPlaying = onOpenNowPlaying,
            onDownloadStreamingItem = onDownloadStreamingItem
        )
    }
}

