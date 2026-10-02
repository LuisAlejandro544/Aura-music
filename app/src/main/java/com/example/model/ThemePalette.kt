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
    val accentGradient: Brush
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
    )
}
