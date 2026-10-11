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
import com.example.data.importer.playlist.ExternalPlaylistImporter
import com.example.model.ExternalPlaylistPreview
import com.example.model.ExternalPlaylistSource
import com.example.model.PlaylistImportSaveMode
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlaylistAdd,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Importar Playlist",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Spotify • YouTube Music • YouTube",
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(
                        onClick = { if (!isImporting) onDismiss() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_import_playlist_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cerrar",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Campo para pegar el enlace de Spotify o YouTube Music
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = onUrlChange,
                    enabled = !isAnalyzing && !isImporting,
                    label = { Text("Enlace de Playlist (Spotify o YT Music)") },
                    placeholder = {
                        Text(
                            text = "https://open.spotify.com/playlist/... o music.youtube.com/playlist?list=...",
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            tint = accentColor
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_import_url_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Botón para analizar la Playlist
                Button(
                    onClick = onAnalyzeClick,
                    enabled = inputUrl.isNotBlank() && !isAnalyzing && !isImporting,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("analyze_playlist_url_button")
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analizando canciones de la Playlist...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analizar Playlist", fontWeight = FontWeight.Bold)
                    }
                }

                // Mensaje de error si ocurre alguno
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1218)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF8A80),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Tarjeta de Previsualización de la Playlist Analizada
                if (playlistPreview != null) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceVariantDark),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (playlistPreview.coverUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = playlistPreview.coverUrl,
                                            contentDescription = playlistPreview.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.MusicNote,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        color = accentColor.copy(alpha = 0.22f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${playlistPreview.source.badgeLabel} • ${playlistPreview.tracks.size} CANCIONES",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accentColor,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = playlistPreview.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier.basicMarquee()
                                    )
                                    Text(
                                        text = "Por ${playlistPreview.author}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Nombre editable para guardar en Tu Biblioteca
                            OutlinedTextField(
                                value = customPlaylistName,
                                onValueChange = onCustomNameChange,
                                enabled = !isImporting,
                                label = { Text("Nombre de la Playlist en Aura Music") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = SurfaceVariantDark,
                                    unfocusedContainerColor = SurfaceVariantDark,
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("playlist_custom_name_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Previsualización desplazable de las pistas encontradas
                            Text(
                                text = "Canciones detectadas (${playlistPreview.tracks.size}):",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 175.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF090B11))
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(
                                    items = playlistPreview.tracks,
                                    key = { "${it.index}_${it.title}_${it.artist}" }
                                ) { trackItem ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${trackItem.index}.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted,
                                            modifier = Modifier.width(26.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = trackItem.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = trackItem.artist,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = trackItem.formattedDuration(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Selector de Modo de Guardado: Streaming Rápido vs Descargar Todo Offline
                    Text(
                        text = "¿Cómo quieres guardar esta Playlist?",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    PlaylistImportSaveMode.values().forEach { mode ->
                        val isSelected = selectedSaveMode == mode
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) accentColor.copy(alpha = 0.14f) else SurfaceCard
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) accentColor else Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable(enabled = !isImporting) { onSaveModeChange(mode) }
                                .testTag("save_mode_${mode.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isSelected) accentColor else TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = mode.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    if (selectedSaveMode == PlaylistImportSaveMode.OFFLINE_DOWNLOAD) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceCard)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Incluir Video Canvas 480p",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Vincula el video oficial de fondo a cada canción descargada",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = includeVideoCanvasOffline,
                                onCheckedChange = onIncludeVideoCanvasChange,
                                enabled = !isImporting
                            )
                        }
                    }

                    // Barra de progreso durante la importación / emparejamiento de Spotify
                    if (isImporting) {
                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { importProgressFraction.coerceIn(0f, 1f) },
                            color = accentColor,
                            trackColor = SurfaceVariantDark,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = importProgressText ?: "Importando Playlist...",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Botones de acción final
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isImporting,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Cancelar", color = TextSecondary)
                        }

                        Button(
                            onClick = onConfirmImportClick,
                            enabled = !isImporting && playlistPreview.tracks.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(48.dp)
                                .testTag("confirm_import_playlist_button")
                        ) {
                            Icon(
                                imageVector = if (selectedSaveMode == PlaylistImportSaveMode.OFFLINE_DOWNLOAD) {
                                    Icons.Filled.CloudDownload
                                } else {
                                    Icons.Filled.PlaylistAdd
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (selectedSaveMode == PlaylistImportSaveMode.STREAMING_INSTANT) {
                                    "Crear en Streaming"
                                } else {
                                    "Descargar Playlist"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
