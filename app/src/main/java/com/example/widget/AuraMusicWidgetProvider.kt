package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.model.Track
import com.example.playback.NativeAudioEngine
import java.io.File
import kotlin.math.abs

/**
 * Aura Music - Widget Interactivo de Pantalla de Inicio (Home Screen Widget)
 *
 * Responsabilidades:
 * - Renderiza el estado actual del reproductor en el escritorio de Android (título, artista, carátula
 *   redondeada o arte procedural matemático, e insignia en tiempo real de formato/Bit-Perfect/AAudio).
 * - Procesa acciones directas de reproducción (Play/Pause, Siguiente, Anterior) sin necesidad de abrir la app.
 * - Soporta redimensionamiento fluido y estética Neo-Glass con Material You.
 */
class AuraMusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(
                context = context,
                appWidgetManager = appWidgetManager,
                appWidgetId = appWidgetId,
                track = lastKnownTrack,
                isPlaying = lastKnownIsPlaying
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_PLAY_PAUSE -> {
                val handled = onTogglePlayPauseCallback?.invoke() ?: false
                if (!handled) {
                    launchAppWithCommand(context, ACTION_WIDGET_PLAY_PAUSE)
                }
            }
            ACTION_WIDGET_NEXT -> {
                val handled = onPlayNextCallback?.invoke() ?: false
                if (!handled) {
                    launchAppWithCommand(context, ACTION_WIDGET_NEXT)
                }
            }
            ACTION_WIDGET_PREV -> {
                val handled = onPlayPreviousCallback?.invoke() ?: false
                if (!handled) {
                    launchAppWithCommand(context, ACTION_WIDGET_PREV)
                }
            }
        }
    }

    private fun launchAppWithCommand(context: Context, actionCommand: String) {
        try {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_WIDGET_COMMAND, actionCommand)
            }
            context.startActivity(launchIntent)
        } catch (_: Throwable) {}
    }

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE = "com.example.widget.ACTION_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.example.widget.ACTION_NEXT"
        const val ACTION_WIDGET_PREV = "com.example.widget.ACTION_PREV"
        const val EXTRA_WIDGET_COMMAND = "extra_widget_command"

        @Volatile
        private var lastKnownTrack: Track? = null

        @Volatile
        private var lastKnownIsPlaying: Boolean = false

        @Volatile
        var onTogglePlayPauseCallback: (() -> Boolean)? = null

        @Volatile
        var onPlayNextCallback: (() -> Boolean)? = null

        @Volatile
        var onPlayPreviousCallback: (() -> Boolean)? = null

        /**
         * Empuja de forma reactiva el estado de reproducción actual hacia todos los widgets activos en el escritorio.
         */
        fun pushPlaybackState(context: Context, track: Track?, isPlaying: Boolean) {
            lastKnownTrack = track
            lastKnownIsPlaying = isPlaying
            try {
                val appContext = context.applicationContext
                val appWidgetManager = AppWidgetManager.getInstance(appContext)
                val componentName = ComponentName(appContext, AuraMusicWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                    for (id in appWidgetIds) {
                        updateSingleWidget(appContext, appWidgetManager, id, track, isPlaying)
                    }
                }
            } catch (_: Throwable) {}
        }

        private fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            track: Track?,
            isPlaying: Boolean
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_aura_player)

            val title = track?.title ?: context.getString(R.string.widget_default_title)
            val artist = track?.artist ?: context.getString(R.string.widget_default_artist)

            val isBitPerfect = NativeAudioEngine.isBitPerfectEnabled()
            val isAAudio = NativeAudioEngine.isLowLatencyAAudioEnabled()
            val formatBadge = track?.formatBadge() ?: "AURA DSP"

            val badgeText = when {
                isBitPerfect && isAAudio -> "$formatBadge • BIT-PERFECT AAUDIO"
                isBitPerfect -> "$formatBadge • BIT-PERFECT 1:1"
                isAAudio -> "$formatBadge • AAUDIO HI-RES"
                track != null -> "$formatBadge • C++20 DSP"
                else -> context.getString(R.string.widget_default_badge)
            }

            views.setTextViewText(R.id.widget_track_title, title)
            views.setTextViewText(R.id.widget_track_artist, artist)
            views.setTextViewText(R.id.widget_badge, badgeText)

            views.setImageViewResource(
                R.id.widget_btn_play_pause,
                if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )

            // Cargar carátula redondeada o generar arte procedural matemático
            val artworkBitmap = loadOrGenerateWidgetArt(track)
            views.setImageViewBitmap(R.id.widget_album_art, artworkBitmap)

            // Intent para abrir la aplicación al tocar el cuerpo del widget
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                100,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_album_art, openAppPendingIntent)

            // Intents de controles de transporte
            views.setOnClickPendingIntent(
                R.id.widget_btn_play_pause,
                buildBroadcastPendingIntent(context, ACTION_WIDGET_PLAY_PAUSE, 101)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_next,
                buildBroadcastPendingIntent(context, ACTION_WIDGET_NEXT, 102)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_prev,
                buildBroadcastPendingIntent(context, ACTION_WIDGET_PREV, 103)
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun buildBroadcastPendingIntent(
            context: Context,
            action: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, AuraMusicWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        /**
         * Carga la carátula WebP de la canción o dibuja proceduralmente una carátula geométrica
         * con bordes redondeados para el widget.
         */
        private fun loadOrGenerateWidgetArt(track: Track?): Bitmap {
            val sizePx = 192
            val artPath = track?.albumArtPath
            if (!artPath.isNullOrBlank()) {
                try {
                    val file = File(artPath)
                    if (file.exists()) {
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 2
                        }
                        val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
                        if (decoded != null) {
                            return createRoundedBitmap(decoded, sizePx)
                        }
                    }
                } catch (_: Throwable) {}
            }
            return generateProceduralWidgetBitmap(track?.title ?: "Aura Music", track?.artist ?: "Aura", sizePx)
        }

        private fun createRoundedBitmap(source: Bitmap, targetSize: Int): Bitmap {
            val scaled = Bitmap.createScaledBitmap(source, targetSize, targetSize, true)
            val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val shader = android.graphics.BitmapShader(
                scaled,
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP
            )
            paint.shader = shader
            val radius = targetSize * 0.18f
            canvas.drawRoundRect(RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat()), radius, radius, paint)
            return output
        }

        private fun generateProceduralWidgetBitmap(title: String, artist: String, size: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val hash = abs((title + artist).hashCode())

            val palettes = arrayOf(
                intArrayOf(0xFF4C1D95.toInt(), 0xFF7C3AED.toInt(), 0xFFEC4899.toInt()),
                intArrayOf(0xFF0F172A.toInt(), 0xFF1D4ED8.toInt(), 0xFF06B6D4.toInt()),
                intArrayOf(0xFF064E3B.toInt(), 0xFF059669.toInt(), 0xFF10B981.toInt()),
                intArrayOf(0xFF7F1D1D.toInt(), 0xFFDC2626.toInt(), 0xFFF59E0B.toInt())
            )
            val palette = palettes[hash % palettes.size]

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, size.toFloat(), size.toFloat(),
                    palette[0], palette[2],
                    Shader.TileMode.CLAMP
                )
            }
            val radius = size * 0.18f
            canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, bgPaint)

            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x44FFFFFF
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawCircle(size * 0.5f, size * 0.5f, size * 0.28f, ringPaint)
            canvas.drawCircle(size * 0.5f, size * 0.5f, size * 0.10f, ringPaint)

            return bitmap
        }
    }
}
