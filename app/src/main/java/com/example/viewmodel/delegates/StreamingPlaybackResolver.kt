package com.example.viewmodel.delegates

import com.example.core.data.streaming.StreamingApiService
import com.example.core.model.StreamingSearchItem
import com.example.core.model.StreamingSourcePlatform
import com.example.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Resolución y transición de pistas, recuperación 403 y radio similar.
 * Rol arquitectónico: extensiones internas de StreamingModeCoordinator; la API pública no cambia.
 */
/**
 * Reproduce un elemento de streaming al instante:
 * 1. Si ya está en la caché temporal de 30 minutos (Audio y opcionalmente Video Canvas), reproduce a 0ms.
 * 2. Si no está en caché, resuelve con `yt-dlp` / `InnerTube`, inicia la reproducción de inmediato,
 *    descarga las letras sincronizadas `.LRC` y pre-descarga por adelantado los siguientes minutos
 *    en la caché temporal (tanto audio como Video Canvas si la política Wi-Fi/Datos lo permite).
 * 3. Además, lanza en segundo plano la pre-consulta a la API de una canción similar para que esté lista.
 */
internal fun StreamingModeCoordinator.playStreamingItemInternal(
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
            // Modo Ahorro de Datos (solo Explorar/streaming): fuerza Opus ahorro, sin video,
            // sin letras automáticas y sin prefetch/radio. Lo local no se toca (este archivo solo resuelve streaming).
            val dataSaver = cacheManager.isStreamingDataSaverEnabled()
            val effectiveQuality = if (dataSaver) {
                com.example.core.model.StreamingAudioQuality.AHORRO_OPUS_48K
            } else {
                cacheManager.getStreamingAudioQuality()
            }
            fun loadLyricsIfAllowed(track: Track) {
                if (!dataSaver && cacheManager.isStreamingLyricsAutoFetchAllowed()) {
                    onLoadLyricsForTrack(track)
                }
            }

            // 1. Verificar si ya existe en la caché temporal de 30 minutos
            val cachedAudio = cacheManager.getCachedAudioFile(item.videoId)
            val cachedVideo = if (!dataSaver && cacheManager.isStreamingVideoAllowedByNetwork()) {
                cacheManager.getCachedVideoFile(item.videoId)
            } else null

            if (cachedAudio != null) {
                val instantTrack = buildStreamingTrackInternal(
                    item = item,
                    audioUri = cachedAudio.absolutePath,
                    videoUri = cachedVideo?.absolutePath
                )
                val queueTracks = buildQueueTracksWithSelectedInternal(item, instantTrack, contextQueue)
                val startIdx = queueTracks.indexOfFirst { it.id == instantTrack.id }.coerceAtLeast(0)
                withContext(Dispatchers.Main) {
                    audioPlayer.playTrackList(queueTracks, startIdx)
                    loadLyricsIfAllowed(instantTrack)
                }
                if (!dataSaver) {
                    prefetchNextSimilarTrackForRadioInternal(instantTrack)
                    warmNextQueueTrackInternal(instantTrack)
                }
                return@launch
            }

            // 2. Resolver con cascada unificada yt-dlp (YT Music y YouTube) + InnerTube multi-cliente
            // v0.2.1-beta.1f: reintento con bypass si el primer intento falla + validación anti-URL vacía.
            var resolved = StreamingApiService.resolvePlayableStreamWithYtDlp(
                context, item, audioQuality = effectiveQuality
            ).getOrNull()
            if (resolved == null) {
                // Reintento inmediato forzando InnerTube/Invidious/WebView (sin yt-dlp bloqueado o con PO token caducado).
                kotlinx.coroutines.delay(600L)
                resolved = StreamingApiService.resolvePlayableStreamWithYtDlp(
                    context, item, forceBypassYtDlp = true, audioQuality = effectiveQuality
                ).getOrNull()
            }
            if (resolved == null) {
                withContext(Dispatchers.Main) {
                    onStatusMessage("No se pudo iniciar el stream de \"${item.title}\". Revisa tu internet e inténtalo de nuevo.")
                }
                return@launch
            }

            val playableAudioUrl = resolved.audioUrl?.takeIf { it.isNotBlank() }
                ?: resolved.videoUrl.takeIf { it.isNotBlank() }
            if (playableAudioUrl.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    onStatusMessage("La pista \"${item.title}\" no devolvió audio reproducible. Prueba con otra.")
                }
                return@launch
            }
            val canUseVideoCanvas = !dataSaver && cacheManager.isStreamingVideoAllowedByNetwork()
            val initialVideoUri = if (canUseVideoCanvas) {
                cacheManager.getCachedVideoFile(item.videoId)?.absolutePath
                    ?: resolved.videoUrl.takeIf { it.isNotBlank() && it != playableAudioUrl }
            } else null

            val playingTrack = buildStreamingTrackInternal(
                item = item.copy(
                    durationSeconds = if (item.durationSeconds > 0) item.durationSeconds else resolved.durationSeconds,
                    thumbnailUrl = item.thumbnailUrl.ifBlank { resolved.coverUrl.orEmpty() }
                ),
                audioUri = playableAudioUrl,
                videoUri = initialVideoUri
            )

            val queueTracks = buildQueueTracksWithSelectedInternal(item, playingTrack, contextQueue)
            val startIdx = queueTracks.indexOfFirst { it.id == playingTrack.id }.coerceAtLeast(0)

            try {
                withContext(Dispatchers.Main) {
                    audioPlayer.playTrackList(queueTracks, startIdx)
                    loadLyricsIfAllowed(playingTrack)
                }
            } catch (t: Throwable) {
                // v0.2.1-beta.1f: si ExoPlayer rechaza la URL (ej. 403 tardío), reintentar con bypass y reanudar.
                withContext(Dispatchers.Main) {
                    onStatusMessage("Reintentando \"${item.title}\" con ruta alternativa...")
                }
                val retry = StreamingApiService.resolvePlayableStreamWithYtDlp(
                    context, item, forceBypassYtDlp = true, audioQuality = effectiveQuality
                ).getOrNull()
                val retryUrl = retry?.audioUrl?.takeIf { it.isNotBlank() } ?: retry?.videoUrl?.takeIf { it.isNotBlank() }
                if (!retryUrl.isNullOrBlank()) {
                    val retryTrack = playingTrack.copy(uriString = retryUrl)
                    val retryQueue = buildQueueTracksWithSelectedInternal(item, retryTrack, contextQueue)
                    val retryIdx = retryQueue.indexOfFirst { it.id == retryTrack.id }.coerceAtLeast(0)
                    try {
                        withContext(Dispatchers.Main) {
                            audioPlayer.playTrackList(retryQueue, retryIdx)
                            loadLyricsIfAllowed(retryTrack)
                        }
                    } catch (_: Throwable) {
                        withContext(Dispatchers.Main) {
                            onStatusMessage("No se pudo reproducir \"${item.title}\". Intenta con otra pista.")
                        }
                        return@launch
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onStatusMessage("No se pudo reproducir \"${item.title}\". Intenta con otra pista.")
                    }
                    return@launch
                }
            }

            if (dataSaver) return@launch
            // 3. Pre-descargar de forma escalonada en la caché temporal para no competir con el buffer inicial
            //    ni recrear el decodificador de video/audio en mitad de la canción activa.
            //    (Omitido por completo con Ahorro de Datos para no gastar MB en 2do plano).
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

            // 5. Preparar por adelantado una canción similar recomendada (pausado en ahorro)
            // + calentar el vecino de la cola (capas A+B) para Siguiente sin silencio.
            prefetchNextSimilarTrackForRadioInternal(playingTrack)
            warmNextQueueTrackInternal(playingTrack)
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
internal fun StreamingModeCoordinator.handleStreamingTrackTransitionRequestInternal(
    targetTrack: Track,
    isAutoCrossfade: Boolean
): Boolean {
    val videoId = targetTrack.streamingVideoId ?: return false
    val platform = targetTrack.streamingPlatform ?: StreamingSourcePlatform.YT_MUSIC
    val dataSaverT = cacheManager.isStreamingDataSaverEnabled()
    val qualityT = if (dataSaverT) {
        com.example.core.model.StreamingAudioQuality.AHORRO_OPUS_48K
    } else cacheManager.getStreamingAudioQuality()

    // Si el audio ya fue pre-descargado en la caché temporal de 30 minutos, se reproduce al instante
    val cachedAudio = cacheManager.getCachedAudioFile(videoId)
    val cachedVideo = if (!dataSaverT && cacheManager.isStreamingVideoAllowedByNetwork()) {
        cacheManager.getCachedVideoFile(videoId)
    } else null

    if (cachedAudio != null) {
        val readyTrack = targetTrack.copy(
            uriString = cachedAudio.absolutePath,
            videoUri = cachedVideo?.absolutePath ?: if (!dataSaverT && cacheManager.isStreamingVideoAllowedByNetwork()) targetTrack.videoUri else null
        )
        scope.launch(Dispatchers.Main) {
            audioPlayer.appendOrUpdateStreamingTrack(readyTrack)
            audioPlayer.playTrack(readyTrack, isAutoCrossfadeOverlap = isAutoCrossfade)
            if (!dataSaverT) onLoadLyricsForTrack(readyTrack)
        }
        if (!dataSaverT) {
            prefetchNextSimilarTrackForRadioInternal(readyTrack)
            warmNextQueueTrackInternal(readyTrack)
        }
        return true
    }

    // Capa A Head-Start: si el calentador ya resolvió URL fresca, arrancar sin yt-dlp (~200-400ms).
    val warmed = try { getWarmedStream(videoId) } catch (_: Throwable) { null }
    if (warmed != null && warmed.audioUrl.isNotBlank()) {
        val warmedTrack = targetTrack.copy(
            uriString = warmed.audioUrl,
            videoUri = warmed.videoUrl.takeIf { it.isNotBlank() }
                ?: if (!dataSaverT && cacheManager.isStreamingVideoAllowedByNetwork()) targetTrack.videoUri else null
        )
        scope.launch(Dispatchers.Main) {
            audioPlayer.appendOrUpdateStreamingTrack(warmedTrack)
            audioPlayer.playTrack(warmedTrack, isAutoCrossfadeOverlap = isAutoCrossfade)
            if (!dataSaverT) onLoadLyricsForTrack(warmedTrack)
        }
        if (!dataSaverT) {
            scope.launch(Dispatchers.IO) {
                try {
                    cacheManager.prefetchAudioAheadAsync(videoId, warmed.audioUrl) { file ->
                        audioPlayer.appendOrUpdateStreamingTrack(
                            warmedTrack.copy(uriString = file.absolutePath),
                            updateCurrentTrackIfSame = false
                        )
                    }
                } catch (_: Throwable) {}
            }
            prefetchNextSimilarTrackForRadioInternal(warmedTrack)
            warmNextQueueTrackInternal(warmedTrack)
        }
        return true
    }

    // Si aún no tiene archivo en caché o su URL era un marcador pendiente, consultamos la API con cascada unificada yt-dlp (YT Music y YouTube)
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
            val resolved = StreamingApiService.resolvePlayableStreamWithYtDlp(
                context, baseItem, audioQuality = qualityT
            ).getOrNull()
            if (resolved != null) {
                val playableAudioUrl = resolved.audioUrl?.takeIf { it.isNotBlank() } ?: resolved.videoUrl
                val allowVideo = !dataSaverT && cacheManager.isStreamingVideoAllowedByNetwork()
                val resolvedTrack = targetTrack.copy(
                    uriString = playableAudioUrl,
                    videoUri = if (allowVideo) resolved.videoUrl.takeIf { it.isNotBlank() } else null,
                    durationMs = if (targetTrack.durationMs > 0) targetTrack.durationMs else resolved.durationSeconds * 1000L
                )
                withContext(Dispatchers.Main) {
                    audioPlayer.appendOrUpdateStreamingTrack(resolvedTrack)
                    audioPlayer.playTrack(resolvedTrack, isAutoCrossfadeOverlap = isAutoCrossfade)
                    if (!dataSaverT) onLoadLyricsForTrack(resolvedTrack)
                }
                if (dataSaverT) return@launch
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
                prefetchNextSimilarTrackForRadioInternal(resolvedTrack)
                warmNextQueueTrackInternal(resolvedTrack)
            } else {
                // Último recurso capa B: si hay head-start local, sonar 20-30s en vez de silencio.
                var playedFallback = false
                try {
                    val headFallback = cacheManager.getCachedHeadStartFile(videoId)
                    if (headFallback != null) {
                        val headTrack = targetTrack.copy(uriString = headFallback.absolutePath)
                        withContext(Dispatchers.Main) {
                            audioPlayer.appendOrUpdateStreamingTrack(headTrack)
                            audioPlayer.playTrack(headTrack, isAutoCrossfadeOverlap = isAutoCrossfade)
                        }
                        playedFallback = true
                    }
                } catch (_: Throwable) {}
                if (!playedFallback) {
                    // v0.2.1-beta.1f: avisar en vez de quedarse en silencio al cambiar de pista.
                    withContext(Dispatchers.Main) {
                        onStatusMessage("No se pudo cargar \"${targetTrack.title}\". Se salta a la siguiente disponible.")
                        try { audioPlayer.playNext() } catch (_: Throwable) {}
                    }
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
 * Recuperación transparente cuando ExoPlayer recibe `ERROR_CODE_IO_BAD_HTTP_STATUS` (ej. HTTP 403 Forbidden):
 * 1. Si `StreamingCacheManager` ya descargó el archivo en la caché temporal, conmuta al archivo local y
 *    continúa desde [resumePositionMs] sin interrupción.
 * 2. Si aún no está en caché, solicita un stream fresco con `forceBypassYtDlp = true` (respaldo directo)
 *    y reanuda la reproducción en [resumePositionMs].
 */
internal fun StreamingModeCoordinator.handleStreamingHttpErrorRecoveryInternal(
    failedTrack: Track,
    resumePositionMs: Long
): Boolean {
    val videoId = failedTrack.streamingVideoId ?: return false
    val platform = failedTrack.streamingPlatform ?: StreamingSourcePlatform.YT_MUSIC

    val dataSaverR = cacheManager.isStreamingDataSaverEnabled()
    val cachedAudio = cacheManager.getCachedAudioFile(videoId)
    val cachedVideo = if (!dataSaverR && cacheManager.isStreamingVideoAllowedByNetwork()) {
        cacheManager.getCachedVideoFile(videoId)
    } else null

    if (cachedAudio != null) {
        val localReadyTrack = failedTrack.copy(
            uriString = cachedAudio.absolutePath,
            videoUri = cachedVideo?.absolutePath ?: if (dataSaverR) null else failedTrack.videoUri
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
                forceBypassYtDlp = true,
                audioQuality = if (dataSaverR) {
                    com.example.core.model.StreamingAudioQuality.AHORRO_OPUS_48K
                } else cacheManager.getStreamingAudioQuality()
            ).getOrNull()
            if (resolved != null) {
                val freshAudioUrl = resolved.audioUrl?.takeIf { it.isNotBlank() } ?: resolved.videoUrl
                val allowVideo = !dataSaverR && cacheManager.isStreamingVideoAllowedByNetwork()
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
 * (cascada unificada yt-dlp → InnerTube → Invidious → WebView para YT Music y YouTube) + pre-descarga en caché temporal antes de que termine la pista actual.
 */
internal fun StreamingModeCoordinator.prefetchNextSimilarTrackForRadioInternal(currentTrack: Track) {
    // Ahorro de Datos: sin radio similar ni prefetch (solo Explorar; local intacto).
    if (cacheManager.isStreamingDataSaverEnabled()) return
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

            // Resolver con cascada unificada (yt-dlp → InnerTube → Invidious → WebView) y pre-almacenar en caché temporal de 30 minutos
            val resolved = StreamingApiService.resolvePlayableStreamWithYtDlp(
                context, similarItem, audioQuality = cacheManager.getStreamingAudioQuality()
            ).getOrNull() ?: return@launch

            val playableAudioUrl = resolved.audioUrl?.takeIf { it.isNotBlank() } ?: resolved.videoUrl
            val allowVideo = cacheManager.isStreamingVideoAllowedByNetwork()
            val cachedAudio = cacheManager.getCachedAudioFile(similarItem.videoId)
            val cachedVideo = if (allowVideo) cacheManager.getCachedVideoFile(similarItem.videoId) else null

            val nextTrack = buildStreamingTrackInternal(
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
