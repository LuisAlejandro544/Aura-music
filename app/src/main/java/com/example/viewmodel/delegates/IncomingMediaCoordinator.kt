package com.example.viewmodel.delegates

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.importer.IncomingMedia
import com.example.data.importer.IncomingMediaHandler
import com.example.data.repository.MusicRepository
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Aura Music - Coordinador Modular de Medios Externos Entrantes ("Abrir con..." y "Compartir con...")
 *
 * Responsabilidades:
 * - Detección y discriminación de audio individual/múltiple, videos y enlaces web (SnapTube, mensajería, etc.).
 * - Almacenamiento seguro en la biblioteca estructurada `songs/`.
 * - Aplicación condicional de eliminación de silencios de entrada/salida (*AudioSilenceTrimmer*).
 * - Notificación reactiva del progreso de importación a la interfaz de usuario.
 */
class IncomingMediaCoordinator(
    private val context: Context,
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
    private val onPlayTrack: (Track, List<Track>) -> Unit,
    private val onExpandNowPlaying: () -> Unit,
    private val onFetchOnlineLyrics: () -> Unit,
    private val setImportingStatus: (isImporting: Boolean, message: String?) -> Unit
) {

    // Gestión de medios externos entrantes ("Abrir con...", "Compartir con...", SnapTube, etc.)
    private val _pendingIncomingAudioUris = MutableStateFlow<List<Uri>>(emptyList())
    val pendingIncomingAudioUris: StateFlow<List<Uri>> = _pendingIncomingAudioUris.asStateFlow()

    private val _pendingIncomingVideoUri = MutableStateFlow<Uri?>(null)
    val pendingIncomingVideoUri: StateFlow<Uri?> = _pendingIncomingVideoUri.asStateFlow()

    private val _pendingIncomingWebLink = MutableStateFlow<String?>(null)
    val pendingIncomingWebLink: StateFlow<String?> = _pendingIncomingWebLink.asStateFlow()

    /**
     * Procesa un [Intent] externo entrante de tipo "Abrir con..." o "Compartir con...".
     * Detecta inteligentemente si se trata de un archivo de audio, un video o un enlace web.
     */
    fun onIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val incoming = IncomingMediaHandler.parseIntent(context, intent) ?: return
        when (incoming) {
            is IncomingMedia.Audio -> {
                _pendingIncomingAudioUris.value = listOf(incoming.uri)
            }
            is IncomingMedia.MultipleAudios -> {
                _pendingIncomingAudioUris.value = incoming.uris
            }
            is IncomingMedia.Video -> {
                _pendingIncomingVideoUri.value = incoming.uri
            }
            is IncomingMedia.WebLink -> {
                _pendingIncomingWebLink.value = incoming.url
            }
        }
    }

    fun clearPendingIncomingAudio() {
        _pendingIncomingAudioUris.value = emptyList()
    }

    fun clearPendingIncomingVideo() {
        _pendingIncomingVideoUri.value = null
    }

    fun clearPendingIncomingWebLink() {
        _pendingIncomingWebLink.value = null
    }

    /**
     * Confirma la importación de los archivos de audio externos pendientes aplicando o no
     * el recorte inteligente de silencios elegido en el interruptor del diálogo.
     */
    fun confirmIncomingAudioImport(trimSilence: Boolean) {
        val uris = _pendingIncomingAudioUris.value
        _pendingIncomingAudioUris.value = emptyList()
        if (uris.isEmpty()) return
        if (uris.size == 1) {
            handleIncomingAudioUri(uris.first(), trimSilence)
        } else {
            handleIncomingMultipleAudioUris(uris, trimSilence)
        }
    }

    /**
     * Procesa y reproduce inmediatamente un archivo de audio recibido desde una app externa.
     * Persiste la canción en la biblioteca estructurada `songs/` y en Room.
     */
    fun handleIncomingAudioUri(uri: Uri, trimSilence: Boolean = false) {
        scope.launch {
            setImportingStatus(
                true,
                if (trimSilence) "Cargando audio externo y eliminando silencios..."
                else "Cargando audio externo..."
            )
            val track = repository.importSingleAudioFromExternalUri(context, uri, trimSilence = trimSilence)
            setImportingStatus(false, null)
            if (track != null) {
                setImportingStatus(false, "Reproduciendo: \"${track.title}\"")
                onPlayTrack(track, listOf(track))
                onExpandNowPlaying()
                onFetchOnlineLyrics()
            } else {
                setImportingStatus(false, "No se pudo leer el archivo de audio recibido.")
            }
        }
    }

    /**
     * Procesa e importa un lote de archivos de audio compartidos a la vez.
     */
    fun handleIncomingMultipleAudioUris(uris: List<Uri>, trimSilence: Boolean = false) {
        if (uris.isEmpty()) return
        scope.launch {
            setImportingStatus(
                true,
                if (trimSilence) "Importando ${uris.size} canciones y eliminando silencios..."
                else "Importando ${uris.size} canciones recibidas..."
            )
            val importedList = mutableListOf<Track>()
            for (u in uris) {
                val t = repository.importSingleAudioFromExternalUri(context, u, trimSilence = trimSilence)
                if (t != null) importedList.add(t)
            }
            setImportingStatus(false, null)
            if (importedList.isNotEmpty()) {
                setImportingStatus(false, "Se importaron ${importedList.size} canciones.")
                onPlayTrack(importedList.first(), importedList)
                onExpandNowPlaying()
                onFetchOnlineLyrics()
            }
        }
    }
}
