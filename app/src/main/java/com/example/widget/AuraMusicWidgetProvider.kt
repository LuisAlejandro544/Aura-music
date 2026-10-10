package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.model.Track
import com.example.model.WidgetPersistedState
import com.example.playback.NativeAudioEngine

/**
 * Aura Music - Widget 1: Reproductor Interactivo Adaptativo con Color Dinámico
 *
 * Responsabilidades:
 * - Reacciona en tiempo real a los colores extraídos de la imagen (carátula WebP o procedural)
 *   de la canción actual, pintando el fondo Neo-Glass, el halo ambiental, el botón Play y la barra de progreso.
 * - Se adapta automáticamente al estirarlo en la pantalla de inicio (`onAppWidgetOptionsChanged`):
 *   • Modo Compacto (< 115dp de alto): Barra horizontal estilizada con carátula, título, Favorito ❤️ y transporte.
 *   • Modo Mediano (115dp - 175dp de alto): Tarjeta con barra de progreso sincronizada, tiempos mm:ss y controles completos.
 *   • Modo Expandido (>= 175dp de alto): Diseño de estudio de gran formato con carátula grande y botonera destacada.
 * - Permite reanudar en segundo plano la última canción escuchada (incluso si la app estaba cerrada)
 *   mediante [WidgetPlaybackHeadlessController].
 */
class AuraMusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val state = WidgetStateStore.getPersistedPlaybackState(context)
        val config = WidgetStateStore.getConfig(context)
        for (appWidgetId in appWidgetIds) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            updateSingleWidget(
                context = context,
                appWidgetManager = appWidgetManager,
                appWidgetId = appWidgetId,
                options = options,
                state = state,
                config = config
            )
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val state = WidgetStateStore.getPersistedPlaybackState(context)
        val config = WidgetStateStore.getConfig(context)
        updateSingleWidget(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetId = appWidgetId,
            options = newOptions,
            state = state,
            config = config
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_PLAY_PAUSE -> {
                val handled = onTogglePlayPauseCallback?.invoke() ?: false
                if (!handled) {
                    WidgetPlaybackHeadlessController.handleTogglePlayPause(context)
                }
            }
            ACTION_WIDGET_NEXT -> {
                val handled = onPlayNextCallback?.invoke() ?: false
                if (!handled) {
                    WidgetPlaybackHeadlessController.handleNext(context)
                }
            }
            ACTION_WIDGET_PREV -> {
                val handled = onPlayPreviousCallback?.invoke() ?: false
                if (!handled) {
                    WidgetPlaybackHeadlessController.handlePrevious(context)
                }
            }
            ACTION_WIDGET_FAVORITE -> {
                val handled = onToggleFavoriteCallback?.invoke() ?: false
                if (!handled) {
                    WidgetPlaybackHeadlessController.handleToggleFavorite(context)
                }
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE = "com.example.widget.ACTION_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.example.widget.ACTION_NEXT"
        const val ACTION_WIDGET_PREV = "com.example.widget.ACTION_PREV"
        const val ACTION_WIDGET_FAVORITE = "com.example.widget.ACTION_FAVORITE"
        const val EXTRA_WIDGET_COMMAND = "extra_widget_command"
        const val EXTRA_INTERNAL_AUTH_TOKEN = "extra_aura_internal_auth_token"

        val INTERNAL_IPC_AUTH_TOKEN: String = java.util.UUID.randomUUID().toString()

        @Volatile
        var onTogglePlayPauseCallback: (() -> Boolean)? = null

        @Volatile
        var onPlayNextCallback: (() -> Boolean)? = null

        @Volatile
        var onPlayPreviousCallback: (() -> Boolean)? = null

        @Volatile
        var onToggleFavoriteCallback: (() -> Boolean)? = null

        @Volatile
        var onPlaySpecificTrackCallback: ((Track, List<Track>) -> Boolean)? = null

        /**
         * Empuja de forma reactiva el estado de reproducción actual hacia todos los widgets activos
         * y persiste la canción, colores de la imagen y posición para permitir reanudación en frío.
         */
        fun pushPlaybackState(
            context: Context,
            track: Track?,
            isPlaying: Boolean,
            positionMs: Long = -1L,
            durationMs: Long = -1L
        ) {
            try {
                val appContext = context.applicationContext
                val isBitPerfect = NativeAudioEngine.isBitPerfectEnabled()
                val isAAudio = NativeAudioEngine.isLowLatencyAAudioEnabled()
                val formatBadge = track?.formatBadge() ?: "AURA DSP"

                val badgeText = when {
                    isBitPerfect && isAAudio -> "$formatBadge • BIT-PERFECT AAUDIO"
                    isBitPerfect -> "$formatBadge • BIT-PERFECT 1:1"
                    isAAudio -> "$formatBadge • AAUDIO HI-RES"
                    track != null -> "$formatBadge • C++20 DSP"
                    else -> null
                }

                val savedState = WidgetStateStore.savePlaybackState(
                    context = appContext,
                    track = track,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    badgeText = badgeText
                )
                val config = WidgetStateStore.getConfig(appContext)
                updateAllActiveInstances(appContext, savedState, config)
                AuraLibraryWidgetProvider.refreshAllWidgets(appContext)
            } catch (_: Throwable) {}
        }

        /**
         * Actualiza periódicamente el progreso visual (mm:ss y barra) cuando la canción está sonando.
         */
        fun pushPlaybackProgress(context: Context, positionMs: Long, durationMs: Long) {
            try {
                val appContext = context.applicationContext
                val savedState = WidgetStateStore.updatePositionOnly(appContext, positionMs, durationMs)
                val config = WidgetStateStore.getConfig(appContext)
                updateAllActiveInstances(appContext, savedState, config)
            } catch (_: Throwable) {}
        }

        fun refreshAllWidgets(context: Context) {
            try {
                val appContext = context.applicationContext
                val state = WidgetStateStore.getPersistedPlaybackState(appContext)
                val config = WidgetStateStore.getConfig(appContext)
                updateAllActiveInstances(appContext, state, config)
            } catch (_: Throwable) {}
        }

        private fun updateAllActiveInstances(
            context: Context,
            state: WidgetPersistedState,
            config: com.example.model.WidgetConfig
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, AuraMusicWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                for (id in appWidgetIds) {
                    val options = appWidgetManager.getAppWidgetOptions(id)
                    updateSingleWidget(context, appWidgetManager, id, options, state, config)
                }
            }
        }

        private fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            options: Bundle?,
            state: WidgetPersistedState,
            config: com.example.model.WidgetConfig
        ) {
            val minWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)?.takeIf { it > 0 } ?: 250
            val minHeightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 72)?.takeIf { it > 0 } ?: 72

            // Seleccionar diseño adaptativo según cómo el usuario haya estirado el widget
            val layoutId = when {
                minHeightDp >= 175 -> R.layout.widget_aura_player_expanded
                minHeightDp >= 110 -> R.layout.widget_aura_player_medium
                else -> R.layout.widget_aura_player
            }

            val views = RemoteViews(context.packageName, layoutId)

            val density = context.resources.displayMetrics.density.coerceAtLeast(1.5f)
            val widthPx = (minWidthDp * density).toInt().coerceIn(320, 1200)
            val heightPx = (minHeightDp * density).toInt().coerceIn(110, 900)

            // 1. Fondo dinámico con colores extraídos de la imagen de la canción
            val bgBitmap = WidgetArtworkRenderer.createDynamicBackgroundBitmap(
                widthPx = widthPx,
                heightPx = heightPx,
                state = state,
                config = config
            )
            views.setImageViewBitmap(R.id.widget_dynamic_bg, bgBitmap)

            // 2. Textos e insignia
            views.setTextViewText(R.id.widget_track_title, state.title)
            views.setTextViewText(R.id.widget_track_artist, state.artist)
            views.setTextViewText(R.id.widget_badge, state.formatBadge)

            // 3. Icono de Play/Pausa y su fondo circular coloreado con el acento de la imagen
            views.setImageViewResource(
                R.id.widget_btn_play_pause,
                if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )
            val playBgBitmap = WidgetArtworkRenderer.createPlayButtonBackground(112, state, config)
            views.setImageViewBitmap(R.id.widget_play_btn_bg, playBgBitmap)

            // 4. Botón de Favorito (Corazón ❤️ rojo lleno o delineado)
            views.setImageViewResource(
                R.id.widget_btn_fav,
                if (state.isFavorite) R.drawable.ic_widget_fav_filled else R.drawable.ic_widget_fav_border
            )

            // 5. Carátula redondeada o arte procedural
            val artSizePx = if (minHeightDp >= 175) 240 else 192
            val artworkBitmap = WidgetArtworkRenderer.loadOrGenerateTrackArt(
                artPath = state.albumArtPath,
                title = state.title,
                artist = state.artist,
                sizePx = artSizePx
            )
            views.setImageViewBitmap(R.id.widget_album_art, artworkBitmap)

            // 6. Barra de progreso y tiempos si estamos en tamaño Mediano o Expandido
            if (minHeightDp >= 110) {
                if (config.showProgressInWidget) {
                    views.setViewVisibility(R.id.widget_progress_container, View.VISIBLE)
                    views.setTextViewText(R.id.widget_time_current, state.formattedPosition())
                    views.setTextViewText(R.id.widget_time_total, state.formattedDuration())
                    val progressBitmap = WidgetArtworkRenderer.createProgressBarBitmap(
                        widthPx = (widthPx * 0.65f).toInt().coerceAtLeast(220),
                        heightPx = 28,
                        progressPercent = state.progressPercent,
                        state = state,
                        config = config
                    )
                    views.setImageViewBitmap(R.id.widget_progress_bitmap, progressBitmap)
                } else {
                    views.setViewVisibility(R.id.widget_progress_container, View.GONE)
                }
            }

            // 7. Intent para abrir la app al tocar el cuerpo del widget o la carátula
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

            // 8. Intents de controles de transporte y favorito en segundo plano
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
            views.setOnClickPendingIntent(
                R.id.widget_btn_fav,
                buildBroadcastPendingIntent(context, ACTION_WIDGET_FAVORITE, 104)
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
    }
}
