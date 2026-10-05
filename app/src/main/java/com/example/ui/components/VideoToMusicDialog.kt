package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.importer.VideoAudioExtractor
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Diálogo interactivo para previsualizar y convertir un video de la galería en canción.
 * Permite ajustar título, artista, álbum, y elegir si vincular el video como Video Canvas de fondo.
 */
@Composable
fun VideoToMusicDialog(
    videoUri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        artist: String,
        album: String,
        attachAsCanvas: Boolean,
        forceLoop: Boolean?,
        trimSilence: Boolean
    ) -> Unit,
    onConfirmWithLoopStyle: ((
        title: String,
        artist: String,
        album: String,
        attachAsCanvas: Boolean,
        forceLoop: Boolean?,
        trimSilence: Boolean,
        loopStyle: com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle
    ) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isLoadingPreview by remember { mutableStateOf(true) }
    var suggestedTitle by remember { mutableStateOf("") }
    var suggestedArtist by remember { mutableStateOf("Video Import") }
    var suggestedAlbum by remember { mutableStateOf("Videos de Galería") }
    var videoDurationMs by remember { mutableStateOf(0L) }
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var attachAsCanvas by remember { mutableStateOf(true) }
    var selectedLoopMode by remember { mutableStateOf<Boolean?>(null) } // null = Auto (<= 20s), true = Loop, false = Sync
    var selectedLoopStyle by remember {
        mutableStateOf(com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE)
    }
    var trimSilence by remember { mutableStateOf(true) }

    LaunchedEffect(videoUri) {
        coroutineScope.launch {
            isLoadingPreview = true
            val preview = VideoAudioExtractor.inspectVideo(context, videoUri)
            suggestedTitle = preview.suggestedTitle
            suggestedArtist = preview.suggestedArtist
            videoDurationMs = preview.durationMs
            thumbnailBitmap = preview.thumbnailBitmap
            selectedLoopMode = if (preview.isLikelyLoop) null else null
            isLoadingPreview = false
        }
    }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MovieFilter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Video a Música",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            }
        },
        text = {
            if (isLoadingPreview) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Inspeccionando video y fotograma...",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Vista previa del fotograma que será la carátula
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (thumbnailBitmap != null) {
                                    Image(
                                        bitmap = thumbnailBitmap!!.asImageBitmap(),
                                        contentDescription = "Carátula extraída del video",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicVideo,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "CARÁTULA AUTOMÁTICA",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Duración: ${formatDuration(videoDurationMs)}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = "El fotograma se guardará en WebP como portada oficial.",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                                )
                            }
                        }
                    }

                    // Campos de texto para metadatos
                    OutlinedTextField(
                        value = suggestedTitle,
                        onValueChange = { suggestedTitle = it },
                        label = { Text("Título de la canción") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_title_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = suggestedArtist,
                        onValueChange = { suggestedArtist = it },
                        label = { Text("Artista / Intérprete") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_artist_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = suggestedAlbum,
                        onValueChange = { suggestedAlbum = it },
                        label = { Text("Álbum o Colección") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_album_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Opción para vincular como Video Canvas
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Vincular Video Canvas",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                    Text(
                                        text = "Reproduce el video de fondo en Now Playing sincronizado.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                    )
                                }
                                Switch(
                                    checked = attachAsCanvas,
                                    onCheckedChange = { attachAsCanvas = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("video_attach_canvas_switch")
                                )
                            }

                            if (attachAsCanvas) {
                                Text(
                                    text = "Modo de Video Canvas:",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedLoopMode == null,
                                        onClick = { selectedLoopMode = null },
                                        label = { Text("Auto (≤20s)", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    FilterChip(
                                        selected = selectedLoopMode == true,
                                        onClick = { selectedLoopMode = true },
                                        label = { Text("Loop", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    FilterChip(
                                        selected = selectedLoopMode == false,
                                        onClick = { selectedLoopMode = false },
                                        label = { Text("Sincronizado", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }

                                if (selectedLoopMode != false) {
                                    Text(
                                        text = "Efecto de bucle FFmpeg (para Loops ≤20s):",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = selectedLoopStyle == com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE,
                                            onClick = {
                                                selectedLoopStyle = com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE
                                            },
                                            label = { Text("✨ Crossfade (xfade)", style = MaterialTheme.typography.labelSmall) }
                                        )
                                        FilterChip(
                                            selected = selectedLoopStyle == com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle.BOOMERANG,
                                            onClick = {
                                                selectedLoopStyle = com.example.data.importer.FFmpegNativeEngine.CanvasLoopStyle.BOOMERANG
                                                selectedLoopMode = true
                                            },
                                            label = { Text("🪃 Boomerang (Ping-Pong)", style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))

                            // Opción de eliminación inteligente de silencios al inicio y final
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Eliminar silencios al inicio y final",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                    Text(
                                        text = "Recorta automáticamente espacios en silencio antes y después de la canción.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                    )
                                }
                                Switch(
                                    checked = trimSilence,
                                    onCheckedChange = { trimSilence = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF10B981)
                                    ),
                                    modifier = Modifier.testTag("video_trim_silence_switch")
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (suggestedTitle.isNotBlank()) {
                        if (onConfirmWithLoopStyle != null) {
                            onConfirmWithLoopStyle(
                                suggestedTitle,
                                suggestedArtist,
                                suggestedAlbum,
                                attachAsCanvas,
                                selectedLoopMode,
                                trimSilence,
                                selectedLoopStyle
                            )
                        } else {
                            onConfirm(
                                suggestedTitle,
                                suggestedArtist,
                                suggestedAlbum,
                                attachAsCanvas,
                                selectedLoopMode,
                                trimSilence
                            )
                        }
                        onDismiss()
                    }
                },
                enabled = !isLoadingPreview && suggestedTitle.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("video_convert_confirm_btn")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Convertir a Música")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("video_convert_cancel_btn")
            ) {
                Text("Cancelar", color = TextSecondary)
            }
        },
        containerColor = SurfaceCard,
        shape = RoundedCornerShape(20.dp)
    )
}

private fun formatDuration(millis: Long): String {
    val totalSec = millis / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(java.util.Locale.US, "%02d:%02d", min, sec)
}
