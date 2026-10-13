package com.example.core.playback

import android.os.Looper
import com.example.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Capa C Head-Start: pre-buffer real en ExoPlayer + Dual-Deck para streaming.
 * Rol arquitectónico: extensiones internas de AuraAudioPlayer; la API pública
 * solo añade [AuraAudioPlayer.prebufferStreamingNext] y
 * [AuraAudioPlayer.clearStreamingPrebuffer] (compatibilidad 100%).
 *
 * Cómo funciona:
 * - Cuando el calentador (capas A+B) deja la URL fresca del vecino en la cola,
 *   se encola su MediaItem como siguiente en el Deck principal (setNext/add).
 *   ExoPlayer empieza a buferizarlo en 2do plano mientras suena la actual.
 * - Al transicionar (manual o crossfade auto), [PlayerTrackStarter] consume el
 *   prebuffer con `seekToNext()` en 0ms en vez de `setMediaItem()+prepare()`
 *   (200-400ms de primer buffer HTTP). Si hay crossfade > 0s, el handoff
 *   Dual-Deck ya copia la saliente al Deck B, así la entrante (ya buferizada)
 *   se superpone con curva de igual potencia: gapless real + crossfade.
 *
 * Guardas (batería y 403):
 * - Solo 1 pista por delante, solo streaming con URL http/file válida, nunca
 *   páginas sin resolver (`aura_stream://`, watch, soundcloud permalink).
 * - Con aleatorio no se puede predecir el vecino: no se prebuferiza.
 * - TTL 45 min (igual que URLs firmadas googlevideo): pasado ese tiempo el
 *   prebuffer se invalida y la recuperación 403 existente regenera el flujo.
 * - Todo ExoPlayer corre en Main Thread; si se llama desde IO se reprograma.
 */
internal fun AuraAudioPlayer.prebufferStreamingNextInternal(nextTrack: Track) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        try {
            playerScope.launch(Dispatchers.Main) { prebufferStreamingNextInternal(nextTrack) }
        } catch (_: Throwable) {}
        return
    }
    try {
        val main = exoPlayer ?: return
        if (!nextTrack.isStreamingTrack) return
        val uri = nextTrack.uriString
        if (uri.isBlank() || isUnresolvedStreamingPageUrl(uri)) {
            clearStreamingPrebufferInternal()
            return
        }
        // Solo el vecino inmediato: si la cola cambió, purgar el obsoleto.
        val q = try { queue.value } catch (_: Throwable) { emptyList() }
        val cur = _currentTrack.value
        val curIdx = q.indexOfFirst { it.id == cur?.id }.let {
            if (it != -1) it else try { currentIndex.value } catch (_: Throwable) { -1 }
        }
        val shuffleOn = try { shuffleEnabled.value } catch (_: Throwable) { false }
        val expectedNext = if (!shuffleOn && curIdx in 0 until q.size - 1) q[curIdx + 1] else null
        if (expectedNext == null || expectedNext.id != nextTrack.id) {
            // Si no es el vecino, no gastar datos/batería en 2do plano.
            // Solo purgar si el prebuffer apunta a otra pista.
            if (streamingPrebufferTrackId != null && streamingPrebufferTrackId != nextTrack.id) {
                clearStreamingPrebufferInternal()
            }
            // Si la cola aún no refleja el append del calentador (expectedNext null
            // porque currentTrack aún no está en cola), aceptar igualmente: el
            // calentador ya filtró aleatorio y vecino antes de llamar.
            if (expectedNext != null) return
        }
        if (repeatMode.value == com.example.core.model.RepeatMode.ONE) return

        // TTL 45 min: la URL firmada pudo expirar si el usuario pausó mucho.
        val now = System.currentTimeMillis()
        if (streamingPrebufferTrackId == nextTrack.id &&
            streamingPrebufferUri == uri &&
            (now - streamingPrebufferAtMs) < PREBUFFER_TTL_MS
        ) {
            return
        }

        val mediaItem = try {
            TrackMediaFactory.buildTrackMedia(nextTrack) { null }.mediaItem
        } catch (_: Throwable) { return }

        try {
            if (main.mediaItemCount == 0) {
                // Sin nada cargado: no prebuferizar, el arranque normal lo hará.
                return
            }
            if (main.mediaItemCount == 1) {
                main.addMediaItem(mediaItem)
            } else {
                // Ya había un siguiente (quizá obsoleto): reemplazar índice 1
                // y purgar restos más allá para mantener solo 1 por delante.
                try {
                    main.replaceMediaItem(1, mediaItem)
                } catch (_: Throwable) {
                    main.removeMediaItems(1, main.mediaItemCount)
                    main.addMediaItem(mediaItem)
                }
                if (main.mediaItemCount > 2) {
                    try { main.removeMediaItems(2, main.mediaItemCount) } catch (_: Throwable) {}
                }
            }
            streamingPrebufferTrackId = nextTrack.id
            streamingPrebufferUri = uri
            streamingPrebufferAtMs = now
        } catch (_: Throwable) {
            // Si ExoPlayer rechaza el playlist (estado Idle), invalidar.
            streamingPrebufferTrackId = null
            streamingPrebufferUri = null
        }
    } catch (_: Throwable) {}
}

/** True si el Deck principal tiene buferizado [track] como siguiente (listo para seek 0ms). */
internal fun AuraAudioPlayer.hasStreamingPrebufferForInternal(track: Track): Boolean {
    try {
        val main = exoPlayer ?: return false
        if (!track.isStreamingTrack) return false
        if (streamingPrebufferTrackId != track.id) return false
        if (streamingPrebufferUri != track.uriString) return false
        if ((System.currentTimeMillis() - streamingPrebufferAtMs) > PREBUFFER_TTL_MS) return false
        if (main.mediaItemCount < 2) return false
        // El índice 1 debe corresponder a la URI esperada (evita consumir obsoleto tras reorden).
        val queuedNext = try { main.getMediaItemAt(1) } catch (_: Throwable) { null }
        val queuedUri = try { queuedNext?.localConfiguration?.uri?.toString() } catch (_: Throwable) { null }
        val expectedUri = try {
            TrackMediaFactory.resolveMediaUri(track.uriString).toString()
        } catch (_: Throwable) { track.uriString }
        if (queuedUri != null && queuedUri != expectedUri) return false
        return true
    } catch (_: Throwable) {
        return false
    }
}

/**
 * Consume el prebuffer: salta al índice 1 ya buferizado y deja el playlist en 1.
 * Retorna true si el salto se ejecutó (0ms percibidos). No cambia la cola;
 * el llamante ya actualizó [_currentTrack] y la UI.
 */
internal fun AuraAudioPlayer.consumeStreamingPrebufferInternal(): Boolean {
    try {
        val main = exoPlayer ?: return false
        if (main.mediaItemCount < 2) return false
        try {
            // seekToNext() arranca el buffer ya listo sin prepare() desde cero.
            main.seekToNext()
        } catch (_: Throwable) {
            try {
                main.seekTo(1, 0L)
            } catch (_: Throwable) {
                return false
            }
        }
        try {
            main.playWhenReady = true
            main.play()
        } catch (_: Throwable) {}
        // Purgar el anterior en diferido para no cortar el crossfade del Deck B
        // (el Deck B tiene su propia copia de la saliente; el índice 0 del Deck A
        // ya no se escucha tras el seek, así que puede eliminarse).
        try {
            playerScope.launch(Dispatchers.Main) {
                kotlinx.coroutines.delay(800L)
                try {
                    val m = exoPlayer ?: return@launch
                    if (m.mediaItemCount > 1 && m.currentMediaItemIndex == 1) {
                        m.removeMediaItem(0)
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {
            try {
                if (main.mediaItemCount > 1 && main.currentMediaItemIndex == 1) {
                    main.removeMediaItem(0)
                }
            } catch (_: Throwable) {}
        }
        streamingPrebufferTrackId = null
        streamingPrebufferUri = null
        return true
    } catch (_: Throwable) {
        return false
    }
}

/** Purga el siguiente prebuferizado si existe (cambio de cola, seek manual, repeat ONE). */
internal fun AuraAudioPlayer.clearStreamingPrebufferInternal() {
    streamingPrebufferTrackId = null
    streamingPrebufferUri = null
    if (Looper.myLooper() != Looper.getMainLooper()) {
        try {
            playerScope.launch(Dispatchers.Main) { clearStreamingPrebufferInternal() }
        } catch (_: Throwable) {}
        return
    }
    try {
        val main = exoPlayer ?: return
        if (main.mediaItemCount > 1) {
            val curIdx = try { main.currentMediaItemIndex } catch (_: Throwable) { 0 }
            if (curIdx == 0) {
                try { main.removeMediaItems(1, main.mediaItemCount) } catch (_: Throwable) {}
            }
        }
    } catch (_: Throwable) {}
}

/** Sincronización cuando ExoPlayer auto-avanza al prebuffer (fin de pista sin seek manual). */
internal fun AuraAudioPlayer.syncQueueAfterPlaylistAutoAdvanceInternal() {
    try {
        val main = exoPlayer ?: return
        // Si auto-avanzó al índice 1, alinear la cola con el vecino y purgar.
        if (main.currentMediaItemIndex == 1 && main.mediaItemCount >= 2) {
            val q = try { queue.value } catch (_: Throwable) { emptyList() }
            val cur = _currentTrack.value
            val curIdx = q.indexOfFirst { it.id == cur?.id }.let {
                if (it != -1) it else try { currentIndex.value } catch (_: Throwable) { -1 }
            }
            val nextIdx = curIdx + 1
            if (nextIdx in q.indices) {
                val next = q[nextIdx]
                _currentTrack.value = next
                progressTracker.resetForNewTrack()
                abLoopController.reset()
                NativeAudioProcessor.isStreamingBypassActive = false
                try { effectManager.onTrackChanged(next.id) } catch (_: Throwable) {}
                try { effectManager.flushBuffers() } catch (_: Throwable) {}
                try { effectManager.applyStreamingPolicy(next.isStreamingTrack) } catch (_: Throwable) {}
                _isBuffering.value = false
                _isNetworkStalled.value = false
                _duration.value = next.durationMs
                try {
                    playerScope.launch(Dispatchers.Main) {
                        kotlinx.coroutines.delay(600L)
                        try {
                            val m = exoPlayer ?: return@launch
                            if (m.mediaItemCount > 1 && m.currentMediaItemIndex == 1) {
                                m.removeMediaItem(0)
                            }
                        } catch (_: Throwable) {}
                    }
                } catch (_: Throwable) {}
                streamingPrebufferTrackId = null
                streamingPrebufferUri = null
            }
        }
    } catch (_: Throwable) {}
}

internal const val PREBUFFER_TTL_MS = 45L * 60L * 1000L
