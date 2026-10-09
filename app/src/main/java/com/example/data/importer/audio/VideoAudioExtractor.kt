package com.example.data.importer

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.storage.AppStorageManager
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Extractor y convertidor de Video a Música de alto rendimiento para Aura Music.
 * 
 * Funcionalidades:
 * 1. Extracción directa del flujo de audio del contenedor de video sin recodificación
 *    (Direct Stream Demuxing) usando MediaExtractor y MediaMuxer a nivel de SO en C++/Java.
 * 2. Captura automática de fotograma clave en alta definición (evitando pantallas negras iniciales)
 *    y compresión a WebP sin pérdida en la carpeta images/ como carátula oficial de la pista.
 * 3. Vinculación opcional del video original como Video Canvas sincronizado o bucle continuo en videos/.
 * 4. Limpieza automática de nombres de archivo para sugerir un título limpio y profesional.
 */
object VideoAudioExtractor {

    data class VideoMetadataPreview(
        val suggestedTitle: String,
        val suggestedArtist: String,
        val durationMs: Long,
        val thumbnailBitmap: Bitmap?,
        val isLikelyLoop: Boolean
    )

    /**
     * Inspecciona rápidamente el video para previsualizar título, duración y carátula capturada.
     */
    suspend fun inspectVideo(context: Context, videoUri: Uri): VideoMetadataPreview = withContext(Dispatchers.IO) {
        var durationMs = 0L
        var thumbnail: Bitmap? = null
        val rawFileName = getFileNameFromUri(context, videoUri)
        val cleanedTitle = cleanVideoTitle(rawFileName)

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull() ?: 0L

            // Capturar fotograma a ~15% de la duración o al segundo 2 para evitar pantallas negras de introducción
            val targetTimeUs = if (durationMs > 6000L) {
                ((durationMs * 0.15) * 1000).toLong()
            } else {
                1_000_000L
            }

            thumbnail = retriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        } catch (_: Throwable) {
            // Ignorar y continuar con valores por defecto
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {}
        }

        val isLoop = durationMs in 1..20500L

        VideoMetadataPreview(
            suggestedTitle = cleanedTitle,
            suggestedArtist = "Video Import",
            durationMs = durationMs,
            thumbnailBitmap = thumbnail,
            isLikelyLoop = isLoop
        )
    }

    /**
     * Convierte y extrae el video a una pista de música completa con carátula y Video Canvas.
     */
    suspend fun convertVideoToTrack(
        context: Context,
        storageManager: AppStorageManager,
        videoUri: Uri,
        title: String,
        artist: String,
        album: String,
        attachAsCanvas: Boolean,
        forceLoop: Boolean?,
        trimSilence: Boolean = false,
        loopStyle: FFmpegNativeEngine.CanvasLoopStyle = FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE
    ): Track? = withContext(Dispatchers.IO) {
        val tempTrackId = System.currentTimeMillis()

        // 1. Extraer o guardar el archivo de audio
        val audioFile = File(storageManager.songsDir, "track_video_${tempTrackId}.m4a")
        var extractSuccess = demuxAudioStream(context, videoUri, audioFile)

        // Si MediaMuxer falló (ej. contenedor MKV/WebM o códec no soportado), usar FFmpeg puro si está disponible
        if ((!extractSuccess || !audioFile.exists() || audioFile.length() == 0L) && FFmpegNativeEngine.isAvailable(context)) {
            val tempSourceFile = File(storageManager.videosDir, "temp_source_${tempTrackId}.tmp")
            if (copyUriToFile(context, videoUri, tempSourceFile)) {
                val ffmpegResult = FFmpegNativeEngine.extractAudio(
                    context = context,
                    inputFile = tempSourceFile,
                    outputFile = audioFile,
                    audioBitrate = "256k",
                    targetFormat = "m4a",
                    totalDurationMs = 0L
                )
                extractSuccess = ffmpegResult.success
                tempSourceFile.delete()
            }
        }

        val finalAudioFile: File = if (extractSuccess && audioFile.exists() && audioFile.length() > 0) {
            audioFile
        } else {
            // Fallback ultra-seguro: copiar el contenedor de video directamente como fuente de audio.
            // ExoPlayer soporta reproducir audio directamente desde archivos de video (.mp4, .mkv, .webm).
            val fallbackFile = File(storageManager.songsDir, "track_video_${tempTrackId}.mp4")
            copyUriToFile(context, videoUri, fallbackFile)
            fallbackFile
        }

        if (!finalAudioFile.exists() || finalAudioFile.length() == 0L) {
            return@withContext null
        }

        // 2. Extraer carátula desde un fotograma clave del video y guardarla como WebP
        var artworkPath: String? = null
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        try {
            retriever.setDataSource(context, videoUri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull() ?: 0L

            val targetTimeUs = if (durationMs > 6000L) {
                ((durationMs * 0.15) * 1000).toLong()
            } else {
                1_000_000L
            }

            val frame = retriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime

            if (frame != null) {
                val artFile = File(storageManager.imagesDir, "art_video_${tempTrackId}.webp")
                FileOutputStream(artFile).use { out ->
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        frame.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out)
                    } else {
                        frame.compress(Bitmap.CompressFormat.WEBP, 95, out)
                    }
                }
                artworkPath = artFile.absolutePath
                frame.recycle()
            }
        } catch (_: Throwable) {
            artworkPath = null
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {}
        }

        // 2.5. Si el usuario activó la eliminación inteligente de silencios al inicio y final
        if (trimSilence) {
            val trimResult = AudioSilenceTrimmer.processLocalAudioFile(
                context = context,
                audioFile = finalAudioFile,
                originalDurationMs = durationMs
            )
            if (trimResult.wasTrimmed && trimResult.newDurationMs > 0L) {
                durationMs = trimResult.newDurationMs
            }
        }

        // 3. Vincular como Video Canvas si el usuario lo solicitó
        var videoCanvasPath: String? = null
        var isLoopMode = forceLoop ?: (loopStyle == FFmpegNativeEngine.CanvasLoopStyle.BOOMERANG || durationMs in 1..20500L)

        if (attachAsCanvas) {
            val canvasResult = storageManager.saveCustomVideoFromUri(
                trackId = tempTrackId,
                sourceUri = videoUri,
                oldVideoPath = null,
                forceLoop = forceLoop,
                loopStyle = loopStyle
            )
            if (canvasResult != null) {
                videoCanvasPath = canvasResult.first
                isLoopMode = canvasResult.second
            }
        }

        val fileSizeFormatted = formatFileSize(finalAudioFile.length())

        Track(
            id = 0L, // Se asignará en Room con AutoGenerate
            title = title.trim().ifBlank { "Video Musical" },
            artist = artist.trim().ifBlank { "Video Import" },
            album = album.trim().ifBlank { "Videos de Galería" },
            durationMs = durationMs,
            uriString = Uri.fromFile(finalAudioFile).toString(),
            albumArtPath = artworkPath,
            videoUri = videoCanvasPath,
            isVideoLoop = isLoopMode,
            mimeType = "audio/mp4",
            dateAdded = System.currentTimeMillis(),
            isFavorite = false,
            playCount = 0,
            folderName = "Video a Música",
            fileSizeFormatted = fileSizeFormatted
        )
    }

    /**
     * Extrae la pista de audio sin recodificación usando MediaExtractor y MediaMuxer.
     */
    private fun demuxAudioStream(context: Context, videoUri: Uri, outputFile: File): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, videoUri, null)

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return false
            }

            extractor.selectTrack(audioTrackIndex)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val maxBufferSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(64 * 1024)
            } else {
                128 * 1024
            }

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            return true
        } catch (_: Throwable) {
            return false
        } finally {
            try {
                extractor?.release()
            } catch (_: Throwable) {}
            try {
                muxer?.release()
            } catch (_: Throwable) {}
        }
    }

    private fun copyUriToFile(context: Context, uri: Uri, targetFile: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String {
        var name = "video_${System.currentTimeMillis()}"
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            cursor.getString(nameIndex)?.let { name = it }
                        }
                    }
                }
            } catch (_: Throwable) {}
        } else if (uri.scheme == "file") {
            uri.lastPathSegment?.let { name = it }
        }
        return name
    }

    private fun cleanVideoTitle(rawName: String): String {
        return rawName
            .replace(Regex("\\.(mp4|mkv|webm|avi|mov|3gp|flv)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\(.*?1080p.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(.*?720p.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(.*?4k.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(.*?official.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(.*?video.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[_\\-]+"), " ")
            .trim()
            .ifBlank { "Video Musical" }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024 * 1024)
        return String.format(java.util.Locale.US, "%.1f MB", mb)
    }
}
