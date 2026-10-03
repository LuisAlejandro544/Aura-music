package com.example.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.R
import com.example.debug.AuraDebugManager

/**
 * Servicio de reproducción en primer plano (Foreground Service) basado en Jetpack Media3.
 *
 * Responsabilidades:
 * - Publicar el controlador multimedia nativo de Android (System Media Controls) en la cortina
 *   de notificaciones y pantalla de bloqueo para todas las versiones soportadas:
 *   - Android 13, 14, 15+ (System Media Player con línea ondulada de progreso).
 *   - Android 11 y 12 (Controles multimedia en Ajustes Rápidos / Quick Settings).
 *   - Android 8.0 Oreo, 9 Pie y 10 (Notificación MediaStyle con compatibilidad retroactiva).
 * - Crear el canal de notificación silencioso (IMPORTANCE_LOW) para evitar sonidos o alertas intrusivas al cambiar de pista.
 * - Mantener la reproducción de música activa en segundo plano cuando la pantalla se apaga o la app se minimiza.
 */
class AuraMediaPlaybackService : MediaSessionService() {

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        try {
            val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.notification_channel_name)
                .build()
            setMediaNotificationProvider(notificationProvider)
        } catch (e: Throwable) {
            AuraDebugManager.logWarning(
                "AuraMediaPlaybackService",
                "No se pudo inicializar DefaultMediaNotificationProvider personalizado, usando el predeterminado: ${e.message}"
            )
        }

        attachActiveSessionIfAvailable()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        attachActiveSessionIfAvailable()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val session = AuraAudioPlayer.activeMediaSession
        if (session != null && !isSessionAdded(session)) {
            addSession(session)
        }
        return session
    }

    private fun attachActiveSessionIfAvailable() {
        val session = AuraAudioPlayer.activeMediaSession
        if (session != null && !isSessionAdded(session)) {
            try {
                addSession(session)
            } catch (e: Throwable) {
                AuraDebugManager.logWarning(
                    "AuraMediaPlaybackService",
                    "Error al registrar sesión en MediaSessionService: ${e.message}"
                )
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = AuraAudioPlayer.activeMediaSession
        val player = session?.player
        // Si no se está reproduciendo música cuando el usuario descarta la app de la lista de recientes,
        // detenemos el servicio para liberar recursos del sistema.
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        val session = AuraAudioPlayer.activeMediaSession
        if (session != null && isSessionAdded(session)) {
            try {
                removeSession(session)
            } catch (ignored: Throwable) {}
        }
        super.onDestroy()
        AuraDebugManager.logInfo(
            "AuraMediaPlaybackService",
            "Servicio de reproducción multimedia destruido."
        )
    }

    /**
     * Crea el canal de notificación en Android 8.0+ (API 26+) con prioridad baja
     * para que las transiciones de pista y los controles aparezcan de forma limpia y silenciosa.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null) {
                val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
                if (existingChannel == null) {
                    val channelName = getString(R.string.notification_channel_name)
                    val channelDesc = getString(R.string.notification_channel_desc)
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        channelName,
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = channelDesc
                        setShowBadge(false)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    }
                    notificationManager.createNotificationChannel(channel)
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "aura_music_playback_channel"
        const val NOTIFICATION_ID = 1001
    }
}
