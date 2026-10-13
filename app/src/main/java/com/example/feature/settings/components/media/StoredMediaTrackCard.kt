package com.example.feature.settings.components.media

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.example.core.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

/**
 * Aura Music - Componente Modular de Tarjeta de Canción y Medios Guardados
 *
 * Muestra metadatos de la canción, tamaño de carátula WebP y video Canvas MP4,
 * con botones directos para liberar el espacio de cada medio o guardar el vídeo
 * con su audio completo en la carpeta pública seleccionada por el usuario.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StoredMediaTrackCard(
    track: Track,
    onDeleteArtworkClick: () -> Unit,
    onDeleteVideoClick: () -> Unit,
    onExportVideoClick: (() -> Unit)? = null,
    isVideoExported: Boolean = false,
    isExportingVideo: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hasArt = !track.albumArtPath.isNullOrBlank()
    val hasVid = !track.videoUri.isNullOrBlank()

    val artFileSizeStr = remember(track.albumArtPath) {
        track.albumArtPath?.let { path ->
            try {
                val f = File(path)
                if (f.exists()) StoredMediaFormatUtils.formatBytes(f.length()) else "WebP"
            } catch (_: Exception) { "WebP" }
        } ?: ""
    }

    val videoFileSizeStr = remember(track.videoUri) {
        track.videoUri?.let { uriStr ->
            try {
                val path = if (uriStr.startsWith("file://")) uriStr.removePrefix("file://") else uriStr
                val f = File(path)
                if (f.exists()) StoredMediaFormatUtils.formatBytes(f.length()) else "MP4"
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
                    try {
                        retriever.setDataSource(f.absolutePath)
                        val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                        val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                        val rot = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                        val isHoriz = if (rot == 90 || rot == 270) h > (w * 1.15f) else w > (h * 1.15f)
                        if (isHoriz) "16:9 Panorámico" else "9:16 Vertical"
                    } catch (_: Exception) {
                        null
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }
                } else null
            } catch (_: Exception) { null }
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("stored_media_track_${track.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Fila principal: Carátula, Título y Artista
            StoredMediaTrackHeader(track = track)

            // Gestión de Carátula
            StoredMediaArtworkRow(
                track = track,
                artFileSizeStr = artFileSizeStr,
                onDeleteArtworkClick = onDeleteArtworkClick
            )

            // Gestión de Video Canvas y Exportación a Carpeta del Usuario
            StoredMediaVideoRow(
                track = track,
                videoFileSizeStr = videoFileSizeStr,
                videoAspectTag = videoAspectTag,
                onDeleteVideoClick = onDeleteVideoClick,
                onExportVideoClick = onExportVideoClick,
                isVideoExported = isVideoExported,
                isExportingVideo = isExportingVideo
            )
        }
    }
}
