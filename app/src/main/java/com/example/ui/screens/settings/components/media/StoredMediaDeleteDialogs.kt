package com.example.ui.screens.settings.components.media

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Track

/**
 * Aura Music - Diálogos Modulares de Confirmación para Eliminación de Medios
 *
 * Muestra diálogos de alerta accesibles y claros antes de purgar permanentemente
 * carátulas WebP o videos MP4 del almacenamiento local.
 */
@Composable
fun StoredMediaDeleteDialogs(
    trackToDeleteArtwork: Track?,
    onDismissArtworkDialog: () -> Unit,
    onConfirmDeleteArtwork: (Track) -> Unit,
    trackToDeleteVideo: Track?,
    onDismissVideoDialog: () -> Unit,
    onConfirmDeleteVideo: (Track) -> Unit
) {
    // Diálogo de confirmación para eliminar carátula
    trackToDeleteArtwork?.let { track ->
        AlertDialog(
            onDismissRequest = onDismissArtworkDialog,
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
                    onClick = { onConfirmDeleteArtwork(track) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar Carátula", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissArtworkDialog) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de confirmación para eliminar video
    trackToDeleteVideo?.let { track ->
        AlertDialog(
            onDismissRequest = onDismissVideoDialog,
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
                    onClick = { onConfirmDeleteVideo(track) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar Video", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissVideoDialog) {
                    Text("Cancelar")
                }
            }
        )
    }
}
