package com.example.ui.navigation

/**
 * Pantallas principales de la aplicación.
 * Separación modular de pantallas para evitar saturar la interfaz de usuario en una sola vista.
 */
sealed class NavScreen(val route: String) {
    object Home : NavScreen("home")
    object Library : NavScreen("library")
    object Import : NavScreen("import")
    object Equalizer : NavScreen("equalizer")
    object PlaylistDetail : NavScreen("playlist_detail")
    object Settings : NavScreen("settings")
}

enum class LibraryTab(val title: String) {
    SONGS("Canciones"),
    ALBUMS("Álbumes"),
    ARTISTS("Artistas"),
    PLAYLISTS("Playlists"),
    FAVORITES("Favoritos")
}
