package com.example.core.data.streaming

import com.example.core.model.StreamingCacheConfig
import com.example.core.model.StreamingVideoNetworkPolicy
import kotlinx.coroutines.launch

/**
 * Límite de caché y política de red de video con persistencia.
 * Rol arquitectónico: pieza de StreamingCacheManager (fachada pública intacta).
 */
internal class StreamingCacheConfigOps(
    private val s: StreamingCacheState,
    private val requestEvict: () -> Unit
) {
    fun setMaxCacheSizeMb(sizeMb: Int) {
        val clamped = sizeMb.coerceIn(
            StreamingCacheConfig.MIN_CACHE_SIZE_MB,
            StreamingCacheConfig.MAX_CACHE_SIZE_MB
        )
        s.prefs.edit().putInt(StreamingCacheState.KEY_MAX_CACHE_MB, clamped).apply()
        s.config.value = s.config.value.copy(maxCacheSizeMb = clamped)
        s.scope.launch {
            requestEvict()
        }
    }

    fun setVideoNetworkPolicy(policy: StreamingVideoNetworkPolicy) {
        s.prefs.edit().putInt(StreamingCacheState.KEY_VIDEO_NETWORK_POLICY, policy.id).apply()
        s.config.value = s.config.value.copy(videoNetworkPolicy = policy)
    }
}
