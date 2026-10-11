package com.example.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Gestor centralizado del almacenamiento estructurado de la aplicación en:
 * Android/data/com.aistudio.musicplayer.aurasound/files/
 *
 * Mantiene 5 carpetas dedicadas:
 * - images/   -> Carátulas de álbumes convertidas a WebP con máxima compresión sin pérdida
 * - songs/    -> Canciones y pistas de audio locales
 * - lyrics/   -> Archivos de letras sincronizadas (.lrc) o texto plano
 * - metadata/ -> Archivos JSON con información técnica y descriptiva de las pistas
 * - videos/   -> Videos de fondo y loops de Canvas sincronizados o continuos
 */
class AppStorageManager(private val context: Context) {

    // Directorio base en almacenamiento externo privado del paquete
    private val baseDir: File = context.getExternalFilesDir(null) ?: context.filesDir

    val imagesDir: File = File(baseDir, "images").apply { if (!exists()) mkdirs() }
    val songsDir: File = File(baseDir, "songs").apply { if (!exists()) mkdirs() }
    val lyricsDir: File = File(baseDir, "lyrics").apply { if (!exists()) mkdirs() }
    val metadataDir: File = File(baseDir, "metadata").apply { if (!exists()) mkdirs() }
    val videosDir: File = File(baseDir, "videos").apply { if (!exists()) mkdirs() }

    /**
     * Resuelve y valida de forma segura un archivo hijo dentro de un directorio base permitido,
     * bloqueando cualquier intento de Path Traversal ('../', enlaces simbólicos o barras).
     */
    private fun resolveSafeChildFile(parentDir: File, rawFileName: String): File {
        val cleanName = rawFileName
            .replace("/", "_")
            .replace("\\", "_")
            .replace("..", "_")
            .trim()
            .ifBlank { "file_${System.currentTimeMillis()}" }

        val targetFile = File(parentDir, cleanName)
        val canonicalParent = parentDir.canonicalFile.toPath()
        val canonicalTarget = targetFile.canonicalFile.toPath()
        if (!canonicalTarget.startsWith(canonicalParent)) {
            throw SecurityException("Intento de Path Traversal bloqueado: '$rawFileName' escapa de '${parentDir.name}'")
        }
        return targetFile
    }

    /**
     * Verifica si un archivo reside estrictamente dentro del directorio permitido usando su ruta canónica.
     */
    private fun isInsideDirectoryCanonical(file: File, allowedDir: File): Boolean {
        return try {
            file.canonicalFile.toPath().startsWith(allowedDir.canonicalFile.toPath())
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Guarda una carátula comprimiéndola a formato WebP sin pérdida de calidad (Lossless)
     * en un hilo secundario (Dispatchers.IO), recortando una única vez posibles franjas negras
     * horizontales (letterbox 4:3) para que la UI jamás tenga que escanear píxeles al hacer scroll.
     */
    suspend fun saveCoverAsWebp(key: String, bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val safeKey = key.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(80).ifBlank { "${System.currentTimeMillis()}" }
        val file = resolveSafeChildFile(imagesDir, "cover_$safeKey.webp")
        val cleanBitmap = com.example.data.importer.MediaAssetProcessor.removeHorizontalLetterboxBars(bitmap)
        FileOutputStream(file).use { outStream ->
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            cleanBitmap.compress(format, 100, outStream)
        }
        if (cleanBitmap !== bitmap && !cleanBitmap.isRecycled) {
            cleanBitmap.recycle()
        }
        file
    }

    /**
     * Convierte bytes de imagen extraídos (ID3) a formato WebP sin pérdida.
     */
    suspend fun saveCoverBytesAsWebp(key: String, bytes: ByteArray): File? = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            val file = saveCoverAsWebp(key, bitmap)
            bitmap.recycle()
            file
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Guarda una pista de audio en la carpeta songs/ con protección contra Path Traversal.
     */
    suspend fun saveSongFile(fileName: String, inputStream: InputStream): File = withContext(Dispatchers.IO) {
        val file = resolveSafeChildFile(songsDir, fileName)
        FileOutputStream(file).use { outStream ->
            inputStream.copyTo(outStream)
        }
        file
    }

    /**
     * Guarda la letra de una canción en lyrics/track_{id}.lrc
     */
    suspend fun saveLyrics(trackId: Long, lyricsContent: String): File = withContext(Dispatchers.IO) {
        val file = File(lyricsDir, "track_$trackId.lrc")
        file.writeText(lyricsContent)
        file
    }

    /**
     * Lee la letra de una canción si existe.
     */
    suspend fun getLyrics(trackId: Long): String? = withContext(Dispatchers.IO) {
        val file = File(lyricsDir, "track_$trackId.lrc")
        if (file.exists()) file.readText() else null
    }

    /**
     * Guarda un archivo JSON con los metadatos de la canción en metadata/track_{id}.json
     */
    suspend fun saveMetadataJson(track: Track): File = withContext(Dispatchers.IO) {
        val file = File(metadataDir, "track_${track.id}.json")
        val json = JSONObject().apply {
            put("id", track.id)
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            put("durationMs", track.durationMs)
            put("uriString", track.uriString)
            put("albumArtPath", track.albumArtPath ?: "")
            put("videoUri", track.videoUri ?: "")
            put("isVideoLoop", track.isVideoLoop)
            put("mimeType", track.mimeType)
            put("dateAdded", track.dateAdded)
            put("isFavorite", track.isFavorite)
            put("playCount", track.playCount)
            put("folderName", track.folderName)
            put("fileSizeFormatted", track.fileSizeFormatted)
        }
        file.writeText(json.toString(2))
        file
    }

    /**
     * Lee los metadatos JSON de una canción.
     */
    suspend fun getMetadataJson(trackId: Long): String? = withContext(Dispatchers.IO) {
        val file = File(metadataDir, "track_$trackId.json")
        if (file.exists()) file.readText() else null
    }

    /**
     * Guarda una carátula personalizada seleccionada por el usuario desde la galería (SAF/Photo Picker),
     * comprimiéndola a WebP sin pérdida en images/ y eliminando físicamente la carátula anterior
     * asociada a la canción para evitar acumulación de archivos huérfanos.
     */
     suspend fun saveCustomArtworkFromUri(
         trackId: Long,
         sourceUri: Uri,
         oldArtworkPath: String?
     ): String? = withContext(Dispatchers.IO) {
         try {
             // 1. Decodificar la imagen desde el Uri
             val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext null
             val bitmap = BitmapFactory.decodeStream(inputStream)
             inputStream.close()
             if (bitmap == null) return@withContext null

             // 2. Eliminar la carátula previa si existía en images/
             if (!oldArtworkPath.isNullOrEmpty()) {
                 try {
                     val oldFile = File(oldArtworkPath)
                     if (oldFile.exists() && oldFile.canonicalPath.startsWith(imagesDir.canonicalPath)) {
                         oldFile.delete()
                     }
                 } catch (ignored: Exception) {}
             }

             // 3. Guardar el nuevo archivo WebP
             val newFile = File(imagesDir, "cover_custom_${trackId}_${System.currentTimeMillis()}.webp")
             FileOutputStream(newFile).use { outStream ->
                 val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                     Bitmap.CompressFormat.WEBP_LOSSLESS
                 } else {
                     @Suppress("DEPRECATION")
                     Bitmap.CompressFormat.WEBP
                 }
                 bitmap.compress(format, 100, outStream)
             }
             bitmap.recycle()
             newFile.absolutePath
         } catch (e: Exception) {
             null
         }
     }

    /**
     * Guarda una carátula personalizada para una Playlist seleccionada por el usuario desde la galería,
     * comprimiéndola a WebP sin pérdida en images/ y eliminando físicamente la carátula previa
     * de la lista para liberar espacio en disco.
     */
    suspend fun savePlaylistArtworkFromUri(
        playlistId: Long,
        sourceUri: Uri,
        oldArtworkPath: String?
    ): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) return@withContext null

            if (!oldArtworkPath.isNullOrEmpty()) {
                try {
                    val oldFile = File(oldArtworkPath)
                    if (oldFile.exists() && oldFile.canonicalPath.startsWith(imagesDir.canonicalPath)) {
                        oldFile.delete()
                    }
                } catch (_: Exception) {}
            }

            val newFile = File(imagesDir, "playlist_cover_${playlistId}_${System.currentTimeMillis()}.webp")
            FileOutputStream(newFile).use { outStream ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                bitmap.compress(format, 100, outStream)
            }
            bitmap.recycle()
            newFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Elimina físicamente un archivo de carátula si existe en el directorio de imágenes.
     */
     suspend fun deleteArtworkFile(artworkPath: String?): Boolean = withContext(Dispatchers.IO) {
         if (artworkPath.isNullOrEmpty()) return@withContext false
         try {
             val file = File(artworkPath)
             if (file.exists() && file.canonicalPath.startsWith(imagesDir.canonicalPath)) {
                 file.delete()
             } else {
                 false
             }
         } catch (e: Exception) {
             false
         }
     }

     /**
      * Guarda un video personalizado o Canvas seleccionado por el usuario desde la galería (SAF/Photo Picker).
      * Analiza la duración del video con MediaMetadataRetriever para determinar si es un loop corto (<= 20s)
      * o un video largo sincronizado con la canción, y elimina el video previo si existía.
      */
     suspend fun saveCustomVideoFromUri(
         trackId: Long,
         sourceUri: Uri,
         oldVideoPath: String?,
         forceLoop: Boolean? = null,
         loopStyle: com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle = com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE
     ): Pair<String, Boolean>? = withContext(Dispatchers.IO) {
         try {
             // 1. Determinar duración con MediaMetadataRetriever o respetar forzado manual
             var isLoop = false
             if (forceLoop != null) {
                 isLoop = forceLoop
             } else if (loopStyle == com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle.BOOMERANG) {
                 isLoop = true
             } else {
                 try {
                     val retriever = android.media.MediaMetadataRetriever()
                     retriever.setDataSource(context, sourceUri)
                     val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                     val durationMs = durStr?.toLongOrNull() ?: 0L
                     // Si dura 20 segundos o menos, se cataloga automáticamente como bucle continuo de Canvas
                     isLoop = durationMs in 1..20500L
                     retriever.release()
                 } catch (e: Exception) {
                     isLoop = false
                 }
             }

             // 2. Eliminar video previo si existía en videosDir
             if (!oldVideoPath.isNullOrEmpty()) {
                 try {
                     val oldFile = File(oldVideoPath)
                     if (oldFile.exists() && oldFile.canonicalPath.startsWith(videosDir.canonicalPath)) {
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
                 val processResult = com.example.data.importer.FFmpegNativeEngine.processVideoForCanvas(
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

     /**
      * Elimina físicamente un archivo de video si existe en el directorio de videos.
      */
     suspend fun deleteVideoFile(videoPath: String?): Boolean = withContext(Dispatchers.IO) {
         if (videoPath.isNullOrEmpty()) return@withContext false
         try {
             val file = File(videoPath)
             if (file.exists() && file.canonicalPath.startsWith(videosDir.canonicalPath)) {
                 file.delete()
             } else {
                 false
             }
         } catch (e: Exception) {
             false
         }
     }

    /**
     * Elimina los archivos asociados a una pista (carátula WebP, video de fondo, letra y metadatos JSON).
     */
    suspend fun deleteTrackFiles(trackId: Long, albumArtPath: String?, videoPath: String? = null) = withContext(Dispatchers.IO) {
        try {
            if (!albumArtPath.isNullOrEmpty()) {
                val artFile = File(albumArtPath)
                if (artFile.exists() && isInsideDirectoryCanonical(artFile, imagesDir)) {
                    artFile.delete()
                }
            }
            if (!videoPath.isNullOrEmpty()) {
                val vidFile = File(videoPath)
                if (vidFile.exists() && isInsideDirectoryCanonical(vidFile, videosDir)) {
                    vidFile.delete()
                }
            }
            resolveSafeChildFile(lyricsDir, "track_$trackId.lrc").takeIf { it.exists() }?.delete()
            resolveSafeChildFile(metadataDir, "track_$trackId.json").takeIf { it.exists() }?.delete()
        } catch (ignored: Exception) {}
    }

    /**
     * Guarda una imagen de la galería como Fondo de Pantalla Personalizado de la aplicación en images/
     * comprimiéndola a WebP sin pérdida y eliminando el fondo anterior si existía.
     */
    suspend fun saveAppWallpaperImageFromUri(
        sourceUri: Uri,
        oldMediaPath: String?
    ): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) return@withContext null

            deleteAppWallpaperFile(oldMediaPath)

            val newFile = File(imagesDir, "app_wallpaper_${System.currentTimeMillis()}.webp")
            FileOutputStream(newFile).use { outStream ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                bitmap.compress(format, 100, outStream)
            }
            bitmap.recycle()
            newFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Guarda un video corto de la galería como Fondo de Pantalla Personalizado de la aplicación en videos/,
     * optimizándolo con FFmpeg (-an sin pista de audio para no interferir con la música) y eliminando el previo.
     */
    suspend fun saveAppWallpaperVideoFromUri(
        sourceUri: Uri,
        oldMediaPath: String?
    ): String? = withContext(Dispatchers.IO) {
        try {
            deleteAppWallpaperFile(oldMediaPath)

            val rawTempFile = File(videosDir, "wallpaper_raw_${System.currentTimeMillis()}.tmp")
            val newFile = File(videosDir, "app_wallpaper_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(rawTempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            try {
                val processResult = com.example.data.importer.FFmpegNativeEngine.processVideoForCanvas(
                    context = context,
                    inputFile = rawTempFile,
                    outputFile = newFile,
                    isLoop = true,
                    loopStyle = com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE
                )
                if (!processResult.success || !newFile.exists() || newFile.length() == 0L) {
                    rawTempFile.renameTo(newFile)
                } else {
                    rawTempFile.delete()
                }
            } catch (_: Exception) {
                rawTempFile.renameTo(newFile)
            }

            newFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Elimina el archivo de fondo de pantalla personalizado si reside en images/ o videos/.
     */
    suspend fun deleteAppWallpaperFile(mediaPath: String?): Boolean = withContext(Dispatchers.IO) {
        if (mediaPath.isNullOrBlank()) return@withContext false
        try {
            val file = File(mediaPath)
            if (file.exists() && (isInsideDirectoryCanonical(file, imagesDir) || isInsideDirectoryCanonical(file, videosDir))) {
                file.delete()
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }


    companion object {
        /**
         * Purga automáticamente al iniciar la aplicación cualquier residuo de archivos temporales,
         * APKs de actualización antiguos ya instalados, copias duplicadas de ejecutables o carpetas
         * legadas en `filesDir` y `getExternalFilesDir` para garantizar cero residuos de peso.
         */
        fun cleanupResidualFiles(context: Context) {
            try {
                val baseExternal = context.getExternalFilesDir(null) ?: context.filesDir
                val internalFiles = context.filesDir
                val cacheDir = context.cacheDir

                // 1. Limpiar APKs de actualización descargados previamente en updates/
                listOf(File(baseExternal, "updates"), File(internalFiles, "updates"), File(cacheDir, "updates")).forEach { updatesDir ->
                    if (updatesDir.exists() && updatesDir.isDirectory) {
                        updatesDir.listFiles()?.forEach { file ->
                            try { file.deleteRecursively() } catch (_: Exception) {}
                        }
                    }
                }

                // 2. Eliminar carpeta duplicada antigua env/ffmpeg (~90 MB liberados si existía de versiones previas)
                val legacyFfmpegEnv = File(internalFiles, "env/ffmpeg")
                if (legacyFfmpegEnv.exists()) {
                    try { legacyFfmpegEnv.deleteRecursively() } catch (_: Exception) {}
                }

                // 3. Eliminar archivos sueltos duplicados de stdlib/ en env/python si stdlib.zip ya está presente
                val pythonEnv = File(internalFiles, "env/python")
                val stdlibZip = File(pythonEnv, "stdlib.zip")
                val stdlibExpandedDir = File(pythonEnv, "stdlib")
                if (stdlibZip.exists() && stdlibExpandedDir.exists() && stdlibExpandedDir.isDirectory) {
                    stdlibExpandedDir.listFiles()?.forEach { f ->
                        if (f.name != "site.py") {
                            try { f.deleteRecursively() } catch (_: Exception) {}
                        }
                    }
                }

                // 4. Eliminar módulos .so duplicados con sufijo .cpython-311.so o .chaquopy.so si ya existe su versión normalizada
                val pyModulesDir = File(pythonEnv, "modules")
                if (pyModulesDir.exists() && pyModulesDir.isDirectory) {
                    pyModulesDir.listFiles()?.forEach { f ->
                        if (f.name.contains(".cpython-") || f.name.contains(".chaquopy.so")) {
                            val normalizedName = f.name
                                .replace(".chaquopy.so", ".so")
                                .replace(Regex("\\.cpython-\\d+.*\\.so$"), ".so")
                            if (normalizedName != f.name && File(pyModulesDir, normalizedName).exists()) {
                                try { f.delete() } catch (_: Exception) {}
                            }
                        }
                    }
                }

                // 5. Eliminar copias duplicadas de python / python3 y archivos .tmp/.bak en files/bin
                val binDir = File(internalFiles, "bin")
                if (binDir.exists() && binDir.isDirectory) {
                    binDir.listFiles()?.forEach { f ->
                        if (f.name == "python" || f.name == "python3" || f.name.endsWith(".tmp") || f.name.endsWith(".bak")) {
                            try { f.delete() } catch (_: Exception) {}
                        }
                    }
                }

                // 6. Eliminar archivos temporales huérfanos (.tmp, .part) en videos/, songs/, images/ y cacheDir
                listOf(
                    File(baseExternal, "videos"),
                    File(baseExternal, "songs"),
                    File(baseExternal, "images"),
                    cacheDir
                ).forEach { dir ->
                    if (dir.exists() && dir.isDirectory) {
                        dir.listFiles()?.forEach { f ->
                            if (f.isFile && (f.name.endsWith(".tmp") || f.name.endsWith(".part") || f.name.startsWith("mixtape_norm_") || f.name.startsWith("canvas_raw_"))) {
                                try { f.delete() } catch (_: Exception) {}
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
