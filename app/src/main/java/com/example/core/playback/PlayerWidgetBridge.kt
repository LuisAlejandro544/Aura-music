package com.example.core.playback

import android.content.Context
import com.example.core.model.Track
import com.example.widget.AuraLibraryWidgetProvider
import com.example.widget.AuraMusicWidgetProvider
import com.example.widget.WidgetStateStore

/**
 * Puente de Widgets para AuraAudioPlayer.
 * Rol arquitectónico: concentra los callbacks directos del Widget de
 * pantalla de inicio y las sincronizaciones de favorito / estado de
 * reproducción, sin acoplar la fachada a los providers.
 */
internal object PlayerWidgetBridge {

    fun bindControlCallbacks(
        hasTrackOrQueue: () -> Boolean,
        hasQueue: () -> Boolean,
        onTogglePlayPause: () -> Unit,
        onPlayNext: () -> Unit,
        onPlayPrevious: () -> Unit
    ) {
        AuraMusicWidgetProvider.onTogglePlayPauseCallback = {
            if (!hasTrackOrQueue()) {
                false
            } else {
                onTogglePlayPause()
                true
            }
        }
        AuraMusicWidgetProvider.onPlayNextCallback = {
            if (!hasQueue()) {
                false
            } else {
                onPlayNext()
                true
            }
        }
        AuraMusicWidgetProvider.onPlayPreviousCallback = {
            if (!hasQueue()) {
                false
            } else {
                onPlayPrevious()
                true
            }
        }
    }

    fun pushPlaybackState(
        context: Context,
        track: Track,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long
    ) {
        AuraMusicWidgetProvider.pushPlaybackState(
            context = context,
            track = track,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs
        )
    }

    fun syncFavorite(context: Context, trackId: Long, isFavorite: Boolean) {
        WidgetStateStore.updateFavoriteOnly(context, trackId, isFavorite)
        AuraMusicWidgetProvider.refreshAllWidgets(context)
        AuraLibraryWidgetProvider.refreshAllWidgets(context)
    }
}
