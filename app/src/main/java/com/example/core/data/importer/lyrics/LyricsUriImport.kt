package com.example.core.data.importer

import android.content.Context
import android.net.Uri
import com.example.core.data.storage.AppStorageManager
import com.example.core.model.LyricsState
import com.example.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Importación de .lrc/.txt desde el celular con tope de 512 KB.
 * Rol arquitectónico: submódulo de LyricsManager (fachada pública intacta).
 */
internal const val MAX_LYRICS_FILE_BYTES: Long = 512 * 1024L

internal object LyricsUriImport {
    suspend fun importFromUri(
        context: Context,
        track: Track,
        uri: Uri,
        storageManager: AppStorageManager
    ): LyricsState? = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8192)
                val out = java.io.ByteArrayOutputStream()
                var totalRead = 0L
                var n: Int
                while (stream.read(buffer).also { n = it } != -1) {
                    totalRead += n
                    if (totalRead > MAX_LYRICS_FILE_BYTES) {
                        return@withContext null
                    }
                    out.write(buffer, 0, n)
                }
                out.toString(Charsets.UTF_8.name())
            } ?: return@withContext null

            if (content.isNotBlank()) {
                val state = LyricsLocalStore.save(track, storageManager, content)
                state
            } else null
        } catch (e: Throwable) {
            null
        }
    }
}
