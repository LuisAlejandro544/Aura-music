package com.example.playback.controllers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.example.playback.AuraMediaPlaybackService

/**
 * Aura Music - Puente Modular para Jetpack Media3 MediaSession y System Media Controls
 *
 * Responsabilidades:
 * - Construcción y vinculación de la sesión nativa de Android MediaSession.
 * - Delegación de comandos del sistema operativo (pantalla de bloqueo, barra de estado, relojes y auriculares).
 * - Inicio seguro de AuraMediaPlaybackService sin activar el temporizador estricto de Android 14.
 * - Exposición del singleton estático seguro de MediaSession para el MediaSessionService del sistema.
 */
class MediaSessionBridge(
    private val context: Context,
    private val player: ExoPlayer,
    private val onPlayNext: () -> Unit,
    private val onPlayPrevious: () -> Unit
) {

    var mediaSession: MediaSession? = null
        private set

    companion object {
        @Volatile
        var activeMediaSession: MediaSession? = null
            internal set
    }

    init {
        initSession()
    }

    private fun initSession() {
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

            val forwardingPlayer = object : ForwardingPlayer(player) {
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
                    onPlayNext()
                }

                override fun seekToNextMediaItem() {
                    onPlayNext()
                }

                override fun seekToPrevious() {
                    onPlayPrevious()
                }

                override fun seekToPreviousMediaItem() {
                    onPlayPrevious()
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
                "MediaSessionBridge",
                "No se pudo inicializar MediaSession: ${e.message}"
            )
        }
    }

    /**
     * Inicia de forma controlada el servicio en segundo plano de reproducción de audio.
     */
    fun ensurePlaybackServiceStarted() {
        try {
            val serviceIntent = Intent(context, AuraMediaPlaybackService::class.java)
            context.startService(serviceIntent)
        } catch (e: Throwable) {
            com.example.debug.AuraDebugManager.logWarning(
                "MediaSessionBridge",
                "No se pudo iniciar AuraMediaPlaybackService: ${e.message}"
            )
        }
    }

    /**
     * Libera de forma atómica los recursos de la sesión multimedia.
     */
    fun release() {
        try {
            mediaSession?.run {
                release()
            }
        } catch (ignored: Throwable) {}
        mediaSession = null
        activeMediaSession = null
    }
}
