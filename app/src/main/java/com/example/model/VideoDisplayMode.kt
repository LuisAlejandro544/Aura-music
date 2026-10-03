package com.example.model

/**
 * Modos de visualización para videos asociados a pistas de música en Now Playing:
 * 
 * 1. [FULLSCREEN_BACKGROUND]: El video se reproduce en TODO EL FONDO de la pantalla completa
 *    detrás de la interfaz, con un velo oscuro/gradiente para garantizar máxima legibilidad
 *    y la carátula flotando al frente con su aura lumínica y sombra.
 * 2. [CARD_CANVAS]: El video se reproduce dentro del marco central de la carátula (aspect ratio 1:1).
 * 3. [OFF]: Video desactivado; se visualiza únicamente la carátula estática o procedural.
 */
enum class VideoDisplayMode(val label: String, val description: String) {
    FULLSCREEN_BACKGROUND(
        label = "Fondo Completo",
        description = "Video en todo el fondo con la carátula flotando al frente"
    ),
    CARD_CANVAS(
        label = "Lienzo en Carátula",
        description = "Video dentro del marco de la carátula"
    ),
    OFF(
        label = "Solo Carátula",
        description = "Muestra únicamente la carátula estática sin video"
    );

    fun next(): VideoDisplayMode = when (this) {
        FULLSCREEN_BACKGROUND -> CARD_CANVAS
        CARD_CANVAS -> OFF
        OFF -> FULLSCREEN_BACKGROUND
    }
}
