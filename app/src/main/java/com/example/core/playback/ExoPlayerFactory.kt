package com.example.core.playback

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * Fábrica de reproductores ExoPlayer para AuraAudioPlayer.
 * Rol arquitectónico: construye el Deck principal (A) y el Deck auxiliar
 * saliente (B) con su NativeAudioProcessor C++20 propio, compartiendo la
 * misma factoría de MediaSource con cabeceras HTTP alineadas por `c=`.
 */
internal object ExoPlayerFactory {

    data class PlayerSet(
        val mainPlayer: ExoPlayer,
        val outgoingPlayer: ExoPlayer,
        val mainProcessor: NativeAudioProcessor,
        val outgoingProcessor: NativeAudioProcessor
    )

    fun build(context: Context): PlayerSet {
        val mediaSourceFactory = DefaultMediaSourceFactory(
            com.example.core.data.streaming.StreamingHttpDataSourceConfig.buildExoPlayerDataSourceFactory(context)
        )

        val mainProcessor = NativeAudioProcessor()
        val mainAudioSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(mainProcessor))
            .build()
        val mainRenderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink = mainAudioSink
        }
        val mainPlayer = ExoPlayer.Builder(context, mainRenderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        // Segundo reproductor auxiliar (Deck B Saliente) para Crossfade Dual-Deck superpuesto real.
        val auxProcessor = NativeAudioProcessor()
        val auxAudioSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(auxProcessor))
            .build()
        val auxRenderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink = auxAudioSink
        }
        val outgoingPlayer = ExoPlayer.Builder(context, auxRenderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        return PlayerSet(mainPlayer, outgoingPlayer, mainProcessor, auxProcessor)
    }
}
