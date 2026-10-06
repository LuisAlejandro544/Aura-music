package com.example.viewmodel.delegates

import android.content.Context
import com.example.data.importer.mixtape.MixtapeEngine
import com.example.model.MixtapeChapter
import com.example.model.MixtapeMetadata
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Aura Music - Coordinador Modular de Mixtape y Capítulos Continuos.
 *
 * Responsabilidades:
 * - Orquestar la creación de Mixtapes Continuos mediante FFmpeg.
 * - Rastrear en tiempo real el capítulo activo según la posición de reproducción (currentPositionMs).
 * - Exponer el capítulo activo para conmutar carátulas WebP, Video Canvas y títulos en Now Playing y Mini Player.
 */
class MixtapeCoordinator(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val _isCreatingMixtape = MutableStateFlow(false)
    val isCreatingMixtape: StateFlow<Boolean> = _isCreatingMixtape.asStateFlow()

    private val _mixtapeProgress = MutableStateFlow(0f)
    val mixtapeProgress: StateFlow<Float> = _mixtapeProgress.asStateFlow()

    private val _mixtapeStatusMessage = MutableStateFlow<String?>(null)
    val mixtapeStatusMessage: StateFlow<String?> = _mixtapeStatusMessage.asStateFlow()

    private val _activeMixtapeMetadata = MutableStateFlow<MixtapeMetadata?>(null)
    val activeMixtapeMetadata: StateFlow<MixtapeMetadata?> = _activeMixtapeMetadata.asStateFlow()

    private val _activeMixtapeChapter = MutableStateFlow<MixtapeChapter?>(null)
    val activeMixtapeChapter: StateFlow<MixtapeChapter?> = _activeMixtapeChapter.asStateFlow()

    private var lastTrackId: Long = -1L

    /**
     * Inicia la creación de un Mixtape continuo con FFmpeg.
     */
    fun createMixtape(
        tracks: List<Track>,
        title: String,
        crossfadeSeconds: Int = 5,
        onCreated: ((Track) -> Unit)? = null
    ) {
        if (tracks.size < 2) return
        scope.launch {
            _isCreatingMixtape.value = true
            _mixtapeProgress.value = 0f
            _mixtapeStatusMessage.value = "Iniciando preparación del Mixtape..."

            val resultTrack = MixtapeEngine.createMixtape(
                context = context,
                tracks = tracks,
                mixtapeTitle = title,
                crossfadeSeconds = crossfadeSeconds,
                onProgress = { p, msg ->
                    _mixtapeProgress.value = p
                    _mixtapeStatusMessage.value = msg
                }
            )

            _isCreatingMixtape.value = false
            if (resultTrack != null) {
                _mixtapeStatusMessage.value = "¡Mixtape '${resultTrack.title}' creado exitosamente!"
                onCreated?.invoke(resultTrack)
            } else {
                _mixtapeStatusMessage.value = "No se pudo generar el Mixtape. Inténtalo de nuevo."
            }
        }
    }

    /**
     * Actualiza el capítulo activo según la posición de reproducción del reproductor.
     */
    fun updateActiveChapter(currentTrack: Track?, currentPositionMs: Long) {
        if (currentTrack == null) {
            if (_activeMixtapeChapter.value != null) {
                _activeMixtapeChapter.value = null
                _activeMixtapeMetadata.value = null
                lastTrackId = -1L
            }
            return
        }

        // Si la pista cambió, consultar si tiene metadatos de Mixtape en metadata/
        if (currentTrack.id != lastTrackId) {
            lastTrackId = currentTrack.id
            val metadata = MixtapeEngine.getMixtapeMetadata(context, currentTrack.id)
            _activeMixtapeMetadata.value = metadata
        }

        val metadata = _activeMixtapeMetadata.value ?: return
        val currentChapter = metadata.getChapterAt(currentPositionMs)

        if (_activeMixtapeChapter.value != currentChapter) {
            _activeMixtapeChapter.value = currentChapter
        }
    }

    fun getActiveChapterIndex(): Int {
        val metadata = _activeMixtapeMetadata.value ?: return -1
        val chapter = _activeMixtapeChapter.value ?: return -1
        return metadata.chapters.indexOf(chapter)
    }

    fun getActiveTotalChapters(): Int {
        return _activeMixtapeMetadata.value?.chapters?.size ?: 0
    }

    fun dismissStatus() {
        _mixtapeStatusMessage.value = null
    }
}
