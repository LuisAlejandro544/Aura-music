package com.example.viewmodel.delegates

import com.example.core.data.importer.FFmpegNativeEngine
import com.example.core.data.streaming.StreamingApiService
import com.example.core.model.StreamingSearchItem
import com.example.core.model.StreamingSourcePlatform
import com.example.core.model.StreamingVideoNetworkPolicy
import com.example.core.model.Track
import com.example.core.playback.AuraDownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Descarga permanente y ajustes de caché temporal.
 * Rol arquitectónico: extensiones internas de StreamingModeCoordinator; la API pública no cambia.
 */
/**
 * Descarga permanentemente una canción o video de streaming (desde el reproductor online o desde Now Playing)
 * hacia la biblioteca local (`songs/`, `images/`, `videos/`, `lyrics/`) mediante `AuraDownloadService`,
 * incluyendo su audio y su Video Canvas 480p sin interrumpir la canción que está sonando.
 */
internal fun StreamingModeCoordinator.downloadStreamingItemPermanentlyInternal(
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
            val resolvedResult = StreamingApiService.resolvePlayableStreamWithYtDlp(
                context, item, audioQuality = cacheManager.getStreamingAudioQuality()
            )
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
internal fun StreamingModeCoordinator.downloadCurrentStreamingTrackPermanentlyInternal(track: Track) {
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
    downloadStreamingItemPermanentlyInternal(item, includeVideoCanvas = true)
}

internal fun StreamingModeCoordinator.setStreamingCacheMaxSizeMbInternal(sizeMb: Int) {
    cacheManager.setMaxCacheSizeMb(sizeMb)
}

internal fun StreamingModeCoordinator.setStreamingAudioQualityInternal(quality: com.example.core.model.StreamingAudioQuality) {
    cacheManager.setAudioQuality(quality)
}

internal fun StreamingModeCoordinator.setStreamingDataSaverEnabledInternal(enabled: Boolean) {
    cacheManager.setDataSaverEnabled(enabled)
    // Al activar el ahorro, cortar el video de la pista de streaming actual al instante (local intacto).
    val cur = audioPlayer.currentTrack.value
    if (enabled && cur != null && cur.isStreamingTrack) {
        audioPlayer.appendOrUpdateStreamingTrack(cur.copy(videoUri = null))
    }
}

internal fun StreamingModeCoordinator.setStreamingVideoNetworkPolicyInternal(policy: StreamingVideoNetworkPolicy) {
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

internal fun StreamingModeCoordinator.clearStreamingCacheInternal() {
    scope.launch {
        cacheManager.clearAllStreamingCache()
        onStatusMessage("Caché temporal de streaming liberada.")
    }
}

internal fun StreamingModeCoordinator.refreshCacheStatsInternal() {
    scope.launch {
        cacheManager.refreshStats()
    }
}
