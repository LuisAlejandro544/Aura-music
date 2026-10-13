package com.example.ui.components
import com.example.core.model.VideoDisplayMode

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
import com.example.core.data.importer.VideoAudioExtractor
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
        loopStyle: com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle
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
        mutableStateOf(com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE)
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
                    VideoPreviewSection(
                        thumbnailBitmap = thumbnailBitmap,
                        videoDurationMs = videoDurationMs
                    )

                    // Campos de texto para metadatos
                    VideoMetadataFields(
                        title = suggestedTitle,
                        onTitleChange = { suggestedTitle = it },
                        artist = suggestedArtist,
                        onArtistChange = { suggestedArtist = it },
                        album = suggestedAlbum,
                        onAlbumChange = { suggestedAlbum = it }
                    )

                    // Opción para vincular como Video Canvas
                    VideoCanvasOptions(
                        attachAsCanvas = attachAsCanvas,
                        onAttachChange = { attachAsCanvas = it },
                        loopMode = selectedLoopMode,
                        onLoopModeChange = { selectedLoopMode = it },
                        loopStyle = selectedLoopStyle,
                        onLoopStyleChange = { selectedLoopStyle = it },
                        trimSilence = trimSilence,
                        onTrimChange = { trimSilence = it }
                    )
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
