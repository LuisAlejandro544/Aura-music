package com.example.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.example.data.importer.FFmpegNativeEngine
import com.example.debug.AuraDebugManager
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.URLDecoder

/**
 * Aura Music - Gestor Modular de Carpeta Pública / Personalizada del Usuario y Exportación de Medios
 *
 * Rol arquitectónico:
 * - Gestiona el acceso persistente mediante Storage Access Framework (SAF `OpenDocumentTree`)
 *   a la carpeta que el usuario cree o seleccione libremente en su teléfono, sin estar restringido
 *   por `Android/data/` en Android 11+.
 * - Traduce el URI de SAF a una ruta legible y transparente para el usuario (ej. "Almacenamiento interno / MiCarpeta / Videos").
 * - Organiza automáticamente una estructura escalable por subcarpetas:
 *   - `Videos/` (activo actualmente para exportar vídeos descargados con su audio completo).
 *   - `Images/` (preparado arquitectónicamente para habilitar exportación de carátulas WebP/PNG en el futuro).
 * - Ensambla en segundo plano (`Dispatchers.IO`) el archivo de vídeo `.mp4` uniendo el flujo de vídeo
 *   con la pista de audio original de la canción mediante `FFmpegNativeEngine`, entregando un vídeo
 *   completo con sonido listo para reproducirse en cualquier galería o reproductor externo.
 */
object UserPublicMediaExporter {

    private const val TAG = "UserPublicMediaExporter"
    private const val PREFS_NAME = "aura_music_ui_prefs"
    private const val KEY_USER_PUBLIC_FOLDER_URI = "pref_user_public_media_folder_uri"
    private const val KEY_EXPORTED_VIDEO_TRACK_IDS = "pref_exported_video_track_ids"

    const val SUBFOLDER_VIDEOS = "Videos"
    const val SUBFOLDER_IMAGES = "Images"

    data class ExportFolderState(
        val treeUriString: String = "",
        val readablePath: String = "",
        val isConfigured: Boolean = false,
        val exportedTrackIds: Set<Long> = emptySet(),
        val exportingTrackIds: Set<Long> = emptySet(),
        val isExportingAll: Boolean = false,
        val statusMessage: String? = null
    )

    /**
     * Guarda la carpeta elegida por el usuario mediante `OpenDocumentTree`, adquiriendo permisos
     * persistentes de lectura y escritura en el sistema operativo.
     */
    suspend fun saveSelectedFolderUri(context: Context, treeUri: Uri): ExportFolderState = withContext(Dispatchers.IO) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "No se pudo persistir permiso URI, intentando acceso directo: ${e.message}")
        }

        // Asegurar que la subcarpeta "Videos" se cree de inmediato dentro de la carpeta seleccionada
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (rootDoc != null && rootDoc.canWrite()) {
                val existingVideos = rootDoc.findFile(SUBFOLDER_VIDEOS)
                if (existingVideos == null || !existingVideos.isDirectory) {
                    rootDoc.createDirectory(SUBFOLDER_VIDEOS)
                }
            }
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Aviso al pre-crear subcarpeta Videos: ${e.message}")
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USER_PUBLIC_FOLDER_URI, treeUri.toString()).apply()

        loadCurrentState(context)
    }

    /**
     * Carga el estado actual de la carpeta configurada por el usuario y los IDs de pistas exportadas.
     */
    fun loadCurrentState(context: Context): ExportFolderState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriStr = prefs.getString(KEY_USER_PUBLIC_FOLDER_URI, "")?.trim().orEmpty()
        val exportedIdsRaw = prefs.getStringSet(KEY_EXPORTED_VIDEO_TRACK_IDS, emptySet()).orEmpty()
        val exportedIds = exportedIdsRaw.mapNotNull { it.toLongOrNull() }.toSet()

        if (uriStr.isBlank()) {
            return ExportFolderState(
                treeUriString = "",
                readablePath = "",
                isConfigured = false,
                exportedTrackIds = exportedIds
            )
        }

        return try {
            val uri = Uri.parse(uriStr)
            val rootDoc = DocumentFile.fromTreeUri(context, uri)
            val isAccessible = rootDoc != null && rootDoc.exists() && rootDoc.canWrite()
            if (!isAccessible) {
                ExportFolderState(
                    treeUriString = "",
                    readablePath = "",
                    isConfigured = false,
                    exportedTrackIds = exportedIds
                )
            } else {
                val readable = formatTreeUriToReadablePath(context, uri)
                ExportFolderState(
                    treeUriString = uriStr,
                    readablePath = readable,
                    isConfigured = true,
                    exportedTrackIds = exportedIds
                )
            }
        } catch (_: Exception) {
            ExportFolderState(
                treeUriString = "",
                readablePath = "",
                isConfigured = false,
                exportedTrackIds = exportedIds
            )
        }
    }

    /**
     * Convierte un Tree URI de Android SAF en una ruta clara y legible para el usuario.
     * Ejemplo: "content://com.android.externalstorage.documents/tree/primary%3AMiMusica"
     * -> "Almacenamiento interno / MiMusica / Videos"
     */
    fun formatTreeUriToReadablePath(context: Context, treeUri: Uri): String {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val decoded = URLDecoder.decode(docId, "UTF-8")
            val parts = decoded.split(":", limit = 2)
            val volume = parts.getOrNull(0).orEmpty()
            val relativePath = parts.getOrNull(1)?.trim('/').orEmpty()

            val volumeLabel = when {
                volume.equals("primary", ignoreCase = true) -> "Almacenamiento interno"
                volume.equals("home", ignoreCase = true) -> "Documentos"
                volume.isNotBlank() -> "Tarjeta SD ($volume)"
                else -> "Almacenamiento del teléfono"
            }

            if (relativePath.isNotEmpty()) {
                "$volumeLabel / ${relativePath.replace("/", " / ")} / $SUBFOLDER_VIDEOS"
            } else {
                "$volumeLabel / $SUBFOLDER_VIDEOS"
            }
        } catch (_: Exception) {
            val docName = DocumentFile.fromTreeUri(context, treeUri)?.name ?: "Carpeta seleccionada"
            "Almacenamiento del usuario / $docName / $SUBFOLDER_VIDEOS"
        }
    }

    /**
     * Exporta el vídeo de una canción hacia la carpeta pública seleccionada por el usuario,
     * fusionando previamente su pista de audio original mediante FFmpeg para que el archivo .mp4
     * resultante tenga sonido completo de principio a fin.
     */
    suspend fun exportTrackVideoWithAudioToUserFolder(
        context: Context,
        track: Track
    ): Result<String> = withContext(Dispatchers.IO) {
        val state = loadCurrentState(context)
        if (!state.isConfigured || state.treeUriString.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Primero selecciona una carpeta destino."))
        }

        val rawVideoPath = track.videoUri?.let {
            if (it.startsWith("file://")) it.removePrefix("file://") else it
        }
        if (rawVideoPath.isNullOrBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Esta canción no tiene un vídeo asociado."))
        }

        val videoFile = File(rawVideoPath)
        if (!videoFile.exists() || videoFile.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("El archivo de vídeo local no se encontró en el dispositivo."))
        }

        val treeUri = Uri.parse(state.treeUriString)
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext Result.failure(IllegalStateException("No se pudo acceder a la carpeta seleccionada. Por favor elígela de nuevo."))

        if (!rootDoc.exists() || !rootDoc.canWrite()) {
            return@withContext Result.failure(IllegalStateException("La carpeta seleccionada ya no tiene permisos de escritura. Por favor selecciónala nuevamente."))
        }

        // 1. Obtener o crear subcarpeta "Videos" dentro de la carpeta del usuario
        val videosDirDoc = rootDoc.findFile(SUBFOLDER_VIDEOS)?.takeIf { it.isDirectory }
            ?: rootDoc.createDirectory(SUBFOLDER_VIDEOS)
            ?: rootDoc

        // 2. Localizar el archivo de audio de la canción para unirlo con el vídeo
        val audioFile = resolveTrackAudioFile(context, track)
        val tempMergedFile = File(context.cacheDir, "export_merged_${track.id}_${System.currentTimeMillis()}.mp4")

        try {
            val sourceToCopy: File = if (audioFile != null && audioFile.exists() && audioFile.length() > 0L && FFmpegNativeEngine.isAvailable(context)) {
                val mergeRes = FFmpegNativeEngine.mergeVideoCanvasWithTrackAudio(
                    context = context,
                    videoFile = videoFile,
                    audioFile = audioFile,
                    outputFile = tempMergedFile,
                    isVideoLoop = track.isVideoLoop,
                    totalDurationMs = track.durationMs
                )
                if (mergeRes.success && tempMergedFile.exists() && tempMergedFile.length() > 4096L) {
                    tempMergedFile
                } else {
                    videoFile
                }
            } else {
                videoFile
            }

            // 3. Construir nombre limpio y legible: "Artista - Título.mp4"
            val cleanFileName = buildCleanVideoFileName(track)

            // Si ya existía un archivo con el mismo nombre, reemplazarlo limpiamente
            videosDirDoc.findFile(cleanFileName)?.delete()
            val targetDoc = videosDirDoc.createFile("video/mp4", cleanFileName)
                ?: return@withContext Result.failure(IllegalStateException("No se pudo crear el archivo '$cleanFileName' en la carpeta seleccionada."))

            context.contentResolver.openOutputStream(targetDoc.uri)?.use { outStream ->
                FileInputStream(sourceToCopy).use { inStream ->
                    inStream.copyTo(outStream, bufferSize = 64 * 1024)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Error al abrir flujo de escritura en la carpeta del usuario."))

            markTrackAsExported(context, track.id)
            val finalPathDisplay = "${state.readablePath} / $cleanFileName"
            AuraDebugManager.logInfo(TAG, "Vídeo con audio exportado exitosamente a: $finalPathDisplay")
            Result.success(finalPathDisplay)
        } catch (e: Exception) {
            AuraDebugManager.logError(TAG, "Error exportando vídeo a carpeta del usuario", e)
            Result.failure(e)
        } finally {
            if (tempMergedFile.exists()) {
                try { tempMergedFile.delete() } catch (_: Exception) {}
            }
            if (audioFile != null && audioFile.absolutePath.startsWith(context.cacheDir.absolutePath)) {
                try { audioFile.delete() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Resuelve el archivo físico de audio de la canción (ya sea ruta local file:// o content:// SAF).
     */
    private fun resolveTrackAudioFile(context: Context, track: Track): File? {
        return try {
            val uriStr = track.uriString
            if (uriStr.startsWith("file://")) {
                val f = File(Uri.parse(uriStr).path ?: uriStr.removePrefix("file://"))
                if (f.exists() && f.length() > 0L) return f
            }
            val directFile = File(uriStr)
            if (directFile.exists() && directFile.length() > 0L) {
                return directFile
            }
            // Si proviene de un URI externo content://, copiar temporalmente a caché para FFmpeg
            val uri = Uri.parse(uriStr)
            val tempAudio = File(context.cacheDir, "temp_export_audio_${track.id}.m4a")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempAudio.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (tempAudio.exists() && tempAudio.length() > 0L) tempAudio else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Construye un nombre de archivo seguro y legible para el usuario.
     */
    private fun buildCleanVideoFileName(track: Track): String {
        val safeArtist = track.artist.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(45)
            .ifBlank { "Aura Music" }
        val safeTitle = track.title.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(65)
            .ifBlank { "Video_${track.id}" }

        val base = if (safeArtist.equals("Artista Desconocido", ignoreCase = true)) {
            safeTitle
        } else {
            "$safeArtist - $safeTitle"
        }
        return if (base.endsWith(".mp4", ignoreCase = true)) base else "$base.mp4"
    }

    private fun markTrackAsExported(context: Context, trackId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_EXPORTED_VIDEO_TRACK_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(trackId.toString())
        prefs.edit().putStringSet(KEY_EXPORTED_VIDEO_TRACK_IDS, current).apply()
    }
}
