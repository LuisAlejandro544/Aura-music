package com.example.core.data.streaming

import com.example.core.model.StreamingCacheConfig
import java.io.File

/**
 * Lectura de bloques cacheados de audio, video y carátulas con TTL.
 * Rol arquitectónico: pieza de StreamingCacheManager (fachada pública intacta).
 */
internal class StreamingCacheLookup(private val s: StreamingCacheState) {
    fun getCachedAudioFile(videoId: String): File? {
        val safeId = s.sanitizeId(videoId)
        val file = File(s.audioDir, "stream_audio_$safeId.m4a")
        if (file.exists() && file.length() > StreamingCacheState.MIN_VALID_AUDIO_BYTES) {
            val ageMs = System.currentTimeMillis() - file.lastModified()
            if (ageMs <= StreamingCacheConfig.CACHE_TTL_MS) {
                s.touchFile(file)
                return file
            } else {
                try { file.delete() } catch (_: Exception) {}
            }
        }
        return null
    }

    fun getCachedVideoFile(videoId: String): File? {
        val safeId = s.sanitizeId(videoId)
        val file = File(s.videoDir, "stream_video_$safeId.mp4")
        if (file.exists() && file.length() > StreamingCacheState.MIN_VALID_VIDEO_BYTES) {
            val ageMs = System.currentTimeMillis() - file.lastModified()
            if (ageMs <= StreamingCacheConfig.CACHE_TTL_MS) {
                s.touchFile(file)
                return file
            } else {
                try { file.delete() } catch (_: Exception) {}
            }
        }
        return null
    }

    fun getCachedArtworkFile(videoId: String): File? {
        val safeId = s.sanitizeId(videoId)
        val file = File(s.artworkDir, "stream_art_$safeId.webp")
        if (file.exists() && file.length() > 512L) {
            s.touchFile(file)
            return file
        }
        return null
    }
}
