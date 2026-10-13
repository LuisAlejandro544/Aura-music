package com.example.viewmodel

import android.app.Application
import android.net.Uri
import com.example.core.data.repository.MusicRepository
import com.example.core.data.storage.AppStorageManager
import com.example.core.model.Track
import com.example.core.playback.AuraAudioPlayer
import com.example.viewmodel.delegates.LyricsCoordinator
import com.example.viewmodel.delegates.MixtapeCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Cableado reactivo de MusicViewModel.
 * Rol arquitectónico: concentra los collectors de init (auto-carga de letras,
 * capítulo activo de mixtape y sincronización Room → ExoPlayer), la resolución
 * de lista/índice para playTrack y la importación de fondos de galería,
 * sin acoplar la fachada a los detalles de cada flujo.
 */
internal object MusicViewModelWiring {

    fun wireReactiveSyncs(
        scope: CoroutineScope,
        audioPlayer: AuraAudioPlayer,
        repository: MusicRepository,
        lyricsCoordinator: LyricsCoordinator,
        mixtapeCoordinator: MixtapeCoordinator
    ) {
        // Carga automática de letras sincronizadas
        scope.launch {
            audioPlayer.currentTrack.collect { track -> lyricsCoordinator.loadLyrics(track) }
        }

        // Seguimiento reactivo del capítulo activo para Mixtapes Continuos
        scope.launch {
            combine(audioPlayer.currentTrack, audioPlayer.currentPosition) { trk, pos ->
                trk to pos
            }.collect { (trk, pos) ->
                mixtapeCoordinator.updateActiveChapter(trk, pos)
            }
        }

        // Sincronización reactiva del reproductor con Room
        scope.launch {
            repository.allTracks.collect { tracks ->
                val current = audioPlayer.currentTrack.value
                if (current != null) {
                    val updated = tracks.find { it.id == current.id }
                    if (updated != null && (updated.isFavorite != current.isFavorite || updated.title != current.title || updated.artist != current.artist || updated.album != current.album || updated.albumArtPath != current.albumArtPath || updated.videoUri != current.videoUri || updated.isVideoLoop != current.isVideoLoop)) {
                        audioPlayer.updateTrackFavorite(current.id, updated.isFavorite)
                        audioPlayer.updateTrackMetadata(
                            trackId = updated.id,
                            title = updated.title,
                            artist = updated.artist,
                            album = updated.album,
                            albumArtPath = updated.albumArtPath,
                            updateArt = true,
                            videoUri = updated.videoUri,
                            isVideoLoop = updated.isVideoLoop,
                            updateVideo = true
                        )
                    }
                }
            }
        }
    }

    /** Resuelve la lista de reproducción y el índice inicial para playTrack. */
    fun resolvePlaybackList(
        track: Track,
        fromList: List<Track>?,
        allTracks: List<Track>
    ): Pair<List<Track>, Int> {
        val list = if (!fromList.isNullOrEmpty()) {
            fromList
        } else if (allTracks.isNotEmpty()) {
            if (allTracks.any { it.id == track.id }) allTracks
            else (listOf(track) + allTracks).distinctBy { it.id }
        } else {
            listOf(track)
        }
        return list to list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
    }

    /** Importa una imagen de galería como fondo de app y devuelve su ruta persistida. */
    suspend fun importWallpaperImage(app: Application, uri: Uri, oldPath: String): String? {
        return AppStorageManager(app).saveAppWallpaperImageFromUri(uri, oldPath)
    }

    /** Importa un video corto de galería como fondo de app y devuelve su ruta persistida. */
    suspend fun importWallpaperVideo(app: Application, uri: Uri, oldPath: String): String? {
        return AppStorageManager(app).saveAppWallpaperVideoFromUri(uri, oldPath)
    }

    /** Elimina el archivo de fondo personalizado anterior. */
    suspend fun deleteWallpaperFile(app: Application, oldPath: String) {
        AppStorageManager(app).deleteAppWallpaperFile(oldPath)
    }
}
