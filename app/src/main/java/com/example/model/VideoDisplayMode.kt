package com.example.model

/**
 * Modos de visualización para videos asociados a pistas de música en Now Playing:
 * 
 * 1. [FULLSCREEN_BACKGROUND]: El video rellena el 100% de la pantalla verticalmente (Zoom/Recorte)
 *    sin divisiones horizontales, tomando el área central aunque se recorten bordes laterales en videos 16:9.
 * 2. [FULLSCREEN_ADAPTED]: El video se muestra completo adaptado al ancho de la pantalla (Fit)
 *    sin recortar rostros ni escenas en videos horizontales.
 * 3. [CARD_CANVAS]: El video se reproduce dentro del marco central de la carátula (aspect ratio 1:1).
 * 4. [OFF]: Video desactivado; se visualiza únicamente la carátula estática o procedural.
 */
enum class VideoDisplayMode(val label: String, val description: String) {
    FULLSCREEN_BACKGROUND(
        label = "Fondo Completo (Rellenar)",
        description = "Llena toda la pantalla de arriba a abajo aunque recorte laterales o caras"
    ),
    FULLSCREEN_ADAPTED(
        label = "Fondo Completo (Adaptado)",
        description = "Muestra el cuadro horizontal completo sin recortar rostros ni bordes"
    ),
    CARD_CANVAS(
        label = "Lienzo en Carátula",
        description = "Video dentro del marco de la carátula"
    ),
    OFF(
        label = "Solo Carátula",
        description = "Muestra únicamente la carátula estática sin video"
    );

    val isFullscreen: Boolean
        get() = this == FULLSCREEN_BACKGROUND || this == FULLSCREEN_ADAPTED

    fun next(): VideoDisplayMode = when (this) {
        FULLSCREEN_BACKGROUND -> FULLSCREEN_ADAPTED
        FULLSCREEN_ADAPTED -> CARD_CANVAS
        CARD_CANVAS -> OFF
        OFF -> FULLSCREEN_BACKGROUND
    }
}
