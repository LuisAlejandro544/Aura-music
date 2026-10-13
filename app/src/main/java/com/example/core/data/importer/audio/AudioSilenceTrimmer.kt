package com.example.core.data.importer

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Motor inteligente de detección y eliminación de silencios iniciales y finales para Aura Music.
 *
 * Rol arquitectónico:
 * 1. Escanea mediante decodificación PCM rápida (MediaExtractor + MediaCodec en Dispatchers.IO)
 *    los primeros y últimos segundos de una pista de audio para medir la energía RMS en decibelios (dBFS).
 * 2. Identifica con precisión dónde comienza realmente la música (fin del silencio inicial)
 *    y dónde concluye antes del silencio muerto final.
 * 3. Para contenedores compatibles (M4A / MP4 / AAC), realiza un recorte físico sin pérdida
 *    (Direct Stream Copy con MediaMuxer) ajustando los tiempos de presentación a 0:00.
 * 4. Además, registra de forma persistente los límites de recorte (startMs / endMs) para que
 *    AuraAudioPlayer aplique MediaItem.ClippingConfiguration en cualquier formato (MP3, FLAC, OGG, WAV, OPUS)
 *    sin recodificar ni perder calidad acústica.
 *
 * Rol arquitectónico: fachada pública intacta que delega en 5 submódulos internos.
 */
object AudioSilenceTrimmer {

    data class SilenceTrimResult(
        val wasTrimmed: Boolean,
        val startTrimMs: Long,
        val endTrimMs: Long,
        val newDurationMs: Long,
        val outputUriString: String
    )

    data class ClippingBounds(
        val startMs: Long,
        val endMs: Long
    )

    suspend fun processLocalAudioFile(
        context: Context,
        audioFile: File,
        originalDurationMs: Long
    ): SilenceTrimResult =
        SilenceTrimProcessor.processFile(context, audioFile, originalDurationMs)

    suspend fun processUriForSilenceTrim(
        context: Context,
        uri: Uri,
        originalDurationMs: Long
    ): SilenceTrimResult =
        SilenceTrimProcessor.processUri(context, uri, originalDurationMs)

    @Synchronized
    fun saveClippingBounds(context: Context, uriString: String, startMs: Long, endMs: Long) =
        ClippingBoundsStore.save(context, uriString, startMs, endMs)

    @Synchronized
    fun getClippingBounds(context: Context, uriString: String): ClippingBounds? =
        ClippingBoundsStore.get(context, uriString)

    @Synchronized
    fun removeClippingBounds(context: Context, uriString: String) =
        ClippingBoundsStore.remove(context, uriString)
}
