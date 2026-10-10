package com.example.playback

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
import com.example.data.importer.AudioSilenceTrimmer
import com.example.model.ABLoopState
import com.example.model.RepeatMode
import com.example.model.Track
import com.example.playback.controllers.ABLoopController
import com.example.playback.controllers.AudioFadeController
import com.example.playback.controllers.MediaSessionBridge
import com.example.playback.controllers.PlayerQueueController
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
    private var progressJob: Job? = null

    private var exoPlayer: ExoPlayer? = null

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
    private var playbackParamsJob: Job? = null
    private var isTransitioningTrack = false

    private val nativeAudioProcessor = NativeAudioProcessor()
    private var mediaSessionBridge: MediaSessionBridge? = null

    companion object {
        /**
         * Referencia estática compatible con AuraMediaPlaybackService.
         */
        val activeMediaSession: MediaSession?
            get() = MediaSessionBridge.activeMediaSession
    }

    init {
        initPlayer()
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

        val player = ExoPlayer.Builder(context, renderersFactory).build()
        exoPlayer = player

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
                        handleTrackEnded()
                    }
                    Player.STATE_IDLE -> {}
                    Player.STATE_BUFFERING -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _playbackError.value = "Error al reproducir pista: ${error.message}"
                com.example.debug.AuraDebugManager.logError(
                    "ExoPlayer",
                    "Error durante la reproducción: ${error.errorCodeName} - ${error.message}",
                    error
                )

                val p = exoPlayer
                if (p != null && _currentTrack.value != null) {
                    try {
                        _playbackSpeed.value = 1.0f
                        _playbackPitch.value = 1.0f
                        p.playbackParameters = androidx.media3.common.PlaybackParameters.DEFAULT
                        p.prepare()
                        p.play()
                        com.example.debug.AuraDebugManager.logWarning("ExoPlayer", "Recuperación automática de reproducción tras error de procesador de audio.")
                        return
                    } catch (e: Throwable) {
                        com.example.debug.AuraDebugManager.logCritical("ExoPlayer", "Fallo al recuperar reproducción local: ${e.message}", e)
                    }
                }
                playNext()
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

    fun playTrack(track: Track) {
        val previousTrackId = _currentTrack.value?.id
        _currentTrack.value = track
        _currentPosition.value = 0L
        _duration.value = track.durationMs
        abLoopController.reset()
        if (previousTrackId != track.id) {
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

        val player = exoPlayer ?: return
        try {
            val artworkUri = if (!track.albumArtPath.isNullOrBlank()) {
                val artFile = File(track.albumArtPath)
                if (artFile.exists()) Uri.fromFile(artFile) else null
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

            val clipBounds = AudioSilenceTrimmer.getClippingBounds(context, track.uriString)
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(Uri.parse(track.uriString))
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

            val fadeDurationMs = if (_crossfadeSeconds.value > 0) {
                (_crossfadeSeconds.value * 1000L).coerceIn(1200L, 5000L)
            } else if (fadeController.isFadeInOnResume) {
                1500L
            } else {
                1000L
            }
            fadeController.startSmoothFadeIn(fadeDurationMs, player)
        } catch (e: Exception) {
            _playbackError.value = "No se pudo cargar la pista: ${e.message}"
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = clamped
        schedulePlaybackParameters()
    }

    fun setPlaybackPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        _playbackPitch.value = clamped
        schedulePlaybackParameters()
    }

    fun resetSpeedAndPitch() {
        playbackParamsJob?.cancel()
        _playbackSpeed.value = 1.0f
        _playbackPitch.value = 1.0f
        applyPlaybackParametersDirect(1.0f, 1.0f)
    }

    private fun schedulePlaybackParameters() {
        playbackParamsJob?.cancel()
        playbackParamsJob = playerScope.launch(Dispatchers.Main) {
            delay(40L)
            val speedTarget = _playbackSpeed.value
            val pitchTarget = _playbackPitch.value
            applyPlaybackParametersDirect(speedTarget, pitchTarget)
        }
    }

    private fun applyPlaybackParametersDirect(speed: Float, pitch: Float) {
        val player = exoPlayer ?: return
        val current = player.playbackParameters
        if (kotlin.math.abs(current.speed - speed) > 0.01f || kotlin.math.abs(current.pitch - pitch) > 0.01f) {
            val wasPlaying = player.isPlaying || player.playWhenReady
            try {
                player.playbackParameters = androidx.media3.common.PlaybackParameters(speed, pitch)
                if (wasPlaying && !player.isPlaying && player.playbackState != Player.STATE_ENDED) {
                    player.play()
                }
            } catch (t: Throwable) {
                com.example.debug.AuraDebugManager.logWarning("ExoPlayer", "Fallo al aplicar velocidad/tono: ${t.message}", t)
            }
        }
    }

    fun setVolume(volume: Float) = fadeController.setVolume(volume, exoPlayer)

    fun getVolume(): Float = fadeController.getVolume()

    fun setFadeInOnResumeEnabled(enabled: Boolean) = fadeController.setFadeInOnResumeEnabled(enabled)

    fun triggerSmoothFadeIn(durationMs: Long = 1200L) = fadeController.triggerSmoothFadeIn(durationMs, exoPlayer)

    fun startSmoothFadeIn(durationMs: Long = 1500L) = fadeController.startSmoothFadeIn(durationMs, exoPlayer)

    fun pause() {
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
        _crossfadeSeconds.value = seconds.coerceIn(0, 15)
    }

    fun setDjAutomixEnabled(enabled: Boolean) {
        _isDjAutomixEnabled.value = enabled
        if (!enabled) {
            effectManager.setDjAutomixTransition(false, 0.0f)
        }
    }

    fun setDjEqCurveEnabled(enabled: Boolean) {
        _isDjEqCurveEnabled.value = enabled
        if (!enabled) {
            effectManager.setDjAutomixTransition(false, 0.0f)
        }
    }

    fun setGaplessEnabled(enabled: Boolean) {
        _isGaplessEnabled.value = enabled
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
            player.pause()
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
        val clamped = positionMs.coerceIn(0L, _duration.value)
        effectManager.flushBuffers()
        player.seekTo(clamped)
        _currentPosition.value = clamped
    }

    fun playNext() {
        val q = queue.value
        if (q.isEmpty()) return

        if (repeatMode.value == RepeatMode.ONE) {
            _currentTrack.value?.let { playTrack(it) }
            return
        }

        val nextIndex = queueController.getNextIndex() ?: return
        playTrack(q[nextIndex])
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
        playTrack(q[prevIndex])
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

    private fun handleTrackEnded() {
        when (repeatMode.value) {
            RepeatMode.ONE -> {
                seekTo(0L)
                exoPlayer?.play()
                startSmoothFadeIn(1200L)
            }
            RepeatMode.ALL, RepeatMode.OFF -> {
                playNext()
            }
        }
    }

    private var widgetProgressTickCounter = 0

    private fun startProgressTracking() {
        progressJob?.cancel()
        widgetProgressTickCounter = 0
        progressJob = playerScope.launch {
            while (isActive) {
                val loopState = abLoopState.value
                exoPlayer?.let { player ->
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    _currentPosition.value = pos
                    if (player.duration > 0) {
                        _duration.value = player.duration
                    }

                    widgetProgressTickCounter++
                    if (widgetProgressTickCounter >= 12) {
                        widgetProgressTickCounter = 0
                        com.example.widget.AuraMusicWidgetProvider.pushPlaybackProgress(
                            context = context,
                            positionMs = pos,
                            durationMs = _duration.value
                        )
                    }

                    if (loopState.isLooping) {
                        val aMs = loopState.pointAMs ?: 0L
                        val bMs = loopState.pointBMs ?: Long.MAX_VALUE
                        if (pos >= bMs || pos < (aMs - 350L)) {
                            player.seekTo(aMs)
                            _currentPosition.value = aMs
                        }
                    } else {
                        val crossfadeSecs = _crossfadeSeconds.value
                        val isDjMix = _isDjAutomixEnabled.value
                        val effectiveCrossfadeSecs = if (isDjMix && crossfadeSecs == 0) 5 else crossfadeSecs

                        if (effectiveCrossfadeSecs > 0 && player.duration > 0 && player.isPlaying) {
                            val remainingMs = player.duration - pos
                            val crossfadeMs = effectiveCrossfadeSecs * 1000L
                            fadeController.applyFadeOut(remainingMs, crossfadeMs, player)

                            // Curva de ecualización DJ en X: atenúa subgraves de la canción saliente
                            if (_isDjEqCurveEnabled.value && remainingMs in 0..crossfadeMs) {
                                val progress = (1.0f - (remainingMs.toFloat() / crossfadeMs.toFloat())).coerceIn(0.0f, 1.0f)
                                effectManager.setDjAutomixTransition(true, progress)
                            } else {
                                effectManager.setDjAutomixTransition(false, 0.0f)
                            }

                            // Detección inteligente de outro en DJ Automix: si la intensidad acústica cae (<0.07f)
                            // en los últimos 4 segundos, avanzar a la siguiente pista para omitir silencio muerto
                            if (isDjMix && remainingMs in 250L..4200L && NativeAudioEngine.getAudioIntensity() < 0.07f && !isTransitioningTrack) {
                                isTransitioningTrack = true
                                playerScope.launch(Dispatchers.Main) {
                                    handleTrackEnded()
                                    delay(600L)
                                    isTransitioningTrack = false
                                }
                            }
                        } else {
                            effectManager.setDjAutomixTransition(false, 0.0f)
                        }

                        if (player.duration > 0 && player.isPlaying && !isTransitioningTrack) {
                            val remainingMs = player.duration - pos
                            if (remainingMs in 1..250L) {
                                isTransitioningTrack = true
                                playerScope.launch(Dispatchers.Main) {
                                    handleTrackEnded()
                                    delay(600L)
                                    isTransitioningTrack = false
                                }
                            }
                        }
                    }
                }
                delay(if (loopState.isLooping) 75L else 180L)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
        playbackParamsJob?.cancel()
        playbackParamsJob = null
    }

    fun release() {
        stopProgressTracking()
        fadeController.cancel()
        playerScope.cancel()
        mediaSessionBridge?.release()
        mediaSessionBridge = null
        effectManager.flushBuffers()
        effectManager.release()
        exoPlayer?.release()
        exoPlayer = null
    }
}
