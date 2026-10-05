package com.example.model

/**
 * Representa una lista de reproducción creada localmente por el usuario.
 */
data class Playlist(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val trackCount: Int = 0,
    val customArtPath: String? = null,
    val previewTracks: List<Track> = emptyList()
)
