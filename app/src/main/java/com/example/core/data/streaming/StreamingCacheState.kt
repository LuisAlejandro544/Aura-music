package com.example.core.data.streaming

import android.content.Context
import com.example.core.model.StreamingCacheConfig
import com.example.core.model.StreamingVideoNetworkPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Holder interno de estado compartido: prefs, scope, mutex, dirs y config.
 * Rol arquitectónico: pieza de StreamingCacheManager (fachada pública intacta).
 */
internal class StreamingCacheState(val context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val mutex = Mutex()

    val rootDir: File = File(context.cacheDir, "streaming_media").apply {
        if (!exists()) mkdirs()
    }
    val audioDir: File = File(rootDir, "audio").apply {
        if (!exists()) mkdirs()
    }
    val videoDir: File = File(rootDir, "video").apply {
        if (!exists()) mkdirs()
    }
    val artworkDir: File = File(rootDir, "artwork").apply {
        if (!exists()) mkdirs()
    }

    val jobs = ConcurrentHashMap<String, Job>()
    val config = MutableStateFlow(loadInitial())

    private fun loadInitial(): StreamingCacheConfig {
        val maxMb = prefs.getInt(
            KEY_MAX_CACHE_MB,
            StreamingCacheConfig.DEFAULT_CACHE_SIZE_MB
        ).coerceIn(StreamingCacheConfig.MIN_CACHE_SIZE_MB, StreamingCacheConfig.MAX_CACHE_SIZE_MB)

        val policyId = prefs.getInt(KEY_VIDEO_NETWORK_POLICY, StreamingVideoNetworkPolicy.WIFI_ONLY.id)
        val policy = StreamingVideoNetworkPolicy.fromId(policyId)

        return StreamingCacheConfig(
            maxCacheSizeMb = maxMb,
            videoNetworkPolicy = policy
        )
    }

    fun sanitizeId(rawId: String): String {
        return rawId.replace(Regex("[^a-zA-Z0-9_-]"), "").take(40)
    }

    fun touchFile(file: File) {
        try {
            file.setLastModified(System.currentTimeMillis())
        } catch (_: Exception) {}
    }

    fun collectAllCacheFiles(): List<File> {
        val list = mutableListOf<File>()
        listOf(audioDir, videoDir, artworkDir).forEach { dir ->
            dir.listFiles()?.forEach { f ->
                if (f.isFile) list.add(f)
            }
        }
        return list
    }

    companion object {
    
        const val PREFS_NAME = "aura_streaming_cache_prefs"
        const val KEY_MAX_CACHE_MB = "max_cache_size_mb"
        const val KEY_VIDEO_NETWORK_POLICY = "streaming_video_network_policy"

        const val MIN_VALID_AUDIO_BYTES = 16 * 1024L
        const val MIN_VALID_VIDEO_BYTES = 32 * 1024L
        const val MAX_SINGLE_AUDIO_CACHE_BYTES = 20L * 1024L * 1024L // 20 MB máx por pista en caché
        const val MAX_SINGLE_VIDEO_CACHE_BYTES = 25L * 1024L * 1024L // 25 MB máx por video canvas en caché
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    
    }
}
