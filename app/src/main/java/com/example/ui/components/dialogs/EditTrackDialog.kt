package com.example.ui.components
import com.example.core.model.VideoDisplayMode

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.model.Track
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

/**
 * Cuadro de diálogo modal avanzado para modificar los metadatos de una pista:
 * título, artista, álbum y carátula personalizada desde la galería del usuario.
 *
 * Si el usuario elige una nueva imagen:
 * - Se comprime automáticamente a formato WebP sin pérdida en segundo plano.
 * - Se elimina físicamente el archivo de carátula WebP previo para evitar desperdicio de almacenamiento.
 * - Se actualiza de forma reactiva en la base de datos Room y en los metadatos JSON.
 */
@Composable
fun EditTrackDialog(
    track: Track,
    onDismiss: () -> Unit,
    onConfirm: (
        trackId: Long,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        customArtUri: Uri?,
        removeArtwork: Boolean,
        customVideoUri: Uri?,
        removeVideo: Boolean
    ) -> Unit,
    onConfirmWithLoopOption: ((
        trackId: Long,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        customArtUri: Uri?,
        removeArtwork: Boolean,
        customVideoUri: Uri?,
        removeVideo: Boolean,
        forceLoop: Boolean?
    ) -> Unit)? = null,
    onConfirmWithLoopStyle: ((
        trackId: Long,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        customArtUri: Uri?,
        removeArtwork: Boolean,
        customVideoUri: Uri?,
        removeVideo: Boolean,
        forceLoop: Boolean?,
        loopStyle: com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle
    ) -> Unit)? = null
) {
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist) }
    var album by remember { mutableStateOf(track.album) }

    var selectedCustomArtUri by remember { mutableStateOf<Uri?>(null) }
    var shouldRemoveArtwork by remember { mutableStateOf(false) }

    var selectedCustomVideoUri by remember { mutableStateOf<Uri?>(null) }
    var shouldRemoveVideo by remember { mutableStateOf(false) }
    var selectedLoopMode by remember { mutableStateOf<Boolean?>(null) }
    var selectedLoopStyle by remember {
        mutableStateOf(com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE)
    }

    // Launcher del Android Photo Picker para imágenes (carátula)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedCustomArtUri = uri
            shouldRemoveArtwork = false
        }
    }

    // Launcher del Android Photo/Media Picker para videos de fondo (Canvas o Video Sincronizado)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedCustomVideoUri = uri
            shouldRemoveVideo = false
        }
    }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Editar Canción y Canvas",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Sección de Carátula (Previsualización y controles de galería)
                EditTrackArtworkSection(
                    track = track,
                    title = title,
                    artist = artist,
                    artUri = selectedCustomArtUri,
                    removeArtwork = shouldRemoveArtwork,
                    onPickArtwork = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemoveArtwork = {
                        selectedCustomArtUri = null
                        shouldRemoveArtwork = true
                    }
                )
                // Sección de Video Canvas / Video de Fondo
                EditTrackVideoSection(
                    track = track,
                    videoUri = selectedCustomVideoUri,
                    removeVideo = shouldRemoveVideo,
                    loopMode = selectedLoopMode,
                    onLoopModeChange = { selectedLoopMode = it },
                    loopStyle = selectedLoopStyle,
                    onLoopStyleChange = { selectedLoopStyle = it },
                    onPickVideo = {
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onRemoveVideo = {
                        selectedCustomVideoUri = null
                        shouldRemoveVideo = true
                    }
                )

                Divider(color = CardBorder.copy(alpha = 0.5f), thickness = 0.8.dp)

                // Campos de texto
                EditTrackTextFields(
                    title = title,
                    onTitleChange = { title = it },
                    artist = artist,
                    onArtistChange = { artist = it },
                    album = album,
                    onAlbumChange = { album = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        when {
                            onConfirmWithLoopStyle != null -> {
                                onConfirmWithLoopStyle(
                                    track.id,
                                    title,
                                    artist,
                                    album,
                                    selectedCustomArtUri,
                                    shouldRemoveArtwork,
                                    selectedCustomVideoUri,
                                    shouldRemoveVideo,
                                    selectedLoopMode,
                                    selectedLoopStyle
                                )
                            }
                            onConfirmWithLoopOption != null -> {
                                onConfirmWithLoopOption(
                                    track.id,
                                    title,
                                    artist,
                                    album,
                                    selectedCustomArtUri,
                                    shouldRemoveArtwork,
                                    selectedCustomVideoUri,
                                    shouldRemoveVideo,
                                    selectedLoopMode
                                )
                            }
                            else -> {
                                onConfirm(
                                    track.id,
                                    title,
                                    artist,
                                    album,
                                    selectedCustomArtUri,
                                    shouldRemoveArtwork,
                                    selectedCustomVideoUri,
                                    shouldRemoveVideo
                                )
                            }
                        }
                        onDismiss()
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("edit_track_save_btn")
            ) {
                Text("Guardar Cambios")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("edit_track_cancel_btn")
            ) {
                Text("Cancelar", color = TextSecondary)
            }
        },
        containerColor = SurfaceCard,
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * Sobrecargas de compatibilidad para llamadas previas.
 */
@Composable
fun EditTrackDialog(
    track: Track,
    onDismiss: () -> Unit,
    onConfirm: (trackId: Long, newTitle: String, newArtist: String, newAlbum: String, customArtUri: Uri?, removeArtwork: Boolean) -> Unit
) {
    EditTrackDialog(
        track = track,
        onDismiss = onDismiss,
        onConfirm = { id, title, artist, album, artUri, remArt, _, _ ->
            onConfirm(id, title, artist, album, artUri, remArt)
        }
    )
}

@Composable
fun EditTrackDialog(
    track: Track,
    onDismiss: () -> Unit,
    onConfirm: (trackId: Long, newTitle: String, newArtist: String, newAlbum: String) -> Unit
) {
    EditTrackDialog(
        track = track,
        onDismiss = onDismiss,
        onConfirm = { id, title, artist, album, _, _, _, _ ->
            onConfirm(id, title, artist, album)
        }
    )
}
