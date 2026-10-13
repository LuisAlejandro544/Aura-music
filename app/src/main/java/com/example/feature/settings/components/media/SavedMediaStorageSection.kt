package com.example.feature.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

/**
 * Pestaña de Transparencia de Almacenamiento y Gestión Multimedia.
 *
 * Permite al usuario auditar con total transparencia todos los archivos de carátulas (.webp)
 * y videos (.mp4) almacenados en el teléfono, conociendo a qué canción pertenecen,
 * su tamaño exacto en disco y brindando opciones individuales para eliminar carátulas o videos
 * liberando espacio al instante.
 */

/**
 * Sección de resumen y listado de medios vinculados a canciones.
 * Rol arquitectónico: orquesta resumen, encabezado, vacío y tarjetas.
 */
@Composable
fun LazyListScope.savedMediaStorageSection(
    tracks: List<Track>,
    onDeleteArtwork: (Track) -> Unit,
    onDeleteVideo: (Track) -> Unit
) {
    val stats = remember(tracks) { computeStoredMediaStats(tracks) }
    val tracksWithMedia = stats.tracks

    // 1. Tarjeta Resumen de Almacenamiento
    item {
        StoredMediaSummaryCard(stats = stats)
    }
    // 2. Encabezado de la lista
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Archivos Vinculados a Canciones (${tracksWithMedia.size})",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    // 3. Estado Vacío
    if (tracksWithMedia.isEmpty()) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Almacenamiento Despejado",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No hay carátulas personalizadas ni videos Canvas guardados en disco. Todo se genera proceduralmente sin ocupar espacio.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    } else {
        // 4. Elementos individuales de canciones con medios vinculados
        items(tracksWithMedia, key = { it.id }) { track ->
            SavedMediaTrackCard(
                track = track,
                onDeleteArtwork = onDeleteArtwork,
                onDeleteVideo = onDeleteVideo
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

