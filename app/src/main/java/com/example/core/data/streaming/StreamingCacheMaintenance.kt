package com.example.core.data.streaming

import com.example.core.model.StreamingCacheConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Expiración TTL, purga LRU, limpieza total y estadísticas.
 * Rol arquitectónico: pieza de StreamingCacheManager (fachada pública intacta).
 */
internal class StreamingCacheMaintenance(private val s: StreamingCacheState) {
    suspend fun cacheArtworkBytes(videoId: String, bytes: ByteArray): File? = withContext(Dispatchers.IO) {
        try {
            val safeId = s.sanitizeId(videoId)
            val file = File(s.artworkDir, "stream_art_$safeId.webp")
            FileOutputStream(file).use { it.write(bytes) }
            s.touchFile(file)
            evictExpiredAndOverLimit()
            file
        } catch (_: Exception) {
            null
        }
    }

    suspend fun evictExpiredAndOverLimit() = withContext(Dispatchers.IO) {
        s.mutex.withLock {
            try {
                val now = System.currentTimeMillis()
                val ttlMs = StreamingCacheConfig.CACHE_TTL_MS
                val maxBytes = s.config.value.maxCacheSizeMb.toLong() * 1024L * 1024L

                val allFiles = s.collectAllCacheFiles()

                // Fase 1: Eliminar archivos expirados (> 30 minutos sin usarse) o .part huérfanos antiguos
                for (file in allFiles) {
                    val age = now - file.lastModified()
                    if (age > ttlMs) {
                        try { file.delete() } catch (_: Exception) {}
                    }
                }

                // Fase 2: Desalojo LRU (los más viejos primero) si supera el límite configurado en MB
                val remainingFiles = s.collectAllCacheFiles()
                    .filter { !it.name.endsWith(".part") }
                    .sortedBy { it.lastModified() } // Más antiguos primero

                var totalBytes = remainingFiles.sumOf { it.length() }
                for (file in remainingFiles) {
                    if (totalBytes <= maxBytes) break
                    val len = file.length()
                    if (try { file.delete() } catch (_: Exception) { false }) {
                        totalBytes = (totalBytes - len).coerceAtLeast(0L)
                    }
                }

                refreshLocked()
            } catch (_: Exception) {}
        }
    }

    suspend fun clearAllStreamingCache() = withContext(Dispatchers.IO) {
        s.mutex.withLock {
            s.jobs.values.forEach { it.cancel() }
            s.jobs.clear()
            s.collectAllCacheFiles().forEach { file ->
                try { file.delete() } catch (_: Exception) {}
            }
            refreshLocked()
        }
    }

    suspend fun refreshStats() = withContext(Dispatchers.IO) {
        s.mutex.withLock {
            refreshLocked()
        }
    }

    private fun refreshLocked() {
        val files = s.collectAllCacheFiles().filter { !it.name.endsWith(".part") }
        val totalBytes = files.sumOf { it.length() }
        val audioCount = s.audioDir.listFiles()?.count { it.isFile && !it.name.endsWith(".part") } ?: 0
        val videoCount = s.videoDir.listFiles()?.count { it.isFile && !it.name.endsWith(".part") } ?: 0

        s.config.value = s.config.value.copy(
            currentUsedBytes = totalBytes,
            cachedAudioCount = audioCount,
            cachedVideoCount = videoCount
        )
    }
}
