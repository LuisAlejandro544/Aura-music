package com.example.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Representa un capítulo o segmento dentro de una pista de Mixtape continuo.
 * Cada capítulo corresponde a una de las canciones que componen la mezcla,
 * almacenando su marca de tiempo exacta de inicio y fin, así como su carátula
 * y Video Canvas asociado para conmutación reactiva en tiempo real.
 */
data class MixtapeChapter(
    val trackId: Long,
    val title: String,
    val artist: String,
    val albumArtPath: String?,
    val videoUri: String?,
    val isVideoLoop: Boolean,
    val startMs: Long,
    val endMs: Long
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)

    fun containsPosition(positionMs: Long): Boolean {
        return positionMs in startMs until endMs
    }

    /**
     * Convierte este capítulo en una pista virtual para NowPlaying, MiniPlayer
     * y el extractor de colores de carátula/video.
     */
    fun toVirtualTrack(parentTrack: Track): Track {
        return parentTrack.copy(
            title = this.title,
            artist = this.artist,
            albumArtPath = this.albumArtPath ?: parentTrack.albumArtPath,
            videoUri = this.videoUri,
            isVideoLoop = this.isVideoLoop
        )
    }

    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("trackId", trackId)
        put("title", title)
        put("artist", artist)
        put("albumArtPath", albumArtPath ?: "")
        put("videoUri", videoUri ?: "")
        put("isVideoLoop", isVideoLoop)
        put("startMs", startMs)
        put("endMs", endMs)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): MixtapeChapter {
            return MixtapeChapter(
                trackId = json.optLong("trackId", 0L),
                title = json.optString("title", "Canción"),
                artist = json.optString("artist", "Artista desconocido"),
                albumArtPath = json.optString("albumArtPath", "").takeIf { it.isNotBlank() },
                videoUri = json.optString("videoUri", "").takeIf { it.isNotBlank() },
                isVideoLoop = json.optBoolean("isVideoLoop", false),
                startMs = json.optLong("startMs", 0L),
                endMs = json.optLong("endMs", 0L)
            )
        }
    }
}

/**
 * Metadatos completos de un Mixtape continuo generado con FFmpeg.
 */
data class MixtapeMetadata(
    val mixtapeId: Long,
    val title: String,
    val totalDurationMs: Long,
    val crossfadeSeconds: Int,
    val chapters: List<MixtapeChapter>
) {
    fun getChapterAt(positionMs: Long): MixtapeChapter? {
        if (chapters.isEmpty()) return null
        return chapters.firstOrNull { it.containsPosition(positionMs) }
            ?: if (positionMs >= (chapters.lastOrNull()?.endMs ?: 0L)) chapters.lastOrNull() else chapters.firstOrNull()
    }

    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("mixtapeId", mixtapeId)
        put("title", title)
        put("totalDurationMs", totalDurationMs)
        put("crossfadeSeconds", crossfadeSeconds)
        val arr = JSONArray()
        chapters.forEach { arr.put(it.toJsonObject()) }
        put("chapters", arr)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): MixtapeMetadata {
            val chaptersList = mutableListOf<MixtapeChapter>()
            val arr = json.optJSONArray("chapters")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { chaptersList.add(MixtapeChapter.fromJsonObject(it)) }
                }
            }
            return MixtapeMetadata(
                mixtapeId = json.optLong("mixtapeId", 0L),
                title = json.optString("title", "Aura Mixtape"),
                totalDurationMs = json.optLong("totalDurationMs", 0L),
                crossfadeSeconds = json.optInt("crossfadeSeconds", 5),
                chapters = chaptersList
            )
        }
    }
}
