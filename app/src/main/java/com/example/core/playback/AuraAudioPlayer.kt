package com.example.core.playback
import com.example.core.model.RepeatMode

import android.content.Context
import androidx.annotation.OptIn
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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Aura Music - Motor Central de Reproducción de Audio Local.
 * Fachada modular sobre Media3 ExoPlayer: delega cola en [PlayerQueueController],
 * transporte en [PlayerTransportDriver], construcción en [ExoPlayerFactory],
 * fundidos en [AudioFadeController], bucle A-B en [ABLoopController],
 * sesión del sistema en [MediaSessionBridge], prefs en [TransitionPrefsStore]
 * y widgets en [PlayerWidgetBridge], con Buffer Flushing anti-chasquidos.
 */
class AuraAudioPlayer(
    private val context: Context,
    val effectManager: AudioEffectManager
) {

    private val playerScope = CoroutineScope(Dispatchers.Main + Job())

    private var exoPlayer: ExoPlayer? = null
    private var outgoingExoPlayer: ExoPlayer? = null
    private var outgoingNativeAudioProcessor: NativeAudioProcessor? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _isNetworkStalled = MutableStateFlow(false)
    val isNetworkStalled: StateFlow<Boolean> = _isNetworkStalled.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(1.0f)
    val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(0)
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    private val _isGaplessEnabled = MutableStateFlow(true)
    val isGaplessEnabled: StateFlow<Boolean> = _isGaplessEnabled.asStateFlow()

    private val _isDjAutomixEnabled = MutableStateFlow(false)
    val isDjAutomixEnabled: StateFlow<Boolean> = _isDjAutomixEnabled.asStateFlow()

    private val _isDjEqCurveEnabled = MutableStateFlow(true)
    val isDjEqCurveEnabled: StateFlow<Boolean> = _isDjEqCurveEnabled.asStateFlow()

    // Controladores modulares desacoplados
    private val queueController = PlayerQueueController()
    val queue: StateFlow<List<Track>> = queueController.queue
    val currentIndex: StateFlow<Int> = queueController.currentIndex
    val shuffleEnabled: StateFlow<Boolean> = queueController.shuffleEnabled
    val repeatMode: StateFlow<RepeatMode> = queueController.repeatMode

    private val abLoopController = ABLoopController()
    val abLoopState: StateFlow<ABLoopState> = abLoopController.abLoopState

    private val fadeController = AudioFadeController(playerScope)
    private val playbackParamsManager = PlaybackParametersManager(
        scope = playerScope,
        getPlayer = { exoPlayer },
        getSpeed = { _playbackSpeed.value },
        getPitch = { _playbackPitch.value },
        setSpeedPitch = { s, p -> _playbackSpeed.value = s; _playbackPitch.value = p }
    )
    private val streamingRecovery = StreamingErrorRecoveryHandler(
        scope = playerScope,
        getTrack = { _currentTrack.value },
        getFallbackPosition = { _currentPosition.value },
        getLivePlayer = { exoPlayer },
        setBuffering = { _isBuffering.value = it },
        setNetworkStalled = { _isNetworkStalled.value = it },
        setPlaybackError = { _playbackError.value = it },
        resetSpeedPitch = { _playbackSpeed.value = 1.0f; _playbackPitch.value = 1.0f },
        getHttpRecoveryHandler = { onStreamingHttpErrorRecoveryCallback },
        requestPlayNext = { playNext() }
    )

    private val transportDriver = PlayerTransportDriver(
        getPlayer = { exoPlayer },
        getOutgoingDeck = { outgoingExoPlayer },
        queueController = queueController,
        fadeController = fadeController,
        effectManager = effectManager,
        getRepeatMode = { repeatMode.value },
        getQueue = { queue.value },
        getCurrentTrack = { _currentTrack.value },
        getDuration = { _duration.value },
        setPosition = { _currentPosition.value = it },
        shouldInterceptStreaming = { track, overlap ->
            track.isStreamingTrack && (onStreamingTrackRequestedCallback?.invoke(track, overlap) ?: false)
        },
        startTrack = { track, overlap -> playTrack(track, overlap) },
        ensureServiceStarted = { ensurePlaybackServiceStarted() }
    )

    private val progressTracker = PlaybackProgressTracker(
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
        onTrackEnded = { autoOverlap -> transportDriver.handleTrackEnded(isAutoCrossfadeOverlap = autoOverlap) }
    )
    private val dualDeckTransitions = DualDeckTransitionManager(
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
    private var nativeAudioProcessor: NativeAudioProcessor? = null
    private var mediaSessionBridge: MediaSessionBridge? = null
    private val transitionPrefs = TransitionPrefsStore(context)

    /** Interceptor para resolver una pista de streaming (API + yt-dlp o caché) antes de reproducirla. */
    var onStreamingTrackRequestedCallback: ((Track, Boolean) -> Boolean)? = null

    /** Prefetch de canción similar cuando una pista de streaming entra en su tramo final. */
    var onStreamingNearEndPrefetchCallback: ((Track) -> Unit)? = null

    /** Recuperación ante rechazo HTTP (ej. 403) en streaming: caché o regeneración del flujo. */
    var onStreamingHttpErrorRecoveryCallback: ((Track, Long) -> Boolean)? = null

    companion object {
        /** Referencia estática compatible con AuraMediaPlaybackService. */
        val activeMediaSession: MediaSession?
            get() = MediaSessionBridge.activeMediaSession
    }

    init {
        loadPersistedTransitionSettings()
        initPlayer()
    }

    private fun loadPersistedTransitionSettings() {
        val saved = transitionPrefs.load()
        _crossfadeSeconds.value = saved.crossfadeSeconds
        _isGaplessEnabled.value = saved.isGaplessEnabled
        _isDjAutomixEnabled.value = saved.isDjAutomixEnabled
        _isDjEqCurveEnabled.value = saved.isDjEqCurveEnabled
    }

    @OptIn(UnstableApi::class)
    private fun initPlayer() {
        val playerSet = ExoPlayerFactory.build(context)
        val player = playerSet.mainPlayer
        exoPlayer = player
        nativeAudioProcessor = playerSet.mainProcessor
        outgoingNativeAudioProcessor = playerSet.outgoingProcessor
        outgoingExoPlayer = playerSet.outgoingPlayer

        val stateListener = PlayerStateListener(
            appContext = context,
            player = player,
            getTrack = { _currentTrack.value },
            getDuration = { _duration.value },
            setPlaying = { _isPlaying.value = it },
            setDuration = { _duration.value = it },
            setBuffering = { _isBuffering.value = it },
            setNetworkStalled = { _isNetworkStalled.value = it },
            setPlaybackError = { _playbackError.value = it },
            attachAudioSession = { effectManager.attachToSession(it) },
            onTrackEnded = { transportDriver.handleTrackEnded() },
            onPlayerError = { streamingRecovery.handlePlayerError(player, it) },
            onStartTracking = { startProgressTracking() },
            onStopTracking = { stopProgressTracking() }
        )
        player.addListener(stateListener)

        mediaSessionBridge = MediaSessionBridge(
            context = context,
            player = player,
            onPlayNext = { playNext() },
            onPlayPrevious = { playPrevious() }
        )

        // Vincular callbacks directos para el Widget de Pantalla de Inicio
        PlayerWidgetBridge.bindControlCallbacks(
            hasTrackOrQueue = { _currentTrack.value != null || queue.value.isNotEmpty() },
            hasQueue = { queue.value.isNotEmpty() },
            onTogglePlayPause = { togglePlayPause() },
            onPlayNext = { playNext() },
            onPlayPrevious = { playPrevious() }
        )
    }

    private fun ensurePlaybackServiceStarted() {
        mediaSessionBridge?.ensurePlaybackServiceStarted()
    }

    fun playTrackList(tracks: List<Track>, startIndex: Int = 0) =
        transportDriver.playTrackList(tracks, startIndex)

    fun playTrack(track: Track, isAutoCrossfadeOverlap: Boolean = false) {
        if (track.isStreamingTrack && isUnresolvedStreamingPageUrl(track.uriString)) {
            val handled = onStreamingTrackRequestedCallback?.invoke(track, isAutoCrossfadeOverlap) ?: false
            if (handled) {
                return
            }
        }

        val player = exoPlayer ?: return
        val previousTrackId = _currentTrack.value?.id
        val handoff = dualDeckTransitions.prepareHandoff(track)

        _currentTrack.value = track
        _currentPosition.value = 0L
        _duration.value = track.durationMs
        _isBuffering.value = track.isStreamingTrack && (track.uriString.startsWith("http://", ignoreCase = true) || track.uriString.startsWith("https://", ignoreCase = true))
        _isNetworkStalled.value = false
        progressTracker.resetForNewTrack()
        abLoopController.reset()
        // En modo Streaming omitimos por ahora el motor C++20 DSP; en modo local lo mantenemos 100% activo
        NativeAudioProcessor.isStreamingBypassActive = track.isStreamingTrack
        if (previousTrackId != track.id && !track.isStreamingTrack) {
            effectManager.onTrackChanged(track.id)
        }
        effectManager.flushBuffers()
        PlayerWidgetBridge.pushPlaybackState(
            context = context,
            track = track,
            isPlaying = true,
            positionMs = 0L,
            durationMs = track.durationMs
        )

        try {
            val mediaItem = TrackMediaFactory.buildTrackMedia(track) { uri ->
                AudioSilenceTrimmer.getClippingBounds(context, uri)
            }.mediaItem
            player.setMediaItem(mediaItem)
            player.playbackParameters = androidx.media3.common.PlaybackParameters(_playbackSpeed.value, _playbackPitch.value)
            player.prepare()
            ensurePlaybackServiceStarted()
            player.play()

            dualDeckTransitions.startEntryTransition(handoff, isAutoCrossfadeOverlap)
        } catch (e: Exception) {
            _playbackError.value = "No se pudo cargar la pista: ${e.message}"
        }
    }

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

    fun pause() = transportDriver.pause()

    fun play() = transportDriver.play()

    fun setCrossfadeSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, 15)
        _crossfadeSeconds.value = clamped
        transitionPrefs.saveCrossfadeSeconds(clamped)
    }

    fun setDjAutomixEnabled(enabled: Boolean) {
        _isDjAutomixEnabled.value = enabled
        transitionPrefs.saveDjAutomixEnabled(enabled)
        if (!enabled) {
            effectManager.setDjAutomixTransition(false, 0.0f)
        }
    }

    fun setDjEqCurveEnabled(enabled: Boolean) {
        _isDjEqCurveEnabled.value = enabled
        transitionPrefs.saveDjEqCurveEnabled(enabled)
        if (!enabled) {
            effectManager.setDjAutomixTransition(false, 0.0f)
        }
    }

    fun setGaplessEnabled(enabled: Boolean) {
        _isGaplessEnabled.value = enabled
        transitionPrefs.saveGaplessEnabled(enabled)
    }

    // --- Controles del Repetidor de Segmento A-B (Delegados en ABLoopController) ---

    fun markABPointA(positionMs: Long = _currentPosition.value) =
        abLoopController.markABPointA(positionMs, _duration.value)

    fun markABPointB(positionMs: Long = _currentPosition.value) {
        val startA = abLoopState.value.pointAMs ?: 0L
        abLoopController.markABPointB(positionMs, _duration.value)
        val curPos = exoPlayer?.currentPosition ?: _currentPosition.value
        val clampedB = abLoopState.value.pointBMs ?: Long.MAX_VALUE
        if (curPos >= clampedB || curPos < startA) {
            seekTo(startA)
        }
    }

    fun toggleABLoopEnabled(enabled: Boolean) {
        abLoopController.toggleABLoopEnabled(enabled)
        if (enabled) {
            val aMs = abLoopState.value.pointAMs ?: 0L
            val bMs = abLoopState.value.pointBMs ?: Long.MAX_VALUE
            val curPos = exoPlayer?.currentPosition ?: _currentPosition.value
            if (curPos < aMs || curPos >= bMs) {
                seekTo(aMs)
            }
        }
    }

    fun adjustABPointA(deltaMs: Long) = abLoopController.adjustABPointA(deltaMs, _duration.value)

    fun adjustABPointB(deltaMs: Long) = abLoopController.adjustABPointB(deltaMs, _duration.value)

    fun clearABLoop() = abLoopController.clearABLoop()

    fun togglePlayPause() = transportDriver.togglePlayPause()

    fun seekTo(positionMs: Long) = transportDriver.seekTo(positionMs)

    fun playNext(isAutoCrossfadeOverlap: Boolean = false) =
        transportDriver.playNext(isAutoCrossfadeOverlap)

    fun playPrevious() = transportDriver.playPrevious()

    /**
     * Encola una canción similar de streaming o actualiza su URI en la cola (ej. cuando ya está en la caché de 30 min).
     * Si [updateCurrentTrackIfSame] es false, actualiza únicamente la cola para futuras repeticiones/vuelta atrás
     * sin recrear el reproductor activo en mitad de la canción.
     */
    fun appendOrUpdateStreamingTrack(track: Track, updateCurrentTrackIfSame: Boolean = true) {
        queueController.appendOrUpdateTrack(track)
        if (updateCurrentTrackIfSame && _currentTrack.value?.id == track.id) {
            _currentTrack.value = track
        }
    }

    fun toggleShuffle() {
        queueController.toggleShuffle()
    }

    fun cycleRepeatMode() {
        queueController.cycleRepeatMode()
    }

    fun updateTrackFavorite(trackId: Long, isFavorite: Boolean) {
        val (updatedCurrent, _) = queueController.updateTrackFavorite(trackId, isFavorite, _currentTrack.value)
        _currentTrack.value = updatedCurrent
        PlayerWidgetBridge.syncFavorite(context, trackId, isFavorite)
    }

    fun removeTrackFromQueue(trackId: Long) {
        queueController.removeTrack(trackId)
        if (_currentTrack.value?.id == trackId) {
            playNext()
        }
    }

    fun updateTrackMetadata(
        trackId: Long,
        title: String,
        artist: String,
        album: String,
        albumArtPath: String? = null,
        updateArt: Boolean = false,
        videoUri: String? = null,
        isVideoLoop: Boolean = false,
        updateVideo: Boolean = false
    ) {
        val (updatedCurrent, _) = queueController.updateTrackMetadata(
            trackId = trackId,
            title = title,
            artist = artist,
            album = album,
            albumArtPath = albumArtPath,
            updateArt = updateArt,
            videoUri = videoUri,
            isVideoLoop = isVideoLoop,
            updateVideo = updateVideo,
            currentTrack = _currentTrack.value
        )
        _currentTrack.value = updatedCurrent
        if (updatedCurrent != null && updatedCurrent.id == trackId) {
            PlayerWidgetBridge.pushPlaybackState(
                context = context,
                track = updatedCurrent,
                isPlaying = _isPlaying.value,
                positionMs = _currentPosition.value,
                durationMs = _duration.value
            )
        }
    }

    private fun startProgressTracking() = progressTracker.start()

    private fun stopProgressTracking() {
        progressTracker.stop()
        playbackParamsManager.cancel()
    }

    fun release() {
        stopProgressTracking()
        fadeController.stopOutgoingDeckImmediately(outgoingExoPlayer)
        fadeController.cancel()
        playerScope.cancel()
        mediaSessionBridge?.release()
        mediaSessionBridge = null
        effectManager.flushBuffers()
        effectManager.release()
        try {
            outgoingExoPlayer?.release()
        } catch (_: Throwable) {}
        outgoingExoPlayer = null
        exoPlayer?.release()
        exoPlayer = null
    }

    /**
     * Determina si la URI de una pista de Streaming aún es una URL de página web (YouTube watch,
     * YouTube Music, SoundCloud permalink o marcador `aura_stream://`) que requiere resolución
     * previa mediante `StreamingModeCoordinator` antes de entregarse a ExoPlayer.
     */
        private fun isUnresolvedStreamingPageUrl(uri: String): Boolean =
        StreamingUrlPolicy.isUnresolvedStreamingPageUrl(uri)
}