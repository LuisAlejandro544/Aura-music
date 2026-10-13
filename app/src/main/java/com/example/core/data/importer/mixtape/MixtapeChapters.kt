package com.example.core.data.importer.mixtape

import com.example.core.model.MixtapeChapter
import com.example.core.model.Track

/**
 * Duración esperada y marcas de capítulos con crossfade.
 * Rol arquitectónico: pieza de MixtapeEngine (fachada pública intacta).
 */
internal object MixtapeChapters {
    private const val TAG = "MixtapeEngine"

    fun expectedDurationMs(tracks: List<Track>, crossfadeSec: Int): Long {
        val sumDurations = tracks.sumOf { it.durationMs.coerceAtLeast(10_000L) }
        val crossfadeOverlapTotal = (tracks.size - 1) * (crossfadeSec * 1000L)
        return (sumDurations - crossfadeOverlapTotal).coerceAtLeast(10_000L)
    }

    fun computeChapters(tracks: List<Track>, crossfadeSec: Int): List<MixtapeChapter> {
        val chapters = mutableListOf<MixtapeChapter>()
        var currentOffsetMs = 0L
        val crossfadeMs = crossfadeSec * 1000L

        for ((index, track) in tracks.withIndex()) {
            val trackDur = track.durationMs.coerceAtLeast(10_000L)
            val startMs = currentOffsetMs
            val endMs = if (index == tracks.size - 1) {
                startMs + trackDur
            } else {
                startMs + trackDur
            }

            chapters.add(
                MixtapeChapter(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    albumArtPath = track.albumArtPath,
                    videoUri = track.videoUri,
                    isVideoLoop = track.isVideoLoop,
                    startMs = startMs,
                    endMs = endMs
                )
            )

            // El siguiente capítulo comienza antes de que termine el actual por la duración del fundido cruzado
            currentOffsetMs = (endMs - crossfadeMs).coerceAtLeast(startMs + 1000L)
        }

        return chapters
    }
}
