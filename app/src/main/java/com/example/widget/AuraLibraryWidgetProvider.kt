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
import com.example.model.WidgetConfig
import com.example.model.WidgetGridContentMode
import com.example.model.WidgetPersistedState
import com.example.model.WidgetQuickItem

/**
 * Aura Music - Widget 2 Independiente: Cuadrícula 2x2 de 4 Canciones Más Escuchadas o 4 Playlists
 *
 * Responsabilidades:
 * - Mostrar en un Widget independiente de buen tamaño (4x2 redimensionable a 4x3) las 4 canciones
 *   que el usuario más ha escuchado o sus 4 Playlists principales, según cómo lo haya configurado
 *   en Ajustes > Widget.
 * - Reaccionar al color dinámico de la imagen de la canción actual.
 * - Permitir reanudar la música actual o iniciar la reproducción inmediata de cualquiera de las 4
 *   canciones/playlists directamente en segundo plano con un solo toque.
 */
class AuraLibraryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetPlaybackHeadlessController.syncQuickGridFromDb(context)
        val state = WidgetStateStore.getPersistedPlaybackState(context)
        val config = WidgetStateStore.getConfig(context)
        for (appWidgetId in appWidgetIds) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            updateSingleLibraryWidget(
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
        updateSingleLibraryWidget(
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
            ACTION_LIB_WIDGET_PLAY_ITEM -> {
                val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -999L)
                val isPlaylist = intent.getBooleanExtra(EXTRA_IS_PLAYLIST, false)
                if (itemId != -999L) {
                    if (isPlaylist) {
                        WidgetPlaybackHeadlessController.handlePlayPlaylist(context, itemId)
                    } else {
                        WidgetPlaybackHeadlessController.handlePlaySpecificTrack(context, itemId)
                    }
                }
            }
            ACTION_LIB_WIDGET_TOGGLE_PLAY -> {
                val handled = AuraMusicWidgetProvider.onTogglePlayPauseCallback?.invoke() ?: false
                if (!handled) {
                    WidgetPlaybackHeadlessController.handleTogglePlayPause(context)
                }
            }
            ACTION_LIB_WIDGET_TOGGLE_FAV -> {
                val handled = AuraMusicWidgetProvider.onToggleFavoriteCallback?.invoke() ?: false
                if (!handled) {
                    WidgetPlaybackHeadlessController.handleToggleFavorite(context)
                }
            }
        }
    }

    companion object {
        const val ACTION_LIB_WIDGET_PLAY_ITEM = "com.example.widget.ACTION_LIB_PLAY_ITEM"
        const val ACTION_LIB_WIDGET_TOGGLE_PLAY = "com.example.widget.ACTION_LIB_TOGGLE_PLAY"
        const val ACTION_LIB_WIDGET_TOGGLE_FAV = "com.example.widget.ACTION_LIB_TOGGLE_FAV"
        const val EXTRA_ITEM_ID = "extra_widget_item_id"
        const val EXTRA_IS_PLAYLIST = "extra_widget_is_playlist"
        const val EXTRA_OPEN_PLAYLIST_ID = "extra_widget_open_playlist_id"

        fun refreshAllWidgets(context: Context) {
            try {
                val appContext = context.applicationContext
                val state = WidgetStateStore.getPersistedPlaybackState(appContext)
                val config = WidgetStateStore.getConfig(appContext)
                val appWidgetManager = AppWidgetManager.getInstance(appContext)
                val componentName = ComponentName(appContext, AuraLibraryWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                    for (id in appWidgetIds) {
                        val options = appWidgetManager.getAppWidgetOptions(id)
                        updateSingleLibraryWidget(appContext, appWidgetManager, id, options, state, config)
                    }
                }
            } catch (_: Throwable) {}
        }

        private fun updateSingleLibraryWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            options: Bundle?,
            state: WidgetPersistedState,
            config: WidgetConfig
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_aura_library_grid)

            val minWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 260)?.takeIf { it > 0 } ?: 260
            val minHeightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 155)?.takeIf { it > 0 } ?: 155
            val density = context.resources.displayMetrics.density.coerceAtLeast(1.5f)
            val widthPx = (minWidthDp * density).toInt().coerceIn(360, 1200)
            val heightPx = (minHeightDp * density).toInt().coerceIn(240, 1000)

            // 1. Fondo dinámico basado en los colores de la imagen de la canción activa
            val bgBitmap = WidgetArtworkRenderer.createDynamicBackgroundBitmap(
                widthPx = widthPx,
                heightPx = heightPx,
                state = state,
                config = config
            )
            views.setImageViewBitmap(R.id.lib_widget_dynamic_bg, bgBitmap)

            // 2. Cabecera con canción actual y modo activo (Top 4 Canciones vs 4 Playlists)
            val modeBadgeText = when (config.gridContentMode) {
                WidgetGridContentMode.TOP_SONGS -> "🔥 TOP 4 CANCIONES • ${state.formatBadge}"
                WidgetGridContentMode.PLAYLISTS -> "💿 TUS 4 PLAYLISTS • ${state.formatBadge}"
            }
            views.setTextViewText(R.id.lib_widget_mode_badge, modeBadgeText)
            views.setTextViewText(R.id.lib_widget_now_title, "${state.title} • ${state.artist}")

            val nowArt = WidgetArtworkRenderer.loadOrGenerateTrackArt(
                artPath = state.albumArtPath,
                title = state.title,
                artist = state.artist,
                sizePx = 112
            )
            views.setImageViewBitmap(R.id.lib_widget_now_art, nowArt)

            views.setImageViewResource(
                R.id.lib_widget_btn_play_pause,
                if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )
            views.setImageViewBitmap(
                R.id.lib_widget_play_bg,
                WidgetArtworkRenderer.createPlayButtonBackground(96, state, config)
            )
            views.setImageViewResource(
                R.id.lib_widget_btn_fav,
                if (state.isFavorite) R.drawable.ic_widget_fav_filled else R.drawable.ic_widget_fav_border
            )

            // Intents de cabecera
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPending = PendingIntent.getActivity(
                context,
                200,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.lib_widget_header, openAppPending)

            val togglePlayIntent = Intent(context, AuraLibraryWidgetProvider::class.java).apply {
                action = ACTION_LIB_WIDGET_TOGGLE_PLAY
            }
            views.setOnClickPendingIntent(
                R.id.lib_widget_btn_play_pause,
                PendingIntent.getBroadcast(
                    context,
                    201,
                    togglePlayIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

            val toggleFavIntent = Intent(context, AuraLibraryWidgetProvider::class.java).apply {
                action = ACTION_LIB_WIDGET_TOGGLE_FAV
            }
            views.setOnClickPendingIntent(
                R.id.lib_widget_btn_fav,
                PendingIntent.getBroadcast(
                    context,
                    202,
                    toggleFavIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

            // 3. Poblar las 4 celdas de la cuadrícula según el modo configurado
            val items: List<WidgetQuickItem> = when (config.gridContentMode) {
                WidgetGridContentMode.TOP_SONGS -> {
                    val top = WidgetStateStore.getTopSongsItems(context)
                    if (top.isNotEmpty()) top else WidgetStateStore.getPlaylistItems(context)
                }
                WidgetGridContentMode.PLAYLISTS -> {
                    val pls = WidgetStateStore.getPlaylistItems(context)
                    if (pls.isNotEmpty()) pls else WidgetStateStore.getTopSongsItems(context)
                }
            }

            val containerIds = intArrayOf(
                R.id.lib_widget_item_1,
                R.id.lib_widget_item_2,
                R.id.lib_widget_item_3,
                R.id.lib_widget_item_4
            )
            val artIds = intArrayOf(
                R.id.lib_widget_item_1_art,
                R.id.lib_widget_item_2_art,
                R.id.lib_widget_item_3_art,
                R.id.lib_widget_item_4_art
            )
            val titleIds = intArrayOf(
                R.id.lib_widget_item_1_title,
                R.id.lib_widget_item_2_title,
                R.id.lib_widget_item_3_title,
                R.id.lib_widget_item_4_title
            )
            val subIds = intArrayOf(
                R.id.lib_widget_item_1_sub,
                R.id.lib_widget_item_2_sub,
                R.id.lib_widget_item_3_sub,
                R.id.lib_widget_item_4_sub
            )

            for (i in 0 until 4) {
                val item = items.getOrNull(i)
                if (item != null) {
                    views.setViewVisibility(containerIds[i], View.VISIBLE)
                    views.setTextViewText(titleIds[i], item.title)
                    val subtitleText = if (item.badge.isNotBlank()) "${item.subtitle} • ${item.badge}" else item.subtitle
                    views.setTextViewText(subIds[i], subtitleText)

                    val artBitmap = WidgetArtworkRenderer.loadQuickItemArt(item, 128)
                    views.setImageViewBitmap(artIds[i], artBitmap)

                    val itemPlayIntent = Intent(context, AuraLibraryWidgetProvider::class.java).apply {
                        action = ACTION_LIB_WIDGET_PLAY_ITEM
                        putExtra(EXTRA_ITEM_ID, item.id)
                        putExtra(EXTRA_IS_PLAYLIST, item.isPlaylist)
                    }
                    val itemPending = PendingIntent.getBroadcast(
                        context,
                        210 + i,
                        itemPlayIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    views.setOnClickPendingIntent(containerIds[i], itemPending)
                } else {
                    // Estado de reserva si el usuario tiene menos de 4 canciones/playlists
                    views.setViewVisibility(containerIds[i], View.VISIBLE)
                    val fallbackTitle = if (config.gridContentMode == WidgetGridContentMode.PLAYLISTS) {
                        "Crear Playlist #${i + 1}"
                    } else {
                        "Importar Música #${i + 1}"
                    }
                    views.setTextViewText(titleIds[i], fallbackTitle)
                    views.setTextViewText(subIds[i], "Toca para abrir Aura Music")
                    val artBitmap = WidgetArtworkRenderer.generateProceduralBitmap(fallbackTitle, "Aura", 128)
                    views.setImageViewBitmap(artIds[i], artBitmap)
                    views.setOnClickPendingIntent(containerIds[i], openAppPending)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
