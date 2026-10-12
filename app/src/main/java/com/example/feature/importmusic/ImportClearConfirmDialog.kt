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
 * Confirmación para reiniciar la biblioteca local.
 * Rol arquitectónico: diálogo destructivo con doble confirmación.
 */
@Composable
fun ImportClearConfirmDialog(
    onConfirmClear: () -> Unit,
    onDismiss: () -> Unit
) {
            AlertDialog(
                onDismissRequest = { onDismiss() },
                title = { Text("¿Reiniciar biblioteca de música?") },
                text = { Text("Se desvincularán todas las canciones importadas de Aura Music. Tus archivos físicos originales no se borrarán de tu teléfono.") },
                confirmButton = {
                    Button(
                        onClick = { onConfirmClear() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Reiniciar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onDismiss() }) {
                        Text("Cancelar")
                    }
                }
            )
}
