package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.model.AuraTheme

/**
 * Tema principal de Aura Music.
 * Por defecto ofrece un modo oscuro de lujo inspirado en Spotify pero con acentos
 * visuales mucho más atractivos y personalizables según la selección de AuraTheme.
 */
@Composable
fun AuraMusicTheme(
    auraTheme: AuraTheme = AuraTheme.NEBULA_GLOW,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = auraTheme.primaryColor,
        onPrimary = Color.White,
        primaryContainer = auraTheme.primaryColor.copy(alpha = 0.2f),
        onPrimaryContainer = Color.White,
        secondary = auraTheme.secondaryColor,
        onSecondary = Color.Black,
        secondaryContainer = auraTheme.secondaryColor.copy(alpha = 0.2f),
        onSecondaryContainer = Color.White,
        tertiary = NebulaTertiary,
        background = BackgroundDark,
        onBackground = TextPrimary,
        surface = SurfaceDark,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceElevatedDark,
        onSurfaceVariant = TextSecondary,
        outline = CardBorder
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Mantener compatibilidad con el nombre por defecto de la plantilla
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    AuraMusicTheme(
        auraTheme = AuraTheme.NEBULA_GLOW,
        content = content
    )
}
