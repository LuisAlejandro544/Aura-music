package com.example.viewmodel.delegates


import android.content.Context
import android.graphics.BitmapFactory
import com.example.core.data.importer.FFmpegNativeEngine
import com.example.core.data.importer.OnlineVideoAudioImporter
import com.example.core.data.importer.playlist.ExternalPlaylistImporter
import com.example.core.data.local.dao.PlaylistDao
import com.example.core.data.local.dao.TrackDao
import com.example.core.data.local.entity.PlaylistEntity
import com.example.core.data.local.entity.PlaylistTrackCrossRef
import com.example.core.data.local.entity.TrackEntity
import com.example.core.data.storage.AppStorageManager
import com.example.core.data.streaming.StreamingApiService
import com.example.debug.AuraDebugManager
import com.example.core.model.ExternalPlaylistPreview
import com.example.core.model.ExternalPlaylistSource
import com.example.core.model.ExternalPlaylistTrackItem
import com.example.core.model.PlaylistImportSaveMode
import com.example.core.model.StreamingSearchItem
import com.example.core.model.StreamingSourcePlatform
import com.example.core.playback.AuraDownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Análisis de URL de playlist Spotify/YouTube con preview.
 * Rol arquitectónico: pieza de PlaylistImportCoordinator (fachada pública intacta).
 */
internal class PlaylistImportAnalyzer(
    private val context: android.content.Context,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val st: PlaylistImportState
) {
    fun analyze(urlOverride: String? = null) {
        val targetUrl = (urlOverride ?: st.inputUrl.value).trim()
        if (targetUrl.isBlank()) {
            st.errorMessage.value = "Pega un enlace de Playlist de Spotify o YouTube Music."
            return
        }

        st.analyzeJob?.cancel()
        st.analyzeJob = scope.launch {
            st.isAnalyzing.value = true
            st.errorMessage.value = null
            st.playlistPreview.value = null
            try {
                val result = ExternalPlaylistImporter.fetchPlaylistPreview(context, targetUrl)
                val preview = result.getOrNull()
                if (preview != null && preview.tracks.isNotEmpty()) {
                    st.playlistPreview.value = preview
                    st.customPlaylistName.value = preview.title
                } else {
                    st.errorMessage.value = result.exceptionOrNull()?.localizedMessage
                        ?: "No se encontraron canciones en la Playlist indicada."
                }
            } catch (e: Exception) {
                st.errorMessage.value = e.localizedMessage ?: "Error al analizar la Playlist."
            } finally {
                st.isAnalyzing.value = false
            }
        }
    }
}
