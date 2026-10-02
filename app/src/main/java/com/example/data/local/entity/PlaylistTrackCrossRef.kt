package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Tabla de unión muchos a muchos entre listas de reproducción y pistas.
 */
@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index(value = ["trackId"])]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val addedAt: Long = System.currentTimeMillis()
)
