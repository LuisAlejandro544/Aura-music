package com.example.ui.screens.nowplaying.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Track
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Diálogo modal con los detalles técnicos del archivo de audio (códec, tasa de muestreo, origen, Video Canvas).
 */
@Composable
fun AudioSpecsDialog(
    currentTrack: Track,
    canEdit: Boolean,
    onOpenEditDialog: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Detalles del Archivo de Audio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioDetailItem("Título", currentTrack.title)
                AudioDetailItem("Artista", currentTrack.artist)
                AudioDetailItem("Álbum", currentTrack.album)
                AudioDetailItem("Formato", currentTrack.formatBadge())
                AudioDetailItem("Tipo MIME", currentTrack.mimeType)
                if (currentTrack.fileSizeFormatted.isNotBlank()) {
                    AudioDetailItem("Tamaño", currentTrack.fileSizeFormatted)
                }
                AudioDetailItem(
                    "Salida Hardware",
                    if (com.example.playback.NativeAudioEngine.isBitPerfectMode()) {
                        "Bit-Perfect Direct 1:1 (DAC Bypass)"
                    } else {
                        "AAudio Low-Latency (${String.format("%.1f", com.example.playback.NativeAudioEngine.getEstimatedLatencyMs())} ms)"
                    }
                )
                AudioDetailItem(
                    "Muestreo / Profundidad",
                    "${com.example.playback.NativeAudioEngine.getActiveSampleRate() / 1000.0} kHz / ${com.example.playback.NativeAudioEngine.getActiveBitDepth()}-bit"
                )
                AudioDetailItem("Origen", if (currentTrack.folderName.isNotBlank()) currentTrack.folderName else "Almacenamiento Local")
                if (!currentTrack.videoUri.isNullOrEmpty()) {
                    AudioDetailItem("Video Canvas", if (currentTrack.isVideoLoop) "Loop Continuo (≤ 10s)" else "Sincronizado con Audio (> 10s)")
                }
            }
        },
        confirmButton = {
            Row {
                if (canEdit) {
                    TextButton(onClick = onOpenEditDialog) {
                        Text("Editar Información", color = MaterialTheme.colorScheme.primary)
                    }
                }
                TextButton(onClick = onDismissRequest) {
                    Text("Cerrar")
                }
            }
        }
    )
}

@Composable
private fun AudioDetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "$label:", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        Text(text = value, color = TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    }
}
