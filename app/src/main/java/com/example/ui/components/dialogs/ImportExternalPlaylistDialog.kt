package com.example.ui.components.dialogs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.core.data.importer.playlist.ExternalPlaylistImporter
import com.example.core.model.ExternalPlaylistPreview
import com.example.core.model.ExternalPlaylistSource
import com.example.core.model.PlaylistImportSaveMode
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Modal deslizable para importar Playlists públicas completas desde Spotify y YouTube Music / YouTube.
 *
 * Rol Arquitectónico:
 * - Detecta automáticamente si el enlace pertenece a Spotify (`#1DB954`) o YouTube Music (`#FF0033`).
 * - Muestra la portada oficial, nombre editable de la Playlist, creador y la lista completa de canciones.
 * - Permite al usuario elegir cómo desea guardar la Playlist:
 *   1. ⚡ Modo Streaming Rápido (Instantáneo)
 *   2. 📥 Descargar Todo Offline (En 2do plano)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImportExternalPlaylistDialog(
    inputUrl: String,
    isAnalyzing: Boolean,
    playlistPreview: ExternalPlaylistPreview?,
    customPlaylistName: String,
    selectedSaveMode: PlaylistImportSaveMode,
    includeVideoCanvasOffline: Boolean,
    isImporting: Boolean,
    importProgressText: String?,
    importProgressFraction: Float,
    errorMessage: String?,
    onUrlChange: (String) -> Unit,
    onCustomNameChange: (String) -> Unit,
    onAnalyzeClick: () -> Unit,
    onSaveModeChange: (PlaylistImportSaveMode) -> Unit,
    onIncludeVideoCanvasChange: (Boolean) -> Unit,
    onConfirmImportClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val detectedSource = playlistPreview?.source ?: ExternalPlaylistImporter.detectSourcePlatform(inputUrl)
    val accentColor = when (detectedSource) {
        ExternalPlaylistSource.SPOTIFY -> Color(0xFF1DB954)
        ExternalPlaylistSource.YOUTUBE_MUSIC -> Color(0xFFFF0033)
        null -> MaterialTheme.colorScheme.primary
    }

    Dialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                .testTag("import_external_playlist_dialog"),
            color = Color(0xFF0E111A)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Encabezado del Modal
                PlaylistImportHeader(
                    accentColor = accentColor,
                    isImporting = isImporting,
                    onDismiss = onDismiss
                )
                // Campo para pegar el enlace de Spotify o YouTube Music
                PlaylistImportUrlField(
                    inputUrl = inputUrl,
                    isAnalyzing = isAnalyzing,
                    isImporting = isImporting,
                    accentColor = accentColor,
                    onUrlChange = onUrlChange
                )
                // Botón para analizar la Playlist
                PlaylistImportAnalyzeButton(
                    inputUrl = inputUrl,
                    isAnalyzing = isAnalyzing,
                    isImporting = isImporting,
                    accentColor = accentColor,
                    onAnalyzeClick = onAnalyzeClick
                )
                // Mensaje de error si ocurre alguno
                PlaylistImportError(errorMessage = errorMessage)
                if (playlistPreview != null) {
                    PlaylistImportPreviewCard(
                        preview = playlistPreview,
                        accentColor = accentColor,
                        customPlaylistName = customPlaylistName,
                        onCustomNameChange = onCustomNameChange,
                        isImporting = isImporting
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    PlaylistImportSaveModeSelector(
                        selectedSaveMode = selectedSaveMode,
                        onSaveModeChange = onSaveModeChange,
                        includeVideoCanvasOffline = includeVideoCanvasOffline,
                        onIncludeVideoCanvasChange = onIncludeVideoCanvasChange,
                        isImporting = isImporting,
                        accentColor = accentColor
                    )

                    PlaylistImportProgress(
                        isImporting = isImporting,
                        importProgressFraction = importProgressFraction,
                        importProgressText = importProgressText,
                        accentColor = accentColor
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    PlaylistImportActions(
                        preview = playlistPreview,
                        selectedSaveMode = selectedSaveMode,
                        isImporting = isImporting,
                        accentColor = accentColor,
                        onDismiss = onDismiss,
                        onConfirmImportClick = onConfirmImportClick
                    )
                }
            }
        }
    }
}
