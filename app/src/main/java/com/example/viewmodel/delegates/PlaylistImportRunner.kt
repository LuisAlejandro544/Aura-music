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
import com.example.core.playback.DownloadNetworkGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
        // Fase A: espera máxima por pista antes de darla por fallida y seguir con la siguiente.
        const val PER_TRACK_TIMEOUT_MS = 10 * 60 * 1000L
        // Pausa anti-bot entre pistas para no gatillar throttling / HTTP 403 en googlevideo.com.
        const val INTER_TRACK_DELAY_MS = 750L
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

                    // Encolar en SERIE 1-en-1 (Fase A): una pista a la vez hasta completar el lote.
                    // El paralelismo real vive dentro de cada descarga (bloques HTTP Range en
                    // ChunkedParallelDownload); lanzar N descargas en paralelo solo provocaba
                    // progreso parpadeante, picos de RAM/CPU y HTTP 403 por throttling.
                    st.currentBatchJob?.cancel()
                    st.currentBatchJob = scope.launch(Dispatchers.IO) {
                        importPlaylistSequential(
                            validTracks = validTracks,
                            playlistId = playlistId,
                            playlistName = finalPlaylistName,
                            includeCanvas = includeCanvas
                        )
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

    /**
     * Fase A: descarga el lote 1-en-1. Resuelve, encola en [AuraDownloadService] y SUSPENDE
     * hasta recibir [AuraDownloadService.taskFinishedEvents] con el mismo `sourceVideoId`
     * antes de lanzar la siguiente pista. Un fallo nunca aborta el lote: se cuenta y se sigue.
     *
     * Fase B: el lote se persiste en [PlaylistBatchStore] (reanudable si la app muere),
     * cada pista espera red con [DownloadNetworkGate] en vez de fallar, y un timeout
     * SIN red re-espera el evento en lugar de contar fallo definitivo.
     */
    private suspend fun importPlaylistSequential(
        validTracks: List<ExternalPlaylistTrackItem>,
        playlistId: Long,
        playlistName: String,
        includeCanvas: Boolean
    ) {
        val total = validTracks.size
        var okCount = 0
        var failCount = 0
        var cancelled = false
        st.batchTotal.value = total
        st.batchDone.value = 0
        st.batchFailed.value = 0
        st.batchActive.value = true
        st.batchPlaylistName.value = playlistName
        st.batchPlaylistId = playlistId
        // Fase B: persistir el lote para reanudarlo si la app muere a mitad de la playlist.
        PlaylistBatchStore.save(storageManager, playlistId, playlistName, includeCanvas, validTracks)
        try {
            for ((index, item) in validTracks.withIndex()) {
                currentCoroutineContext().ensureActive()
                val vid = item.videoIdOrUrl.trim()
                if (vid.isBlank()) {
                    failCount++
                    st.batchFailed.value = failCount
                    continue
                }
                val pos = index + 1

                // Fase B: pausa cooperativa sin red (no cuenta como fallo ni consume timeout).
                if (!DownloadNetworkGate.isOnline(context)) {
                    st.importProgressText.value = "Sin conexión — en pausa ($pos/$total: ${item.title})..."
                    DownloadNetworkGate.awaitOnline(
                        context,
                        isActive = { st.currentBatchJob?.isActive == true },
                        onWaiting = { waiting -> st.batchWaitingForNetwork.value = waiting }
                    )
                    st.batchWaitingForNetwork.value = false
                    currentCoroutineContext().ensureActive()
                }
                st.importProgressFraction.value = index.toFloat() / total.coerceAtLeast(1).toFloat()
                st.importProgressText.value = "Descargando $pos/$total: ${item.title} — ${item.artist}"

                // Dedup: si la pista ya existe en biblioteca (modo streaming previo), vincular sin descargar.
                val watchUrl = if (vid.startsWith("http", ignoreCase = true)) {
                    vid
                } else {
                    "https://music.youtube.com/watch?v=$vid"
                }
                try {
                    val existing = trackDao.getTrackByUri(watchUrl)
                    if (existing != null) {
                        linkTrack(playlistId, existing.id)
                        okCount++
                        st.batchDone.value = okCount + failCount
                        PlaylistBatchStore.markDone(storageManager, vid)
                        continue
                    }
                } catch (_: Exception) {}

                val searchItem = StreamingSearchItem(
                    videoId = vid,
                    title = item.title,
                    artist = item.artist,
                    album = playlistName,
                    durationSeconds = item.durationSeconds,
                    thumbnailUrl = item.thumbnailUrl,
                    platform = StreamingSourcePlatform.YT_MUSIC
                )
                val resolved = try {
                    StreamingApiService.resolvePlayableStreamWithYtDlp(context, searchItem).getOrNull()
                } catch (_: Exception) { null }
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
                // Suscribirse ANTES de encolar: si startDownload falla en el acto emite
                // taskFinished de forma síncrona y sin esta pre-suscripción lo perderíamos
                // (SharedFlow sin replay) quedando 10 min colgados en timeout.
                // Fase B: si el timeout salta SIN red, no es fallo: el servicio también
                // está en pausa, así que se re-espera el evento sin re-encolar.
                var finished: AuraDownloadService.DownloadFinishedEvent? = null
                var enqueued = false
                var timedOutWithNetwork = false
                while (finished == null && !timedOutWithNetwork) {
                    // Hijo estructurado del lote: el botón Cancelar lo interrumpe de inmediato.
                    // La suscripción se crea ANTES de encolar (SharedFlow sin replay: un fallo
                    // síncrono de startDownload se perdería y colgaría 10 min en timeout).
                    finished = coroutineScope {
                        val waiter = async(Dispatchers.IO) {
                            withTimeoutOrNull(PER_TRACK_TIMEOUT_MS) {
                                AuraDownloadService.taskFinishedEvents.first { it.sourceVideoId == vid }
                            }
                        }
                        if (!enqueued) {
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
                            enqueued = true
                        }
                        // Esperar a que ESTA pista termine (éxito o fallo) antes de lanzar la siguiente.
                        try {
                            waiter.await()
                        } catch (_: Exception) { null }
                    }
                    if (finished == null) {
                        if (!DownloadNetworkGate.isOnline(context)) {
                            st.batchWaitingForNetwork.value = true
                            st.importProgressText.value = "Sin conexión — en pausa ($pos/$total: ${item.title})..."
                            DownloadNetworkGate.awaitOnline(
                                context,
                                isActive = { st.currentBatchJob?.isActive == true }
                            )
                            st.batchWaitingForNetwork.value = false
                            currentCoroutineContext().ensureActive()
                        } else {
                            // Timeout CON red: el servicio no reportó; fallo definitivo.
                            timedOutWithNetwork = true
                        }
                    }
                }
                if (timedOutWithNetwork) {
                    st.pendingMap.remove(vid)
                    failCount++
                    AuraDebugManager.logWarning(TAG, "Timeout esperando descarga 1-en-1 de vid=$vid ($pos/$total)")
                } else if (finished != null && !finished.success) {
                    failCount++
                } else {
                    okCount++
                }
                PlaylistBatchStore.markDone(storageManager, vid)
                st.batchDone.value = okCount + failCount
                st.batchFailed.value = failCount
                st.importProgressFraction.value = (okCount + failCount).toFloat() / total.coerceAtLeast(1).toFloat()

                // Respiro anti-bot entre pistas (los bloques intra-archivo ya van en paralelo).
                if (pos < total) {
                    try { delay(INTER_TRACK_DELAY_MS) } catch (_: Exception) { currentCoroutineContext().ensureActive() }
                }
            }
        } catch (_: CancellationException) {
            // Cancelación del lote (botón Cancelar): no es error, se resume abajo.
            cancelled = true
        } catch (e: Exception) {
            // Error inesperado: no relanzar, solo resumir.
            AuraDebugManager.logWarning(TAG, "Lote offline 1-en-1 interrumpido: ${e.message}")
        } finally {
            st.batchActive.value = false
            st.batchWaitingForNetwork.value = false
            st.importProgressFraction.value = 0f
            st.importProgressText.value = null
            val summary = when {
                cancelled -> "Descarga de \"$playlistName\" cancelada: $okCount/$total listas."
                failCount == 0 -> "Playlist \"$playlistName\": $okCount/$total descargadas."
                else -> "Playlist \"$playlistName\": $okCount/$total descargadas, $failCount fallaron."
            }
            if (cancelled) {
                // Un lote cancelado por el usuario no debe reanudarse al reabrir la app.
                PlaylistBatchStore.clear(storageManager)
                st.batchPlaylistId = -1L
            }
            withContext(Dispatchers.Main) { onStatusMessage(summary) }
        }
    }

    /**
     * Fase B: reanuda el lote persistido en [PlaylistBatchStore] si la app murió
     * a mitad de una playlist (las pistas ya descargadas se vinculan por dedup,
     * sin descargar dos veces). No hace nada si no hay lote pendiente o la
     * playlist ya no existe en Room.
     */
    fun resumePendingBatch() {
        if (st.batchActive.value || st.currentBatchJob?.isActive == true) return
        st.currentBatchJob = scope.launch(Dispatchers.IO) {
            val persisted = PlaylistBatchStore.load(storageManager) ?: return@launch
            if (persisted.tracks.isEmpty()) {
                PlaylistBatchStore.clear(storageManager)
                return@launch
            }
            val playlistExists = try {
                playlistDao.getPlaylistById(persisted.playlistId) != null
            } catch (_: Exception) { false }
            if (!playlistExists) {
                PlaylistBatchStore.clear(storageManager)
                return@launch
            }
            withContext(Dispatchers.Main) {
                onStatusMessage("Reanudando descarga de \"${persisted.playlistName}\" (${persisted.tracks.size} pendientes)...")
            }
            importPlaylistSequential(
                validTracks = persisted.tracks,
                playlistId = persisted.playlistId,
                playlistName = persisted.playlistName,
                includeCanvas = persisted.includeCanvas
            )
        }
    }

    /**
     * Cancela el lote 1-en-1 en curso: ya no se lanzan más pistas (la activa termina sola).
     * Fase B: además retira de la FIFO del servicio las pistas del lote aún no iniciadas
     * y limpia sus entradas de [st.pendingMap] para que una futura descarga del mismo
     * video no se vincule por error a esta playlist.
     */
    fun cancelBatchDownload() {
        val batchVids = st.pendingMap.keys
            .filter { st.pendingMap[it] == st.batchPlaylistId }
            .toSet()
        if (batchVids.isNotEmpty()) {
            AuraDownloadService.cancelQueuedTasks(batchVids)
            for (vid in batchVids) st.pendingMap.remove(vid)
        }
        st.currentBatchJob?.cancel()
        st.currentBatchJob = null
        st.batchActive.value = false
        st.batchWaitingForNetwork.value = false
        scope.launch(Dispatchers.IO) { PlaylistBatchStore.clear(storageManager) }
        st.batchPlaylistId = -1L
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
