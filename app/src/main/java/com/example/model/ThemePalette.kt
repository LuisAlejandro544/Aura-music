package com.example.model

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*

/**
 * Representa un esquema de color personalizado seleccionable por el usuario.
 * Ofrece combinaciones estéticas modernas superiores al clásico verde plano de Spotify.
 */
enum class AuraTheme(
    val title: String,
    val description: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val accentGradient: Brush,
    val isDynamic: Boolean = false
) {
    NEBULA_GLOW(
        title = "Nebula Violet",
        description = "Violeta eléctrico y cyan neón futurista",
        primaryColor = NebulaPrimary,
        secondaryColor = NebulaSecondary,
        accentGradient = NebulaGradient
    ),
    CYBER_EMERALD(
        title = "Cyber Mint",
        description = "Esmeralda brillante y menta líquida",
        primaryColor = EmeraldPrimary,
        secondaryColor = EmeraldSecondary,
        accentGradient = EmeraldGradient
    ),
    SUNSET_FIRE(
        title = "Sunset Ember",
        description = "Coral cálido, naranja fuego y destellos dorados",
        primaryColor = SunsetPrimary,
        secondaryColor = SunsetSecondary,
        accentGradient = SunsetGradient
    ),
    OCEAN_ABYSS(
        title = "Ocean Abyss",
        description = "Azul zafiro profundo y agua bioluminiscente",
        primaryColor = OceanPrimary,
        secondaryColor = OceanSecondary,
        accentGradient = OceanGradient
    ),
    MATERIAL_YOU(
        title = "Material You",
        description = "Colores dinámicos del sistema Android (Material 3)",
        primaryColor = Color(0xFF6750A4),
        secondaryColor = Color(0xFF625B71),
        accentGradient = Brush.horizontalGradient(listOf(Color(0xFF6750A4), Color(0xFF7D5260))),
        isDynamic = true
    )
}
