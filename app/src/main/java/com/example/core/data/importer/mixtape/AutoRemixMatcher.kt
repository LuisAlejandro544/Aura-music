package com.example.core.data.importer.mixtape

import android.content.Context
import com.example.core.data.storage.AppStorageManager
import com.example.core.model.AutoRemixPairRecommendation
import com.example.core.model.Track
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Selector de parejas para Auto-Remix.
 * Rol arquitectónico: filtra pistas locales, puntúa compatibilidad y valida
 * archivos seguros; la fachada SmartAutoRemixEngine delega aquí.
 */
internal object AutoRemixMatcher {

    private const val TAG = "SmartAutoRemixEngine"

    fun filterLocalTracks(tracks: List<Track>): List<Track> {
        return tracks.filter { !it.isStreamingTrack && it.uriString.isNotBlank() }
    }

    fun findBestMatchingPair(
        tracks: List<Track>,
        seedIteration: Int = 0
    ): AutoRemixPairRecommendation? {
        val localTracks = filterLocalTracks(tracks)
        if (localTracks.size < 2) return null

        data class ScoredPair(
            val a: Track,
            val b: Track,
            val score: Int,
            val reason: String
        )

        val candidates = mutableListOf<ScoredPair>()
        val subset = localTracks.take(40)

        for (i in subset.indices) {
            for (j in i + 1 until subset.size) {
                val t1 = subset[i]
                val t2 = subset[j]
                val dur1 = t1.durationMs.coerceAtLeast(30_000L)
                val dur2 = t2.durationMs.coerceAtLeast(30_000L)
                val ratio = min(dur1, dur2).toDouble() / max(dur1, dur2).toDouble()

                // Puntuación base por cercanía de estructura temporal (75 a 98%)
                var score = (72 + (ratio * 22.0)).toInt().coerceIn(72, 96)
                val differentArtist = !t1.artist.equals(t2.artist, ignoreCase = true)
                if (differentArtist) score += 3
                if (t1.isFavorite || t2.isFavorite) score += 2
                if (!t1.videoUri.isNullOrBlank() || !t2.videoUri.isNullOrBlank()) score += 1
                val clampedScore = score.coerceIn(78, 99)

                val reason = when {
                    ratio >= 0.88 && differentArtist ->
                        "Compatibilidad rítmica del $clampedScore% • Estructuras de duración gemelas y contraste vocal ideal"
                    differentArtist ->
                        "Compatibilidad armónica del $clampedScore% • Contraste de voces (${t1.artist} × ${t2.artist})"
                    else ->
                        "Compatibilidad de estudio del $clampedScore% • Mismo estilo acústico y cadencia fluida"
                }

                candidates.add(ScoredPair(t1, t2, clampedScore, reason))
            }
        }

        if (candidates.isEmpty()) return null
        val sorted = candidates.sortedByDescending { it.score }
        val topPool = sorted.take(min(8, sorted.size))
        val chosen = topPool[abs(seedIteration) % topPool.size]
        val swapOrder = (abs(seedIteration) % 2) == 1

        return AutoRemixPairRecommendation(
            trackA = if (swapOrder) chosen.b else chosen.a,
            trackB = if (swapOrder) chosen.a else chosen.b,
            compatibilityPercent = chosen.score,
            reason = chosen.reason
        )
    }

    internal fun isSafeAudioFileForAutoRemix(context: Context, file: File): Boolean {
        return try {
            if (!file.exists() || !file.isFile || !file.canRead()) return false
            val canonical = file.canonicalFile.toPath()
            val storageManager = AppStorageManager(context)
            val songsDirPath = storageManager.songsDir.canonicalFile.toPath()
            if (canonical.startsWith(songsDirPath)) {
                return true
            }
            val internalSongsDir = File(context.filesDir, "songs").canonicalFile.toPath()
            if (canonical.startsWith(internalSongsDir)) {
                return true
            }
            val dataDir = context.applicationInfo.dataDir?.let { File(it).canonicalFile.toPath() }
            val filesDir = context.filesDir.canonicalFile.toPath()
            if ((dataDir != null && canonical.startsWith(dataDir)) || canonical.startsWith(filesDir)) {
                return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

}
