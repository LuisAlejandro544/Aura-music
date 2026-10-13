package com.example.feature.settings.components.media

/**
 * Utilidades de formato de almacenamiento para medios de Aura Music.
 */
internal object AspectTagCache {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String?>()

    fun getOrPut(uri: String, compute: () -> String?): String? {
        if (cache.containsKey(uri)) return cache[uri]
        val value = compute()
        if (value != null) cache[uri] = value
        return value
    }
}

object StoredMediaFormatUtils {
    fun formatBytes(bytes: Long): String {        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format("%.1f MB", mb)
        } else {
            String.format("%.0f KB", kb)
        }
    }
}
