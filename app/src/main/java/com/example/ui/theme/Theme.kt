package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.model.AuraTheme

/**
 * Tema principal de Aura Music.
 * - Soporta temas vibrantes personalizados y Material You dinámico del sistema Android 12+.
 * - Mantiene superficies oscuras OLED de alto contraste y legibilidad.
 * - Fija la escala tipográfica a un valor cómodo y estable (fontScale = 1.0f) para que
 *   los ajustes de texto del sistema operativo no desborden ni rompan la interfaz.
 */
@Composable
fun AuraMusicTheme(
    auraTheme: AuraTheme = AuraTheme.NEBULA_GLOW,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = if (auraTheme.isDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamicDark = dynamicDarkColorScheme(context)
        dynamicDark.copy(
            background = BackgroundDark,
            onBackground = TextPrimary,
            surface = SurfaceDark,
            onSurface = TextPrimary,
            surfaceVariant = SurfaceElevatedDark,
            onSurfaceVariant = TextSecondary,
            outline = CardBorder
        )
    } else {
        darkColorScheme(
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
    }

    // Fija la escala tipográfica al estándar cómodo para evitar desbordamientos visuales
    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = currentDensity.density,
            fontScale = 1.0f
        )
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Mantener compatibilidad con el nombre por defecto de la plantilla
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    AuraMusicTheme(
        auraTheme = if (dynamicColor) AuraTheme.MATERIAL_YOU else AuraTheme.NEBULA_GLOW,
        content = content
    )
}
