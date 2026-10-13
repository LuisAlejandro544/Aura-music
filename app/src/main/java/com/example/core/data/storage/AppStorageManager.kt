package com.example.core.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.core.model.Track
import java.io.File
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
 *
 * Rol arquitectónico: fachada pública intacta que delega en 6 piezas internas.
 */
class AppStorageManager(private val context: Context) {

    // Directorio base en almacenamiento externo privado del paquete
    private val baseDir: File = context.getExternalFilesDir(null) ?: context.filesDir

    val imagesDir: File = File(baseDir, "images").apply { if (!exists()) mkdirs() }
    val songsDir: File = File(baseDir, "songs").apply { if (!exists()) mkdirs() }
    val lyricsDir: File = File(baseDir, "lyrics").apply { if (!exists()) mkdirs() }
    val metadataDir: File = File(baseDir, "metadata").apply { if (!exists()) mkdirs() }
    val videosDir: File = File(baseDir, "videos").apply { if (!exists()) mkdirs() }

    private val covers = CoverArtworkStore(context, imagesDir)
    private val songs = SongLyricsMetadataStore(context, songsDir, lyricsDir, metadataDir, imagesDir, videosDir)
    private val videos = CanvasVideoStore(context, videosDir)
    private val wallpapers = WallpaperStore(context, imagesDir, videosDir)

    suspend fun saveCoverAsWebp(key: String, bitmap: Bitmap): File =
        covers.saveCoverAsWebp(key, bitmap)

    suspend fun saveCoverBytesAsWebp(key: String, bytes: ByteArray): File? =
        covers.saveCoverBytesAsWebp(key, bytes)

    suspend fun saveSongFile(fileName: String, inputStream: InputStream): File =
        songs.saveSongFile(fileName, inputStream)

    suspend fun saveLyrics(trackId: Long, lyricsContent: String): File =
        songs.saveLyrics(trackId, lyricsContent)

    suspend fun getLyrics(trackId: Long): String? =
        songs.getLyrics(trackId)

    suspend fun saveMetadataJson(track: Track): File =
        songs.saveMetadataJson(track)

    suspend fun getMetadataJson(trackId: Long): String? =
        songs.getMetadataJson(trackId)

    suspend fun saveCustomArtworkFromUri(
        trackId: Long,
        sourceUri: Uri,
        oldArtworkPath: String?
    ): String? = covers.saveCustomArtworkFromUri(trackId, sourceUri, oldArtworkPath)

    suspend fun savePlaylistArtworkFromUri(
        playlistId: Long,
        sourceUri: Uri,
        oldArtworkPath: String?
    ): String? = covers.savePlaylistArtworkFromUri(playlistId, sourceUri, oldArtworkPath)

    suspend fun deleteArtworkFile(artworkPath: String?): Boolean =
        covers.deleteArtworkFile(artworkPath)

    suspend fun saveCustomVideoFromUri(
        trackId: Long,
        sourceUri: Uri,
        oldVideoPath: String?,
        forceLoop: Boolean? = null,
        loopStyle: com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle = com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE
    ): Pair<String, Boolean>? = videos.saveCustomVideoFromUri(trackId, sourceUri, oldVideoPath, forceLoop, loopStyle)

    suspend fun deleteVideoFile(videoPath: String?): Boolean =
        videos.deleteVideoFile(videoPath)

    suspend fun deleteTrackFiles(trackId: Long, albumArtPath: String?, videoPath: String? = null) =
        songs.deleteTrackFiles(trackId, albumArtPath, videoPath)

    suspend fun saveAppWallpaperImageFromUri(
        sourceUri: Uri,
        oldMediaPath: String?
    ): String? = wallpapers.saveAppWallpaperImageFromUri(sourceUri, oldMediaPath)

    suspend fun saveAppWallpaperVideoFromUri(
        sourceUri: Uri,
        oldMediaPath: String?
    ): String? = wallpapers.saveAppWallpaperVideoFromUri(sourceUri, oldMediaPath)

    suspend fun deleteAppWallpaperFile(mediaPath: String?): Boolean =
        wallpapers.deleteAppWallpaperFile(mediaPath)

    companion object {
        /**
         * Purga automáticamente al iniciar la aplicación cualquier residuo de archivos temporales,
         * APKs de actualización antiguos ya instalados, copias duplicadas de ejecutables o carpetas
         * legadas en `filesDir` y `getExternalFilesDir` para garantizar cero residuos de peso.
         */
        fun cleanupResidualFiles(context: Context) =
            ResidualCleanup.cleanupResidualFiles(context)
    }
}
