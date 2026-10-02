package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.Track

/**
 * Entidad Room para persistir las pistas de música importadas por el usuario.
 * Almacena metadatos y la URI del archivo seleccionada mediante SAF.
 */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uriString: String,
    val albumArtPath: String?,
    val mimeType: String,
    val dateAdded: Long,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val folderName: String = "",
    val fileSizeFormatted: String = ""
) {
    fun toDomain(): Track = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        uriString = uriString,
        albumArtPath = albumArtPath,
        mimeType = mimeType,
        dateAdded = dateAdded,
        isFavorite = isFavorite,
        playCount = playCount,
        folderName = folderName,
        fileSizeFormatted = fileSizeFormatted
    )

    companion object {
        fun fromDomain(track: Track): TrackEntity = TrackEntity(
            id = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs,
            uriString = track.uriString,
            albumArtPath = track.albumArtPath,
            mimeType = track.mimeType,
            dateAdded = track.dateAdded,
            isFavorite = track.isFavorite,
            playCount = track.playCount,
            folderName = track.folderName,
            fileSizeFormatted = track.fileSizeFormatted
        )
    }
}
