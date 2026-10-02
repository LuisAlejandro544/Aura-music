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
     * Retorna una etiqueta de audio moderna (ej. FLAC, MP3, WAV, HI-RES)
     */
    fun formatBadge(): String {
        return when {
            mimeType.contains("flac", ignoreCase = true) || uriString.endsWith(".flac", ignoreCase = true) -> "FLAC"
            mimeType.contains("wav", ignoreCase = true) || uriString.endsWith(".wav", ignoreCase = true) -> "WAV"
            mimeType.contains("ogg", ignoreCase = true) || uriString.endsWith(".ogg", ignoreCase = true) -> "OGG"
            mimeType.contains("aac", ignoreCase = true) || uriString.endsWith(".aac", ignoreCase = true) -> "AAC"
            mimeType.contains("m4a", ignoreCase = true) || uriString.endsWith(".m4a", ignoreCase = true) -> "M4A"
            else -> "MP3"
        }
    }
}
