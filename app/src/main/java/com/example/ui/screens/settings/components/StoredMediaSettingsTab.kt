package com.example.ui.screens.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

/**
 * Apartado de Máxima Transparencia y Gestión de Almacenamiento Multimedia.
 *
 * Permite a los usuarios inspeccionar minuciosamente todos los archivos de carátulas (.webp)
 * y videos de fondo / Video Canvas (.mp4) almacenados en el dispositivo, saber con exactitud
 * a qué canción pertenecen, cuánto espacio consumen, y borrarlos de forma individual con un toque.
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.storedMediaSettingsTab(
    allTracks: List<Track>,
    onDeleteTrackArtwork: (Track) -> Unit,
    onDeleteTrackVideo: (Track) -> Unit
) {
    // 1. Cabecera con Métricas de Almacenamiento y Transparencia
    item {
        val tracksWithArt = allTracks.filter { !it.albumArtPath.isNullOrBlank() }
        val tracksWithVideo = allTracks.filter { !it.videoUri.isNullOrBlank() }

        // Calcular tamaños reales de archivos en disco
        val totalArtBytes = remember(tracksWithArt) {
            tracksWithArt.sumOf { track ->
                track.albumArtPath?.let { path ->
                    try {
                        val f = File(path)
                        if (f.exists()) f.length() else 0L
                    } catch (_: Exception) { 0L }
                } ?: 0L
            }
        }
        val totalVideoBytes = remember(tracksWithVideo) {
            tracksWithVideo.sumOf { track ->
                track.videoUri?.let { uriStr ->
                    try {
                        val path = if (uriStr.startsWith("file://")) uriStr.removePrefix("file://") else uriStr
                        val f = File(path)
                        if (f.exists()) f.length() else 0L
                    } catch (_: Exception) { 0L }
                } ?: 0L
            }
        }

        val totalMediaBytes = totalArtBytes + totalVideoBytes
        val formattedTotal = formatBytes(totalMediaBytes)
        val formattedArt = formatBytes(totalArtBytes)
        val formattedVideo = formatBytes(totalVideoBytes)

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.2.dp, CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("stored_media_summary_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FolderSpecial,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Transparencia de Almacenamiento",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Espacio ocupado por carátulas y videos",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = formattedTotal,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Métricas desglosadas: Carátulas vs Videos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Carátulas WebP
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.04f),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Carátulas WebP",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${tracksWithArt.size} archivos",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = formattedArt,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }

                    // Videos Canvas MP4
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.04f),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Videos Canvas",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${tracksWithVideo.size} archivos",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = formattedVideo,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Organizado en: Android/data/.../files/ (images/ y videos/)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                )
            }
        }
    }

    // 2. Filtros y Búsqueda
    item {
        StoredMediaFilterAndList(
            allTracks = allTracks,
            onDeleteTrackArtwork = onDeleteTrackArtwork,
            onDeleteTrackVideo = onDeleteTrackVideo
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoredMediaFilterAndList(
    allTracks: List<Track>,
    onDeleteTrackArtwork: (Track) -> Unit,
    onDeleteTrackVideo: (Track) -> Unit
) {
    var selectedFilter by remember { mutableIntStateOf(0) } // 0: Todos, 1: Solo Carátulas, 2: Solo Videos
    var searchQuery by remember { mutableStateOf("") }
    var trackToDeleteArtwork by remember { mutableStateOf<Track?>(null) }
    var trackToDeleteVideo by remember { mutableStateOf<Track?>(null) }

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
                label = { Text("Videos (${allTracks.count { !it.videoUri.isNullOrBlank() }})") },
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
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    )
                }
            }
        } else {
            tracksWithMedia.forEach { track ->
                StoredMediaTrackCard(
                    track = track,
                    onDeleteArtworkClick = { trackToDeleteArtwork = track },
                    onDeleteVideoClick = { trackToDeleteVideo = track }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    // Diálogo de confirmación para eliminar carátula
    trackToDeleteArtwork?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToDeleteArtwork = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(text = "Eliminar carátula guardada", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "¿Deseas eliminar permanentemente el archivo WebP de carátula de \"${track.title}\"? El espacio se liberará de inmediato y la canción utilizará carátula procedural dinámica."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTrackArtwork(track)
                        trackToDeleteArtwork = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar Carátula", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { trackToDeleteArtwork = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de confirmación para eliminar video
    trackToDeleteVideo?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToDeleteVideo = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.VideocamOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(text = "Eliminar Video Canvas guardado", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "¿Deseas eliminar permanentemente el video MP4 de fondo de \"${track.title}\"? Se liberará el espacio de almacenamiento y Now Playing mostrará solo la carátula."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTrackVideo(track)
                        trackToDeleteVideo = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar Video", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { trackToDeleteVideo = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/**
 * Tarjeta individual para mostrar una canción y sus archivos asociados de carátula y video.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoredMediaTrackCard(
    track: Track,
    onDeleteArtworkClick: () -> Unit,
    onDeleteVideoClick: () -> Unit
) {
    val hasArt = !track.albumArtPath.isNullOrBlank()
    val hasVid = !track.videoUri.isNullOrBlank()

    // Medidas de archivo
    val artFileSizeStr = remember(track.albumArtPath) {
        track.albumArtPath?.let { path ->
            try {
                val f = File(path)
                if (f.exists()) formatBytes(f.length()) else "WebP"
            } catch (_: Exception) { "WebP" }
        } ?: ""
    }

    val videoFileSizeStr = remember(track.videoUri) {
        track.videoUri?.let { uriStr ->
            try {
                val path = if (uriStr.startsWith("file://")) uriStr.removePrefix("file://") else uriStr
                val f = File(path)
                if (f.exists()) formatBytes(f.length()) else "MP4"
            } catch (_: Exception) { "MP4" }
        } ?: ""
    }

    val videoAspectTag = remember(track.videoUri) {
        track.videoUri?.let { uriStr ->
            try {
                val path = if (uriStr.startsWith("file://")) uriStr.removePrefix("file://") else uriStr
                val f = File(path)
                if (f.exists()) {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(f.absolutePath)
                    val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val rot = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    retriever.release()
                    val isHoriz = if (rot == 90 || rot == 270) h > (w * 1.15f) else w > (h * 1.15f)
                    if (isHoriz) "16:9 Panorámico" else "9:16 Vertical"
                } else null
            } catch (_: Exception) { null }
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stored_media_track_${track.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Fila principal: Carátula, Título y Artista
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ArtworkImage(
                    track = track,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    cornerRadius = 10.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds()
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clipToBounds()
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                repeatDelayMillis = 1800,
                                initialDelayMillis = 1000,
                                velocity = 28.dp
                            )
                    )
                    Text(
                        text = "${track.artist} • ${track.album}",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = CardBorder.copy(alpha = 0.5f), thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Fila de Gestión de Carátula (si tiene guardada)
            if (hasArt) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Carátula WebP",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Espacio: $artFileSizeStr",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    // Botón para borrar la carátula
                    OutlinedButton(
                        onClick = onDeleteArtworkClick,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("delete_artwork_${track.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Borrar carátula",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
            }

            // Fila de Gestión de Video Canvas (si tiene guardado)
            if (hasVid) {
                if (hasArt) Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Video Canvas",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = "• $videoFileSizeStr",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.secondary
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (track.isVideoLoop) "Loop 480p" else "Sincronizado 480p",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        ),
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                                if (videoAspectTag != null) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = videoAspectTag,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            ),
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Botón para borrar el video
                    OutlinedButton(
                        onClick = onDeleteVideoClick,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("delete_video_${track.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Borrar video",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Formatea bytes en cadena legible (KB / MB).
 */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format("%.1f MB", mb)
    } else {
        String.format("%.0f KB", kb)
    }
}
