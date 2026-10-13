package com.example.viewmodel.delegates

import android.content.Context
import com.example.core.data.importer.FFmpegNativeEngine
import com.example.core.data.importer.YtDlpNativeEngine
import com.example.core.data.streaming.StreamingApiService
import com.example.core.data.streaming.StreamingCacheManager
import com.example.core.model.StreamingCacheConfig
import com.example.core.model.StreamingSearchFilter
import com.example.core.model.StreamingSearchItem
import com.example.core.model.StreamingSourcePlatform
import com.example.core.model.StreamingVideoNetworkPolicy
import com.example.core.model.Track
import com.example.core.playback.AuraAudioPlayer
import com.example.core.playback.AuraDownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinador MVVM del Modo Streaming Híbrido.
 *
 * Responsabilidades:
 * 1. Búsqueda y Exploración en Tiempo Real mediante API (sin usar yt-dlp para buscar) con soporte
 *    simultáneo para YouTube Music (`YT MUSIC`) y YouTube (`YOUTUBE`).
 * 2. Resolución de Reproducción mediante `yt-dlp` (con normalización `music.youtube.com` y respaldo `InnerTube`).
 * 3. Pre-descarga No Lineal (Read-Ahead Buffer) en Caché Temporal (50 MB a 500 MB, expiración a los 30 minutos sin uso)
 *    tanto para audio como para Video Canvas.
 * 4. Consulta anticipada a la API y resolución con `yt-dlp` antes de pasar a la canción anterior o
 *    al acercarse al final de la pista actual (Radio / Automix de canción similar).
 * 5. Descarga Permanente con 1 Toque hacia la biblioteca local (`songs/`, `images/`, `videos/`, `lyrics/`).
 */
class StreamingModeCoordinator(
    internal val context: Context,
    internal val audioPlayer: AuraAudioPlayer,
    internal val scope: CoroutineScope,
    internal val onLoadLyricsForTrack: (Track) -> Unit,
    internal val onStatusMessage: (String?) -> Unit
) {

    val cacheManager = StreamingCacheManager(context)
    val cacheConfig: StateFlow<StreamingCacheConfig> = cacheManager.cacheConfig

    internal val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    internal val _selectedFilter = MutableStateFlow(StreamingSearchFilter.ALL)
    val selectedFilter: StateFlow<StreamingSearchFilter> = _selectedFilter.asStateFlow()

    internal val _searchResults = MutableStateFlow<List<StreamingSearchItem>>(emptyList())
    val searchResults: StateFlow<List<StreamingSearchItem>> = _searchResults.asStateFlow()

    internal val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    internal val _isResolvingStream = MutableStateFlow(false)
    val isResolvingStream: StateFlow<Boolean> = _isResolvingStream.asStateFlow()

    internal val _resolvingVideoId = MutableStateFlow<String?>(null)
    val resolvingVideoId: StateFlow<String?> = _resolvingVideoId.asStateFlow()

    internal val _nextSimilarTrackPreview = MutableStateFlow<StreamingSearchItem?>(null)
    val nextSimilarTrackPreview: StateFlow<StreamingSearchItem?> = _nextSimilarTrackPreview.asStateFlow()

    internal val _downloadingVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingVideoIds: StateFlow<Set<String>> = _downloadingVideoIds.asStateFlow()

    internal val _downloadedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedVideoIds: StateFlow<Set<String>> = _downloadedVideoIds.asStateFlow()

    internal var searchJob: Job? = null
    internal var playResolutionJob: Job? = null
    internal var similarPrefetchJob: Job? = null

    internal val playedVideoIdsHistory = LinkedHashSet<String>()
    internal val itemRegistry = ConcurrentHashMap<String, StreamingSearchItem>()

    init {
        // Vincular hooks en AuraAudioPlayer para consultar la API + yt-dlp antes de ir a la canción
        // anterior/siguiente y para pre-cargar una canción similar antes de que termine la actual
        audioPlayer.onStreamingTrackRequestedCallback = { track, isAutoCrossfade ->
            handleStreamingTrackTransitionRequestInternal(track, isAutoCrossfade)
        }
        audioPlayer.onStreamingNearEndPrefetchCallback = { currentTrack ->
            prefetchNextSimilarTrackForRadioInternal(currentTrack)
        }
        audioPlayer.onStreamingHttpErrorRecoveryCallback = { failedTrack, resumePositionMs ->
            handleStreamingHttpErrorRecoveryInternal(failedTrack, resumePositionMs)
        }

        // Cargar catálogo inicial de tendencias al arrancar
        loadInitialTrendingIfNeededInternal()

        // Limpiar estado de descarga del botón cuando AuraDownloadService termina (éxito o error)
        // y activar el signo de verificación (✓) únicamente cuando la descarga haya culminado con éxito
        scope.launch {
            AuraDownloadService.taskFinishedEvents.collect { event ->
                val vid = event.sourceVideoId
                if (!vid.isNullOrBlank()) {
                    _downloadingVideoIds.value = _downloadingVideoIds.value - vid
                    if (event.success) {
                        _downloadedVideoIds.value = _downloadedVideoIds.value + vid
                    }
                } else {
                    _downloadingVideoIds.value = emptySet()
                }
            }
        }
    }

    fun loadInitialTrendingIfNeeded() = loadInitialTrendingIfNeededInternal()


    fun updateSearchQuery(query: String) = updateSearchQueryInternal(query)


    fun setSearchFilter(filter: StreamingSearchFilter) = setSearchFilterInternal(filter)


    fun performSearch(query: String = _searchQuery.value, filter: StreamingSearchFilter = _selectedFilter.value) = performSearchInternal(query, filter)


    fun playStreamingItem(item: StreamingSearchItem, contextQueue: List<StreamingSearchItem> = _searchResults.value) = playStreamingItemInternal(item, contextQueue)




    fun prefetchNextSimilarTrackForRadio(currentTrack: Track) = prefetchNextSimilarTrackForRadioInternal(currentTrack)


    fun downloadStreamingItemPermanently(item: StreamingSearchItem, includeVideoCanvas: Boolean = true) = downloadStreamingItemPermanentlyInternal(item, includeVideoCanvas)


    fun downloadCurrentStreamingTrackPermanently(track: Track) = downloadCurrentStreamingTrackPermanentlyInternal(track)


    fun setStreamingCacheMaxSizeMb(sizeMb: Int) = setStreamingCacheMaxSizeMbInternal(sizeMb)


    fun setStreamingVideoNetworkPolicy(policy: StreamingVideoNetworkPolicy) = setStreamingVideoNetworkPolicyInternal(policy)


    fun setStreamingAudioQuality(quality: com.example.core.model.StreamingAudioQuality) = setStreamingAudioQualityInternal(quality)


    fun setExploreLyricsEnabled(enabled: Boolean) = setExploreLyricsEnabledInternal(enabled)


    fun clearStreamingCache() = clearStreamingCacheInternal()


    fun refreshCacheStats() = refreshCacheStatsInternal()



}
