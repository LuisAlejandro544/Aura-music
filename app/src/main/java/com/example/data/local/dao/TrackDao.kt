package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) para operaciones de la tabla de pistas.
 * Todas las consultas reactivas retornan Flow para integración fluida con Compose.
 */
@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY dateAdded DESC")
    fun getFavoriteTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY playCount DESC, dateAdded DESC LIMIT 30")
    fun getTopPlayedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY dateAdded DESC LIMIT 30")
    fun getRecentlyAddedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE uriString = :uriString LIMIT 1")
    suspend fun getTrackByUri(uriString: String): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query("UPDATE tracks SET title = :title, artist = :artist, album = :album WHERE id = :id")
    suspend fun updateTrackInfo(id: Long, title: String, artist: String, album: String)

    @Query("UPDATE tracks SET title = :title, artist = :artist, album = :album, albumArtPath = :albumArtPath WHERE id = :id")
    suspend fun updateTrackDetails(id: Long, title: String, artist: String, album: String, albumArtPath: String?)

    @Query("UPDATE tracks SET title = :title, artist = :artist, album = :album, albumArtPath = :albumArtPath, videoUri = :videoUri, isVideoLoop = :isVideoLoop WHERE id = :id")
    suspend fun updateTrackDetailsWithVideo(id: Long, title: String, artist: String, album: String, albumArtPath: String?, videoUri: String?, isVideoLoop: Boolean)

    @Query("UPDATE tracks SET videoUri = :videoUri, isVideoLoop = :isVideoLoop WHERE id = :id")
    suspend fun updateTrackVideo(id: Long, videoUri: String?, isVideoLoop: Boolean)

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE tracks SET playCount = playCount + 1 WHERE id = :id")
    suspend fun incrementPlayCount(id: Long)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrackById(id: Long)

    @Query("DELETE FROM tracks")
    suspend fun deleteAllTracks()

    @Query("SELECT COUNT(*) FROM tracks")
    fun getTrackCount(): Flow<Int>

    @Query("SELECT SUM(durationMs) FROM tracks")
    fun getTotalDuration(): Flow<Long?>
}
