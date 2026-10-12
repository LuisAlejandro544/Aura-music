package com.example.core.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Recuperación de errores de ExoPlayer para AuraAudioPlayer.
 * Rol arquitectónico: política de reintento en streaming (HTTP 403/416, cortes de red)
 * y restauración local; la fachada AuraAudioPlayer delega su onPlayerError aquí.
 */
internal class StreamingErrorRecoveryHandler(
    private val scope: CoroutineScope,
    private val getTrack: () -> Track?,
    private val getFallbackPosition: () -> Long,
    private val getLivePlayer: () -> ExoPlayer?,
    private val setBuffering: (Boolean) -> Unit,
    private val setNetworkStalled: (Boolean) -> Unit,
    private val setPlaybackError: (String?) -> Unit,
    private val resetSpeedPitch: () -> Unit,
    private val getHttpRecoveryHandler: () -> ((Track, Long) -> Boolean)?,
    private val requestPlayNext: () -> Unit
) {

    private var lastHttpRecoveryTrackId: Long = 0L
    private var lastHttpRecoveryTimestampMs: Long = 0L

    fun handlePlayerError(player: ExoPlayer, error: PlaybackException) {

        val activeTrack = getTrack()
        val isBadHttpStatus = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS

        // Recuperación transparente ante HTTP 403 / 416 en pistas de Streaming:
        // En lugar de re-preparar la misma URL rechazada, delegamos al coordinador para usar el archivo
        // en caché o resolver un stream fresco con cabeceras firmadas y continuar en la posición actual.
        if (isBadHttpStatus && activeTrack?.isStreamingTrack == true) {
            val now = System.currentTimeMillis()
            val canAttemptRecovery = lastHttpRecoveryTrackId != activeTrack.id || (now - lastHttpRecoveryTimestampMs) > 6000L
            if (canAttemptRecovery) {
                lastHttpRecoveryTrackId = activeTrack.id
                lastHttpRecoveryTimestampMs = now
                setBuffering(true)
                val resumePos = player.currentPosition.coerceAtLeast(getFallbackPosition())
                com.example.debug.AuraDebugManager.logWarning(
                    "ExoPlayer",
                    "Detectado rechazo HTTP en stream (${error.errorCodeName}). Regenerando flujo firmado automáticamente..."
                )
                val handled = getHttpRecoveryHandler()?.invoke(activeTrack, resumePos) ?: false
                if (handled) return
            }
        }

        val isNetworkError = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED

        if (isNetworkError && activeTrack?.isStreamingTrack == true) {
            setBuffering(true)
            setNetworkStalled(true)
            com.example.debug.AuraDebugManager.logWarning(
                "ExoPlayer",
                "Interrupción de red en streaming (${error.errorCodeName}), esperando reconexión..."
            )
            scope.launch {
                delay(2200L)
                val p = getLivePlayer()
                if (p != null && getTrack()?.isStreamingTrack == true && p.playbackState == Player.STATE_IDLE) {
                    try {
                        p.prepare()
                        p.play()
                    } catch (_: Throwable) {}
                }
            }
            return
        }

        setBuffering(false)
        setPlaybackError("Error al reproducir pista: ${error.message}")
        com.example.debug.AuraDebugManager.logError(
            "ExoPlayer",
            "Error durante la reproducción: ${error.errorCodeName} - ${error.message}",
            error
        )

        val p = getLivePlayer()
        if (p != null && activeTrack != null && !activeTrack.isStreamingTrack) {
            try {
                resetSpeedPitch()
                p.playbackParameters = androidx.media3.common.PlaybackParameters.DEFAULT
                p.prepare()
                p.play()
                com.example.debug.AuraDebugManager.logWarning("ExoPlayer", "Recuperación automática de reproducción tras error de procesador de audio.")
                return
            } catch (e: Throwable) {
                com.example.debug.AuraDebugManager.logCritical("ExoPlayer", "Fallo al recuperar reproducción local: ${e.message}", e)
            }
        }
        requestPlayNext()
            
    }
}
