package com.example.model

/**
 * Modo de contenido para el segundo Widget independiente de cuadrícula (2x2) en Aura Music.
 * Permite al usuario elegir desde Ajustes > Widget si desea mostrar sus 4 canciones
 * más escuchadas o sus 4 playlists principales.
 */
enum class WidgetGridContentMode(
    val label: String,
    val subtitle: String
) {
    TOP_SONGS(
        label = "4 Canciones Más Escuchadas",
        subtitle = "Muestra las 4 canciones que más escuchas para reproducirlas con un solo toque"
    ),
    PLAYLISTS(
        label = "4 Playlists",
        subtitle = "Muestra tus 4 listas de reproducción principales con su portada o collage dinámico"
    )
}

/**
 * Configuración global y personalización de los Widgets de escritorio de Aura Music.
 * Se persiste permanentemente en SharedPreferences y se sincroniza en tiempo real
 * con ambos Widgets (Reproductor Adaptativo y Cuadrícula Top 4 / Playlists).
 */
data class WidgetConfig(
    val isDynamicColorEnabled: Boolean = true,
    val gridContentMode: WidgetGridContentMode = WidgetGridContentMode.TOP_SONGS,
    val showProgressInWidget: Boolean = true,
    val colorIntensityPercent: Int = 85
)

/**
 * Elemento ligero serializable para renderizar cualquiera de las 4 celdas del Widget de Cuadrícula
 * (ya sea una de las 4 canciones más escuchadas o una de las 4 playlists).
 */
data class WidgetQuickItem(
    val id: Long,
    val title: String,
    val subtitle: String,
    val badge: String,
    val primaryArtPath: String? = null,
    val collageArtPaths: List<String> = emptyList(),
    val isPlaylist: Boolean = false
)

/**
 * Estado completo y persistido del reproductor para los Widgets de escritorio.
 * Garantiza que incluso si el sistema operativo cierra la app o reinicia el teléfono,
 * el Widget conserve los colores de la carátula, título, artista, estado de favorito,
 * progreso y pueda reanudar exactamente donde quedó en segundo plano.
 */
data class WidgetPersistedState(
    val trackId: Long = -1L,
    val title: String = "Aura Music",
    val artist: String = "Toca Play para reanudar tu música",
    val album: String = "Biblioteca Aura",
    val uriString: String = "",
    val albumArtPath: String? = null,
    val isFavorite: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val formatBadge: String = "AURA DSP C++20",
    val primaryColorInt: Int = 0xFF8B5CF6.toInt(),
    val secondaryColorInt: Int = 0xFF06B6D4.toInt()
) {
    val progressPercent: Int
        get() = if (durationMs > 0L) {
            ((positionMs.toFloat() / durationMs.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } else {
            0
        }

    fun formattedPosition(): String = formatTimeMs(positionMs)
    fun formattedDuration(): String = formatTimeMs(durationMs)

    companion object {
        fun formatTimeMs(ms: Long): String {
            if (ms <= 0L) return "0:00"
            val totalSec = (ms / 1000L).coerceAtLeast(0L)
            val min = totalSec / 60L
            val sec = totalSec % 60L
            return String.format("%d:%02d", min, sec)
        }
    }
}
