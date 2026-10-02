package com.example.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.navigation.NavScreen
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

/**
 * Barra de navegación inferior principal con estilo Spotify pero acentos vibrantes modernos.
 * Conecta las pantallas modulares para una experiencia fluida y organizada.
 */
@Composable
fun BottomNavBar(
    currentScreen: NavScreen,
    onNavigate: (NavScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("bottom_nav_bar"),
        containerColor = BackgroundDark,
        tonalElevation = 10.dp
    ) {
        NavigationBarItem(
            selected = currentScreen is NavScreen.Home,
            onClick = { onNavigate(NavScreen.Home) },
            icon = {
                Icon(
                    imageVector = if (currentScreen is NavScreen.Home) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "Inicio"
                )
            },
            label = { Text("Inicio") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            ),
            modifier = Modifier.testTag("nav_home")
        )

        NavigationBarItem(
            selected = currentScreen is NavScreen.Library || currentScreen is NavScreen.PlaylistDetail,
            onClick = { onNavigate(NavScreen.Library) },
            icon = {
                Icon(
                    imageVector = if (currentScreen is NavScreen.Library) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic,
                    contentDescription = "Tu Biblioteca"
                )
            },
            label = { Text("Biblioteca") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            ),
            modifier = Modifier.testTag("nav_library")
        )

        NavigationBarItem(
            selected = currentScreen is NavScreen.Import,
            onClick = { onNavigate(NavScreen.Import) },
            icon = {
                Icon(
                    imageVector = if (currentScreen is NavScreen.Import) Icons.Filled.FolderOpen else Icons.Outlined.FolderOpen,
                    contentDescription = "Importar Música"
                )
            },
            label = { Text("Importar") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            ),
            modifier = Modifier.testTag("nav_import")
        )

        NavigationBarItem(
            selected = currentScreen is NavScreen.Settings,
            onClick = { onNavigate(NavScreen.Settings) },
            icon = {
                Icon(
                    imageVector = if (currentScreen is NavScreen.Settings) Icons.Filled.Palette else Icons.Outlined.Palette,
                    contentDescription = "Temas y Ajustes"
                )
            },
            label = { Text("Temas") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            ),
            modifier = Modifier.testTag("nav_settings")
        )
    }
}
