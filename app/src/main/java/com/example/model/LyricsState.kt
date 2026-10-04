package com.example.model

/**
 * Modelo de datos para una línea individual de letra sincronizada (.LRC).
 */
data class LyricLine(
    val timeMs: Long,
    val text: String
)

/**
 * Estado general de las letras para una pista musical.
 */
data class LyricsState(
    val trackId: Long = 0L,
    val isSynced: Boolean = false,
    val lines: List<LyricLine> = emptyList(),
    val plainLyrics: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
