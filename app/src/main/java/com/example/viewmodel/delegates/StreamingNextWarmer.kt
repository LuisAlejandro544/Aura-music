package com.example.viewmodel.delegates

import com.example.core.data.streaming.StreamingApiService
import com.example.core.model.StreamingSearchItem
import com.example.core.model.StreamingSourcePlatform
import com.example.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Calentador del vecino (capas A + B Head-Start).
 * Rol arquitectónico: extensiones internas de StreamingModeCoordinator; la API pública no cambia.
 *
 * A: pre-resuelve la URL del siguiente en cola mientras suena el actual, guardándola
 * en [StreamingModeCoordinator.warmedStreamRegistry] y actualizando la cola con la URL
 * HTTP directa. Así pulsar Siguiente arranca el buffer en ~200-400ms sin invocar a yt-dlp.
 *
 * B: descarga por adelantado los primeros segundos (head 2MB) + prefetch completo
 * del siguiente audio, para que la transición no dependa de la red en ese instante.
 * Solo 1 pista por delante, cancelable, y nada con Ahorro de Datos activo.
 */
internal fun StreamingModeCoordinator.warmNextQueueTrackInternal(currentTrack: Track) {
    if (cacheManager.isStreamingDataSaverEnabled()) return
    val videoIdCurrent = currentTrack.streamingVideoId ?: return
    playedVideoIdsHistory.add(videoIdCurrent)

    val queue = try { audioPlayer.queue.value } catch (_: Throwable) { emptyList() }
    if (queue.size < 2) return
    // Con aleatorio no se puede predecir el vecino; no calentar para no gastar datos.
    val shuffleOn = try { audioPlayer.shuffleEnabled.value } catch (_: Throwable) { false }
    if (shuffleOn) return

    val currentIdx = queue.indexOfFirst { it.id == currentTrack.id }.let {
        if (it != -1) it else try { audioPlayer.currentIndex.value } catch (_: Throwable) { -1 }
    }.coerceIn(0, queue.size - 1)
    val nextIdx = currentIdx + 1
    if (nextIdx >= queue.size) return
    val nextTrack = queue[nextIdx]
    if (!nextTrack.isStreamingTrack) return
    val nextVideoId = nextTrack.streamingVideoId ?: return
    if (nextVideoId == videoIdCurrent) return

    // Ya instantáneo en caché completa: nada que calentar.
    try {
        if (cacheManager.getCachedAudioFile(nextVideoId) != null) return
    } catch (_: Throwable) {}

    // Ya calentado y fresco: asegurar que el prefetch completo corre y salir.
    val alreadyWarmed = try { getWarmedStream(nextVideoId) } catch (_: Throwable) { null }
    if (alreadyWarmed != null && alreadyWarmed.audioUrl.isNotBlank()) {
        try {
            cacheManager.prefetchAudioAheadAsync(nextVideoId, alreadyWarmed.audioUrl) { file ->
                audioPlayer.appendOrUpdateStreamingTrack(
                    nextTrack.copy(uriString = file.absolutePath),
                    updateCurrentTrackIfSame = false
                )
            }
        } catch (_: Throwable) {}
        return
    }

    // Si el vecino ya trae URL HTTP directa (no es página sin resolver), solo pre-descargar.
    val nextUri = nextTrack.uriString
    val isUnresolved = try {
        audioPlayer.isUnresolvedStreamingPageUrl(nextUri)
    } catch (_: Throwable) { true }
    if (!isUnresolved && nextUri.startsWith("http", ignoreCase = true)) {
        nextTrackWarmerJob?.cancel()
        nextTrackWarmerJob = scope.launch(Dispatchers.IO) {
            try {
                cacheManager.prefetchHeadStartAsync(nextVideoId, nextUri)
                cacheManager.prefetchAudioAheadAsync(nextVideoId, nextUri) { file ->
                    audioPlayer.appendOrUpdateStreamingTrack(
                        nextTrack.copy(uriString = file.absolutePath),
                        updateCurrentTrackIfSame = false
                    )
                }
            } catch (_: Throwable) {}
        }
        return
    }

    nextTrackWarmerJob?.cancel()
    nextTrackWarmerJob = scope.launch(Dispatchers.IO) {
        try {
            val platform = nextTrack.streamingPlatform ?: StreamingSourcePlatform.YT_MUSIC
            val baseItem = itemRegistry[nextVideoId] ?: StreamingSearchItem(
                videoId = nextVideoId,
                title = nextTrack.title,
                artist = nextTrack.artist,
                album = nextTrack.album,
                durationSeconds = nextTrack.durationMs / 1000L,
                thumbnailUrl = nextTrack.albumArtPath ?: "https://i.ytimg.com/vi/$nextVideoId/hqdefault.jpg",
                platform = platform
            )
            val resolved = StreamingApiService.resolvePlayableStreamWithYtDlp(
                context, baseItem, audioQuality = cacheManager.getStreamingAudioQuality()
            ).getOrNull() ?: return@launch
            val playableAudioUrl = resolved.audioUrl?.takeIf { it.isNotBlank() } ?: resolved.videoUrl
            if (playableAudioUrl.isBlank()) return@launch
            val allowVideo = cacheManager.isStreamingVideoAllowedByNetwork()
            val warmedVideoUrl = if (allowVideo) resolved.videoUrl.takeIf { it.isNotBlank() } else null

            warmedStreamRegistry[nextVideoId] = StreamingModeCoordinator.WarmedStreamEntry(
                audioUrl = playableAudioUrl,
                videoUrl = warmedVideoUrl.orEmpty()
            )
            // Dejar la cola con URL directa para arranque en ~200-400ms al pulsar Siguiente.
            try {
                audioPlayer.appendOrUpdateStreamingTrack(
                    nextTrack.copy(
                        uriString = playableAudioUrl,
                        videoUri = warmedVideoUrl ?: nextTrack.videoUri
                    ),
                    updateCurrentTrackIfSame = false
                )
            } catch (_: Throwable) {}

            // Capa B: head rápido (2MB) + prefetch completo en 2do plano.
            try {
                cacheManager.prefetchHeadStartAsync(nextVideoId, playableAudioUrl)
                cacheManager.prefetchAudioAheadAsync(nextVideoId, playableAudioUrl) { file ->
                    audioPlayer.appendOrUpdateStreamingTrack(
                        nextTrack.copy(
                            uriString = file.absolutePath,
                            videoUri = warmedVideoUrl ?: nextTrack.videoUri
                        ),
                        updateCurrentTrackIfSame = false
                    )
                }
            } catch (_: Throwable) {}
        } catch (_: Throwable) {}
    }
}
