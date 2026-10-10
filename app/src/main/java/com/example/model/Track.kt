package com.example.model

/**
 * Representa una pista de audio local importada por el usuario.
 * Contiene metadatos extraídos mediante MediaMetadataRetriever,
 * ruta de acceso de URI persistente y atributos de biblioteca.
 */
data class Track(
    val id: Long = 0,
    val title: String,
    val artist: String = "Artista desconocido",
    val album: String = "Álbum desconocido",
    val durationMs: Long = 0L,
    val uriString: String,
    val albumArtPath: String? = null,
    val videoUri: String? = null,
    val isVideoLoop: Boolean = false,
    val mimeType: String = "audio/mpeg",
    val dateAdded: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val folderName: String = "",
    val fileSizeFormatted: String = ""
) {
    /**
     * Retorna la duración en formato estándar mm:ss o hh:mm:ss
     */
    fun formattedDuration(): String {
        if (durationMs <= 0) return "0:00"
        val totalSeconds = durationMs / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    /**
     * Retorna una etiqueta de audio moderna (ej. DSD, APE, FLAC, WAV, ALAC, MOD/XM, HI-RES)
     */
    fun formatBadge(): String {
        val lowerUri = uriString.lowercase()
        val lowerMime = mimeType.lowercase()
        return when {
            lowerMime.contains("dsd") || lowerUri.endsWith(".dsf") || lowerUri.endsWith(".dff") -> "DSD HI-RES"
            lowerMime.contains("ape") || lowerMime.contains("monkeys-audio") || lowerUri.endsWith(".ape") -> "APE LOSSLESS"
            lowerMime.contains("alac") || lowerUri.endsWith(".alac") -> "ALAC"
            lowerMime.contains("aiff") || lowerUri.endsWith(".aiff") || lowerUri.endsWith(".aif") -> "AIFF"
            lowerMime.contains("wavpack") || lowerUri.endsWith(".wv") -> "WV LOSSLESS"
            lowerUri.endsWith(".mod") || lowerUri.endsWith(".xm") || lowerUri.endsWith(".it") || lowerUri.endsWith(".s3m") -> "CHIPTUNE"
            lowerMime.contains("flac") || lowerUri.endsWith(".flac") -> "FLAC"
            lowerMime.contains("wav") || lowerUri.endsWith(".wav") -> "WAV"
            lowerMime.contains("opus") || lowerUri.endsWith(".opus") -> "OPUS"
            lowerMime.contains("ogg") || lowerUri.endsWith(".ogg") -> "OGG"
            lowerMime.contains("aac") || lowerUri.endsWith(".aac") -> "AAC"
            lowerMime.contains("m4a") || lowerUri.endsWith(".m4a") -> "M4A"
            folderName.startsWith("STREAM:YT_MUSIC") -> "YT MUSIC"
            folderName.startsWith("STREAM:YOUTUBE") -> "YOUTUBE"
            else -> "MP3"
        }
    }

    /**
     * Indica si la pista actual corresponde a una reproducción en modo Streaming (ID negativo o prefijo STREAM:).
     */
    val isStreamingTrack: Boolean
        get() = id < 0L || folderName.startsWith("STREAM:")

    /**
     * Extrae el ID de video original (YouTube / YT Music) si la pista es de Streaming.
     */
    val streamingVideoId: String?
        get() {
            if (!isStreamingTrack) return null
            val parts = folderName.split(":")
            return if (parts.size >= 3) parts[2].takeIf { it.isNotBlank() } else null
        }

    /**
     * Retorna la plataforma de origen si es una pista de Streaming.
     */
    val streamingPlatform: StreamingSourcePlatform?
        get() {
            if (!isStreamingTrack) return null
            val parts = folderName.split(":")
            return if (parts.size >= 2) StreamingSourcePlatform.fromId(parts[1]) else StreamingSourcePlatform.YT_MUSIC
        }
}
