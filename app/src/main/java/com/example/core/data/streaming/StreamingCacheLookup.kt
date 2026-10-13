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
        // Acepta tanto .m4a (AAC) como .opus (Opus/WebM) según la calidad elegida por el usuario.
        val candidates = listOf(
            File(s.audioDir, "stream_audio_$safeId.opus"),
            File(s.audioDir, "stream_audio_$safeId.m4a")
        )
        for (file in candidates) {
            if (file.exists() && file.length() > StreamingCacheState.MIN_VALID_AUDIO_BYTES) {
                val ageMs = System.currentTimeMillis() - file.lastModified()
                if (ageMs <= StreamingCacheConfig.CACHE_TTL_MS) {
                    s.touchFile(file)
                    return file
                } else {
                    try { file.delete() } catch (_: Exception) {}
                }
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

    /**
     * Head-Start (capa B): primeros ~2 MB del siguiente audio, descargados por el
     * calentador mientras suena la pista actual. Sirve como arranque instantáneo
     * de último recurso si la URL fresca falla (mejor 20-30s locales que silencio).
     * TTL corto de 45 min, igual que las URLs firmadas de googlevideo.com.
     */
    fun getCachedHeadStartFile(videoId: String): File? {
        val safeId = s.sanitizeId(videoId)
        if (safeId.isBlank()) return null
        val candidates = listOf(
            File(s.audioDir, "stream_head_$safeId.opus"),
            File(s.audioDir, "stream_head_$safeId.m4a")
        )
        for (file in candidates) {
            if (file.exists() && file.length() > StreamingCacheState.MIN_VALID_AUDIO_BYTES) {
                val ageMs = System.currentTimeMillis() - file.lastModified()
                if (ageMs <= HEAD_START_TTL_MS) {
                    s.touchFile(file)
                    return file
                } else {
                    try { file.delete() } catch (_: Exception) {}
                }
            }
        }
        return null
    }

    companion object {
        const val HEAD_START_TTL_MS = 45L * 60L * 1000L
    }
}
