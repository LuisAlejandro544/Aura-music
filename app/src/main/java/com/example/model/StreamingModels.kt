package com.example.model

/**
 * Modelos de datos y configuración para el Modo Streaming Híbrido,
 * Política de Datos para Video Canvas y Caché Temporal Inteligente (30 min TTL, 50MB - 500MB).
 *
 * Rol Arquitectónico:
 * Define las entidades de plataforma de origen (YouTube vs YouTube Music), políticas de red
 * para Video Canvas en streaming (Solo Wi-Fi, Siempre o Desactivado) y el estado de la caché temporal.
 */

/**
 * Plataforma de origen del resultado de streaming o recomendación.
 */
enum class StreamingSourcePlatform(
    val id: String,
    val badgeText: String,
    val label: String
) {
    YT_MUSIC(
        id = "YT_MUSIC",
        badgeText = "YT MUSIC",
        label = "YouTube Music"
    ),
    YOUTUBE(
        id = "YOUTUBE",
        badgeText = "YOUTUBE",
        label = "YouTube"
    ),
    SOUNDCLOUD(
        id = "SOUNDCLOUD",
        badgeText = "SOUNDCLOUD",
        label = "SoundCloud"
    );

    companion object {
        fun fromId(id: String?): StreamingSourcePlatform {
            return values().find { it.id.equals(id, ignoreCase = true) } ?: YOUTUBE
        }
    }
}

/**
 * Filtro de origen en el buscador de la pestaña Explorar / Streaming.
 */
enum class StreamingSearchFilter(val label: String) {
    ALL("Todo (YT Music, YT & SoundCloud)"),
    YT_MUSIC("🎵 Solo YT Music"),
    YOUTUBE("▶️ Solo YouTube"),
    SOUNDCLOUD("☁️ Solo SoundCloud")
}

/**
 * Plataforma externa de origen al importar una Playlist pública (Spotify o YouTube Music / YouTube).
 */
enum class ExternalPlaylistSource(
    val id: String,
    val displayName: String,
    val badgeLabel: String
) {
    SPOTIFY(
        id = "SPOTIFY",
        displayName = "Spotify",
        badgeLabel = "SPOTIFY"
    ),
    YOUTUBE_MUSIC(
        id = "YOUTUBE_MUSIC",
        displayName = "YouTube Music / YouTube",
        badgeLabel = "YT MUSIC"
    );
}

/**
 * Modo de guardado elegido por el usuario en el Importador de Playlists:
 * - STREAMING_INSTANT: Crea la playlist al instante lista para reproducir en Modo Streaming.
 * - OFFLINE_DOWNLOAD: Crea la playlist y descarga todas sus pistas al almacenamiento privado en segundo plano.
 */
enum class PlaylistImportSaveMode(
    val title: String,
    val subtitle: String
) {
    STREAMING_INSTANT(
        title = "⚡ Modo Streaming Rápido (Instantáneo)",
        subtitle = "Crea la Playlist en segundos con su portada WebP lista para escuchar bajo demanda sin ocupar almacenamiento."
    ),
    OFFLINE_DOWNLOAD(
        title = "📥 Descargar Todo Offline (En 2do plano)",
        subtitle = "Crea la Playlist y descarga todas las canciones en alta fidelidad (con carátula WebP y Video Canvas opcional)."
    )
}

/**
 * Pista individual detectada dentro de una Playlist externa (Spotify o YouTube Music / YouTube).
 */
data class ExternalPlaylistTrackItem(
    val index: Int,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String = "",
    val videoIdOrUrl: String = "",
    val sourcePlatform: ExternalPlaylistSource = ExternalPlaylistSource.YOUTUBE_MUSIC
) {
    fun formattedDuration(): String {
        if (durationSeconds <= 0L) return "--:--"
        val mins = (durationSeconds / 60) % 60
        val secs = durationSeconds % 60
        val hours = durationSeconds / 3600
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, mins, secs)
        } else {
            String.format("%d:%02d", mins, secs)
        }
    }
}

/**
 * Metadatos y listado completo de pistas extraídos de una Playlist pública de Spotify o YouTube Music.
 */
data class ExternalPlaylistPreview(
    val sourceUrl: String,
    val playlistId: String,
    val title: String,
    val author: String,
    val description: String = "",
    val coverUrl: String = "",
    val source: ExternalPlaylistSource,
    val tracks: List<ExternalPlaylistTrackItem>
)

/**
 * Política exclusiva de red para la reproducción y pre-descarga en caché de Video Canvas durante el Streaming.
 * No afecta en absoluto a la reproducción de videos de las canciones locales ya guardadas en el teléfono.
 */
enum class StreamingVideoNetworkPolicy(
    val id: Int,
    val title: String,
    val description: String
) {
    WIFI_ONLY(
        id = 0,
        title = "Videos solo con Wi-Fi",
        description = "Reproduce y guarda en caché el Video Canvas de streaming únicamente cuando estés conectado a una red Wi-Fi (ahorra tus datos móviles)."
    ),
    ALWAYS(
        id = 1,
        title = "Siempre (Wi-Fi y Datos Móviles)",
        description = "Descarga y reproduce el Video Canvas de fondo en streaming en todo momento, usando tanto Wi-Fi como tus datos móviles."
    ),
    DISABLED(
        id = 2,
        title = "Desactivado en Streaming",
        description = "Desactiva por completo los videos de fondo en modo streaming (solo audio, carátula y letras). Tus videos locales siguen funcionando normal."
    );

    companion object {
        fun fromId(id: Int): StreamingVideoNetworkPolicy {
            return values().find { it.id == id } ?: WIFI_ONLY
        }
    }
}

/**
 * Representa un elemento musical obtenido mediante la API de búsqueda o el motor de recomendaciones similares.
 */
data class StreamingSearchItem(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String = "Streaming",
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String = "",
    val platform: StreamingSourcePlatform = StreamingSourcePlatform.YT_MUSIC,
    val canonicalWatchUrl: String = if (platform == StreamingSourcePlatform.SOUNDCLOUD && videoId.startsWith("http", ignoreCase = true)) {
        videoId
    } else {
        "https://www.youtube.com/watch?v=$videoId"
    },
    val musicWatchUrl: String = if (platform == StreamingSourcePlatform.SOUNDCLOUD && videoId.startsWith("http", ignoreCase = true)) {
        videoId
    } else {
        "https://music.youtube.com/watch?v=$videoId"
    }
) {
    fun formattedDuration(): String {
        if (durationSeconds <= 0L) return "En vivo / Stream"
        val mins = (durationSeconds / 60) % 60
        val secs = durationSeconds % 60
        val hours = durationSeconds / 3600
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, mins, secs)
        } else {
            String.format("%d:%02d", mins, secs)
        }
    }

    /**
     * Genera un ID numérico negativo determinista para identificar pistas de streaming en ExoPlayer y la cola,
     * sin colisionar con los IDs positivos de Room Database.
     */
    val syntheticTrackId: Long
        get() {
            val hash = videoId.hashCode().toLong()
            val absHash = if (hash == Long.MIN_VALUE) 100000L else kotlin.math.abs(hash)
            return -(absHash.coerceAtLeast(1000L))
        }
}

/**
 * Estado y configuración de la caché temporal inteligente para Streaming (Audio + Video Canvas).
 * - Límite configurable por el usuario: 50 MB por defecto, máximo 500 MB.
 * - Expiración automática (TTL): 30 minutos sin usarse.
 * - Desalojo LRU: cuando se llena, elimina primero los elementos más antiguos.
 */
data class StreamingCacheConfig(
    val maxCacheSizeMb: Int = DEFAULT_CACHE_SIZE_MB,
    val currentUsedBytes: Long = 0L,
    val cachedAudioCount: Int = 0,
    val cachedVideoCount: Int = 0,
    val videoNetworkPolicy: StreamingVideoNetworkPolicy = StreamingVideoNetworkPolicy.WIFI_ONLY
) {
    val usedMbFormatted: String
        get() {
            val mb = currentUsedBytes.toDouble() / (1024.0 * 1024.0)
            return String.format("%.1f MB", mb)
        }

    val usageFraction: Float
        get() {
            val maxBytes = maxCacheSizeMb.toLong() * 1024L * 1024L
            if (maxBytes <= 0L) return 0f
            return (currentUsedBytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f)
        }

    companion object {
        const val DEFAULT_CACHE_SIZE_MB = 50
        const val MIN_CACHE_SIZE_MB = 50
        const val MAX_CACHE_SIZE_MB = 500
        const val CACHE_TTL_MS = 30L * 60L * 1000L // 30 minutos
    }
}
