package com.example.model

/**
 * Modelo de datos para un resultado de búsqueda de letras en el servicio libre LRCLIB.
 *
 * Permite al usuario previsualizar opciones, comparar versiones y seleccionar la deseada,
 * destacando en primera posición la versión oficial recomendada.
 *
 * @param id Identificador numérico único de la letra en LRCLIB
 * @param trackName Nombre de la pista o canción
 * @param artistName Nombre del artista o banda
 * @param albumName Nombre del álbum (si está disponible)
 * @param durationSeconds Duración de la pista asociada en segundos
 * @param isSynced Indica si contiene marcas de tiempo sincronizadas (.LRC) para Karaoke
 * @param isOfficialRecommended Indica si es la coincidencia oficial canónica recomendada
 * @param syncedLyrics Contenido completo en formato sincronizado .LRC
 * @param plainLyrics Contenido completo en texto plano
 * @param previewSnippet Extracto breve (primeros versos) para previsualización en la lista
 */
data class LyricSearchResult(
    val id: Long = 0L,
    val trackName: String = "",
    val artistName: String = "",
    val albumName: String = "",
    val durationSeconds: Int = 0,
    val isSynced: Boolean = false,
    val isOfficialRecommended: Boolean = false,
    val syncedLyrics: String = "",
    val plainLyrics: String = "",
    val previewSnippet: String = ""
)
