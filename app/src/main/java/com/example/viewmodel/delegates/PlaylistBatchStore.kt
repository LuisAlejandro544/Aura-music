package com.example.viewmodel.delegates

import com.example.core.data.storage.AppStorageManager
import com.example.core.model.ExternalPlaylistTrackItem
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistencia del lote offline 1-en-1 (Fase B).
 *
 * Rol arquitectónico: pieza de PlaylistImportCoordinator. Guarda la cola pendiente
 * en `metadata/playlist_batch.json` (vía [AppStorageManager.metadataDir]) para que,
 * si la app muere a mitad de una playlist de 50-100 pistas, el lote se reanude al
 * volver a entrar en lugar de perderse (antes vivía solo en memoria).
 *
 * Formato: un objeto con `playlistId`, `playlistName`, `includeCanvas` y el arreglo
 * `tracks` (videoId, título, artista, miniatura, duración). Cada pista terminada se
 * descuenta con [markDone] reescribiendo el archivo; al completar o cancelar el lote
 * se borra con [clear]. Toda la E/S ocurre en `Dispatchers.IO`.
 */
internal object PlaylistBatchStore {

    private const val TAG = "PlaylistBatchStore"
    private const val FILE_NAME = "playlist_batch.json"

    data class PersistedBatch(
        val playlistId: Long,
        val playlistName: String,
        val includeCanvas: Boolean,
        val tracks: List<ExternalPlaylistTrackItem>
    )

    private fun batchFile(storageManager: AppStorageManager): File {
        val dir = storageManager.metadataDir.apply { if (!exists()) mkdirs() }
        return File(dir, FILE_NAME)
    }

    suspend fun save(
        storageManager: AppStorageManager,
        playlistId: Long,
        playlistName: String,
        includeCanvas: Boolean,
        tracks: List<ExternalPlaylistTrackItem>
    ) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject()
                .put("playlistId", playlistId)
                .put("playlistName", playlistName)
                .put("includeCanvas", includeCanvas)
            val arr = JSONArray()
            for (t in tracks) {
                arr.put(
                    JSONObject()
                        .put("videoId", t.videoIdOrUrl)
                        .put("title", t.title)
                        .put("artist", t.artist)
                        .put("thumbnailUrl", t.thumbnailUrl)
                        .put("durationSeconds", t.durationSeconds)
                        .put("index", t.index)
                )
            }
            json.put("tracks", arr)
            batchFile(storageManager).writeText(json.toString())
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "No se pudo persistir el lote: ${e.message}")
        }
    }

    suspend fun load(storageManager: AppStorageManager): PersistedBatch? =
        withContext(Dispatchers.IO) {
            try {
                val file = batchFile(storageManager)
                if (!file.exists()) return@withContext null
                val json = JSONObject(file.readText())
                val arr = json.optJSONArray("tracks") ?: return@withContext null
                if (arr.length() == 0) {
                    file.delete()
                    return@withContext null
                }
                val tracks = ArrayList<ExternalPlaylistTrackItem>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    tracks.add(
                        ExternalPlaylistTrackItem(
                            index = o.optInt("index", i),
                            title = o.optString("title"),
                            artist = o.optString("artist"),
                            durationSeconds = o.optLong("durationSeconds", 0L),
                            thumbnailUrl = o.optString("thumbnailUrl"),
                            videoIdOrUrl = o.optString("videoId")
                        )
                    )
                }
                PersistedBatch(
                    playlistId = json.optLong("playlistId", -1L),
                    playlistName = json.optString("playlistName"),
                    includeCanvas = json.optBoolean("includeCanvas", true),
                    tracks = tracks
                ).takeIf { it.playlistId > 0L }
            } catch (e: Exception) {
                AuraDebugManager.logWarning(TAG, "Lote persistido ilegible, se descarta: ${e.message}")
                try { batchFile(storageManager).delete() } catch (_: Exception) {}
                null
            }
        }

    /** Descuenta una pista terminada (éxito o fallo definitivo) y borra el archivo si ya no queda nada. */
    suspend fun markDone(storageManager: AppStorageManager, videoId: String) =
        withContext(Dispatchers.IO) {
            try {
                val current = load(storageManager) ?: return@withContext
                val remaining = current.tracks.filter { it.videoIdOrUrl != videoId }
                if (remaining.isEmpty()) {
                    batchFile(storageManager).delete()
                } else {
                    save(storageManager, current.playlistId, current.playlistName, current.includeCanvas, remaining)
                }
            } catch (e: Exception) {
                AuraDebugManager.logWarning(TAG, "No se pudo descontar $videoId del lote: ${e.message}")
            }
        }

    suspend fun clear(storageManager: AppStorageManager) = withContext(Dispatchers.IO) {
        try { batchFile(storageManager).delete() } catch (_: Exception) {}
    }
}
