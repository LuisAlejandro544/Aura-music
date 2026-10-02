package com.example.ui.components

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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.example.model.Track
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
    onConfirm: (trackId: Long, newTitle: String, newArtist: String, newAlbum: String, customArtUri: Uri?, removeArtwork: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist) }
    var album by remember { mutableStateOf(track.album) }

    var selectedCustomArtUri by remember { mutableStateOf<Uri?>(null) }
    var shouldRemoveArtwork by remember { mutableStateOf(false) }

    // Launcher del Android Photo Picker (cero permisos invasivos, moderno y seguro)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedCustomArtUri = uri
            shouldRemoveArtwork = false
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
                    text = "Editar Canción",
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
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        // 1. Imagen recién seleccionada de la galería
                        selectedCustomArtUri != null -> {
                            AsyncImage(
                                model = selectedCustomArtUri,
                                contentDescription = "Nueva carátula seleccionada",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // 2. Se solicitó quitar la carátula -> mostrar arte procedural
                        shouldRemoveArtwork -> {
                            ProceduralArtwork(
                                title = title.ifBlank { "Aura" },
                                artist = artist.ifBlank { "Music" },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // 3. Carátula existente de la pista si existe
                        !track.albumArtPath.isNullOrEmpty() && File(track.albumArtPath).exists() -> {
                            AsyncImage(
                                model = File(track.albumArtPath),
                                contentDescription = "Carátula actual",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // 4. Arte procedural predeterminado
                        else -> {
                            ProceduralArtwork(
                                title = track.title,
                                artist = track.artist,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Overlay con botón de cámara / galería para mayor claridad táctil
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Seleccionar de galería",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Botones para gestionar la carátula
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("change_artwork_btn")
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Galería", style = MaterialTheme.typography.labelMedium)
                    }

                    val hasArtwork = (!track.albumArtPath.isNullOrEmpty() || selectedCustomArtUri != null) && !shouldRemoveArtwork
                    if (hasArtwork) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                selectedCustomArtUri = null
                                shouldRemoveArtwork = true
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("remove_artwork_btn")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Procedural", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Text(
                    text = if (selectedCustomArtUri != null) {
                        "✨ Nueva imagen seleccionada. Al guardar, se convertirá a WebP y se reemplazará la anterior."
                    } else if (shouldRemoveArtwork) {
                        "🎨 Se eliminará la carátula personalizada y se generará arte procedural dinámico."
                    } else {
                        "Toca la carátula para elegir una foto personalizada desde tu galería."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Divider(color = CardBorder.copy(alpha = 0.5f), thickness = 0.8.dp)

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título de la canción") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_track_title_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artista / Intérprete") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_track_artist_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Álbum o Colección") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_track_album_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(
                            track.id,
                            title,
                            artist,
                            album,
                            selectedCustomArtUri,
                            shouldRemoveArtwork
                        )
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
 * Sobrecarga de compatibilidad para llamadas previas con 4 argumentos.
 */
@Composable
fun EditTrackDialog(
    track: Track,
    onDismiss: () -> Unit,
    onConfirm: (trackId: Long, newTitle: String, newArtist: String, newAlbum: String) -> Unit
) {
    EditTrackDialog(
        track = track,
        onDismiss = onDismiss,
        onConfirm = { id, title, artist, album, _, _ ->
            onConfirm(id, title, artist, album)
        }
    )
}
