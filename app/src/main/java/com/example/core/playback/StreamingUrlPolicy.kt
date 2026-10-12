package com.example.core.playback

/**
 * Política pura para detectar URLs de streaming sin resolver.
 * Rol arquitectónico: clasifica URIs de YouTube, YT Music y SoundCloud que requieren
 * resolución previa antes de entregarse a ExoPlayer.
 */
internal object StreamingUrlPolicy {

    fun isUnresolvedStreamingPageUrl(uri: String): Boolean {

        val clean = uri.trim().lowercase()
        if (clean.isBlank() || clean.startsWith("aura_stream://")) return true
        if (clean.contains("youtube.com/watch") ||
            clean.contains("music.youtube.com/watch") ||
            clean.contains("youtu.be/")
        ) {
            return true
        }
        if (clean.contains("soundcloud.com/") && !clean.contains("cf-media.sndcdn.com") && !clean.contains("akmedia.sndcdn.com")) {
            return true
        }
        return false
    }
}
