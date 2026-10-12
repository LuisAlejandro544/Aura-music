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
        onTrackEnded = { autoOverlap -> handleTrackEnded(isAutoCrossfadeOverlap = autoOverlap) }
    )
    private val nativeAudioProcessor = NativeAudioProcessor()
    private var mediaSessionBridge: MediaSessionBridge? = null

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

    private val transitionPrefs by lazy {
        context.getSharedPreferences("aura_transitions_prefs", Context.MODE_PRIVATE)
    }

    init {
        loadPersistedTransitionSettings()
        initPlayer()
    }

    private fun loadPersistedTransitionSettings() {
        try {
            _crossfadeSeconds.value = transitionPrefs.getInt("crossfade_seconds", 0).coerceIn(0, 15)
            _isGaplessEnabled.value = transitionPrefs.getBoolean("gapless_enabled", true)
            _isDjAutomixEnabled.value = transitionPrefs.getBoolean("dj_automix_enabled", false)
            _isDjEqCurveEnabled.value = transitionPrefs.getBoolean("dj_eq_curve_enabled", true)
        } catch (_: Throwable) {}
    }

    @OptIn(UnstableApi::class)
    private fun initPlayer() {
        val audioSink = androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(nativeAudioProcessor))
            .build()

        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return audioSink
            }
        }

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
            com.example.core.data.streaming.StreamingHttpDataSourceConfig.buildExoPlayerDataSourceFactory(context)
        )

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        exoPlayer = player

        // Inicializar segundo reproductor auxiliar (Deck B Saliente) para Crossfade Dual-Deck superpuesto real
        val auxProcessor = NativeAudioProcessor()
        outgoingNativeAudioProcessor = auxProcessor
        val auxAudioSink = androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(auxProcessor))
            .build()
        val auxRenderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return auxAudioSink
            }
        }
        outgoingExoPlayer = ExoPlayer.Builder(context, auxRenderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                com.example.widget.AuraMusicWidgetProvider.pushPlaybackState(
                    context = context,
                    track = _currentTrack.value,
                    isPlaying = isPlaying,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = player.duration.coerceAtLeast(_duration.value)
                )
                if (isPlaying) {
                    startProgressTracking()
                } else {
                    stopProgressTracking()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        _isBuffering.value = false
                        _isNetworkStalled.value = false
                        _duration.value = player.duration.coerceAtLeast(0L)
                        _playbackError.value = null
                        effectManager.attachToSession(player.audioSessionId)
                        com.example.widget.AuraMusicWidgetProvider.pushPlaybackState(
                            context = context,
                            track = _currentTrack.value,
                            isPlaying = player.isPlaying,
                            positionMs = player.currentPosition.coerceAtLeast(0L),
                            durationMs = _duration.value
                        )
                    }
                    Player.STATE_ENDED -> {
                        _isBuffering.value = false
                        handleTrackEnded()
                    }
                    Player.STATE_IDLE -> {
                        _isBuffering.value = false
                    }
                    Player.STATE_BUFFERING -> {
                        val cur = _currentTrack.value
                        val isRemoteStream = cur?.isStreamingTrack == true &&
                            (cur.uriString.startsWith("http://", ignoreCase = true) ||
                                cur.uriString.startsWith("https://", ignoreCase = true))
                        _isBuffering.value = isRemoteStream
                    }
                }
            }

                        override fun onPlayerError(error: PlaybackException) {
                streamingRecovery.handlePlayerError(player, error)
            }
        })

        mediaSessionBridge = MediaSessionBridge(
            context = context,
            player = player,
            onPlayNext = { playNext() },
            onPlayPrevious = { playPrevious() }
        )

        // Vincular callbacks directos para el Widget de Pantalla de Inicio
        com.example.widget.AuraMusicWidgetProvider.onTogglePlayPauseCallback = {
            if (_currentTrack.value == null && queue.value.isEmpty()) {
                false
            } else {
                togglePlayPause()
                true
            }
        }
        com.example.widget.AuraMusicWidgetProvider.onPlayNextCallback = {
            if (queue.value.isEmpty()) {
                false
            } else {
                playNext()
                true
            }
        }
        com.example.widget.AuraMusicWidgetProvider.onPlayPreviousCallback = {
            if (queue.value.isEmpty()) {
                false
            } else {
                playPrevious()
                true
            }
        }
    }

    private fun ensurePlaybackServiceStarted() {
        mediaSessionBridge?.ensurePlaybackServiceStarted()
    }

    fun playTrackList(tracks: List<Track>, startIndex: Int = 0) {
        val selected = queueController.setQueue(tracks, startIndex) ?: return
        playTrack(selected)
    }

    fun playTrack(track: Track, isAutoCrossfadeOverlap: Boolean = false) {
        if (track.isStreamingTrack && isUnresolvedStreamingPageUrl(track.uriString)) {
            val handled = onStreamingTrackRequestedCallback?.invoke(track, isAutoCrossfadeOverlap) ?: false
            if (handled) {
                return
            }
        }

        val player = exoPlayer ?: return
        val outDeck = outgoingExoPlayer
        val previousTrack = _currentTrack.value
        val previousTrackId = previousTrack?.id
        val isLocalPair = previousTrack != null && !previousTrack.isStreamingTrack && !track.isStreamingTrack
        val effectiveCrossfadeSecs = if (_isDjAutomixEnabled.value && isLocalPair && _crossfadeSeconds.value == 0) 6 else _crossfadeSeconds.value

        // Si el Crossfade o Smart AutoMix DJ (música local) está activo y había una pista sonando en el reproductor primario,
        // transferimos la cola final de la pista saliente al Deck B auxiliar para que ambas suenen superpuestas
        var handoffToDualDeckSucceeded = false
        if (effectiveCrossfadeSecs > 0 && outDeck != null && previousTrack != null && previousTrack.id != track.id && (player.isPlaying || player.playWhenReady)) {
            val outgoingPos = player.currentPosition.coerceAtLeast(0L)
            val outgoingDur = player.duration
            if (outgoingDur <= 0L || (outgoingDur - outgoingPos) > 350L) {
                try {
                    outDeck.stop()
                    outDeck.clearMediaItems()
                    val prevMediaUri = if (previousTrack.uriString.startsWith("/")) {
                        Uri.fromFile(File(previousTrack.uriString))
                    } else {
                        Uri.parse(previousTrack.uriString)
                    }
                    val prevClipBounds = if (previousTrack.isStreamingTrack) {
                        null
                    } else {
                        AudioSilenceTrimmer.getClippingBounds(context, previousTrack.uriString)
                    }
                    val absoluteSeekPos = (prevClipBounds?.startMs ?: 0L) + outgoingPos
                    val outItemBuilder = MediaItem.Builder().setUri(prevMediaUri)
                    if (prevClipBounds != null && prevClipBounds.endMs > absoluteSeekPos) {
                        outItemBuilder.setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(0L)
                                .setEndPositionMs(prevClipBounds.endMs)
                                .build()
                        )
                    }
                    outDeck.setMediaItem(outItemBuilder.build(), absoluteSeekPos)
                    outDeck.playbackParameters = player.playbackParameters
                    // La Canción A comienza la transición al 100% del volumen base para bajar simultáneamente de 100% a 0%
                    outDeck.volume = fadeController.getVolume().coerceIn(0.0f, 1.0f)
                    outDeck.prepare()
                    outDeck.play()
                    handoffToDualDeckSucceeded = true
                } catch (_: Throwable) {
                    handoffToDualDeckSucceeded = false
                }
            }
        } else {
            fadeController.stopOutgoingDeckImmediately(outDeck)
        }

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
        com.example.widget.AuraMusicWidgetProvider.pushPlaybackState(
            context = context,
            track = track,
            isPlaying = true,
            positionMs = 0L,
            durationMs = track.durationMs
        )

        try {
            val artworkUri = if (!track.albumArtPath.isNullOrBlank()) {
                if (track.albumArtPath.startsWith("http://", ignoreCase = true) ||
                    track.albumArtPath.startsWith("https://", ignoreCase = true)
                ) {
                    Uri.parse(track.albumArtPath)
                } else {
                    val artFile = File(track.albumArtPath)
                    if (artFile.exists()) Uri.fromFile(artFile) else null
                }
            } else null

            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .apply {
                    if (artworkUri != null) {
                        setArtworkUri(artworkUri)
                    }
                }
                .build()

            val clipBounds = if (track.isStreamingTrack) {
                null
            } else {
                AudioSilenceTrimmer.getClippingBounds(context, track.uriString)
            }
            val resolvedMediaUri = if (track.uriString.startsWith("/")) {
                Uri.fromFile(File(track.uriString))
            } else {
                Uri.parse(track.uriString)
            }
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(resolvedMediaUri)
                .setMediaMetadata(mediaMetadata)

            if (clipBounds != null && clipBounds.endMs > clipBounds.startMs) {
                mediaItemBuilder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clipBounds.startMs)
                        .setEndPositionMs(clipBounds.endMs)
                        .build()
                )
            }

            val mediaItem = mediaItemBuilder.build()
            player.setMediaItem(mediaItem)
            player.playbackParameters = androidx.media3.common.PlaybackParameters(_playbackSpeed.value, _playbackPitch.value)
            player.prepare()
            ensurePlaybackServiceStarted()
            player.play()

            if (handoffToDualDeckSucceeded && outDeck != null) {
                val overlapDurationMs = if (isAutoCrossfadeOverlap) {
                    (effectiveCrossfadeSecs * 1000L).coerceIn(1200L, 12000L)
                } else {
                    // En cambio manual de canción con Crossfade activo, mezcla sedosa y rápida (1.6s a 3.0s)
                    (effectiveCrossfadeSecs * 650L).coerceIn(1500L, 3200L)
                }
                val applyEqCurve = _isDjEqCurveEnabled.value && isLocalPair
                fadeController.startDualDeckCrossfade(
                    durationMs = overlapDurationMs,
                    outgoingPlayer = outDeck,
                    incomingPlayer = player,
                    onProgressStep = { progress ->
                        if (applyEqCurve) {
                            // Modulación progresiva Smart AutoMix DJ (pico de limpieza en el centro de la mezcla)
                            val bellCurve = kotlin.math.sin(progress * kotlin.math.PI).toFloat().coerceIn(0.15f, 1.0f)
                            effectManager.setDjAutomixTransition(true, bellCurve)
                        }
                    }
                ) {
                    effectManager.setDjAutomixTransition(false, 0.0f)
                }
            } else {
                val fadeDurationMs = if (effectiveCrossfadeSecs > 0) {
                    (effectiveCrossfadeSecs * 1000L).coerceIn(1200L, 5000L)
                } else if (fadeController.isFadeInOnResume) {
                    1500L
                } else {
                    1000L
                }
                fadeController.startSmoothFadeIn(fadeDurationMs, player)
            }
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

    fun pause() {
        fadeController.stopOutgoingDeckImmediately(outgoingExoPlayer)
        exoPlayer?.pause()
        effectManager.flushBuffers()
    }

    fun play() {
        val player = exoPlayer ?: return
        if (_currentTrack.value == null && queue.value.isNotEmpty()) {
            playTrackList(queue.value, 0)
            return
        }
        ensurePlaybackServiceStarted()
        player.play()
        if (fadeController.isFadeInOnResume) {
            fadeController.startSmoothFadeIn(1200L, player)
        } else {
            fadeController.restoreFullVolume(player)
        }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, 15)
        _crossfadeSeconds.value = clamped
        try {
            transitionPrefs.edit().putInt("crossfade_seconds", clamped).apply()
        } catch (_: Throwable) {}
    }

    fun setDjAutomixEnabled(enabled: Boolean) {
        _isDjAutomixEnabled.value = enabled
        try {
            transitionPrefs.edit().putBoolean("dj_automix_enabled", enabled).apply()
        } catch (_: Throwable) {}
        if (!enabled) {
            effectManager.setDjAutomixTransition(false, 0.0f)
        }
    }

    fun setDjEqCurveEnabled(enabled: Boolean) {
        _isDjEqCurveEnabled.value = enabled
        try {
            transitionPrefs.edit().putBoolean("dj_eq_curve_enabled", enabled).apply()
        } catch (_: Throwable) {}
        if (!enabled) {
            effectManager.setDjAutomixTransition(false, 0.0f)
        }
    }

    fun setGaplessEnabled(enabled: Boolean) {
        _isGaplessEnabled.value = enabled
        try {
            transitionPrefs.edit().putBoolean("gapless_enabled", enabled).apply()
        } catch (_: Throwable) {}
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

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            fadeController.stopOutgoingDeckImmediately(outgoingExoPlayer)
            player.pause()
            effectManager.flushBuffers()
        } else {
            if (_currentTrack.value == null && queue.value.isNotEmpty()) {
                playTrackList(queue.value, 0)
            } else {
                ensurePlaybackServiceStarted()
                player.play()
                if (fadeController.isFadeInOnResume) {
                    fadeController.startSmoothFadeIn(1200L, player)
                } else {
                    fadeController.restoreFullVolume(player)
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        fadeController.stopOutgoingDeckImmediately(outgoingExoPlayer)
        val clamped = positionMs.coerceIn(0L, _duration.value)
        effectManager.flushBuffers()
        player.seekTo(clamped)
        _currentPosition.value = clamped
    }

    fun playNext(isAutoCrossfadeOverlap: Boolean = false) {
        val q = queue.value
        if (q.isEmpty()) return

        if (repeatMode.value == RepeatMode.ONE) {
            _currentTrack.value?.let { playTrack(it, isAutoCrossfadeOverlap = false) }
            return
        }

        val nextIndex = queueController.getNextIndex() ?: return
        val nextTrack = q[nextIndex]
        if (nextTrack.isStreamingTrack) {
            val intercepted = onStreamingTrackRequestedCallback?.invoke(nextTrack, isAutoCrossfadeOverlap) ?: false
            if (intercepted) return
        }
        playTrack(nextTrack, isAutoCrossfadeOverlap = isAutoCrossfadeOverlap)
    }

    fun playPrevious() {
        val player = exoPlayer ?: return
        if (player.currentPosition > 3000L) {
            seekTo(0L)
            return
        }

        val q = queue.value
        if (q.isEmpty()) return

        val prevIndex = queueController.getPreviousIndex() ?: return
        val prevTrack = q[prevIndex]
        if (prevTrack.isStreamingTrack) {
            val intercepted = onStreamingTrackRequestedCallback?.invoke(prevTrack, false) ?: false
            if (intercepted) return
        }
        playTrack(prevTrack, isAutoCrossfadeOverlap = false)
    }

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
        com.example.widget.WidgetStateStore.updateFavoriteOnly(context, trackId, isFavorite)
        com.example.widget.AuraMusicWidgetProvider.refreshAllWidgets(context)
        com.example.widget.AuraLibraryWidgetProvider.refreshAllWidgets(context)
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
            com.example.widget.AuraMusicWidgetProvider.pushPlaybackState(
                context = context,
                track = updatedCurrent,
                isPlaying = _isPlaying.value,
                positionMs = _currentPosition.value,
                durationMs = _duration.value
            )
        }
    }

    private fun handleTrackEnded(isAutoCrossfadeOverlap: Boolean = false) {
        when (repeatMode.value) {
            RepeatMode.ONE -> {
                seekTo(0L)
                exoPlayer?.play()
                startSmoothFadeIn(1200L)
            }
            RepeatMode.ALL, RepeatMode.OFF -> {
                playNext(isAutoCrossfadeOverlap = isAutoCrossfadeOverlap)
            }
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