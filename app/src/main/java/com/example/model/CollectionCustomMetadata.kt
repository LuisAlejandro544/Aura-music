package com.example.model

/**
 * Metadatos personalizados (foto de portada en WebP sin pérdida y descripción/biografía)
 * asignados por el usuario a un Álbum o Artista de su biblioteca local.
 */
data class CollectionCustomMetadata(
    val name: String,
    val isArtist: Boolean = false,
    val description: String = "",
    val customArtPath: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
