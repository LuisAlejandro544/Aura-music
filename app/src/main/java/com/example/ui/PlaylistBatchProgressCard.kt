package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Tarjeta flotante del lote offline 1-en-1 (Fase B).
 *
 * Rol arquitectónico: progreso global visible aunque el diálogo de importación ya
 * se haya cerrado. Muestra `i/N`, fallos, posición de la FIFO global (`En cola: N`),
 * indicador de pausa sin red y botón Cancelar (área táctil ≥ 48.dp) que detiene el
 * lote sin matar la descarga activa ni las descargas sueltas.
 */
@Composable
fun PlaylistBatchProgressCard(
    playlistName: String?,
    done: Int,
    total: Int,
    failed: Int,
    queuedGlobal: Int,
    waitingForNetwork: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 150.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, CardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (waitingForNetwork) Icons.Default.WifiOff else Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = if (waitingForNetwork) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlistName ?: "Playlist",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                waitingForNetwork -> "Sin conexión — en pausa ($done/$total)"
                                else -> "Descargando $done/$total" +
                                    (if (failed > 0) " • $failed fallaron" else "") +
                                    (if (queuedGlobal > 0) " • En cola: $queuedGlobal" else "")
                            },
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (waitingForNetwork) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
                    trackColor = CardBorder
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancelar lote")
                }
            }
        }
    }
}
