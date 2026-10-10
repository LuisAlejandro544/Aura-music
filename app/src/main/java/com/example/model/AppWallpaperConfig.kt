package com.example.model

/**
 * Tipos de medio admitidos para el fondo de pantalla personalizado de la aplicación.
 */
enum class WallpaperMediaType(val id: Int, val label: String) {
    NONE(0, "Sin fondo (Negro OLED)"),
    IMAGE(1, "Imagen de Galería (WebP)"),
    VIDEO(2, "Video Corto de Galería (MP4)");

    companion object {
        fun fromId(id: Int): WallpaperMediaType =
            entries.find { it.id == id } ?: NONE
    }
}

/**
 * Alcance de pantallas donde se muestra el fondo personalizado elegido por el usuario.
 */
enum class WallpaperScreenScope(val id: Int, val label: String, val description: String) {
    LIBRARY_ONLY(
        id = 0,
        label = "Solo en Tu Biblioteca",
        description = "Muestra tu fondo personalizado únicamente dentro de la pestaña Tu Biblioteca y listas."
    ),
    HOME_AND_LIBRARY(
        id = 1,
        label = "En Biblioteca e Inicio",
        description = "Muestra tu fondo personalizado en Tu Biblioteca y en la pantalla de Inicio."
    ),
    ALL_SCREENS(
        id = 2,
        label = "En toda la aplicación",
        description = "Muestra tu fondo personalizado en Inicio, Biblioteca, Playlists, Importar y Ajustes."
    );

    companion object {
        fun fromId(id: Int): WallpaperScreenScope =
            entries.find { it.id == id } ?: HOME_AND_LIBRARY
    }
}

/**
 * Configuración inmutable del Fondo de Pantalla Personalizado de Galería en Aura Music.
 *
 * @property isEnabled Indica si el fondo personalizado está activo.
 * @property mediaType Tipo de medio (NONE, IMAGE o VIDEO).
 * @property mediaPath Ruta absoluta del archivo WebP o MP4 en el almacenamiento privado de la app.
 * @property screenScope En qué pantallas de la aplicación se renderiza el fondo.
 * @property dimOverlayAlpha Opacidad del velo oscuro OLED (0.25f a 0.92f) para garantizar lectura perfecta.
 * @property blurRadiusDp Radio de desenfoque en dp (0 a 25) aplicado sobre el fondo.
 */
data class AppWallpaperConfig(
    val isEnabled: Boolean = false,
    val mediaType: WallpaperMediaType = WallpaperMediaType.NONE,
    val mediaPath: String = "",
    val screenScope: WallpaperScreenScope = WallpaperScreenScope.HOME_AND_LIBRARY,
    val dimOverlayAlpha: Float = 0.62f,
    val blurRadiusDp: Int = 0
) {
    val hasValidMedia: Boolean
        get() = isEnabled && mediaType != WallpaperMediaType.NONE && mediaPath.isNotBlank()
}
