package com.example.core.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.core.playback.controllers.MediaSessionBridge

/**
 * Construcción del motor ExoPlayer (Deck A/B, listeners, widgets) y ajustes persistidos.
 * Rol arquitectónico: extensiones internas de AuraAudioPlayer; la API pública no cambia.
 */
internal fun AuraAudioPlayer.loadPersistedTransitionSettingsInternal() {
    try {
        _crossfadeSeconds.value = transitionPrefs.getInt("crossfade_seconds", 0).coerceIn(0, 15)
        _isGaplessEnabled.value = transitionPrefs.getBoolean("gapless_enabled", true)
        _isDjAutomixEnabled.value = transitionPrefs.getBoolean("dj_automix_enabled", false)
        _isDjEqCurveEnabled.value = transitionPrefs.getBoolean("dj_eq_curve_enabled", true)
    } catch (_: Throwable) {}
}

@OptIn(UnstableApi::class)
internal fun AuraAudioPlayer.initPlayerInternal() {
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
        onTrackEnded = { handleTrackEndedInternal() },
        onPlayerError = { streamingRecovery.handlePlayerError(player, it) },
        onStartTracking = { startProgressTrackingInternal() },
        onStopTracking = { stopProgressTrackingInternal() }
    )
    player.addListener(stateListener)
    // Capa C: auto-avance gapless al prebuffer (ExoPlayer playlist índice 1).
    try {
        stateListener.onPlaylistAutoAdvance = { syncQueueAfterPlaylistAutoAdvanceInternal() }
    } catch (_: Throwable) {}

    mediaSessionBridge = MediaSessionBridge(
        context = context,
        player = player,
        onPlayNext = { playNextInternal() },
        onPlayPrevious = { playPreviousInternal() }
    )

    // Vincular callbacks directos para el Widget de Pantalla de Inicio
    com.example.widget.AuraMusicWidgetProvider.onTogglePlayPauseCallback = {
        if (_currentTrack.value == null && queue.value.isEmpty()) {
            false
        } else {
            togglePlayPauseInternal()
            true
        }
    }
    com.example.widget.AuraMusicWidgetProvider.onPlayNextCallback = {
        if (queue.value.isEmpty()) {
            false
        } else {
            playNextInternal()
            true
        }
    }
    com.example.widget.AuraMusicWidgetProvider.onPlayPreviousCallback = {
        if (queue.value.isEmpty()) {
            false
        } else {
            playPreviousInternal()
            true
        }
    }
}

internal fun AuraAudioPlayer.ensurePlaybackServiceStartedInternal() {
    mediaSessionBridge?.ensurePlaybackServiceStarted()
}
