package com.example.ui.screens.settings.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.storage.UserPublicMediaExporter
import com.example.model.Track
import com.example.ui.screens.settings.components.media.StoredMediaDeleteDialogs
import com.example.ui.screens.settings.components.media.StoredMediaSummaryHeader
import com.example.ui.screens.settings.components.media.StoredMediaTrackCard
import com.example.ui.screens.settings.components.media.UserPublicFolderCard
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Apartado Modular de Transparencia y Gestión de Almacenamiento Multimedia (< 500 líneas).
 *
 * Delega en componentes modulares especializados:
 * - [StoredMediaSummaryHeader]: Resumen con métricas totales y desglosadas (WebP vs MP4).
 * - [UserPublicFolderCard]: Selección de carpeta pública/personalizada del usuario y visualización de ruta exacta.
 * - [StoredMediaTrackCard]: Tarjeta individual con marquesina, botones de liberación y exportación de vídeo con audio.
 * - [StoredMediaDeleteDialogs]: Diálogos de confirmación previa a la purga de archivos.
 */
fun LazyListScope.storedMediaSettingsTab(
    allTracks: List<Track>,
    onDeleteTrackArtwork: (Track) -> Unit,
    onDeleteTrackVideo: (Track) -> Unit
) {
    // 1. Cabecera con Métricas de Almacenamiento
    item {
        StoredMediaSummaryHeader(allTracks = allTracks)
    }

    // 2. Carpeta Personalizada del Usuario, Filtros, Búsqueda y Lista de Pistas con Medios
    item {
        StoredMediaFilterAndList(
            allTracks = allTracks,
            onDeleteTrackArtwork = onDeleteTrackArtwork,
            onDeleteTrackVideo = onDeleteTrackVideo
        )
    }
}

@Composable
private fun StoredMediaFilterAndList(
    allTracks: List<Track>,
    onDeleteTrackArtwork: (Track) -> Unit,
    onDeleteTrackVideo: (Track) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedFilter by remember { mutableIntStateOf(0) } // 0: Todos, 1: Solo Carátulas, 2: Solo Videos
    var searchQuery by remember { mutableStateOf("") }
    var trackToDeleteArtwork by remember { mutableStateOf<Track?>(null) }
    var trackToDeleteVideo by remember { mutableStateOf<Track?>(null) }

    var exportFolderState by remember {
        mutableStateOf(UserPublicMediaExporter.loadCurrentState(context))
    }
    var pendingTrackToExportAfterFolderSelect by remember { mutableStateOf<Track?>(null) }
    var pendingExportAllAfterFolderSelect by remember { mutableStateOf(false) }

    val tracksWithVideos = remember(allTracks) {
        allTracks.filter { !it.videoUri.isNullOrBlank() }
    }

    fun triggerSingleVideoExport(track: Track) {
        if (exportFolderState.exportingTrackIds.contains(track.id)) return
        coroutineScope.launch {
            exportFolderState = exportFolderState.copy(
                exportingTrackIds = exportFolderState.exportingTrackIds + track.id,
                statusMessage = null
            )
            val result = UserPublicMediaExporter.exportTrackVideoWithAudioToUserFolder(context, track)
            val refreshed = UserPublicMediaExporter.loadCurrentState(context)
            exportFolderState = refreshed.copy(
                exportingTrackIds = exportFolderState.exportingTrackIds - track.id,
                isExportingAll = exportFolderState.isExportingAll,
                statusMessage = result.fold(
                    onSuccess = { path -> "Vídeo con audio guardado en: $path" },
                    onFailure = { err -> "No se pudo guardar: ${err.message}" }
                )
            )
        }
    }

    fun triggerExportAllVideos(videoTracks: List<Track>) {
        if (videoTracks.isEmpty() || exportFolderState.isExportingAll) return
        coroutineScope.launch {
            exportFolderState = exportFolderState.copy(
                isExportingAll = true,
                statusMessage = "Uniendo y guardando ${videoTracks.size} vídeo(s) con su audio..."
            )
            var successCount = 0
            for (track in videoTracks) {
                exportFolderState = exportFolderState.copy(
                    exportingTrackIds = exportFolderState.exportingTrackIds + track.id
                )
                val res = UserPublicMediaExporter.exportTrackVideoWithAudioToUserFolder(context, track)
                if (res.isSuccess) successCount++
                exportFolderState = exportFolderState.copy(
                    exportingTrackIds = exportFolderState.exportingTrackIds - track.id
                )
            }
            val refreshed = UserPublicMediaExporter.loadCurrentState(context)
            exportFolderState = refreshed.copy(
                exportingTrackIds = emptySet(),
                isExportingAll = false,
                statusMessage = if (successCount > 0) {
                    "¡Listo! Se guardaron $successCount vídeo(s) con audio en ${refreshed.readablePath}"
                } else {
                    "No se pudieron exportar los vídeos seleccionados."
                }
            )
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            coroutineScope.launch {
                val updated = UserPublicMediaExporter.saveSelectedFolderUri(context, treeUri)
                exportFolderState = updated.copy(
                    statusMessage = "Carpeta configurada: ${updated.readablePath}"
                )
                val pendingSingle = pendingTrackToExportAfterFolderSelect
                val pendingAll = pendingExportAllAfterFolderSelect
                pendingTrackToExportAfterFolderSelect = null
                pendingExportAllAfterFolderSelect = false

                if (pendingSingle != null) {
                    triggerSingleVideoExport(pendingSingle)
                } else if (pendingAll) {
                    triggerExportAllVideos(tracksWithVideos)
                }
            }
        } else {
            pendingTrackToExportAfterFolderSelect = null
            pendingExportAllAfterFolderSelect = false
        }
    }

    // Filtrar canciones que tengan al menos carátula personalizada o video canvas
    val tracksWithMedia = remember(allTracks, selectedFilter, searchQuery) {
        allTracks.filter { track ->
            val hasArt = !track.albumArtPath.isNullOrBlank()
            val hasVid = !track.videoUri.isNullOrBlank()

            val matchesFilter = when (selectedFilter) {
                1 -> hasArt
                2 -> hasVid
                else -> hasArt || hasVid
            }

            val matchesSearch = if (searchQuery.isBlank()) true else {
                track.title.contains(searchQuery, ignoreCase = true) ||
                        track.artist.contains(searchQuery, ignoreCase = true) ||
                        track.album.contains(searchQuery, ignoreCase = true)
            }

            matchesFilter && matchesSearch
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(6.dp))

        // Tarjeta de Carpeta Personalizada del Usuario (Muestra ruta exacta y permite elegir carpeta o exportar vídeos)
        UserPublicFolderCard(
            exportState = exportFolderState,
            totalVideosCount = tracksWithVideos.size,
            onSelectFolderClick = {
                pendingTrackToExportAfterFolderSelect = null
                pendingExportAllAfterFolderSelect = false
                folderPickerLauncher.launch(null)
            },
            onExportAllVideosClick = {
                if (!exportFolderState.isConfigured) {
                    pendingExportAllAfterFolderSelect = true
                    folderPickerLauncher.launch(null)
                } else {
                    triggerExportAllVideos(tracksWithVideos)
                }
            },
            onDismissStatusMessage = {
                exportFolderState = exportFolderState.copy(statusMessage = null)
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Barra de búsqueda rápida
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar canción, artista o álbum...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = CardBorder,
                focusedContainerColor = SurfaceCard.copy(alpha = 0.6f),
                unfocusedContainerColor = SurfaceCard.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag("stored_media_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Chips de filtro
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == 0,
                onClick = { selectedFilter = 0 },
                label = { Text("Todos (${allTracks.count { !it.albumArtPath.isNullOrBlank() || !it.videoUri.isNullOrBlank() }})") },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.Black
                ),
                modifier = Modifier.testTag("filter_media_all")
            )
            FilterChip(
                selected = selectedFilter == 1,
                onClick = { selectedFilter = 1 },
                label = { Text("Carátulas (${allTracks.count { !it.albumArtPath.isNullOrBlank() }})") },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.Black
                ),
                modifier = Modifier.testTag("filter_media_art")
            )
            FilterChip(
                selected = selectedFilter == 2,
                onClick = { selectedFilter = 2 },
                label = { Text("Videos (${tracksWithVideos.size})") },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.Black
                ),
                modifier = Modifier.testTag("filter_media_video")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (tracksWithMedia.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceCard.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.PermMedia,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No se encontraron coincidencias" else "No hay archivos multimedia guardados",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Las carátulas descargadas o asignadas y los videos de fondo aparecerán aquí para control total.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        } else {
            tracksWithMedia.forEach { track ->
                StoredMediaTrackCard(
                    track = track,
                    onDeleteArtworkClick = { trackToDeleteArtwork = track },
                    onDeleteVideoClick = { trackToDeleteVideo = track },
                    onExportVideoClick = if (!track.videoUri.isNullOrBlank()) {
                        {
                            if (!exportFolderState.isConfigured) {
                                pendingTrackToExportAfterFolderSelect = track
                                folderPickerLauncher.launch(null)
                            } else {
                                triggerSingleVideoExport(track)
                            }
                        }
                    } else null,
                    isVideoExported = exportFolderState.exportedTrackIds.contains(track.id),
                    isExportingVideo = exportFolderState.exportingTrackIds.contains(track.id)
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    // Diálogos modulares de confirmación
    StoredMediaDeleteDialogs(
        trackToDeleteArtwork = trackToDeleteArtwork,
        onDismissArtworkDialog = { trackToDeleteArtwork = null },
        onConfirmDeleteArtwork = { track ->
            onDeleteTrackArtwork(track)
            trackToDeleteArtwork = null
        },
        trackToDeleteVideo = trackToDeleteVideo,
        onDismissVideoDialog = { trackToDeleteVideo = null },
        onConfirmDeleteVideo = { track ->
            onDeleteTrackVideo(track)
            trackToDeleteVideo = null
        }
    )
}
