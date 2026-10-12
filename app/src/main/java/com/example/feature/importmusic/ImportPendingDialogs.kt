package com.example.feature.importmusic

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.data.importer.FFmpegNativeEngine
import com.example.core.data.importer.OnlineVideoAudioImporter
import com.example.core.model.DownloadProgress
import com.example.core.model.Track
import com.example.ui.components.DownloadFromLinkDialog
import com.example.ui.components.DownloadSourceMode
import com.example.ui.components.VideoToMusicDialog
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Diálogos pendientes de importación (archivos, video y descarga web).
 * Rol arquitectónico: agrupa los overlays que interrumpen el flujo de importar.
 */
@Composable
fun ImportPendingDialogs(
    pendingAudioUris: List<Uri>?,
    pendingFolderUri: Uri?,
    trimSilenceSelection: Boolean,
    onTrimSilenceChange: (Boolean) -> Unit,
    onDismissPending: () -> Unit,
    onConfirmPending: () -> Unit,
    selectedVideoForConversion: Uri?,
    onDismissVideo: () -> Unit,
    onImportVideoAsMusic: (title: String, artist: String, album: String, attachAsCanvas: Boolean, forceLoop: Boolean?, trimSilence: Boolean) -> Unit,
    onImportVideoAsMusicWithLoopStyle: (title: String, artist: String, album: String, attachAsCanvas: Boolean, forceLoop: Boolean?, trimSilence: Boolean, loopStyle: FFmpegNativeEngine.CanvasLoopStyle) -> Unit,
    showDownloadFromLinkDialog: Boolean,
    downloadProgress: DownloadProgress,
    downloadDialogMode: DownloadSourceMode,
    onDismissDownload: () -> Unit,
    onDownloadFromLink: (resolvedInfo: OnlineVideoAudioImporter.ResolvedMediaInfo, customTitle: String, customArtist: String, attachAsCanvas: Boolean, trimSilence: Boolean) -> Unit,
    onDownloadFromLinkWithLoopStyle: (resolvedInfo: OnlineVideoAudioImporter.ResolvedMediaInfo, customTitle: String, customArtist: String, attachAsCanvas: Boolean, trimSilence: Boolean, loopStyle: FFmpegNativeEngine.CanvasLoopStyle) -> Unit
) {
        if (pendingAudioUris != null || pendingFolderUri != null) {
            val isFolder = pendingFolderUri != null
            val itemCount = pendingAudioUris?.size ?: 1
            AlertDialog(
                onDismissRequest = {
                    onDismissPending()
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isFolder) "Opciones de Importación de Carpeta" else "Opciones de Importación ($itemCount)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (isFolder) {
                                "Antes de importar las pistas de la carpeta seleccionada, elige si deseas aplicar el recorte inteligente de silencios."
                            } else {
                                "Antes de importar ${if (itemCount == 1) "la canción seleccionada" else "las $itemCount canciones seleccionadas"}, configura el recorte inteligente:"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
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
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Detecta y recorta automáticamente espacios vacíos al principio y al final de cada pista sin perder calidad.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = trimSilenceSelection,
                                    onCheckedChange = { onTrimSilenceChange(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF10B981)
                                    ),
                                    modifier = Modifier.testTag("import_files_trim_silence_switch")
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onConfirmPending()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("confirm_import_files_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Importar Ahora", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            onDismissPending()
                        }
                    ) {
                        Text("Cancelar", color = TextSecondary)
                    }
                },
                containerColor = SurfaceCard,
                shape = RoundedCornerShape(20.dp)
            )
        }

        if (selectedVideoForConversion != null) {
            com.example.ui.components.VideoToMusicDialog(
                videoUri = selectedVideoForConversion!!,
                onDismiss = { onDismissVideo() },
                onConfirm = { title, artist, album, attachAsCanvas, forceLoop, trimSilence ->
                    onImportVideoAsMusic(title, artist, album, attachAsCanvas, forceLoop, trimSilence)
                },
                onConfirmWithLoopStyle = { title, artist, album, attachAsCanvas, forceLoop, trimSilence, loopStyle ->
                    onImportVideoAsMusicWithLoopStyle(title, artist, album, attachAsCanvas, forceLoop, trimSilence, loopStyle)
                }
            )
        }

        if (showDownloadFromLinkDialog) {
            com.example.ui.components.DownloadFromLinkDialog(
                initialMode = downloadDialogMode,
                downloadProgress = downloadProgress,
                onDismiss = { onDismissDownload() },
                onConfirmDownload = { info, title, artist, attachCanvas, trimSilence ->
                    onDownloadFromLink(info, title, artist, attachCanvas, trimSilence)
                },
                onConfirmDownloadWithLoopStyle = { info, title, artist, attachCanvas, trimSilence, loopStyle ->
                    onDownloadFromLinkWithLoopStyle(info, title, artist, attachCanvas, trimSilence, loopStyle)
                }
            )
}
}
