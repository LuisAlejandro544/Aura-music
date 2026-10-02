package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Paleta de colores para Aura Music.
 * Diseñado con una estética moderna estilo Spotify pero con colores vibrantes,
 * gradientes de neón y fondos profundos OLED de alto contraste visual.
 */

// Fondos y superficies profundos (Dark Luxury OLED)
val BackgroundDark = Color(0xFF090D16)
val SurfaceDark = Color(0xFF111827)
val SurfaceElevatedDark = Color(0xFF1E293B)
val SurfaceCard = Color(0xFF161F33)
val CardBorder = Color(0xFF26334D)

// Texto y elementos de contenido
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// Tema 1: Nebula Glow (Violeta Eléctrico & Cyan Neón)
val NebulaPrimary = Color(0xFFA855F7)
val NebulaSecondary = Color(0xFF06B6D4)
val NebulaTertiary = Color(0xFFEC4899)
val NebulaAccent = Color(0xFF8B5CF6)

// Tema 2: Cyber Emerald (Verde Neón & Menta Líquida)
val EmeraldPrimary = Color(0xFF10B981)
val EmeraldSecondary = Color(0xFF06D6A0)
val EmeraldTertiary = Color(0xFF3B82F6)
val EmeraldAccent = Color(0xFF14B8A6)

// Tema 3: Sunset Fire (Naranja Coral & Ámbar Brillante)
val SunsetPrimary = Color(0xFFF97316)
val SunsetSecondary = Color(0xFFFB7185)
val SunsetTertiary = Color(0xFFF59E0B)
val SunsetAccent = Color(0xFFFF5722)

// Tema 4: Ocean Abyss (Azul Zafiro & Aqua Radiante)
val OceanPrimary = Color(0xFF38BDF8)
val OceanSecondary = Color(0xFF6366F1)
val OceanTertiary = Color(0xFF0EA5E9)
val OceanAccent = Color(0xFF2563EB)

// Gradientes predefinidos para tarjetas y reproductor
val NebulaGradient = Brush.horizontalGradient(
    listOf(NebulaPrimary, NebulaSecondary)
)

val EmeraldGradient = Brush.horizontalGradient(
    listOf(EmeraldPrimary, EmeraldSecondary)
)

val SunsetGradient = Brush.horizontalGradient(
    listOf(SunsetPrimary, SunsetSecondary)
)

val OceanGradient = Brush.horizontalGradient(
    listOf(OceanPrimary, OceanSecondary)
)

val PlayerBackgroundGradient = Brush.verticalGradient(
    listOf(
        Color(0xFF1A1230),
        Color(0xFF0E1322),
        Color(0xFF090D16)
    )
)
