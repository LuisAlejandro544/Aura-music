package com.example.model

/**
 * Modos de diseño visual para la pantalla completa Now Playing:
 *
 * 1. [CLASSIC]: Modo clásico tradicional de Aura Music con carátula central grande 1:1,
 *    visualizador dinámico de 28 bandas, barra de balance estéreo y botonera inferior.
 * 2. [CINEMATIC_CANVAS]: Modo cinemático inmersivo (estilo Spotify Canvas) optimizado para Video Canvas,
 *    con video de fondo completo despejado, miniatura de carátula lateral en el tercio inferior,
 *    frase de letra en vivo flotante sobre el video y controles ergonómicos compactos.
 * 3. [AUTO]: Selección inteligente: activa automáticamente el Modo Cinemático Canvas cuando la canción
 *    cuenta con Video Canvas activo, y el Modo Clásico cuando es únicamente carátula de audio.
 */
enum class NowPlayingDesignMode(val label: String, val description: String) {
    AUTO(
        label = "Automático Inteligente",
        description = "Cinemático cuando hay video de fondo, Clásico cuando es solo carátula"
    ),
    CINEMATIC_CANVAS(
        label = "Cinemático Canvas (Spotify)",
        description = "Video de fondo completo, minicarátula lateral y controles ergonómicos"
    ),
    CLASSIC(
        label = "Clásico (Carátula Central)",
        description = "Carátula grande 1:1, visualizador de 28 bandas y controles tradicionales"
    );

    fun next(): NowPlayingDesignMode = when (this) {
        AUTO -> CINEMATIC_CANVAS
        CINEMATIC_CANVAS -> CLASSIC
        CLASSIC -> AUTO
    }
}
