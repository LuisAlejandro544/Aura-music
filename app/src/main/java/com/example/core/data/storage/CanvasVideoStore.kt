package com.example.core.data.storage

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Video Canvas .mp4 en videos/ con FFmpeg y borrado seguro.
 * Rol arquitectónico: pieza de AppStorageManager (fachada pública intacta).
 */
internal class CanvasVideoStore(
    private val context: Context,
    private val videosDir: File
) {
     suspend fun saveCustomVideoFromUri(
         trackId: Long,
         sourceUri: Uri,
         oldVideoPath: String?,
         forceLoop: Boolean? = null,
         loopStyle: com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle = com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE
     ): Pair<String, Boolean>? = withContext(Dispatchers.IO) {
         try {
             // 1. Determinar duración con MediaMetadataRetriever o respetar forzado manual
             var isLoop = false
             if (forceLoop != null) {
                 isLoop = forceLoop
             } else if (loopStyle == com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle.BOOMERANG) {
                 isLoop = true
             } else {
                 try {
                     val retriever = android.media.MediaMetadataRetriever()
                     try {
                     retriever.setDataSource(context, sourceUri)
                     val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                     val durationMs = durStr?.toLongOrNull() ?: 0L
                     // Si dura 20 segundos o menos, se cataloga automáticamente como bucle continuo de Canvas
                     isLoop = durationMs in 1..20500L
                     } finally {
                     try { retriever.release() } catch (_: Exception) {}
                     }
                 } catch (e: Exception) {
                     isLoop = false
                 }
             }

             // 2. Eliminar video previo si existía en videosDir
             if (!oldVideoPath.isNullOrEmpty()) {
                 try {
                     val oldFile = File(oldVideoPath)
                     if (oldFile.exists() && StoragePathGuards.isInside(oldFile, videosDir)) {
                         oldFile.delete()
                     }
                 } catch (ignored: Exception) {}
             }

             // 3. Copiar archivo temporal y procesar con FFmpeg para fluidez absoluta
             val rawTempFile = File(videosDir, "canvas_raw_${trackId}_${System.currentTimeMillis()}.tmp")
             val newFile = File(videosDir, "canvas_${trackId}_${System.currentTimeMillis()}.mp4")
             context.contentResolver.openInputStream(sourceUri)?.use { input ->
                 FileOutputStream(rawTempFile).use { output ->
                     input.copyTo(output)
                 }
             } ?: return@withContext null

             // 4. Optimizar con FFmpeg nativo: Seamless Loop (xfade o Boomerang reverse+concat) si es loop, o GOP corto a 30fps si es sincronizado
             try {
                 val processResult = com.example.core.data.importer.FFmpegNativeEngine.processVideoForCanvas(
                     context = context,
                     inputFile = rawTempFile,
                     outputFile = newFile,
                     isLoop = isLoop,
                     loopStyle = loopStyle
                 )
                 if (!processResult.success || !newFile.exists() || newFile.length() == 0L) {
                     rawTempFile.renameTo(newFile)
                 } else {
                     rawTempFile.delete()
                 }
             } catch (_: Exception) {
                 rawTempFile.renameTo(newFile)
             }

             Pair(newFile.absolutePath, isLoop)
         } catch (e: Exception) {
             null
         }
     }

     suspend fun deleteVideoFile(videoPath: String?): Boolean = withContext(Dispatchers.IO) {
         if (videoPath.isNullOrEmpty()) return@withContext false
         try {
             val file = File(videoPath)
             if (file.exists() && StoragePathGuards.isInside(file, videosDir)) {
                 file.delete()
             } else {
                 false
             }
         } catch (e: Exception) {
             false
         }
     }
}
