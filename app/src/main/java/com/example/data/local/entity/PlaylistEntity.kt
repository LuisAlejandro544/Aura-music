package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.Playlist

/**
 * Entidad Room para representar listas de reproducción de usuario.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val customArtPath: String? = null
) {
    fun toDomain(
        trackCount: Int = 0,
        previewTracks: List<com.example.model.Track> = emptyList(),
        totalPlays: Int = 0
    ): Playlist = Playlist(
        id = id,
        name = name,
        description = description,
        createdAt = createdAt,
        trackCount = trackCount,
        customArtPath = customArtPath,
        previewTracks = previewTracks,
        totalPlays = totalPlays
    )

    companion object {
        fun fromDomain(playlist: Playlist): PlaylistEntity = PlaylistEntity(
            id = playlist.id,
            name = playlist.name,
            description = playlist.description,
            createdAt = playlist.createdAt,
            customArtPath = playlist.customArtPath
        )
    }
}
