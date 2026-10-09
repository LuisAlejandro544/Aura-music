package com.example.data.importer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Analizador de metadatos de audio local.
 * Extrae título, artista, álbum, duración y portada incrustada usando MediaMetadataRetriever
 * en hilos de fondo mediante Kotlin Coroutines (Dispatchers.IO).
 */
object AudioMetadataParser {

    suspend fun parseUri(context: Context, uri: Uri, folderName: String = ""): Track? =
        withContext(Dispatchers.IO) {
            val rawFileName = getFileName(context, uri) ?: "Audio_${System.currentTimeMillis()}"
            val fileExt = getFileExtension(context, uri)?.lowercase() ?: ""

            // Si es un formato especial (DSD .dsf/.dff, Monkey's Audio .ape, WavPack .wv, Chiptune .mod/.xm)
            // que requiere decodificación asistida por FFmpeg antes de pasarlo a MediaMetadataRetriever:
            var effectiveUri = uri
            var forcedSpecialMime: String? = null
            if (SpecialAudioFormatDecoder.isSpecialFormat(rawFileName)) {
                val decoded = SpecialAudioFormatDecoder.decodeSpecialUriIfNeeded(context, uri, fileExt)
                if (decoded != null) {
                    effectiveUri = Uri.fromFile(decoded.first)
                    forcedSpecialMime = decoded.second
                }
            }

            val retriever = MediaMetadataRetriever()
            try {
                try {
                    retriever.setDataSource(context, effectiveUri)
                } catch (_: Exception) {
                    // En caso de un módulo tracker o DSD crudo sin transcodificar aún
                    val fallbackSize = getFileSizeFormatted(context, effectiveUri).ifBlank { getFileSizeFormatted(context, uri) }
                    return@withContext Track(
                        id = 0,
                        title = rawFileName.substringBeforeLast("."),
                        artist = "Formato Especial Hi-Res",
                        album = fileExt.uppercase().ifBlank { "Hi-Res Audio" },
                        durationMs = 180_000L,
                        uriString = effectiveUri.toString(),
                        albumArtPath = null,
                        mimeType = forcedSpecialMime ?: SpecialAudioFormatDecoder.resolveSpecialMimeType(fileExt),
                        dateAdded = System.currentTimeMillis(),
                        isFavorite = false,
                        playCount = 0,
                        folderName = folderName,
                        fileSizeFormatted = fallbackSize
                    )
                }

                // Extraer nombre de archivo por si el título está vacío
                val fileName = rawFileName

                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() }
                    ?: fileName.substringBeforeLast(".")

                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() }
                    ?: "Artista desconocido"

                val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.takeIf { it.isNotBlank() }
                    ?: "Álbum desconocido"

                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLongOrNull() ?: 0L

                val mimeType = forcedSpecialMime
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                    ?: context.contentResolver.getType(uri)
                    ?: "audio/mpeg"

                // Extraer portada incrustada si existe y comprimir a WebP sin pérdida
                val storageManager = com.example.data.storage.AppStorageManager(context)
                val embeddedArt = retriever.embeddedPicture
                val albumArtPath = if (embeddedArt != null && embeddedArt.isNotEmpty()) {
                    val webpFile = storageManager.saveCoverBytesAsWebp("${effectiveUri.toString().hashCode()}", embeddedArt)
                    webpFile?.absolutePath
                } else null

                val fileSizeFormatted = getFileSizeFormatted(context, effectiveUri).ifBlank {
                    getFileSizeFormatted(context, uri)
                }

                Track(
                    id = 0,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    uriString = effectiveUri.toString(),
                    albumArtPath = albumArtPath,
                    mimeType = mimeType,
                    dateAdded = System.currentTimeMillis(),
                    isFavorite = false,
                    playCount = 0,
                    folderName = folderName,
                    fileSizeFormatted = fileSizeFormatted
                )
            } catch (e: Exception) {
                null
            } finally {
                try {
                    retriever.release()
                } catch (ignored: Exception) {}
            }
        }


    fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        return cursor.getString(nameIndex)
                    }
                }
            } catch (ignored: Exception) {}
        }
        return uri.lastPathSegment
    }

    fun getFileExtension(context: Context, uri: Uri): String? {
        val name = getFileName(context, uri) ?: return null
        return if (name.contains(".")) name.substringAfterLast(".").lowercase() else null
    }

    private fun getFileSizeFormatted(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    val bytes = cursor.getLong(sizeIndex)
                    if (bytes > 0) {
                        val mb = bytes / (1024f * 1024f)
                        return String.format("%.1f MB", mb)
                    }
                }
            }
        } catch (ignored: Exception) {}
        return ""
    }
}
