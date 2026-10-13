package com.example.core.data.importer.mixtape

import android.content.Context
import android.net.Uri
import com.example.debug.AuraDebugManager
import com.example.core.model.Track
import com.example.core.data.storage.AppStorageManager
import java.io.File
import java.io.FileOutputStream

/**
 * Validación segura y preparación de audios de entrada.
 * Rol arquitectónico: pieza de MixtapeEngine (fachada pública intacta).
 */
internal object MixtapeAudioPrep {
    private const val TAG = "MixtapeEngine"

    fun isSafeAudio(context: Context, file: File): Boolean {
        return try {
            if (!file.exists() || !file.isFile || !file.canRead()) return false
            val canonical = file.canonicalFile.toPath()
            val storageManager = AppStorageManager(context)
            val songsDirPath = storageManager.songsDir.canonicalFile.toPath()
            if (canonical.startsWith(songsDirPath)) {
                return true
            }
            val internalSongsDir = File(context.filesDir, "songs").canonicalFile.toPath()
            if (canonical.startsWith(internalSongsDir)) {
                return true
            }
            val dataDir = context.applicationInfo.dataDir?.let { File(it).canonicalFile.toPath() }
            val filesDir = context.filesDir.canonicalFile.toPath()
            if ((dataDir != null && canonical.startsWith(dataDir)) || canonical.startsWith(filesDir)) {
                return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun prepareInput(
        context: Context,
        track: Track,
        index: Int,
        tempFiles: MutableList<File>
    ): File? {
        val uriStr = track.uriString
        if (uriStr.startsWith("/")) {
            val f = File(uriStr)
            if (isSafeAudio(context, f)) return f.canonicalFile
            return null
        } else if (uriStr.startsWith("file://")) {
            val f = File(Uri.parse(uriStr).path ?: "")
            if (isSafeAudio(context, f)) return f.canonicalFile
            return null
        }

        // Si es content:// o no se pudo leer directamente, copiar a temporal en cache previa validación
        return try {
            val uri = Uri.parse(uriStr)
            if (!com.example.core.data.importer.IncomingMediaHandler.isSafeExternalUri(context, uri)) {
                return null
            }
            val tempFile = File(context.cacheDir, "mixtape_in_${index}_${System.currentTimeMillis()}.tmp")
            tempFiles.add(tempFile)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (tempFile.exists() && tempFile.length() > 0) tempFile else null
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Error preparando audio de '${track.title}': ${e.message}")
            null
        }
    }
}
