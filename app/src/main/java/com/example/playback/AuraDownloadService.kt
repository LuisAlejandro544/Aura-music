package com.example.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.AuraApplication
import com.example.MainActivity
import com.example.R
import com.example.data.importer.FFmpegNativeEngine
import com.example.data.importer.LyricsManager
import com.example.data.importer.OnlineVideoAudioImporter
import com.example.data.importer.YtDlpAutoUpdater
import com.example.data.local.AppDatabase
import com.example.data.repository.MusicRepository
import com.example.data.storage.AppStorageManager
import com.example.debug.AuraDebugManager
import com.example.model.DownloadProgress
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Servicio en primer plano (Foreground Service) para descargas continuas de música, Video Canvas
 * y notificaciones nativas de verificación/actualización de paquetes necesarios (yt-dlp).
 */
class AuraDownloadService : Service() {

    data class PendingDownloadTask(
        val resolvedInfo: OnlineVideoAudioImporter.ResolvedMediaInfo,
        val customTitle: String,
        val customArtist: String,
        val attachAsCanvas: Boolean,
        val trimSilence: Boolean,
        val loopStyle: FFmpegNativeEngine.CanvasLoopStyle,
        val autoPlayOnComplete: Boolean = true,
        val sourceVideoId: String? = null
    )

    data class DownloadCompletedEvent(
        val track: Track,
        val autoPlayImmediately: Boolean,
        val sourceVideoId: String? = null
    )

    data class DownloadFinishedEvent(
        val sourceVideoId: String?,
        val success: Boolean
    )

    companion object {
        private const val TAG = "AuraDownloadService"

        const val CHANNEL_ID_PROGRESS = "aura_download_progress_channel"
        const val CHANNEL_ID_COMPLETE = "aura_download_complete_channel"
        const val CHANNEL_ID_PACKAGE_UPDATE = "aura_package_update_channel"

        private const val NOTIFICATION_ID_PROGRESS = 4201
        private const val NOTIFICATION_ID_PACKAGE_UPDATE = 4299
        private val completionNotificationIdCounter = AtomicInteger(4300)

        private const val EXTRA_TASK_ID = "extra_download_task_id"
        const val EXTRA_PLAY_DOWNLOADED_TRACK_ID = "extra_play_downloaded_track_id"
        const val ACTION_APPLY_PACKAGE_RESTART = "com.example.action.APPLY_PACKAGE_RESTART"

        private val pendingTasks = ConcurrentHashMap<Int, PendingDownloadTask>()
        private val taskIdCounter = AtomicInteger(1)

        private val _downloadProgress = MutableStateFlow(DownloadProgress(isDownloading = false))
        val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

        private val _statusMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
        val statusMessages: SharedFlow<String> = _statusMessages.asSharedFlow()

        private val _completedEvents = MutableSharedFlow<DownloadCompletedEvent>(extraBufferCapacity = 8)
        val completedEvents: SharedFlow<DownloadCompletedEvent> = _completedEvents.asSharedFlow()

        private val _taskFinishedEvents = MutableSharedFlow<DownloadFinishedEvent>(extraBufferCapacity = 16)
        val taskFinishedEvents: SharedFlow<DownloadFinishedEvent> = _taskFinishedEvents.asSharedFlow()

        private fun ensurePackageChannel(context: Context, nm: NotificationManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID_PACKAGE_UPDATE,
                    "Verificación y actualización de paquetes",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Notifica cuando Aura Music verifica o descarga actualizaciones de paquetes necesarios (yt-dlp)"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }

        private fun buildOpenAppPendingIntent(context: Context): PendingIntent {
            val openAppIntent = Intent(context.applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context.applicationContext,
                90,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun notifyPackageChecking(context: Context, message: String) {
            try {
                val appContext = context.applicationContext
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                ensurePackageChannel(appContext, nm)
                val notification = NotificationCompat.Builder(appContext, CHANNEL_ID_PACKAGE_UPDATE)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentTitle("Verificando paquetes necesarios")
                    .setContentText(message)
                    .setProgress(0, 0, true)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setTimeoutAfter(18_000L) // Se auto-descarta en el sistema si el proceso entra en Doze/Freezer
                    .setOnlyAlertOnce(true)
                    .setContentIntent(buildOpenAppPendingIntent(appContext))
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build()
                nm.notify(NOTIFICATION_ID_PACKAGE_UPDATE, notification)
            } catch (_: Throwable) {}
        }

        fun notifyPackageUpToDate(context: Context, version: String) {
            try {
                val appContext = context.applicationContext
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                ensurePackageChannel(appContext, nm)
                val notification = NotificationCompat.Builder(appContext, CHANNEL_ID_PACKAGE_UPDATE)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Paquetes necesarios al día")
                    .setContentText("Versión verificada: $version")
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setTimeoutAfter(3_500L) // El sistema Android la retira automáticamente en 3.5s incluso con la app cerrada
                    .setOnlyAlertOnce(true)
                    .setContentIntent(buildOpenAppPendingIntent(appContext))
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build()
                nm.notify(NOTIFICATION_ID_PACKAGE_UPDATE, notification)
            } catch (_: Throwable) {}
        }

        fun notifyPackageDownloadProgress(context: Context, percent: Int, version: String) {
            try {
                val appContext = context.applicationContext
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                ensurePackageChannel(appContext, nm)
                val clamped = percent.coerceIn(0, 100)
                val notification = NotificationCompat.Builder(appContext, CHANNEL_ID_PACKAGE_UPDATE)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("Descargando actualización de paquetes")
                    .setContentText("Actualizando motor yt-dlp ($version) • $clamped%")
                    .setSubText("$clamped%")
                    .setProgress(100, clamped, false)
                    .setOngoing(clamped < 100)
                    .setTimeoutAfter(45_000L) // Si la conexión se congela fuera de la app, se limpia tras 45s sin actividad
                    .setOnlyAlertOnce(true)
                    .setContentIntent(buildOpenAppPendingIntent(appContext))
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build()
                nm.notify(NOTIFICATION_ID_PACKAGE_UPDATE, notification)
            } catch (_: Throwable) {}
        }

        fun notifyPackageRestartRequired(context: Context, version: String) {
            try {
                val appContext = context.applicationContext
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                ensurePackageChannel(appContext, nm)

                val restartServiceIntent = Intent(appContext, AuraDownloadService::class.java).apply {
                    action = ACTION_APPLY_PACKAGE_RESTART
                }
                val restartPendingIntent = PendingIntent.getService(
                    appContext,
                    91,
                    restartServiceIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(appContext, CHANNEL_ID_PACKAGE_UPDATE)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Actualización de paquetes descargada ($version)")
                    .setContentText("Sal de la app o toca 'Actualizar y Reiniciar' para aplicar los nuevos paquetes.")
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            "Se descargaron los nuevos paquetes necesarios ($version). Se recomienda salir de la app o tocar 'Actualizar y Reiniciar' para reflejar los cambios."
                        )
                    )
                    .setAutoCancel(false)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(buildOpenAppPendingIntent(appContext))
                    .addAction(
                        android.R.drawable.ic_popup_sync,
                        "Actualizar y Reiniciar",
                        restartPendingIntent
                    )
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build()
                nm.notify(NOTIFICATION_ID_PACKAGE_UPDATE, notification)
            } catch (_: Throwable) {}
        }

        fun dismissPackageNotification(context: Context) {
            try {
                val appContext = context.applicationContext
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(NOTIFICATION_ID_PACKAGE_UPDATE)
            } catch (_: Throwable) {}
        }

        /**
         * Encola e inicia una descarga en segundo plano respaldada por ForegroundService y notificación nativa.
         * Si [autoPlayOnComplete] es `false` (ej. al descargar desde el reproductor online), guarda la pista
         * y su Video Canvas en la biblioteca local sin interrumpir la canción que está sonando.
         */
        fun startDownload(
            context: Context,
            resolvedInfo: OnlineVideoAudioImporter.ResolvedMediaInfo,
            customTitle: String,
            customArtist: String,
            attachAsCanvas: Boolean,
            trimSilence: Boolean,
            loopStyle: FFmpegNativeEngine.CanvasLoopStyle = FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE,
            autoPlayOnComplete: Boolean = true,
            sourceVideoId: String? = null
        ) {
            val taskId = taskIdCounter.incrementAndGet()
            pendingTasks[taskId] = PendingDownloadTask(
                resolvedInfo = resolvedInfo,
                customTitle = customTitle,
                customArtist = customArtist,
                attachAsCanvas = attachAsCanvas,
                trimSilence = trimSilence,
                loopStyle = loopStyle,
                autoPlayOnComplete = autoPlayOnComplete,
                sourceVideoId = sourceVideoId
            )

            val appContext = context.applicationContext
            val intent = Intent(appContext, AuraDownloadService::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
            }
            try {
                ContextCompat.startForegroundService(appContext, intent)
            } catch (t: Throwable) {
                AuraDebugManager.logWarning(TAG, "Aviso al iniciar AuraDownloadService en foreground, intentando startService: ${t.message}")
                try {
                    appContext.startService(intent)
                } catch (inner: Throwable) {
                    AuraDebugManager.logError(TAG, "No se pudo iniciar el servicio de descarga: ${inner.message}", inner)
                    pendingTasks.remove(taskId)
                    _taskFinishedEvents.tryEmit(DownloadFinishedEvent(sourceVideoId = sourceVideoId, success = false))
                }
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager
    private lateinit var repository: MusicRepository
    private val activeTaskCount = AtomicInteger(0)

    override fun attachBaseContext(newBase: Context?) {
        // Precalentar los recursos y servicios base del ApplicationContext antes de adjuntar el ContextImpl del Service
        // para que ROMs OEM (TECNO HiOS, Xiaomi/MIUI, vivo, ColorOS) nunca registren el ContextImpl del servicio
        // en ResourcesImpl.mAppContext ni inicialicen VirtualDeviceManager sobre el Service.
        try {
            val appCtx = newBase?.applicationContext
            appCtx?.resources
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                appCtx?.getSystemService("virtualdevice")
            }
        } catch (_: Throwable) {}
        super.attachBaseContext(newBase)
    }

    override fun getResources(): android.content.res.Resources {
        val appCtx = applicationContext
        return if (appCtx != null && appCtx !== this) {
            appCtx.resources
        } else {
            super.getResources()
        }
    }

    override fun getSystemService(name: String): Any? {
        val appCtx = applicationContext
        if (appCtx != null && appCtx !== this) {
            // Delegar servicios de sistema (especialmente VirtualDeviceManager en Android 14+ y NotificationManager)
            // al ApplicationContext para evitar que ContextImpl.mServiceCache retenga mContext = AuraDownloadService.
            return appCtx.getSystemService(name)
        }
        return super.getSystemService(name)
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()

        val database = AppDatabase.getInstance(applicationContext)
        repository = MusicRepository(database)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_APPLY_PACKAGE_RESTART) {
            YtDlpAutoUpdater.applyPendingUpdateAndRestart(applicationContext)
            return START_NOT_STICKY
        }

        val taskId = intent?.getIntExtra(EXTRA_TASK_ID, -1) ?: -1
        val task = pendingTasks.remove(taskId)

        if (task == null) {
            if (activeTaskCount.get() == 0) {
                try {
                    stopSelfResult(startId)
                } catch (_: Throwable) {}
            }
            return START_NOT_STICKY
        }

        val displayTitle = task.customTitle.ifBlank { task.resolvedInfo.suggestedTitle }.ifBlank { "Canción" }
        val displayArtist = task.customArtist.ifBlank { task.resolvedInfo.suggestedArtist }.ifBlank { "Artista" }

        activeTaskCount.incrementAndGet()

        val initialProgress = DownloadProgress(
            isDownloading = true,
            phase = "Preparando descarga de \"$displayTitle\"..."
        )
        _downloadProgress.value = initialProgress

        try {
            val fgNotification = buildProgressNotification(
                title = displayTitle,
                artist = displayArtist,
                progress = initialProgress
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID_PROGRESS,
                    fgNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID_PROGRESS, fgNotification)
            }
        } catch (t: Throwable) {
            AuraDebugManager.logWarning(TAG, "Aviso al promover servicio a primer plano: ${t.message}")
        }

        serviceScope.launch {
            var succeeded = false
            try {
                _statusMessages.tryEmit("Descargando \"$displayTitle\"...")
                var lastNotifUpdateMs = 0L
                val storageManager = AppStorageManager(applicationContext)

                val result = OnlineVideoAudioImporter.downloadAndImport(
                    context = applicationContext,
                    storageManager = storageManager,
                    resolvedInfo = task.resolvedInfo,
                    customTitle = task.customTitle,
                    customArtist = task.customArtist,
                    attachAsCanvas = task.attachAsCanvas,
                    trimSilence = task.trimSilence,
                    loopStyle = task.loopStyle,
                    onProgressUpdate = { progress ->
                        _downloadProgress.value = progress
                        _statusMessages.tryEmit("${progress.phase} • ${progress.formattedProgress} • ${progress.formattedSpeed}")
                        val now = System.currentTimeMillis()
                        if (now - lastNotifUpdateMs >= 250L || progress.progressFraction >= 0.99f) {
                            lastNotifUpdateMs = now
                            updateProgressNotification(displayTitle, displayArtist, progress)
                        }
                    }
                )

                _downloadProgress.value = DownloadProgress(isDownloading = false)

                result.onSuccess { rawTrack ->
                    succeeded = true
                    val savedTrack = repository.insertCustomTrack(applicationContext, rawTrack)

                    // Precargar letras sincronizadas LRC en segundo plano sin bloquear
                    launch(Dispatchers.IO) {
                        try {
                            LyricsManager.fetchLyricsOnline(savedTrack, storageManager)
                        } catch (_: Throwable) {}
                    }

                    val isUserInsideApp = AuraApplication.isAppInForeground
                    val shouldAutoPlay = task.autoPlayOnComplete && isUserInsideApp
                    val canvasInfo = if (savedTrack.videoUri != null) " con Video Canvas" else ""
                    if (task.autoPlayOnComplete) {
                        _statusMessages.tryEmit("¡Éxito! Se descargó \"${savedTrack.title}\"$canvasInfo.")
                    } else {
                        _statusMessages.tryEmit("¡Guardado en tu biblioteca! \"${savedTrack.title}\"$canvasInfo.")
                    }

                    // Emitir evento al ViewModel respetando si se solicitó guardar sin interrumpir lo que suena
                    _completedEvents.tryEmit(
                        DownloadCompletedEvent(
                            track = savedTrack,
                            autoPlayImmediately = shouldAutoPlay,
                            sourceVideoId = task.sourceVideoId
                        )
                    )

                    // Mostrar notificación final en la barra de estado
                    showCompletionNotification(savedTrack, autoPlayHint = !shouldAutoPlay)
                }.onFailure { error ->
                    _statusMessages.tryEmit("Error al descargar: ${error.message ?: "Verifica tu conexión y el enlace"}")
                    showErrorNotification(displayTitle)
                }
            } catch (t: Throwable) {
                AuraDebugManager.logError(TAG, "Error durante la descarga en segundo plano", t)
                _downloadProgress.value = DownloadProgress(isDownloading = false)
                _statusMessages.tryEmit("Error al descargar: ${t.localizedMessage ?: "Fallo al procesar el archivo"}")
                showErrorNotification(displayTitle)
            } finally {
                _taskFinishedEvents.tryEmit(
                    DownloadFinishedEvent(
                        sourceVideoId = task.sourceVideoId,
                        success = succeeded
                    )
                )
                if (activeTaskCount.decrementAndGet() <= 0) {
                    activeTaskCount.set(0)
                    pendingTasks.clear()
                    try {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } catch (_: Throwable) {}
                    try {
                        stopSelf()
                    } catch (_: Throwable) {}
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pendingTasks.clear()
        serviceScope.cancel()
        super.onDestroy()

        // Desvincular ContextImpl.mOuterContext -> AuraDownloadService y limpiar ResourcesImpl.mAppContext
        AuraApplication.detachServiceFromContextImplAndOemResources(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progressChannel = NotificationChannel(
                CHANNEL_ID_PROGRESS,
                "Descargas en curso",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra el progreso en vivo al descargar canciones y Video Canvas"
                setShowBadge(false)
            }

            val completeChannel = NotificationChannel(
                CHANNEL_ID_COMPLETE,
                "Descargas completadas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisa cuando una canción y su Video Canvas están listos para reproducirse"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(completeChannel)
        }
    }

    private fun buildProgressNotification(
        title: String,
        artist: String,
        progress: DownloadProgress
    ): Notification {
        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hasDeterminateProgress = progress.totalBytes > 0L
        val percent = (progress.progressFraction * 100f).toInt().coerceIn(0, 100)
        val subText = if (progress.bytesPerSecond > 0L) {
            "${progress.formattedProgress} • ${progress.formattedSpeed}"
        } else {
            progress.phase.ifBlank { "Procesando..." }
        }

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Descargando: $title")
            .setContentText(progress.phase.ifBlank { subText })
            .setSubText(if (hasDeterminateProgress) "$percent% • ${progress.formattedSpeed}" else artist)
            .setProgress(100, percent, !hasDeterminateProgress)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun updateProgressNotification(
        title: String,
        artist: String,
        progress: DownloadProgress
    ) {
        try {
            val notification = buildProgressNotification(title, artist, progress)
            notificationManager.notify(NOTIFICATION_ID_PROGRESS, notification)
        } catch (_: Exception) {}
    }

    private fun showCompletionNotification(track: Track, autoPlayHint: Boolean) {
        try {
            val playIntent = Intent(applicationContext, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_PLAY_DOWNLOADED_TRACK_ID, track.id)
                putExtra(
                    com.example.widget.AuraMusicWidgetProvider.EXTRA_INTERNAL_AUTH_TOKEN,
                    com.example.widget.AuraMusicWidgetProvider.INTERNAL_IPC_AUTH_TOKEN
                )
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                track.id.hashCode(),
                playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val contentText = if (autoPlayHint) {
                "${track.artist} • Toca para reproducir ahora"
            } else {
                "${track.artist} • Listo en tu biblioteca"
            }

            val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID_COMPLETE)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("¡Descarga completada! • ${track.title}")
                .setContentText(contentText)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            val artPath = track.albumArtPath
            if (!artPath.isNullOrBlank() && File(artPath).exists()) {
                try {
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = 2
                        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                    }
                    val bitmap = BitmapFactory.decodeFile(artPath, opts)
                    if (bitmap != null) {
                        builder.setLargeIcon(bitmap)
                    }
                } catch (_: Throwable) {}
            }

            notificationManager.notify(
                completionNotificationIdCounter.incrementAndGet(),
                builder.build()
            )
        } catch (t: Throwable) {
            AuraDebugManager.logWarning(TAG, "No se pudo mostrar notificación final: ${t.message}")
        }
    }

    private fun showErrorNotification(title: String) {
        try {
            val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                1,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID_COMPLETE)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Error en la descarga")
                .setContentText("No se pudo completar la descarga de \"$title\"")
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            notificationManager.notify(
                completionNotificationIdCounter.incrementAndGet(),
                notification
            )
        } catch (_: Throwable) {}
    }
}
