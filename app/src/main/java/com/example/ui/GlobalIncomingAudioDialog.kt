package com.example.ui
import com.example.core.model.LyricSearchResult

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.data.updater.AppReleaseUpdater
import com.example.core.model.AppUpdateState
import com.example.core.model.DownloadProgress
import com.example.core.model.HeadphoneConfig
import com.example.core.model.SleepTimerState
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.AudioEffectsBottomSheet
import com.example.ui.components.DownloadFromLinkDialog
import com.example.ui.components.SearchLyricsDialog
import com.example.ui.components.VideoToMusicDialog
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.MusicViewModel

/**
 * Importación de audio entrante con recorte de silencios.
 * Rol arquitectónico: anfitrión de diálogo global (fachada pública intacta).
 */
@Composable
fun GlobalIncomingAudioDialog(
    viewModel: com.example.viewmodel.MusicViewModel,
    uris: List<android.net.Uri>?
) {
    // 3. Diálogo de audio entrante ("Abrir con..." / "Compartir con...")
    if (!uris.isNullOrEmpty()) {
        val incomingCount = uris.size
        var incomingTrimSilence by remember(uris) { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { viewModel.clearPendingIncomingAudio() },
            title = {
                Text(
                    text = if (incomingCount == 1) "Importar y Reproducir Audio" else "Importar $incomingCount Pistas de Audio",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Se guardará una copia en el almacenamiento privado de Aura Music y comenzará la reproducción.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, CardBorder)
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
                                    text = "Recorta automáticamente espacios silenciosos antes y después de la canción.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = incomingTrimSilence,
                                onCheckedChange = { incomingTrimSilence = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF10B981)
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmIncomingAudioImport(incomingTrimSilence) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Importar y Reproducir", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearPendingIncomingAudio() }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
