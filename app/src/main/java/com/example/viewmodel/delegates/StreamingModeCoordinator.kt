package com.example.viewmodel.delegates

import android.content.Context
import com.example.data.importer.FFmpegNativeEngine
import com.example.data.importer.YtDlpNativeEngine
import com.example.data.streaming.StreamingApiService
import com.example.data.streaming.StreamingCacheManager
import com.example.model.StreamingCacheConfig
import com.example.model.StreamingSearchFilter
import com.example.model.StreamingSearchItem
import com.example.model.StreamingSourcePlatform
import com.example.model.StreamingVideoNetworkPolicy
import com.example.model.Track
import com.example.playback.AuraAudioPlayer
import com.example.playback.AuraDownloadService
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
    private val context: Context,
    private val audioPlayer: AuraAudioPlayer,
    private val scope: CoroutineScope,
    private val onLoadLyricsForTrack: (Track) -> Unit,
    private val onStatusMessage: (String?) -> Unit
) {

    val cacheManager = StreamingCacheManager(context)
    val cacheConfig: StateFlow<StreamingCacheConfig> = cacheManager.cacheConfig

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(StreamingSearchFilter.ALL)
    val selectedFilter: StateFlow<StreamingSearchFilter> = _selectedFilter.asStateFlow()

    private val _searchResults = MutableStateFlow<List<StreamingSearchItem>>(emptyList())
    val searchResults: StateFlow<List<StreamingSearchItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isResolvingStream = MutableStateFlow(false)
    val isResolvingStream: StateFlow<Boolean> = _isResolvingStream.asStateFlow()

    private val _resolvingVideoId = MutableStateFlow<String?>(null)
    val resolvingVideoId: StateFlow<String?> = _resolvingVideoId.asStateFlow()

    private val _nextSimilarTrackPreview = MutableStateFlow<StreamingSearchItem?>(null)
    val nextSimilarTrackPreview: StateFlow<StreamingSearchItem?> = _nextSimilarTrackPreview.asStateFlow()

    private val _downloadingVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingVideoIds: StateFlow<Set<String>> = _downloadingVideoIds.asStateFlow()

    private val _downloadedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedVideoIds: StateFlow<Set<String>> = _downloadedVideoIds.asStateFlow()

    private var searchJob: Job? = null
    private var playResolutionJob: Job? = null
    private var similarPrefetchJob: Job? = null

    private val playedVideoIdsHistory = LinkedHashSet<String>()
    private val itemRegistry = ConcurrentHashMap<String, StreamingSearchItem>()

    init {
        // Vincular hooks en AuraAudioPlayer para consultar la API + yt-dlp antes de ir a la canción
        // anterior/siguiente y para pre-cargar una canción similar antes de que termine la actual
        audioPlayer.onStreamingTrackRequestedCallback = { track, isAutoCrossfade ->
            handleStreamingTrackTransitionRequest(track, isAutoCrossfade)
        }
        audioPlayer.onStreamingNearEndPrefetchCallback = { currentTrack ->
            prefetchNextSimilarTrackForRadio(currentTrack)
        }
        audioPlayer.onStreamingHttpErrorRecoveryCallback = { failedTrack, resumePositionMs ->
            handleStreamingHttpErrorRecovery(failedTrack, resumePositionMs)
        }

        // Cargar catálogo inicial de tendencias al arrancar
        loadInitialTrendingIfNeeded()

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

    fun loadInitialTrendingIfNeeded() {
        if (_searchResults.value.isNotEmpty() || _isSearching.value) return
        performSearch(query = "", filter = _selectedFilter.value)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchFilter(filter: StreamingSearchFilter) {
        if (_selectedFilter.value == filter) return
        _selectedFilter.value = filter
        performSearch(_searchQuery.value, filter)
    }

    /**
     * Ejecuta la búsqueda en la API de YouTube Music y/o YouTube según el filtro activo.
     */
    fun performSearch(query: String = _searchQuery.value, filter: StreamingSearchFilter = _selectedFilter.value) {
        searchJob?.cancel()
        searchJob = scope.launch {
            _isSearching.value = true
            try {
                cacheManager.evictExpiredAndOverLimit()
                val items = StreamingApiService.searchStreamingCatalog(query, filter, context)
                items.forEach { itemRegistry[it.videoId] = it }
                _searchResults.value = items
            } catch (_: Exception) {
            } finally {
                _isSearching.value = false
            }
        }
    }

    /**
     * Reproduce un elemento de streaming al instante:
     * 1. Si ya está en la caché temporal de 30 minutos (Audio y opcionalmente Video Canvas), reproduce a 0ms.
     * 2. Si no está en caché, resuelve con `yt-dlp` / `InnerTube`, inicia la reproducción de inmediato,
     *    descarga las letras sincronizadas `.LRC` y pre-descarga por adelantado los siguientes minutos
     *    en la caché temporal (tanto audio como Video Canvas si la política Wi-Fi/Datos lo permite).
     * 3. Además, lanza en segundo plano la pre-consulta a la API de una canción similar para que esté lista.
     */
    fun playStreamingItem(
        item: StreamingSearchItem,
        contextQueue: List<StreamingSearchItem> = _searchResults.value
    ) {
        itemRegistry[item.videoId] = item
        contextQueue.forEach { itemRegistry[it.videoId] = it }
        playedVideoIdsHistory.add(item.videoId)

        playResolutionJob?.cancel()
        playResolutionJob = scope.launch {
            _isResolvingStream.value = true
            _resolvingVideoId.value = item.videoId
            try {
                cacheManager.evictExpiredAndOverLimit()

                // 1. Verificar si ya existe en la caché temporal de 30 minutos
                val cachedAudio = cacheManager.getCachedAudioFile(item.videoId)
                val cachedVideo = if (cacheManager.isStreamingVideoAllowedByNetwork()) {
                    cacheManager.getCachedVideoFile(item.videoId)
                } else null

                if (cachedAudio != null) {
                    val instantTrack = buildStreamingTrack(
                        item = item,
                        audioUri = cachedAudio.absolutePath,
                        videoUri = cachedVideo?.absolutePath
                    )
                    val queueTracks = buildQueueTracksWithSelected(item, instantTrack, contextQueue)
                    val startIdx = queueTracks.indexOfFirst { it.id == instantTrack.id }.coerceAtLeast(0)
                    withContext(Dispatchers.Main) {
                        audioPlayer.playTrackList(queueTracks, startIdx)
                        onLoadLyricsForTrack(instantTrack)
                    }
                    prefetchNextSimilarTrackForRadio(instantTrack)
                    return@launch
                }

                // 2. Resolver con yt-dlp (normalizado para YT y YT Music) + InnerTube
                val resolvedResult = StreamingApiService.resolvePlayableStreamWithYtDlp(context, item)
                val resolved = resolvedResult.getOrNull()
                if (resolved == null) {
                    onStatusMessage("No se pudo iniciar el stream de \"${item.title}\". Intenta con otra pista.")
                    return@launch
                }

                val playableAudioUrl = resolved.audioUrl?.takeIf { it.isNotBlank() } ?: resolved.videoUrl
                val canUseVideoCanvas = cacheManager.isStreamingVideoAllowedByNetwork()
                val initialVideoUri = if (canUseVideoCanvas) {
                    cacheManager.getCachedVideoFile(item.videoId)?.absolutePath
                        ?: resolved.videoUrl.takeIf { it.isNotBlank() && it != playableAudioUrl }
                } else null

                val playingTrack = buildStreamingTrack(
                    item = item.copy(
                        durationSeconds = if (item.durationSeconds > 0) item.durationSeconds else resolved.durationSeconds,
                        thumbnailUrl = item.thumbnailUrl.ifBlank { resolved.coverUrl.orEmpty() }
                    ),
                    audioUri = playableAudioUrl,
                    videoUri = initialVideoUri
                )

                val queueTracks = buildQueueTracksWithSelected(item, playingTrack, contextQueue)
                val startIdx = queueTracks.indexOfFirst { it.id == playingTrack.id }.coerceAtLeast(0)

                withContext(Dispatchers.Main) {
                    audioPlayer.playTrackList(queueTracks, startIdx)
                    onLoadLyricsForTrack(playingTrack)
                }

                // 3. Pre-descargar de forma escalonada en la caché temporal para no competir con el buffer inicial
                //    ni recrear el decodificador de video/audio en mitad de la canción activa.
                scope.launch(Dispatchers.IO) {
                    kotlinx.coroutines.delay(3500L)
                    cacheManager.prefetchAudioAheadAsync(
                        videoId = item.videoId,
                        streamUrl = playableAudioUrl
                    ) { cachedFile ->
                        val updated = playingTrack.copy(uriString = cachedFile.absolutePath)
                        audioPlayer.appendOrUpdateStreamingTrack(updated, updateCurrentTrackIfSame = false)
                    }

                    // 4. Si la política de Video Canvas lo permite, pre-cargar en caché después del arranque inicial
                    if (canUseVideoCanvas && resolved.videoUrl.isNotBlank() && initialVideoUri?.startsWith("http", ignoreCase = true) == true) {
                        kotlinx.coroutines.delay(5000L)
                        cacheManager.prefetchVideoCanvasAsync(
                            videoId = item.videoId,
                            videoStreamUrl = resolved.videoUrl
                        ) { cachedVideoFile ->
                            val cur = audioPlayer.currentTrack.value
                            val updated = (if (cur?.id == playingTrack.id) cur else playingTrack).copy(
                                videoUri = cachedVideoFile.absolutePath
                            )
                            // Actualizar solo en la cola para que no destruya ni reinicie el BackgroundVideoPlayer activo
                            audioPlayer.appendOrUpdateStreamingTrack(updated, updateCurrentTrackIfSame = false)
                        }
                    }
                }

                // 5. Preparar por adelantado una canción similar recomendada
                prefetchNextSimilarTrackForRadio(playingTrack)
            } finally {
                _isResolvingStream.value = false
                _resolvingVideoId.value = null
            }
        }
    }

    /**
     * Antes de pasar a la canción anterior (o siguiente en la cola de streaming),
     * verifica si ya está en la caché de 30 minutos o consulta a la API y `yt-dlp` para tomar
     * un flujo fresco y reproducirlo sin errores de expiración de token.
     */
    private fun handleStreamingTrackTransitionRequest(
        targetTrack: Track,
        isAutoCrossfade: Boolean
    ): Boolean {
        val videoId = targetTrack.streamingVideoId ?: return false
        val platform = targetTrack.streamingPlatform ?: StreamingSourcePlatform.YT_MUSIC

        // Si el audio ya fue pre-descargado en la caché temporal de 30 minutos, se reproduce al instante
        val cachedAudio = cacheManager.getCachedAudioFile(videoId)
        val cachedVideo = if (cacheManager.isStreamingVideoAllowedByNetwork()) {
            cacheManager.getCachedVideoFile(videoId)
        } else null

        if (cachedAudio != null) {
            val readyTrack = targetTrack.copy(
                uriString = cachedAudio.absolutePath,
                videoUri = cachedVideo?.absolutePath ?: if (cacheManager.isStreamingVideoAllowedByNetwork()) targetTrack.videoUri else null
            )
            scope.launch(Dispatchers.Main) {
                audioPlayer.appendOrUpdateStreamingTrack(readyTrack)
                audioPlayer.playTrack(readyTrack, isAutoCrossfadeOverlap = isAutoCrossfade)
                onLoadLyricsForTrack(readyTrack)
            }
            prefetchNextSimilarTrackForRadio(readyTrack)
            return true
        }

        // Si aún no tiene archivo en caché o su URL era un marcador pendiente, consultamos la API y yt-dlp (solo para YouTube)
        playResolutionJob?.cancel()
        playResolutionJob = scope.launch {
            _isResolvingStream.value = true
            _resolvingVideoId.value = videoId
            try {
                val baseItem = itemRegistry[videoId] ?: StreamingSearchItem(
                    videoId = videoId,
                    title = targetTrack.title,
                    artist = targetTrack.artist,
                    album = targetTrack.album,
                    durationSeconds = targetTrack.durationMs / 1000L,
                    thumbnailUrl = targetTrack.albumArtPath ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    platform = platform
                )
                val resolved = StreamingApiService.resolvePlayableStreamWithYtDlp(context, baseItem).getOrNull()
                if (resolved != null) {
                    val playableAudioUrl = resolved.audioUrl?.takeIf { it.isNotBlank() } ?: resolved.videoUrl
                    val allowVideo = cacheManager.isStreamingVideoAllowedByNetwork()
                    val resolvedTrack = targetTrack.copy(
                        uriString = playableAudioUrl,
                        videoUri = if (allowVideo) resolved.videoUrl.takeIf { it.isNotBlank() } else null,
                        durationMs = if (targetTrack.durationMs > 0) targetTrack.durationMs else resolved.durationSeconds * 1000L
                    )
                    withContext(Dispatchers.Main) {
                        audioPlayer.appendOrUpdateStreamingTrack(resolvedTrack)
                        audioPlayer.playTrack(resolvedTrack, isAutoCrossfadeOverlap = isAutoCrossfade)
                        onLoadLyricsForTrack(resolvedTrack)
                    }
                    scope.launch(Dispatchers.IO) {
                        kotlinx.coroutines.delay(3500L)
                        cacheManager.prefetchAudioAheadAsync(videoId, playableAudioUrl) { file ->
                            audioPlayer.appendOrUpdateStreamingTrack(resolvedTrack.copy(uriString = file.absolutePath), updateCurrentTrackIfSame = false)
                        }
                        if (allowVideo && resolved.videoUrl.isNotBlank()) {
                            kotlinx.coroutines.delay(5000L)
                            cacheManager.prefetchVideoCanvasAsync(videoId, resolved.videoUrl) { vFile ->
                                audioPlayer.appendOrUpdateStreamingTrack(resolvedTrack.copy(videoUri = vFile.absolutePath), updateCurrentTrackIfSame = false)
                            }
                        }
                    }
                    prefetchNextSimilarTrackForRadio(resolvedTrack)
                }
            } finally {
                _isResolvingStream.value = false
                _resolvingVideoId.value = null
            }
        }
        return true
    }

    /**
     * Recuperación transparente cuando ExoPlayer recibe `ERROR_CODE_IO_BAD_HTTP_STATUS` (ej. HTTP 403 Forbidden):
     * 1. Si `StreamingCacheManager` ya descargó el archivo en la caché temporal, conmuta al archivo local y
     *    continúa desde [resumePositionMs] sin interrupción.
     * 2. Si aún no está en caché, solicita un stream fresco con `forceBypassYtDlp = true` (respaldo directo)
     *    y reanuda la reproducción en [resumePositionMs].
     */
    private fun handleStreamingHttpErrorRecovery(
        failedTrack: Track,
        resumePositionMs: Long
    ): Boolean {
        val videoId = failedTrack.streamingVideoId ?: return false
        val platform = failedTrack.streamingPlatform ?: StreamingSourcePlatform.YT_MUSIC

        val cachedAudio = cacheManager.getCachedAudioFile(videoId)
        val cachedVideo = if (cacheManager.isStreamingVideoAllowedByNetwork()) {
            cacheManager.getCachedVideoFile(videoId)
        } else null

        if (cachedAudio != null) {
            val localReadyTrack = failedTrack.copy(
                uriString = cachedAudio.absolutePath,
                videoUri = cachedVideo?.absolutePath ?: failedTrack.videoUri
            )
            scope.launch(Dispatchers.Main) {
                audioPlayer.appendOrUpdateStreamingTrack(localReadyTrack)
                audioPlayer.playTrack(localReadyTrack, isAutoCrossfadeOverlap = false)
                if (resumePositionMs > 1000L) {
                    audioPlayer.seekTo(resumePositionMs)
                }
            }
            return true
        }

        playResolutionJob?.cancel()
        playResolutionJob = scope.launch {
            _isResolvingStream.value = true
            _resolvingVideoId.value = videoId
            try {
                val baseItem = itemRegistry[videoId] ?: StreamingSearchItem(
                    videoId = videoId,
                    title = failedTrack.title,
                    artist = failedTrack.artist,
                    album = failedTrack.album,
                    durationSeconds = failedTrack.durationMs / 1000L,
                    thumbnailUrl = failedTrack.albumArtPath ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    platform = platform
                )
                val resolved = StreamingApiService.resolvePlayableStreamWithYtDlp(
                    context = context,
                    item = baseItem,
                    forceBypassYtDlp = true
                ).getOrNull()
                if (resolved != null) {
                    val freshAudioUrl = resolved.audioUrl?.takeIf { it.isNotBlank() } ?: resolved.videoUrl
                    val allowVideo = cacheManager.isStreamingVideoAllowedByNetwork()
                    val recoveredTrack = failedTrack.copy(
                        uriString = freshAudioUrl,
                        videoUri = if (allowVideo) resolved.videoUrl.takeIf { it.isNotBlank() } else null
                    )
                    withContext(Dispatchers.Main) {
                        audioPlayer.appendOrUpdateStreamingTrack(recoveredTrack)
                        audioPlayer.playTrack(recoveredTrack, isAutoCrossfadeOverlap = false)
                        if (resumePositionMs > 1000L) {
                            audioPlayer.seekTo(resumePositionMs)
                        }
                    }
                    scope.launch(Dispatchers.IO) {
                        cacheManager.prefetchAudioAheadAsync(videoId, freshAudioUrl) { file ->
                            audioPlayer.appendOrUpdateStreamingTrack(
                                recoveredTrack.copy(uriString = file.absolutePath),
                                updateCurrentTrackIfSame = false
                            )
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        audioPlayer.playNext()
                    }
                }
            } finally {
                _isResolvingStream.value = false
                _resolvingVideoId.value = null
            }
        }
        return true
    }

    /**
     * Consulta automáticamente a la API por una canción similar y resuelve su stream
     * (`yt-dlp` para YouTube, `InnerTube` para YT Music) + pre-descarga en caché temporal antes de que termine la pista actual.
     */
    fun prefetchNextSimilarTrackForRadio(currentTrack: Track) {
        val videoId = currentTrack.streamingVideoId ?: return
        val platform = currentTrack.streamingPlatform ?: StreamingSourcePlatform.YT_MUSIC

        similarPrefetchJob?.cancel()
        similarPrefetchJob = scope.launch(Dispatchers.IO) {
            try {
                playedVideoIdsHistory.add(videoId)
                val similarItem = StreamingApiService.fetchSimilarNextTrack(
                    currentVideoId = videoId,
                    currentTitle = currentTrack.title,
                    currentArtist = currentTrack.artist,
                    preferredPlatform = platform,
                    excludedVideoIds = playedVideoIdsHistory
                ) ?: return@launch

                itemRegistry[similarItem.videoId] = similarItem
                _nextSimilarTrackPreview.value = similarItem

                // Resolver según plataforma (yt-dlp para YouTube, InnerTube para YT Music) y pre-almacenar en caché temporal de 30 minutos
                val resolved = StreamingApiService.resolvePlayableStreamWithYtDlp(context, similarItem).getOrNull()
                    ?: return@launch

                val playableAudioUrl = resolved.audioUrl?.takeIf { it.isNotBlank() } ?: resolved.videoUrl
                val allowVideo = cacheManager.isStreamingVideoAllowedByNetwork()
                val cachedAudio = cacheManager.getCachedAudioFile(similarItem.videoId)
                val cachedVideo = if (allowVideo) cacheManager.getCachedVideoFile(similarItem.videoId) else null

                val nextTrack = buildStreamingTrack(
                    item = similarItem.copy(
                        durationSeconds = if (similarItem.durationSeconds > 0) similarItem.durationSeconds else resolved.durationSeconds
                    ),
                    audioUri = cachedAudio?.absolutePath ?: playableAudioUrl,
                    videoUri = cachedVideo?.absolutePath ?: if (allowVideo) resolved.videoUrl.takeIf { it.isNotBlank() } else null
                )

                // Añadir a la cola activa para que al finalizar o pulsar Siguiente suene sin esperas
                audioPlayer.appendOrUpdateStreamingTrack(nextTrack, updateCurrentTrackIfSame = false)

                // Pre-descargar los primeros minutos de la canción similar en la caché temporal
                cacheManager.prefetchAudioAheadAsync(similarItem.videoId, playableAudioUrl) { readyAudioFile ->
                    audioPlayer.appendOrUpdateStreamingTrack(nextTrack.copy(uriString = readyAudioFile.absolutePath), updateCurrentTrackIfSame = false)
                }
                if (allowVideo && resolved.videoUrl.isNotBlank()) {
                    cacheManager.prefetchVideoCanvasAsync(similarItem.videoId, resolved.videoUrl) { readyVideoFile ->
                        audioPlayer.appendOrUpdateStreamingTrack(nextTrack.copy(videoUri = readyVideoFile.absolutePath), updateCurrentTrackIfSame = false)
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Descarga permanentemente una canción o video de streaming (desde el reproductor online o desde Now Playing)
     * hacia la biblioteca local (`songs/`, `images/`, `videos/`, `lyrics/`) mediante `AuraDownloadService`,
     * incluyendo su audio y su Video Canvas 480p sin interrumpir la canción que está sonando.
     */
    fun downloadStreamingItemPermanently(
        item: StreamingSearchItem,
        includeVideoCanvas: Boolean = true
    ) {
        if (_downloadingVideoIds.value.contains(item.videoId)) {
            onStatusMessage("Ya se está descargando \"${item.title}\" en segundo plano...")
            return
        }
        _downloadingVideoIds.value = _downloadingVideoIds.value + item.videoId
        onStatusMessage("Preparando descarga de \"${item.title}\" (${item.platform.badgeText}) en tu biblioteca local...")

        scope.launch(Dispatchers.IO) {
            try {
                val resolvedResult = StreamingApiService.resolvePlayableStreamWithYtDlp(context, item)
                val resolved = resolvedResult.getOrNull()
                if (resolved == null) {
                    _downloadingVideoIds.value = _downloadingVideoIds.value - item.videoId
                    onStatusMessage("No se pudo iniciar la descarga de \"${item.title}\".")
                    return@launch
                }

                // Reutilizar archivos si ya estaban en la caché temporal de 30 minutos para acelerar la descarga y evitar fallos de red
                val cachedAudio = cacheManager.getCachedAudioFile(item.videoId)
                val cachedVideo = cacheManager.getCachedVideoFile(item.videoId)
                val optimizedResolved = resolved.copy(
                    audioUrl = cachedAudio?.absolutePath ?: resolved.audioUrl,
                    videoUrl = cachedVideo?.absolutePath ?: resolved.videoUrl
                )

                AuraDownloadService.startDownload(
                    context = context,
                    resolvedInfo = optimizedResolved,
                    customTitle = item.title,
                    customArtist = item.artist,
                    attachAsCanvas = includeVideoCanvas,
                    trimSilence = false,
                    loopStyle = FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE,
                    autoPlayOnComplete = false,
                    sourceVideoId = item.videoId
                )
            } catch (t: Throwable) {
                _downloadingVideoIds.value = _downloadingVideoIds.value - item.videoId
                onStatusMessage("Error al preparar descarga de \"${item.title}\": ${t.localizedMessage ?: "Intenta nuevamente"}")
            }
        }
    }

    /**
     * Permite descargar permanentemente la pista que está sonando actualmente en modo Streaming desde Now Playing.
     */
    fun downloadCurrentStreamingTrackPermanently(track: Track) {
        val videoId = track.streamingVideoId ?: return
        val platform = track.streamingPlatform ?: StreamingSourcePlatform.YT_MUSIC
        val item = itemRegistry[videoId] ?: StreamingSearchItem(
            videoId = videoId,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationSeconds = track.durationMs / 1000L,
            thumbnailUrl = track.albumArtPath ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            platform = platform
        )
        downloadStreamingItemPermanently(item, includeVideoCanvas = true)
    }

    fun setStreamingCacheMaxSizeMb(sizeMb: Int) {
        cacheManager.setMaxCacheSizeMb(sizeMb)
    }

    fun setStreamingVideoNetworkPolicy(policy: StreamingVideoNetworkPolicy) {
        cacheManager.setVideoNetworkPolicy(policy)
        // Si el usuario cambia la política mientras suena una pista en streaming, actualizamos su video al instante
        val cur = audioPlayer.currentTrack.value
        if (cur != null && cur.isStreamingTrack) {
            val videoId = cur.streamingVideoId
            if (!cacheManager.isStreamingVideoAllowedByNetwork()) {
                audioPlayer.appendOrUpdateStreamingTrack(cur.copy(videoUri = null))
            } else if (videoId != null) {
                val cachedVid = cacheManager.getCachedVideoFile(videoId)
                if (cachedVid != null) {
                    audioPlayer.appendOrUpdateStreamingTrack(cur.copy(videoUri = cachedVid.absolutePath))
                }
            }
        }
    }

    fun clearStreamingCache() {
        scope.launch {
            cacheManager.clearAllStreamingCache()
            onStatusMessage("Caché temporal de streaming liberada.")
        }
    }

    fun refreshCacheStats() {
        scope.launch {
            cacheManager.refreshStats()
        }
    }

    private fun buildStreamingTrack(
        item: StreamingSearchItem,
        audioUri: String,
        videoUri: String?
    ): Track {
        return Track(
            id = item.syntheticTrackId,
            title = item.title,
            artist = item.artist,
            album = "${item.platform.badgeText} • ${item.album}",
            durationMs = item.durationSeconds * 1000L,
            uriString = audioUri,
            albumArtPath = item.thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/${item.videoId}/hqdefault.jpg" },
            videoUri = videoUri,
            isVideoLoop = false,
            mimeType = "audio/mp4",
            folderName = "STREAM:${item.platform.id}:${item.videoId}",
            fileSizeFormatted = item.platform.badgeText
        )
    }

    private fun buildQueueTracksWithSelected(
        selectedItem: StreamingSearchItem,
        resolvedSelectedTrack: Track,
        contextQueue: List<StreamingSearchItem>
    ): List<Track> {
        val baseList = if (contextQueue.any { it.videoId == selectedItem.videoId }) {
            contextQueue
        } else {
            listOf(selectedItem) + contextQueue
        }
        return baseList.take(25).map { item ->
            if (item.videoId == selectedItem.videoId) {
                resolvedSelectedTrack
            } else {
                val cachedAudio = cacheManager.getCachedAudioFile(item.videoId)
                val cachedVideo = if (cacheManager.isStreamingVideoAllowedByNetwork()) {
                    cacheManager.getCachedVideoFile(item.videoId)
                } else null
                buildStreamingTrack(
                    item = item,
                    audioUri = cachedAudio?.absolutePath ?: item.canonicalWatchUrl,
                    videoUri = cachedVideo?.absolutePath
                )
            }
        }
    }
}
