package com.example.core.data.importer.mixtape

import android.content.Context
import com.example.core.model.MixtapeMetadata
import com.example.core.model.Track

/**
 * Motor nativo de creación y gestión de Mixtapes Continuos con FFmpeg en Aura Music.
 *
 * Características:
 * 1. Fusión de N canciones con fundido cruzado continuo (acrossfade en FFmpeg) en una sola pista M4A.
 * 2. Normalización de formato acústico (44.1 kHz, estéreo, coma flotante) previo al crossfade para cero ruidos.
 * 3. Cálculo matemático exacto de marcas de tiempo de capítulos para conmutar carátulas y Video Canvas en vivo.
 * 4. Concatenación inteligente de letras sincronizadas (.LRC) con ajuste de desfase temporal por capítulo.
 * 5. Generación de portada oficial en collage WebP sin pérdida a partir de las carátulas de las pistas unidas.
 * 6. Persistencia de metadatos de capítulos en metadata/mixtape_{id}.json.
 *
 * Rol arquitectónico: fachada pública intacta que delega en 7 piezas internas.
 */
object MixtapeEngine {

    suspend fun createMixtape(
        context: Context,
        tracks: List<Track>,
        mixtapeTitle: String,
        crossfadeSeconds: Int = 5,
        onProgress: ((Float, String) -> Unit)? = null
    ): Track? = MixtapeCreator.create(context, tracks, mixtapeTitle, crossfadeSeconds, onProgress)

    fun getMixtapeMetadata(context: Context, trackId: Long): MixtapeMetadata? =
        MixtapeMetadataStore.readMetadata(context, trackId)
}
