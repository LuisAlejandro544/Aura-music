package com.example.core.playback
import com.example.core.model.RepeatMode

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.example.core.data.importer.AudioSilenceTrimmer
import com.example.core.model.ABLoopState
import com.example.core.model.Track
import com.example.core.playback.controllers.ABLoopController
import com.example.core.playback.controllers.AudioFadeController
import com.example.core.playback.controllers.MediaSessionBridge
import com.example.core.playback.controllers.PlayerQueueController
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Aura Music - Motor Central de Reproducción de Audio Local
 *
 * Arquitectura y Diseño Modular:
 * - Orquestador central de reproducción multimedia sobre Jetpack Media3 ExoPlayer.
 * - Desacopla la gestión de cola a [PlayerQueueController].
 * - Desacopla la atenuación y transición de volumen a [AudioFadeController].
 * - Desacopla el bucle repetidor A-B a [ABLoopController].
 * - Desacopla los controles del sistema y notificaciones a [MediaSessionBridge].
 * - Ejecuta la limpieza de buffer (Buffer Flushing) en cambios de pista, saltos de tiempo (seekTo),
 *   pausas y liberación para prevenir chasquidos o ruidos residuales en filtros DSP nativos.
 */
class AuraAudioPlayer(
    internal val context: Context,
    val effectManager: AudioEffectManager
) {

    internal val playerScope = CoroutineScope(Dispatchers.Main + Job())

    internal var exoPlayer: ExoPlayer? = null
    internal var outgoingExoPlayer: ExoPlayer? = null
    internal var outgoingNativeAudioProcessor: NativeAudioProcessor? = null

    internal val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    internal val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    internal val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    internal val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    internal val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    internal val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    internal val _isNetworkStalled = MutableStateFlow(false)
    val isNetworkStalled: StateFlow<Boolean> = _isNetworkStalled.asStateFlow()

    internal val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    internal val _playbackPitch = MutableStateFlow(1.0f)
    val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    internal val _crossfadeSeconds = MutableStateFlow(0)
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    internal val _isGaplessEnabled = MutableStateFlow(true)
    val isGaplessEnabled: StateFlow<Boolean> = _isGaplessEnabled.asStateFlow()

    internal val _isDjAutomixEnabled = MutableStateFlow(false)
    val isDjAutomixEnabled: StateFlow<Boolean> = _isDjAutomixEnabled.asStateFlow()

    internal val _isDjEqCurveEnabled = MutableStateFlow(true)
    val isDjEqCurveEnabled: StateFlow<Boolean> = _isDjEqCurveEnabled.asStateFlow()

    // Controladores modulares desacoplados
    internal val queueController = PlayerQueueController()
    val queue: StateFlow<List<Track>> = queueController.queue
    val currentIndex: StateFlow<Int> = queueController.currentIndex
    val shuffleEnabled: StateFlow<Boolean> = queueController.shuffleEnabled
    val repeatMode: StateFlow<RepeatMode> = queueController.repeatMode

    internal val abLoopController = ABLoopController()
    val abLoopState: StateFlow<ABLoopState> = abLoopController.abLoopState

    internal val fadeController = AudioFadeController(playerScope)
    internal val playbackParamsManager = PlaybackParametersManager(
        scope = playerScope,
        getPlayer = { exoPlayer },
        getSpeed = { _playbackSpeed.value },
        getPitch = { _playbackPitch.value },
        setSpeedPitch = { s, p -> _playbackSpeed.value = s; _playbackPitch.value = p }
    )
    internal val streamingRecovery = StreamingErrorRecoveryHandler(
        scope = playerScope,
        getTrack = { _currentTrack.value },
        getFallbackPosition = { _currentPosition.value },
        getLivePlayer = { exoPlayer },
        setBuffering = { _isBuffering.value = it },
        setNetworkStalled = { _isNetworkStalled.value = it },
        setPlaybackError = { _playbackError.value = it },
        resetSpeedPitch = { _playbackSpeed.value = 1.0f; _playbackPitch.value = 1.0f },
        getHttpRecoveryHandler = { onStreamingHttpErrorRecoveryCallback },
        requestPlayNext = { playNextInternal() }
    )

    internal val progressTracker = PlaybackProgressTracker(
        scope = playerScope,
        appContext = context,
        getPlayer = { exoPlayer },
        getTrack = { _currentTrack.value },
        getDuration = { _duration.value },
        setDuration = { _duration.value = it },
        setPosition = { _currentPosition.value = it },
        getAbLoopState = { abLoopState.value },
        getCrossfadeSeconds = { _crossfadeSeconds.value },
        isDjAutomixEnabled = { _isDjAutomixEnabled.value },
        isDjEqCurveEnabled = { _isDjEqCurveEnabled.value },
        getQueueSize = { queue.value.size },
        getRepeatMode = { repeatMode.value },
        onDjAutomixTransition = { enabled, progress -> effectManager.setDjAutomixTransition(enabled, progress) },
        getNearEndPrefetch = { onStreamingNearEndPrefetchCallback },
        onTrackEnded = { autoOverlap -> handleTrackEndedInternal(isAutoCrossfadeOverlap = autoOverlap) }
    )
    internal val dualDeckTransitions = DualDeckTransitionManager(
        fadeController = fadeController,
        getPlayer = { exoPlayer },
        getOutgoingDeck = { outgoingExoPlayer },
        getCurrentTrack = { _currentTrack.value },
        getCrossfadeSeconds = { _crossfadeSeconds.value },
        isDjAutomixEnabled = { _isDjAutomixEnabled.value },
        isDjEqCurveEnabled = { _isDjEqCurveEnabled.value },
        resolveClipBounds = { uri -> AudioSilenceTrimmer.getClippingBounds(context, uri) },
        onDjAutomixTransition = { enabled, progress -> effectManager.setDjAutomixTransition(enabled, progress) }
    )
    internal val nativeAudioProcessor = NativeAudioProcessor()
    internal var mediaSessionBridge: MediaSessionBridge? = null

    /**
     * Interceptor opcional para resolver con la API + yt-dlp (o desde caché de 30 min)
     * una pista de streaming antes de pasar a la canción anterior o siguiente.
     */
    var onStreamingTrackRequestedCallback: ((Track, Boolean) -> Boolean)? = null

    /**
     * Notificación cuando una pista de streaming entra en el último tramo para pre-consultar
     * a la API una canción similar y resolverla con yt-dlp por adelantado.
     */
    var onStreamingNearEndPrefetchCallback: ((Track) -> Unit)? = null

    /**
     * Callback invocado cuando ExoPlayer recibe un rechazo HTTP (ej. HTTP 403 Forbidden por firma o token expirado)
     * en una pista de Streaming para conmutar a la copia en caché o regenerar el flujo automáticamente.
     */
    var onStreamingHttpErrorRecoveryCallback: ((Track, Long) -> Boolean)? = null

    companion object {
        /**
         * Referencia estática compatible con AuraMediaPlaybackService.
         */
        val activeMediaSession: MediaSession?
            get() = MediaSessionBridge.activeMediaSession
    }

    internal val transitionPrefs by lazy {
        context.getSharedPreferences("aura_transitions_prefs", Context.MODE_PRIVATE)
    }

    init {
        loadPersistedTransitionSettingsInternal()
        initPlayerInternal()
    }

    fun playTrackList(tracks: List<Track>, startIndex: Int = 0) = playTrackListInternal(tracks, startIndex)

    fun playTrack(track: Track, isAutoCrossfadeOverlap: Boolean = false) = playTrackInternal(track, isAutoCrossfadeOverlap)

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = clamped
        playbackParamsManager.schedule()
    }

    fun setPlaybackPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        _playbackPitch.value = clamped
        playbackParamsManager.schedule()
    }

    fun resetSpeedAndPitch() {
        playbackParamsManager.reset()
    }

    fun setVolume(volume: Float) = fadeController.setVolume(volume, exoPlayer, outgoingExoPlayer)

    fun getVolume(): Float = fadeController.getVolume()

    fun setFadeInOnResumeEnabled(enabled: Boolean) = fadeController.setFadeInOnResumeEnabled(enabled)

    fun triggerSmoothFadeIn(durationMs: Long = 1200L) = fadeController.triggerSmoothFadeIn(durationMs, exoPlayer)

    fun startSmoothFadeIn(durationMs: Long = 1500L) = fadeController.startSmoothFadeIn(durationMs, exoPlayer)

    fun pause() = pauseInternal()

    fun play() = playInternal()

    fun setCrossfadeSeconds(seconds: Int) = setCrossfadeSecondsInternal(seconds)

    fun setDjAutomixEnabled(enabled: Boolean) = setDjAutomixEnabledInternal(enabled)

    fun setDjEqCurveEnabled(enabled: Boolean) = setDjEqCurveEnabledInternal(enabled)

    fun setGaplessEnabled(enabled: Boolean) = setGaplessEnabledInternal(enabled)

    // --- Controles del Repetidor de Segmento A-B (Delegados en ABLoopController) ---

    fun markABPointA(positionMs: Long = _currentPosition.value) =
        abLoopController.markABPointA(positionMs, _duration.value)

    fun markABPointB(positionMs: Long = _currentPosition.value) {
        val startA = abLoopState.value.pointAMs ?: 0L
        abLoopController.markABPointB(positionMs, _duration.value)
        val curPos = exoPlayer?.currentPosition ?: _currentPosition.value
        val clampedB = abLoopState.value.pointBMs ?: Long.MAX_VALUE
        if (curPos >= clampedB || curPos < startA) {
            seekToInternal(startA)
        }
    }

    fun toggleABLoopEnabled(enabled: Boolean) {
        abLoopController.toggleABLoopEnabled(enabled)
        if (enabled) {
            val aMs = abLoopState.value.pointAMs ?: 0L
            val bMs = abLoopState.value.pointBMs ?: Long.MAX_VALUE
            val curPos = exoPlayer?.currentPosition ?: _currentPosition.value
            if (curPos < aMs || curPos >= bMs) {
                seekToInternal(aMs)
            }
        }
    }

    fun adjustABPointA(deltaMs: Long) = abLoopController.adjustABPointA(deltaMs, _duration.value)

    fun adjustABPointB(deltaMs: Long) = abLoopController.adjustABPointB(deltaMs, _duration.value)

    fun clearABLoop() = abLoopController.clearABLoop()

    fun togglePlayPause() = togglePlayPauseInternal()

    fun seekTo(positionMs: Long) = seekToInternal(positionMs)

    fun playNext(isAutoCrossfadeOverlap: Boolean = false) = playNextInternal(isAutoCrossfadeOverlap)

    fun playPrevious() = playPreviousInternal()

    fun appendOrUpdateStreamingTrack(track: Track, updateCurrentTrackIfSame: Boolean = true) = appendOrUpdateStreamingTrackInternal(track, updateCurrentTrackIfSame)

    fun toggleShuffle() = toggleShuffleInternal()

    fun cycleRepeatMode() = cycleRepeatModeInternal()

    fun updateTrackFavorite(trackId: Long, isFavorite: Boolean) = updateTrackFavoriteInternal(trackId, isFavorite)

    fun removeTrackFromQueue(trackId: Long) = removeTrackFromQueueInternal(trackId)

    fun updateTrackMetadata(trackId: Long, title: String, artist: String, album: String, albumArtPath: String? = null, updateArt: Boolean = false, videoUri: String? = null, isVideoLoop: Boolean = false, updateVideo: Boolean = false) = updateTrackMetadataInternal(trackId, title, artist, album, albumArtPath, updateArt, videoUri, isVideoLoop, updateVideo)

    fun release() = releaseInternal()

    /**
     * Determina si la URI de una pista de Streaming aún es una URL de página web (YouTube watch,
     * YouTube Music, SoundCloud permalink o marcador `aura_stream://`) que requiere resolución
     * previa mediante `StreamingModeCoordinator` antes de entregarse a ExoPlayer.
     */
        internal fun isUnresolvedStreamingPageUrl(uri: String): Boolean =
        StreamingUrlPolicy.isUnresolvedStreamingPageUrl(uri)
}
