package com.example.viewmodel.delegates


import android.content.Context
import android.graphics.BitmapFactory
import com.example.core.data.importer.FFmpegNativeEngine
import com.example.core.data.importer.OnlineVideoAudioImporter
import com.example.core.data.importer.playlist.ExternalPlaylistImporter
import com.example.core.data.local.dao.PlaylistDao
import com.example.core.data.local.dao.TrackDao
import com.example.core.data.local.entity.PlaylistEntity
import com.example.core.data.local.entity.PlaylistTrackCrossRef
import com.example.core.data.local.entity.TrackEntity
import com.example.core.data.storage.AppStorageManager
import com.example.core.data.streaming.StreamingApiService
import com.example.debug.AuraDebugManager
import com.example.core.model.ExternalPlaylistPreview
import com.example.core.model.ExternalPlaylistSource
import com.example.core.model.ExternalPlaylistTrackItem
import com.example.core.model.PlaylistImportSaveMode
import com.example.core.model.StreamingSearchItem
import com.example.core.model.StreamingSourcePlatform
import com.example.core.playback.AuraDownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ejecución de la importación: playlist, pistas, portada y vinculación offline.
 * Rol arquitectónico: pieza de PlaylistImportCoordinator (fachada pública intacta).
 */
internal class PlaylistImportRunner(
    private val context: android.content.Context,
    private val trackDao: com.example.core.data.local.dao.TrackDao,
    private val playlistDao: com.example.core.data.local.dao.PlaylistDao,
    private val storageManager: com.example.core.data.storage.AppStorageManager,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val st: PlaylistImportState,
    private val onStatusMessage: (String?) -> Unit,
    private val onOpenCreatedPlaylist: (Long) -> Unit
) {
    private companion object {
        const val TAG = "PlaylistImportCoordinator"
    }
    fun import() {
        val preview = st.playlistPreview.value ?: return
        if (st.isImporting.value) return

        val finalPlaylistName = st.customPlaylistName.value.trim().ifBlank { preview.title }
        val saveMode = st.selectedSaveMode.value
        val includeCanvas = st.includeVideoCanvasOffline.value

        st.importJob?.cancel()
        st.importJob = scope.launch(Dispatchers.IO) {
            st.isImporting.value = true
            st.errorMessage.value = null
            st.importProgressFraction.value = 0.05f
            st.importProgressText.value = "Descargando portada oficial en WebP sin pérdida..."

            try {
                // 1. Descargar y comprimir la portada oficial de la Playlist a WebP Lossless
                val savedCoverPath = saveCover(preview.coverUrl)

                // 2. Crear la entidad Playlist en Room
                val playlistId = playlistDao.insertPlaylist(
                    PlaylistEntity(
                        name = finalPlaylistName,
                        description = "Importada desde ${preview.source.badgeLabel} • ${preview.author}",
                        customArtPath = savedCoverPath
                    )
                )

                // 3. Si la Playlist proviene de Spotify, emparejar sus canciones con el catálogo de streaming
                val resolvedTracks: List<ExternalPlaylistTrackItem> = if (preview.source == ExternalPlaylistSource.SPOTIFY) {
                    st.importProgressText.value = "Emparejando ${preview.tracks.size} canciones de Spotify con el catálogo de alta fidelidad..."
                    ExternalPlaylistImporter.matchSpotifyTracksBatch(preview.tracks) { done, total ->
                        val frac = 0.10f + (done.toFloat() / total.coerceAtLeast(1).toFloat()) * 0.65f
                        st.importProgressFraction.value = frac.coerceIn(0.10f, 0.75f)
                        st.importProgressText.value = "Emparejando pistas de Spotify ($done / $total)..."
                    }
                } else {
                    preview.tracks
                }

                if (saveMode == PlaylistImportSaveMode.STREAMING_INSTANT) {
                    // === MODO STREAMING RÁPIDO (INSTANTÁNEO) ===
                    st.importProgressText.value = "Guardando ${resolvedTracks.size} canciones en la Playlist '$finalPlaylistName'..."
                    st.importProgressFraction.value = 0.85f

                    val baseTime = System.currentTimeMillis()
                    var position = 0
                    for (item in resolvedTracks) {
                        val videoId = item.videoIdOrUrl.trim()
                        if (videoId.isBlank() || videoId.startsWith("spotify:")) continue

                        val watchUrl = if (videoId.startsWith("http", ignoreCase = true)) {
                            videoId
                        } else {
                            "https://music.youtube.com/watch?v=$videoId"
                        }

                        val existing = trackDao.getTrackByUri(watchUrl)
                        val trackId = if (existing != null) {
                            existing.id
                        } else {
                            val entity = TrackEntity(
                                title = item.title,
                                artist = item.artist,
                                album = finalPlaylistName,
                                durationMs = item.durationSeconds * 1000L,
                                uriString = watchUrl,
                                albumArtPath = item.thumbnailUrl.ifBlank { savedCoverPath },
                                videoUri = null,
                                isVideoLoop = false,
                                mimeType = "audio/mp4",
                                dateAdded = baseTime + position,
                                folderName = "STREAM:YT_MUSIC:$videoId",
                                fileSizeFormatted = "YT MUSIC",
                                isFavorite = false
                            )
                            val insertedId = trackDao.insertTrack(entity)
                            if (insertedId <= 0L) {
                                trackDao.getTrackByUri(watchUrl)?.id ?: -1L
                            } else {
                                insertedId
                            }
                        }

                        if (trackId > 0L) {
                            playlistDao.insertCrossRef(
                                PlaylistTrackCrossRef(
                                    playlistId = playlistId,
                                    trackId = trackId,
                                    addedAt = baseTime + position
                                )
                            )
                            position++
                        }
                    }

                    st.importProgressFraction.value = 1.0f
                    st.importProgressText.value = "¡Playlist '$finalPlaylistName' creada con éxito!"

                    withContext(Dispatchers.Main) {
                        st.isImporting.value = false
                        st.showDialog.value = false
                        st.playlistPreview.value = null
                        st.inputUrl.value = ""
                        onStatusMessage("Playlist \"$finalPlaylistName\" ($position canciones) importada en Modo Streaming.")
                        onOpenCreatedPlaylist(playlistId)
                    }
                } else {
                    // === MODO DESCARGAR TODO OFFLINE (EN 2DO PLANO) ===
                    st.importProgressText.value = "Preparando descarga offline de ${resolvedTracks.size} canciones..."
                    st.importProgressFraction.value = 0.80f

                    val validTracks = resolvedTracks.filter {
                        it.videoIdOrUrl.isNotBlank() && !it.videoIdOrUrl.startsWith("spotify:")
                    }

                    withContext(Dispatchers.Main) {
                        st.isImporting.value = false
                        st.showDialog.value = false
                        st.playlistPreview.value = null
                        st.inputUrl.value = ""
                        onStatusMessage("Descargando ${validTracks.size} canciones de \"$finalPlaylistName\" en segundo plano...")
                        onOpenCreatedPlaylist(playlistId)
                    }

                    // Encolar secuencialmente en segundo plano para no saturar la red y vincular cada pista a la Playlist
                    scope.launch(Dispatchers.IO) {
                        for (item in validTracks) {
                            val vid = item.videoIdOrUrl.trim()
                            val searchItem = StreamingSearchItem(
                                videoId = vid,
                                title = item.title,
                                artist = item.artist,
                                album = finalPlaylistName,
                                durationSeconds = item.durationSeconds,
                                thumbnailUrl = item.thumbnailUrl,
                                platform = StreamingSourcePlatform.YT_MUSIC
                            )
                            val resolved = StreamingApiService.resolvePlayableStreamWithYtDlp(context, searchItem).getOrNull()
                            val mediaInfo = resolved ?: OnlineVideoAudioImporter.ResolvedMediaInfo(
                                originalUrl = searchItem.canonicalWatchUrl,
                                suggestedTitle = item.title,
                                suggestedArtist = item.artist,
                                videoUrl = searchItem.canonicalWatchUrl,
                                audioUrl = null,
                                coverUrl = item.thumbnailUrl,
                                durationSeconds = item.durationSeconds
                            )

                            st.pendingMap[vid] = playlistId
                            AuraDownloadService.startDownload(
                                context = context,
                                resolvedInfo = mediaInfo,
                                customTitle = item.title,
                                customArtist = item.artist,
                                attachAsCanvas = includeCanvas,
                                trimSilence = false,
                                loopStyle = FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE,
                                autoPlayOnComplete = false,
                                sourceVideoId = vid
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                AuraDebugManager.logWarning(TAG, "Error importando Playlist: ${e.message}")
                st.errorMessage.value = e.localizedMessage ?: "Ocurrió un error al importar la Playlist."
                st.isImporting.value = false
            } finally {
                st.importProgressText.value = null
                st.importProgressFraction.value = 0f
            }
        }
    }

    suspend fun linkTrack(playlistId: Long, trackId: Long) {
        try {
            playlistDao.insertCrossRef(
                PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = trackId,
                    addedAt = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {}
    }

    private suspend fun saveCover(coverUrl: String): String? {
        if (coverUrl.isBlank() || !coverUrl.startsWith("http", ignoreCase = true)) return null
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(coverUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            if (conn.responseCode !in 200..299) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val savedFile = storageManager.saveCoverAsWebp(
                key = "playlist_import_${System.currentTimeMillis()}",
                bitmap = bmp
            )
            bmp.recycle()
            savedFile.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
