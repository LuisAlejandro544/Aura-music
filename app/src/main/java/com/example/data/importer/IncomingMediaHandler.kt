package com.example.data.importer

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.example.debug.AuraDebugManager
import java.io.File

/**
 * Representa el tipo de medio detectado inteligentemente desde un Intent del sistema Android
 * (menú "Abrir con...", "Compartir con...", enlaces externos o integraciones con aplicaciones
 * como gestores de descargas, SnapTube, navegadores o redes sociales).
 */
sealed class IncomingMedia {
    /**
     * Archivo de audio detectado (ej: MP3, M4A, FLAC, WAV, OGG, OPUS, AAC).
     */
    data class Audio(val uri: Uri, val title: String? = null) : IncomingMedia()

    /**
     * Archivo de video detectado (ej: MP4, MKV, WEBM, 3GP) listo para reproducirse
     * o transformarse mediante el conversor "Video a Música" 3 en 1 con Video Canvas.
     */
    data class Video(val uri: Uri, val title: String? = null) : IncomingMedia()

    /**
     * Enlace web extraído (URL http/https) listo para resolverse y descargarse.
     */
    data class WebLink(val url: String) : IncomingMedia()

    /**
     * Conjunto de múltiples archivos de audio compartidos en bloque.
     */
    data class MultipleAudios(val uris: List<Uri>) : IncomingMedia()
}

/**
 * Enumeración interna de clasificación de tipos de archivo multimedia.
 */
enum class DetectedMediaType {
    AUDIO,
    VIDEO
}

/**
 * Analizador y detector inteligente de intenciones externas (Intents) en Aura Music.
 *
 * Responsabilidades arquitectónicas:
 * 1. Inspeccionar `ACTION_VIEW`, `ACTION_SEND` y `ACTION_SEND_MULTIPLE`.
 * 2. Discernir de forma certera entre audios y videos combinando MIME types,
 *    extensiones de archivo y sondeo profundo mediante `MediaMetadataRetriever`.
 * 3. Extraer enlaces web cuando se comparte texto desde navegadores o apps externas.
 */
object IncomingMediaHandler {

    /**
     * Verifica que una URI recibida desde un Intent externo no apunte maliciosamente
     * al directorio privado interno de Aura Music (prevención de ataques de File Stealing / Symlink).
     */
    fun isSafeExternalUri(context: Context, uri: Uri): Boolean {
        return try {
            val scheme = uri.scheme?.lowercase() ?: return false
            if (scheme != "content" && scheme != "file") {
                return false
            }

            // Si es content://, rechazar autoridades pertenecientes al propio paquete si alguien intenta forzar acceso interno
            if (scheme == "content") {
                val authority = uri.authority?.lowercase() ?: ""
                val pkg = context.packageName.lowercase()
                if (authority.startsWith(pkg)) {
                    AuraDebugManager.logWarning("IncomingMediaHandler", "Rechazada URI content:// que apunta al propio paquete: $uri")
                    return false
                }
            }

            // Si es file:// o contiene ruta en sistema de archivos, resolver ruta canónica y bloquear acceso al sandbox privado
            val path = uri.path
            if (!path.isNullOrBlank()) {
                val canonicalTarget = File(path).canonicalFile.toPath()
                val privateDataDir = context.applicationInfo.dataDir?.let { File(it).canonicalFile.toPath() }
                val filesDir = context.filesDir.canonicalFile.toPath()
                val cacheDir = context.cacheDir.canonicalFile.toPath()

                if ((privateDataDir != null && canonicalTarget.startsWith(privateDataDir)) ||
                    canonicalTarget.startsWith(filesDir) ||
                    canonicalTarget.startsWith(cacheDir)
                ) {
                    AuraDebugManager.logWarning("IncomingMediaHandler", "Bloqueado intento de File Stealing hacia ruta interna privada: $canonicalTarget")
                    return false
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Procesa un [Intent] recibido y retorna el [IncomingMedia] detectado, o `null` si no corresponde.
     */
    fun parseIntent(context: Context, intent: Intent): IncomingMedia? {
        val action = intent.action ?: return null

        when (action) {
            Intent.ACTION_VIEW -> {
                val dataUri = intent.data ?: return null
                val scheme = dataUri.scheme?.lowercase() ?: ""

                // 1. Si es un enlace HTTP/HTTPS
                if (scheme == "http" || scheme == "https") {
                    return IncomingMedia.WebLink(dataUri.toString())
                }

                // 2. Validar que la URI de archivo o contenido no apunte al sandbox interno privado
                if (!isSafeExternalUri(context, dataUri)) {
                    return null
                }

                val mediaType = detectMediaType(context, dataUri, intent.type)
                val fileName = AudioMetadataParser.getFileName(context, dataUri)

                return if (mediaType == DetectedMediaType.VIDEO) {
                    IncomingMedia.Video(dataUri, fileName)
                } else {
                    IncomingMedia.Audio(dataUri, fileName)
                }
            }

            Intent.ACTION_SEND -> {
                // Caso A: Archivo compartido vía EXTRA_STREAM
                val streamUri = extractStreamUri(intent)
                if (streamUri != null) {
                    if (!isSafeExternalUri(context, streamUri)) {
                        return null
                    }
                    val mediaType = detectMediaType(context, streamUri, intent.type)
                    val fileName = AudioMetadataParser.getFileName(context, streamUri)
                    return if (mediaType == DetectedMediaType.VIDEO) {
                        IncomingMedia.Video(streamUri, fileName)
                    } else {
                        IncomingMedia.Audio(streamUri, fileName)
                    }
                }

                // Caso B: Texto o Enlace web compartido vía EXTRA_TEXT
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank()) {
                    val extractedUrl = extractUrlFromText(text)
                    if (extractedUrl != null) {
                        return IncomingMedia.WebLink(extractedUrl)
                    }
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = extractMultipleStreamUris(intent).filter { isSafeExternalUri(context, it) }
                if (uris.isNotEmpty()) {
                    // Filtrar solo audios válidos
                    val audioUris = uris.filter { uri ->
                        detectMediaType(context, uri, intent.type) == DetectedMediaType.AUDIO
                    }
                    if (audioUris.isNotEmpty()) {
                        return IncomingMedia.MultipleAudios(audioUris)
                    } else if (uris.size == 1) {
                        val single = uris.first()
                        val isVid = detectMediaType(context, single, intent.type) == DetectedMediaType.VIDEO
                        val name = AudioMetadataParser.getFileName(context, single)
                        return if (isVid) IncomingMedia.Video(single, name) else IncomingMedia.Audio(single, name)
                    }
                }
            }
        }

        return null
    }

    /**
     * Detecta con precisión si una URI apunta a un archivo de Video o de Audio.
     * Combina:
     * 1. Tipo MIME oficial del Intent o del ContentResolver.
     * 2. Extensión del nombre de archivo.
     * 3. Análisis de pistas mediante `MediaMetadataRetriever` (verificación de pista de video real).
     */
    fun detectMediaType(context: Context, uri: Uri, mimeTypeHint: String?): DetectedMediaType {
        // 1. Verificación por MIME type
        val resolvedMime = mimeTypeHint
            ?: try { context.contentResolver.getType(uri) } catch (_: Exception) { null }

        if (!resolvedMime.isNullOrBlank()) {
            if (resolvedMime.startsWith("video/", ignoreCase = true)) {
                return DetectedMediaType.VIDEO
            }
            if (resolvedMime.startsWith("audio/", ignoreCase = true)) {
                return DetectedMediaType.AUDIO
            }
        }

        // 2. Verificación por extensión de archivo
        val fileName = AudioMetadataParser.getFileName(context, uri)?.lowercase()
            ?: uri.lastPathSegment?.lowercase()
            ?: ""

        val videoExtensions = listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".3gp", ".m4v", ".flv", ".ts", ".wmv")
        val audioExtensions = listOf(
            ".mp3", ".m4a", ".flac", ".wav", ".ogg", ".opus", ".aac", ".wma", ".mid", ".midi", ".amr",
            ".dsf", ".dff", ".ape", ".wv", ".alac", ".aiff", ".aif", ".mpc", ".mod", ".xm", ".it", ".s3m"
        )

        if (videoExtensions.any { fileName.endsWith(it) }) {
            // Nota: Algunos archivos .m4a o .mp4 solo contienen pistas de audio (sin video)
            if (fileName.endsWith(".m4a")) {
                return DetectedMediaType.AUDIO
            }
            return probeForVideoTrack(context, uri, fallbackIsVideo = true)
        }

        if (audioExtensions.any { fileName.endsWith(it) }) {
            return DetectedMediaType.AUDIO
        }

        // 3. Inspección profunda de cabeceras mediante MediaMetadataRetriever
        return probeForVideoTrack(context, uri, fallbackIsVideo = false)
    }

    /**
     * Inspecciona los metadatos reales del archivo multimedia con `MediaMetadataRetriever`
     * para certificar si contiene pistas de video activas.
     */
    private fun probeForVideoTrack(context: Context, uri: Uri, fallbackIsVideo: Boolean): DetectedMediaType {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            if (hasVideo.equals("yes", ignoreCase = true)) {
                DetectedMediaType.VIDEO
            } else {
                DetectedMediaType.AUDIO
            }
        } catch (_: Exception) {
            if (fallbackIsVideo) DetectedMediaType.VIDEO else DetectedMediaType.AUDIO
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun extractStreamUri(intent: Intent): Uri? {
        val uriExtra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
        if (uriExtra != null) return uriExtra

        val clipData = intent.clipData
        if (clipData != null && clipData.itemCount > 0) {
            return clipData.getItemAt(0)?.uri
        }

        return intent.data
    }

    private fun extractMultipleStreamUris(intent: Intent): List<Uri> {
        val list = mutableListOf<Uri>()

        val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        }

        if (uris != null) {
            list.addAll(uris.filterNotNull())
        }

        val clipData = intent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i)?.uri?.let { if (!list.contains(it)) list.add(it) }
            }
        }

        return list
    }

    private fun extractUrlFromText(text: String): String? {
        val urlRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
        val match = urlRegex.find(text)
        return match?.value?.trim()
    }
}
