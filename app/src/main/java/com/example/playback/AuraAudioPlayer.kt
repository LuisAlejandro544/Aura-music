package com.example.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.example.model.RepeatMode
import com.example.model.Track
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Motor central de reproducción de audio local usando Jetpack Media3 ExoPlayer.
 * Gestiona el ciclo de vida del reproductor, cola de reproducción, aleatorio, repetición
 * y exposición de estados reactivos en StateFlows para la interfaz de Compose.
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

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

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

    private var baseVolume = 1.0f
    private var fadeInJob: Job? = null
    private var playbackParamsJob: Job? = null

    private val nativeAudioProcessor = NativeAudioProcessor()

    private var mediaSession: MediaSession? = null

    companion object {
        @Volatile
        var activeMediaSession: MediaSession? = null
            private set
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
                        // Conectar ecualizador y bass boost al sessionId
                        effectManager.attachToSession(player.audioSessionId)
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
                com.example.debug.AuraDebugManager.logError("ExoPlayer", "Error durante la reproducción: ${error.errorCodeName} - ${error.message}", error)

                // Si el error ocurrió por saturación del procesador de audio (Sonic / AudioSink), intentar recuperarse
                val player = exoPlayer
                if (player != null && _currentTrack.value != null) {
                    try {
                        _playbackSpeed.value = 1.0f
                        _playbackPitch.value = 1.0f
                        player.playbackParameters = androidx.media3.common.PlaybackParameters.DEFAULT
                        player.prepare()
                        player.play()
                        com.example.debug.AuraDebugManager.logWarning("ExoPlayer", "Recuperación automática de reproducción tras error de procesador de audio.")
                        return
                    } catch (e: Throwable) {
                        com.example.debug.AuraDebugManager.logCritical("ExoPlayer", "Fallo al recuperar reproducción local: ${e.message}", e)
                    }
                }

                // Pasar a la siguiente automáticamente si hay error irrecuperable en el archivo
                playNext()
            }
        })

        // Inicializar MediaSession vinculada a ExoPlayer para System Media Controls y compatibilidad retroactiva
        try {
            val sessionActivityIntent = Intent(context, com.example.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val sessionActivityPendingIntent = PendingIntent.getActivity(
                context,
                0,
                sessionActivityIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val forwardingPlayer = object : androidx.media3.common.ForwardingPlayer(player) {
                override fun getAvailableCommands(): Player.Commands {
                    return super.getAvailableCommands().buildUpon()
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build()
                }

                override fun isCommandAvailable(command: Int): Boolean {
                    return when (command) {
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                        else -> super.isCommandAvailable(command)
                    }
                }

                override fun seekToNext() {
                    playNext()
                }

                override fun seekToNextMediaItem() {
                    playNext()
                }

                override fun seekToPrevious() {
                    playPrevious()
                }

                override fun seekToPreviousMediaItem() {
                    playPrevious()
                }

                override fun hasNextMediaItem(): Boolean = true
                override fun hasPreviousMediaItem(): Boolean = true
            }

            val session = MediaSession.Builder(context, forwardingPlayer)
                .setSessionActivity(sessionActivityPendingIntent)
                .build()
            mediaSession = session
            activeMediaSession = session
        } catch (e: Throwable) {
            com.example.debug.AuraDebugManager.logWarning(
                "AuraAudioPlayer",
                "No se pudo inicializar MediaSession: ${e.message}"
            )
        }
    }

    private fun ensurePlaybackServiceStarted() {
        try {
            val serviceIntent = Intent(context, AuraMediaPlaybackService::class.java)
            // Iniciamos con startService para no activar el temporizador estricto de 5 segundos de Android 14
            // (ForegroundServiceDidNotStartInTimeException). Media3 MediaSessionService gestionará la elevación
            // a primer plano de forma autónoma y reactiva una vez enlazada la sesión con addSession.
            context.startService(serviceIntent)
        } catch (e: Throwable) {
            com.example.debug.AuraDebugManager.logWarning(
                "AuraAudioPlayer",
                "No se pudo iniciar AuraMediaPlaybackService: ${e.message}"
            )
        }
    }

    fun playTrackList(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        _queue.value = tracks
        val safeIndex = startIndex.coerceIn(0, tracks.size - 1)
        _currentIndex.value = safeIndex
        playTrack(tracks[safeIndex])
    }

    fun playTrack(track: Track) {
        _currentTrack.value = track
        _currentPosition.value = 0L
        _duration.value = track.durationMs

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

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(track.uriString))
                .setMediaMetadata(mediaMetadata)
                .build()

            player.setMediaItem(mediaItem)
            player.playbackParameters = androidx.media3.common.PlaybackParameters(_playbackSpeed.value, _playbackPitch.value)
            player.prepare()
            ensurePlaybackServiceStarted()
            player.play()

            // Manejar fundido de entrada si el crossfade está configurado
            fadeInJob?.cancel()
            if (_crossfadeSeconds.value > 0) {
                player.volume = 0.05f * baseVolume
                fadeInJob = playerScope.launch {
                    val steps = 15
                    val stepDelay = (_crossfadeSeconds.value * 1000L) / steps
                    for (i in 1..steps) {
                        delay(stepDelay.coerceAtLeast(40L))
                        if (!isActive) break
                        val factor = i.toFloat() / steps.toFloat()
                        player.volume = baseVolume * factor
                    }
                    player.volume = baseVolume
                }
            } else {
                player.volume = baseVolume
            }
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
            delay(40L) // Coalescing / Throttling seguro contra movimientos rápidos del slider
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
                // Si estaba reproduciendo, asegurar que no se quede pausado
                if (wasPlaying && !player.isPlaying && player.playbackState != Player.STATE_ENDED) {
                    player.play()
                }
            } catch (t: Throwable) {
                com.example.debug.AuraDebugManager.logWarning("ExoPlayer", "Fallo al aplicar velocidad/tono: ${t.message}", t)
            }
        }
    }

    fun setVolume(volume: Float) {
        baseVolume = volume.coerceIn(0.0f, 1.0f)
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            exoPlayer?.volume = baseVolume
        } else {
            playerScope.launch(Dispatchers.Main) {
                exoPlayer?.volume = baseVolume
            }
        }
    }

    fun getVolume(): Float = baseVolume

    private var isFadeInOnResume = true

    fun setFadeInOnResumeEnabled(enabled: Boolean) {
        isFadeInOnResume = enabled
    }

    fun triggerSmoothFadeIn(durationMs: Long = 1000L) {
        val player = exoPlayer ?: return
        fadeInJob?.cancel()
        player.volume = 0.08f * baseVolume
        fadeInJob = playerScope.launch {
            val steps = 16
            val delayStep = durationMs / steps
            for (i in 1..steps) {
                delay(delayStep.coerceAtLeast(30L))
                if (!isActive) break
                val factor = i.toFloat() / steps.toFloat()
                player.volume = baseVolume * factor
            }
            player.volume = baseVolume
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun play() {
        val player = exoPlayer ?: return
        if (_currentTrack.value == null && _queue.value.isNotEmpty()) {
            playTrackList(_queue.value, 0)
            return
        }
        ensurePlaybackServiceStarted()
        player.play()
        if (isFadeInOnResume) {
            triggerSmoothFadeIn()
        }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        _crossfadeSeconds.value = seconds.coerceIn(0, 12)
    }

    fun setGaplessEnabled(enabled: Boolean) {
        _isGaplessEnabled.value = enabled
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (_currentTrack.value == null && _queue.value.isNotEmpty()) {
                playTrackList(_queue.value, 0)
            } else {
                ensurePlaybackServiceStarted()
                player.play()
                if (isFadeInOnResume) {
                    triggerSmoothFadeIn()
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        val clamped = positionMs.coerceIn(0L, _duration.value)
        player.seekTo(clamped)
        _currentPosition.value = clamped
    }

    fun playNext() {
        val q = _queue.value
        if (q.isEmpty()) return

        if (_repeatMode.value == RepeatMode.ONE) {
            _currentTrack.value?.let { playTrack(it) }
            return
        }

        val nextIndex = if (_shuffleEnabled.value) {
            (q.indices - _currentIndex.value).randomOrNull() ?: 0
        } else {
            (_currentIndex.value + 1) % q.size
        }

        if (nextIndex == 0 && _repeatMode.value == RepeatMode.OFF && _currentIndex.value == q.size - 1) {
            // Llegó al final y la repetición está apagada
            exoPlayer?.pause()
            _isPlaying.value = false
            return
        }

        _currentIndex.value = nextIndex
        playTrack(q[nextIndex])
    }

    fun playPrevious() {
        val player = exoPlayer ?: return
        // Si ya lleva más de 3 segundos, retroceder al inicio de la canción
        if (player.currentPosition > 3000L) {
            seekTo(0L)
            return
        }

        val q = _queue.value
        if (q.isEmpty()) return

        val prevIndex = if (_currentIndex.value > 0) {
            _currentIndex.value - 1
        } else {
            q.size - 1
        }

        _currentIndex.value = prevIndex
        playTrack(q[prevIndex])
    }

    fun toggleShuffle() {
        _shuffleEnabled.value = !_shuffleEnabled.value
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun updateTrackFavorite(trackId: Long, isFavorite: Boolean) {
        val current = _currentTrack.value
        if (current != null && current.id == trackId) {
            _currentTrack.value = current.copy(isFavorite = isFavorite)
        }
        _queue.value = _queue.value.map {
            if (it.id == trackId) it.copy(isFavorite = isFavorite) else it
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
        val current = _currentTrack.value
        if (current != null && current.id == trackId) {
            _currentTrack.value = current.copy(
                title = title,
                artist = artist,
                album = album,
                albumArtPath = if (updateArt) albumArtPath else current.albumArtPath,
                videoUri = if (updateVideo) videoUri else current.videoUri,
                isVideoLoop = if (updateVideo) isVideoLoop else current.isVideoLoop
            )
        }
        _queue.value = _queue.value.map {
            if (it.id == trackId) {
                it.copy(
                    title = title,
                    artist = artist,
                    album = album,
                    albumArtPath = if (updateArt) albumArtPath else it.albumArtPath,
                    videoUri = if (updateVideo) videoUri else it.videoUri,
                    isVideoLoop = if (updateVideo) isVideoLoop else it.isVideoLoop
                )
            } else it
        }
    }

    private fun handleTrackEnded() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                seekTo(0L)
                exoPlayer?.play()
            }
            RepeatMode.ALL -> {
                playNext()
            }
            RepeatMode.OFF -> {
                if (_currentIndex.value < _queue.value.size - 1) {
                    playNext()
                } else {
                    _isPlaying.value = false
                }
            }
        }
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = playerScope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
                    if (player.duration > 0) {
                        _duration.value = player.duration
                    }

                    // Atenuación progresiva al acercarse al final si el crossfade está habilitado
                    if (_crossfadeSeconds.value > 0 && player.duration > 0 && player.isPlaying && fadeInJob?.isActive != true) {
                        val remainingMs = player.duration - player.currentPosition
                        val crossfadeMs = _crossfadeSeconds.value * 1000L
                        if (remainingMs in 0..crossfadeMs) {
                            val factor = (remainingMs.toFloat() / crossfadeMs.toFloat()).coerceIn(0.05f, 1.0f)
                            player.volume = baseVolume * factor
                        }
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
        fadeInJob?.cancel()
        fadeInJob = null
        playbackParamsJob?.cancel()
        playbackParamsJob = null
    }

    fun release() {
        stopProgressTracking()
        try {
            mediaSession?.run {
                release()
            }
        } catch (ignored: Throwable) {}
        mediaSession = null
        activeMediaSession = null
        effectManager.release()
        exoPlayer?.release()
        exoPlayer = null
    }
}
