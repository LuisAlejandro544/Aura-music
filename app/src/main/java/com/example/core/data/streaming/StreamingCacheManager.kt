package com.example.core.data.streaming

import android.content.Context
import com.example.core.model.StreamingCacheConfig
import com.example.core.model.StreamingVideoNetworkPolicy
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Gestor de Caché Temporal Inteligente para el Modo Streaming (Audio + Video Canvas).
 *
 * Rol Arquitectónico:
 * - Almacena bloques pre-descargados por adelantado (Read-Ahead Buffer no lineal) tanto de música
 *   como de Video Canvas en `cacheDir/streaming_media/`.
 * - Permite reproducción instantánea a 0ms cuando el usuario retrocede a la canción anterior o repite en bucle.
 * - Aplica expiración automática tras 30 minutos sin usarse (TTL = 30 min) y desalojo LRU (los más viejos
 *   se eliminan primero) respetando el límite configurable por el usuario (50 MB por defecto, máximo 500 MB).
 * - Evalúa en tiempo real la política de red para Video Canvas en streaming (Solo Wi-Fi, Siempre o Desactivado).
 *
 * Rol arquitectónico: fachada pública intacta que delega en 6 piezas internas.
 */
class StreamingCacheManager(private val context: Context) {

    private val s = StreamingCacheState(context)
    private val lookup = StreamingCacheLookup(s)
    private val maintenance = StreamingCacheMaintenance(s)
    private val network = StreamingNetworkPolicy(s)
    private val configOps = StreamingCacheConfigOps(s) {
        s.scope.launch { maintenance.evictExpiredAndOverLimit() }
    }
    private val prefetcher = StreamingPrefetcher(
        s = s,
        lookup = lookup,
        requestEvict = { maintenance.evictExpiredAndOverLimit() },
        isVideoAllowed = { network.isStreamingVideoAllowedByNetwork() }
    )

    val cacheRootDir: File get() = s.rootDir
    val audioCacheDir: File get() = s.audioDir
    val videoCacheDir: File get() = s.videoDir
    val artworkCacheDir: File get() = s.artworkDir

    val cacheConfig: StateFlow<StreamingCacheConfig> = s.config.asStateFlow()

    init {
        s.scope.launch {
            maintenance.evictExpiredAndOverLimit()
        }
    }

    fun setMaxCacheSizeMb(sizeMb: Int) =
        configOps.setMaxCacheSizeMb(sizeMb)

    fun setVideoNetworkPolicy(policy: StreamingVideoNetworkPolicy) =
        configOps.setVideoNetworkPolicy(policy)

    fun setAudioQuality(quality: com.example.core.model.StreamingAudioQuality) =
        configOps.setAudioQuality(quality)

    fun setDataSaverEnabled(enabled: Boolean) =
        configOps.setDataSaverEnabled(enabled)

    /** Ahorro activo: bloquea video + letras auto + prefetch solo en Explorar/streaming. Local intacto. */
    fun isStreamingDataSaverEnabled(): Boolean =
        cacheConfig.value.dataSaverEnabled

    fun getStreamingAudioQuality(): com.example.core.model.StreamingAudioQuality =
        cacheConfig.value.audioQuality

    /** Letras automáticas online solo si NO hay ahorro en streaming. Local siempre permitido. */
    fun isStreamingLyricsAutoFetchAllowed(): Boolean =
        !cacheConfig.value.dataSaverEnabled

    fun isStreamingVideoAllowedByNetwork(): Boolean =
        network.isStreamingVideoAllowedByNetwork()

    fun isWifiConnected(): Boolean =
        network.isWifiConnected()

    fun getCachedAudioFile(videoId: String): File? =
        lookup.getCachedAudioFile(videoId)

    fun getCachedVideoFile(videoId: String): File? =
        lookup.getCachedVideoFile(videoId)

    fun getCachedArtworkFile(videoId: String): File? =
        lookup.getCachedArtworkFile(videoId)

    fun prefetchAudioAheadAsync(
        videoId: String,
        streamUrl: String,
        onReadyForLocalPlayback: ((File) -> Unit)? = null
    ) = prefetcher.prefetchAudioAheadAsync(videoId, streamUrl, onReadyForLocalPlayback)

    fun prefetchVideoCanvasAsync(
        videoId: String,
        videoStreamUrl: String,
        onVideoCached: ((File) -> Unit)? = null
    ) = prefetcher.prefetchVideoCanvasAsync(videoId, videoStreamUrl, onVideoCached)

    suspend fun cacheArtworkBytes(videoId: String, bytes: ByteArray): File? =
        maintenance.cacheArtworkBytes(videoId, bytes)

    suspend fun evictExpiredAndOverLimit() =
        maintenance.evictExpiredAndOverLimit()

    suspend fun clearAllStreamingCache() =
        maintenance.clearAllStreamingCache()

    suspend fun refreshStats() =
        maintenance.refreshStats()
}
