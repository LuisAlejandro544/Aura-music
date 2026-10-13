package com.example.core.data.importer

import android.content.Context
import android.net.Uri
import com.example.core.data.storage.AppStorageManager
import com.example.core.model.LyricLine
import com.example.core.model.LyricSearchResult
import com.example.core.model.LyricsState
import com.example.core.model.Track

/**
 * Gestor y analizador de Letras Sincronizadas (.LRC) estilo Karaoke.
 *
 * Responsabilidades:
 * 1. Parser de archivos y formatos estándar .LRC con marcas de tiempo [mm:ss.xx].
 * 2. Carga y persistencia local en la carpeta estructurada `lyrics/` de Aura Music.
 * 3. Descarga automática 100% gratuita y sin API key desde el servicio público LRCLIB.
 * 4. Edición y guardado de letras manuales por el usuario.
 *
 * Rol arquitectónico: fachada pública intacta que delega en 7 submódulos internos.
 */
object LyricsManager {

    fun parseLrc(content: String): List<LyricLine> =
        LyricsTextParsing.parse(content)

    fun loadLocalLyrics(track: Track, storageManager: AppStorageManager): LyricsState? =
        LyricsLocalStore.load(track, storageManager)

    fun saveLyrics(track: Track, storageManager: AppStorageManager, lrcContent: String): LyricsState =
        LyricsLocalStore.save(track, storageManager, lrcContent)

    suspend fun fetchLyricsOnline(
        track: Track,
        storageManager: AppStorageManager
    ): Result<LyricsState> =
        LyricsOnlineFetch.fetch(track, storageManager)

    suspend fun searchLyricsOptions(
        trackTitle: String,
        artistName: String = "",
        durationSec: Long = 0L
    ): List<LyricSearchResult> =
        LyricsSearchOptions.search(trackTitle, artistName, durationSec)

    fun applySearchResult(
        track: Track,
        storageManager: AppStorageManager,
        result: LyricSearchResult
    ): LyricsState =
        LyricsSearchOptions.apply(track, storageManager, result)

    suspend fun importLyricsFromUri(
        context: Context,
        track: Track,
        uri: Uri,
        storageManager: AppStorageManager
    ): LyricsState? =
        LyricsUriImport.importFromUri(context, track, uri, storageManager)

    suspend fun autoDetectAndAssociateLyrics(
        context: Context,
        track: Track,
        storageManager: AppStorageManager
    ): LyricsState? =
        LyricsAutoDetect.detect(context, track, storageManager)
}
