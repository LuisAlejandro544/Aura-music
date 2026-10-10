package com.example.widget

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.example.model.Playlist
import com.example.model.Track
import com.example.model.WidgetConfig
import com.example.model.WidgetGridContentMode
import com.example.model.WidgetPersistedState
import com.example.model.WidgetQuickItem
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Almacén persistente y sincronizador de estado para los Widgets de Aura Music.
 *
 * Responsabilidades:
 * - Guardar y restaurar el estado de la última canción reproducida (título, artista, URI, carátula,
 *   estado de favorito, segundo actual y colores extraídos de la imagen) para permitir reanudación
 *   instantánea en segundo plano incluso tras cerrar la app.
 * - Persistir las 4 Canciones más escuchadas y las 4 Playlists principales para el segundo Widget.
 * - Gestionar las preferencias del usuario configuradas en Ajustes > Widget.
 */
object WidgetStateStore {

    private const val PREFS_NAME = "aura_widget_state_prefs"
    private const val KEY_TRACK_ID = "widget_track_id"
    private const val KEY_TITLE = "widget_title"
    private const val KEY_ARTIST = "widget_artist"
    private const val KEY_ALBUM = "widget_album"
    private const val KEY_URI = "widget_uri"
    private const val KEY_ART_PATH = "widget_art_path"
    private const val KEY_IS_FAVORITE = "widget_is_favorite"
    private const val KEY_IS_PLAYING = "widget_is_playing"
    private const val KEY_POSITION_MS = "widget_position_ms"
    private const val KEY_DURATION_MS = "widget_duration_ms"
    private const val KEY_FORMAT_BADGE = "widget_format_badge"
    private const val KEY_PRIMARY_COLOR = "widget_primary_color"
    private const val KEY_SECONDARY_COLOR = "widget_secondary_color"

    private const val KEY_CFG_DYNAMIC_COLOR = "widget_cfg_dynamic_color"
    private const val KEY_CFG_GRID_MODE = "widget_cfg_grid_mode"
    private const val KEY_CFG_SHOW_PROGRESS = "widget_cfg_show_progress"
    private const val KEY_CFG_COLOR_INTENSITY = "widget_cfg_color_intensity"

    private const val KEY_TOP_SONGS_JSON = "widget_top_songs_json"
    private const val KEY_PLAYLISTS_JSON = "widget_playlists_json"

    // Caché rápida en memoria de colores por ruta de carátula para no recalcular Palette en cada tick de progreso
    private var lastArtKey: String? = null
    private var lastExtractedColors: Pair<Int, Int>? = null

    fun getConfig(context: Context): WidgetConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeName = prefs.getString(KEY_CFG_GRID_MODE, WidgetGridContentMode.TOP_SONGS.name)
        val mode = try {
            WidgetGridContentMode.valueOf(modeName ?: WidgetGridContentMode.TOP_SONGS.name)
        } catch (_: Exception) {
            WidgetGridContentMode.TOP_SONGS
        }
        return WidgetConfig(
            isDynamicColorEnabled = prefs.getBoolean(KEY_CFG_DYNAMIC_COLOR, true),
            gridContentMode = mode,
            showProgressInWidget = prefs.getBoolean(KEY_CFG_SHOW_PROGRESS, true),
            colorIntensityPercent = prefs.getInt(KEY_CFG_COLOR_INTENSITY, 85).coerceIn(30, 100)
        )
    }

    fun saveConfig(context: Context, config: WidgetConfig) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_CFG_DYNAMIC_COLOR, config.isDynamicColorEnabled)
            .putString(KEY_CFG_GRID_MODE, config.gridContentMode.name)
            .putBoolean(KEY_CFG_SHOW_PROGRESS, config.showProgressInWidget)
            .putInt(KEY_CFG_COLOR_INTENSITY, config.colorIntensityPercent.coerceIn(30, 100))
            .apply()
    }

    fun getPersistedPlaybackState(context: Context): WidgetPersistedState {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetPersistedState(
            trackId = prefs.getLong(KEY_TRACK_ID, -1L),
            title = prefs.getString(KEY_TITLE, "Aura Music") ?: "Aura Music",
            artist = prefs.getString(KEY_ARTIST, "Toca Play para reanudar tu música") ?: "Toca Play para reanudar tu música",
            album = prefs.getString(KEY_ALBUM, "Biblioteca Aura") ?: "Biblioteca Aura",
            uriString = prefs.getString(KEY_URI, "") ?: "",
            albumArtPath = prefs.getString(KEY_ART_PATH, null),
            isFavorite = prefs.getBoolean(KEY_IS_FAVORITE, false),
            isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false),
            positionMs = prefs.getLong(KEY_POSITION_MS, 0L),
            durationMs = prefs.getLong(KEY_DURATION_MS, 0L),
            formatBadge = prefs.getString(KEY_FORMAT_BADGE, "AURA DSP C++20") ?: "AURA DSP C++20",
            primaryColorInt = prefs.getInt(KEY_PRIMARY_COLOR, 0xFF8B5CF6.toInt()),
            secondaryColorInt = prefs.getInt(KEY_SECONDARY_COLOR, 0xFF06B6D4.toInt())
        )
    }

    fun savePlaybackState(
        context: Context,
        track: Track?,
        isPlaying: Boolean,
        positionMs: Long = -1L,
        durationMs: Long = -1L,
        badgeText: String? = null
    ): WidgetPersistedState {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getPersistedPlaybackState(context)

        if (track == null) {
            val updated = current.copy(
                isPlaying = isPlaying,
                positionMs = if (positionMs >= 0L) positionMs else current.positionMs,
                durationMs = if (durationMs > 0L) durationMs else current.durationMs
            )
            prefs.edit()
                .putBoolean(KEY_IS_PLAYING, updated.isPlaying)
                .putLong(KEY_POSITION_MS, updated.positionMs)
                .putLong(KEY_DURATION_MS, updated.durationMs)
                .apply()
            return updated
        }

        val (primaryColor, secondaryColor) = extractColorsFromTrackImage(track)
        val finalPosition = when {
            positionMs >= 0L -> positionMs
            current.trackId == track.id -> current.positionMs
            else -> 0L
        }
        val finalDuration = when {
            durationMs > 0L -> durationMs
            track.durationMs > 0L -> track.durationMs
            else -> current.durationMs
        }
        val finalBadge = badgeText ?: "${track.formatBadge()} • C++20 DSP"

        val newState = WidgetPersistedState(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            uriString = track.uriString,
            albumArtPath = track.albumArtPath,
            isFavorite = track.isFavorite,
            isPlaying = isPlaying,
            positionMs = finalPosition,
            durationMs = finalDuration,
            formatBadge = finalBadge,
            primaryColorInt = primaryColor,
            secondaryColorInt = secondaryColor
        )

        prefs.edit()
            .putLong(KEY_TRACK_ID, newState.trackId)
            .putString(KEY_TITLE, newState.title)
            .putString(KEY_ARTIST, newState.artist)
            .putString(KEY_ALBUM, newState.album)
            .putString(KEY_URI, newState.uriString)
            .putString(KEY_ART_PATH, newState.albumArtPath)
            .putBoolean(KEY_IS_FAVORITE, newState.isFavorite)
            .putBoolean(KEY_IS_PLAYING, newState.isPlaying)
            .putLong(KEY_POSITION_MS, newState.positionMs)
            .putLong(KEY_DURATION_MS, newState.durationMs)
            .putString(KEY_FORMAT_BADGE, newState.formatBadge)
            .putInt(KEY_PRIMARY_COLOR, newState.primaryColorInt)
            .putInt(KEY_SECONDARY_COLOR, newState.secondaryColorInt)
            .apply()

        return newState
    }

    fun updatePositionOnly(context: Context, positionMs: Long, durationMs: Long): WidgetPersistedState {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getPersistedPlaybackState(context)
        val updated = current.copy(
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = if (durationMs > 0L) durationMs else current.durationMs
        )
        prefs.edit()
            .putLong(KEY_POSITION_MS, updated.positionMs)
            .putLong(KEY_DURATION_MS, updated.durationMs)
            .apply()
        return updated
    }

    fun updateFavoriteOnly(context: Context, trackId: Long, isFavorite: Boolean): WidgetPersistedState {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getPersistedPlaybackState(context)
        if (current.trackId == trackId) {
            val updated = current.copy(isFavorite = isFavorite)
            prefs.edit().putBoolean(KEY_IS_FAVORITE, isFavorite).apply()
            return updated
        }
        return current
    }

    /**
     * Extrae los colores vibrantes y armónicos directamente de la imagen de carátula WebP de la canción
     * (o genera su par cromático procedural si la canción no tiene carátula en disco).
     */
    fun extractColorsFromTrackImage(track: Track): Pair<Int, Int> {
        val cacheKey = "${track.id}_${track.albumArtPath ?: ""}"
        if (cacheKey == lastArtKey && lastExtractedColors != null) {
            return lastExtractedColors!!
        }

        val artPath = track.albumArtPath
        if (!artPath.isNullOrBlank()) {
            try {
                val file = File(artPath)
                if (file.exists() && file.length() > 0L) {
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 4
                    }
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                    if (bitmap != null) {
                        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
                        bitmap.recycle()

                        val primaryRaw = palette.vibrantSwatch?.rgb
                            ?: palette.lightVibrantSwatch?.rgb
                            ?: palette.dominantSwatch?.rgb
                            ?: 0xFF8B5CF6.toInt()

                        val secondaryRaw = palette.lightVibrantSwatch?.rgb
                            ?: palette.mutedSwatch?.rgb
                            ?: palette.darkVibrantSwatch?.rgb
                            ?: 0xFF06B6D4.toInt()

                        val boostedPrimary = boostWidgetColor(primaryRaw, minSat = 0.42f, minLight = 0.48f)
                        val boostedSecondary = boostWidgetColor(secondaryRaw, minSat = 0.36f, minLight = 0.44f)
                        val pair = boostedPrimary to boostedSecondary
                        lastArtKey = cacheKey
                        lastExtractedColors = pair
                        return pair
                    }
                }
            } catch (_: Throwable) {}
        }

        // Paleta procedural armónica basada en el hash de título y artista
        val hash = abs((track.title + track.artist).hashCode())
        val proceduralPalettes = arrayOf(
            0xFF8B5CF6.toInt() to 0xFF06B6D4.toInt(),
            0xFFEC4899.toInt() to 0xFF8B5CF6.toInt(),
            0xFF10B981.toInt() to 0xFF06B6D4.toInt(),
            0xFFF59E0B.toInt() to 0xFFEF4444.toInt(),
            0xFF3B82F6.toInt() to 0xFF6366F1.toInt()
        )
        val fallbackPair = proceduralPalettes[hash % proceduralPalettes.size]
        lastArtKey = cacheKey
        lastExtractedColors = fallbackPair
        return fallbackPair
    }

    private fun boostWidgetColor(colorInt: Int, minSat: Float, minLight: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(colorInt, hsl)
        if (hsl[1] > 0.05f && hsl[1] < minSat) {
            hsl[1] = minSat
        }
        if (hsl[2] < minLight) {
            hsl[2] = minLight
        } else if (hsl[2] > 0.78f) {
            hsl[2] = 0.78f
        }
        return ColorUtils.HSLToColor(hsl)
    }

    /**
     * Guarda las 4 canciones más escuchadas y las 4 playlists principales para el Widget de Cuadrícula.
     */
    fun saveQuickGridItems(
        context: Context,
        topTracks: List<Track>,
        playlists: List<Playlist>,
        favoriteTracks: List<Track> = emptyList()
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val songsArray = JSONArray()
            topTracks.take(4).forEach { track ->
                val obj = JSONObject().apply {
                    put("id", track.id)
                    put("title", track.title)
                    put("subtitle", track.artist)
                    put("badge", if (track.playCount > 0) "${track.playCount} reprod." else track.formatBadge())
                    put("primaryArtPath", track.albumArtPath ?: "")
                }
                songsArray.put(obj)
            }

            // Construir lista de hasta 4 playlists (incluyendo "Tus Me Gusta" si hay favoritos y faltan playlists)
            val combinedPlaylists = mutableListOf<WidgetQuickItem>()
            if (favoriteTracks.isNotEmpty()) {
                combinedPlaylists.add(
                    WidgetQuickItem(
                        id = -1L,
                        title = "Tus Me Gusta",
                        subtitle = "${favoriteTracks.size} canciones",
                        badge = "FAVORITOS",
                        primaryArtPath = favoriteTracks.firstOrNull()?.albumArtPath,
                        collageArtPaths = favoriteTracks.mapNotNull { it.albumArtPath }.filter { it.isNotBlank() }.take(4),
                        isPlaylist = true
                    )
                )
            }
            playlists.forEach { pl ->
                if (combinedPlaylists.size < 4) {
                    val previewArts = pl.previewTracks.mapNotNull { it.albumArtPath }.filter { it.isNotBlank() }.take(4)
                    combinedPlaylists.add(
                        WidgetQuickItem(
                            id = pl.id,
                            title = pl.name,
                            subtitle = "${pl.trackCount} canciones",
                            badge = "PLAYLIST",
                            primaryArtPath = pl.customArtPath ?: previewArts.firstOrNull(),
                            collageArtPaths = previewArts,
                            isPlaylist = true
                        )
                    )
                }
            }

            val playlistsArray = JSONArray()
            combinedPlaylists.take(4).forEach { item ->
                val collageArr = JSONArray()
                item.collageArtPaths.forEach { collageArr.put(it) }
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("subtitle", item.subtitle)
                    put("badge", item.badge)
                    put("primaryArtPath", item.primaryArtPath ?: "")
                    put("collageArtPaths", collageArr)
                }
                playlistsArray.put(obj)
            }

            prefs.edit()
                .putString(KEY_TOP_SONGS_JSON, songsArray.toString())
                .putString(KEY_PLAYLISTS_JSON, playlistsArray.toString())
                .apply()
        } catch (_: Throwable) {}
    }

    fun getTopSongsItems(context: Context): List<WidgetQuickItem> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TOP_SONGS_JSON, null) ?: return emptyList()
        return parseQuickItemsJson(raw, isPlaylist = false)
    }

    fun getPlaylistItems(context: Context): List<WidgetQuickItem> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PLAYLISTS_JSON, null) ?: return emptyList()
        return parseQuickItemsJson(raw, isPlaylist = true)
    }

    private fun parseQuickItemsJson(raw: String, isPlaylist: Boolean): List<WidgetQuickItem> {
        val result = mutableListOf<WidgetQuickItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val collageList = mutableListOf<String>()
                val collageJson = obj.optJSONArray("collageArtPaths")
                if (collageJson != null) {
                    for (j in 0 until collageJson.length()) {
                        val p = collageJson.optString(j)
                        if (!p.isNullOrBlank()) collageList.add(p)
                    }
                }
                val primaryArt = obj.optString("primaryArtPath").takeIf { it.isNotBlank() }
                result.add(
                    WidgetQuickItem(
                        id = obj.getLong("id"),
                        title = obj.optString("title", "Aura Music"),
                        subtitle = obj.optString("subtitle", ""),
                        badge = obj.optString("badge", ""),
                        primaryArtPath = primaryArt,
                        collageArtPaths = collageList,
                        isPlaylist = isPlaylist
                    )
                )
            }
        } catch (_: Throwable) {}
        return result
    }
}
