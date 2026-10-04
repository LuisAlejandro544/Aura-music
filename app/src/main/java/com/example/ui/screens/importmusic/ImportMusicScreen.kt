package com.example.ui.screens.importmusic

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Track
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pantalla dedicada de Importación de Música.
 * Cumple con el requisito estricto de privacidad: NO escanea todo el almacenamiento
 * del teléfono automáticamente, sino que permite al usuario importar selectivamente
 * archivos de audio o carpetas completas mediante el Storage Access Framework (SAF).
 */
@Composable
fun ImportMusicScreen(
    allTracks: List<Track>,
    isImporting: Boolean,
    importStatusMessage: String?,
    onImportUris: (List<Uri>, Boolean) -> Unit,
    onImportFolder: (Uri, Boolean) -> Unit,
    onSeedDemoTracks: () -> Unit,
    onClearLibrary: () -> Unit,
    onDismissStatusMessage: () -> Unit,
    downloadProgress: com.example.model.DownloadProgress = com.example.model.DownloadProgress(),
    onImportVideoAsMusic: (videoUri: Uri, title: String, artist: String, album: String, attachAsCanvas: Boolean, forceLoop: Boolean?, trimSilence: Boolean) -> Unit = { _, _, _, _, _, _, _ -> },
    onDownloadFromLink: (resolvedInfo: com.example.data.importer.OnlineVideoAudioImporter.ResolvedMediaInfo, customTitle: String, customArtist: String, attachAsCanvas: Boolean, trimSilence: Boolean) -> Unit = { _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var showClearConfirmation by remember { mutableStateOf(false) }
    var selectedVideoForConversion by remember { mutableStateOf<Uri?>(null) }
    var showDownloadFromLinkDialog by remember { mutableStateOf(false) }
    var downloadDialogMode by remember { mutableStateOf(com.example.ui.components.DownloadSourceMode.TIKTOK) }

    // Estados pendientes para preguntar al usuario con interruptor antes de importar archivos o carpeta
    var pendingAudioUris by remember { mutableStateOf<List<Uri>?>(null) }
    var pendingFolderUri by remember { mutableStateOf<Uri?>(null) }
    var trimSilenceSelection by remember { mutableStateOf(true) }

    // Lanzador Photo/Media Picker para seleccionar un video y convertirlo a música
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoForConversion = uri
        }
    }

    // Lanzador SAF para seleccionar múltiples archivos de audio
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            trimSilenceSelection = true
            pendingAudioUris = uris
        }
    }

    // Lanzador SAF para seleccionar una carpeta completa
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            trimSilenceSelection = true
            pendingFolderUri = treeUri
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Importar Música",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tú decides qué música añadir a tu reproductor.",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Mensaje de estado / notificación
        if (importStatusMessage != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = importStatusMessage,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismissStatusMessage) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Indicador de carga activa
        if (isImporting) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    border = CardBorder.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Procesando metadatos...",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Extrayendo etiquetas ID3, duración y carátulas.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Tarjeta de Privacidad y Consentimiento
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = CardBorder.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Privacidad Garantizada",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Sin escaneo masivo del dispositivo. Tus audios sólo se leen cuando tú los seleccionas.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Opción 1: Descargar desde TikTok (Música, Carátula y Video Canvas)
        item {
            ImportActionCard(
                title = "Descargar desde TikTok",
                description = "Pega un enlace de video de TikTok: descarga la música en alta fidelidad de cualquier duración, con carátula oficial y Video Canvas de fondo.",
                buttonText = "Pegar Enlace de TikTok",
                icon = Icons.Default.MusicNote,
                accentGradient = Brush.horizontalGradient(
                    listOf(Color(0xFF00F2FE), Color(0xFF4FACFE), Color(0xFFFF007F))
                ),
                onClick = {
                    downloadDialogMode = com.example.ui.components.DownloadSourceMode.TIKTOK
                    showDownloadFromLinkDialog = true
                },
                testTag = "import_tiktok_link_btn"
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Opción 2: Descargar desde YouTube / Enlace Web
        item {
            ImportActionCard(
                title = "Descargar desde YouTube / Web",
                description = "Pega un enlace de YouTube o video web: extrae el audio de alta fidelidad con resolución rápida y optimizada, carátula en WebP y Video Canvas sincronizado.",
                buttonText = "Pegar Enlace de Video",
                icon = Icons.Default.SmartDisplay,
                accentGradient = Brush.horizontalGradient(
                    listOf(Color(0xFFFF0033), Color(0xFFFF3366), Color(0xFFFF8800))
                ),
                onClick = {
                    downloadDialogMode = com.example.ui.components.DownloadSourceMode.YOUTUBE_WEB
                    showDownloadFromLinkDialog = true
                },
                testTag = "import_youtube_link_btn"
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Opción 2: Video a Música de Galería (Extraer Audio, Carátula y Video Canvas)
        item {
            ImportActionCard(
                title = "Video a Música (Galería)",
                description = "Elige un video de tu galería: extraerá el audio en alta fidelidad, capturará la portada en WebP y vinculará el Video Canvas automáticamente.",
                buttonText = "Elegir Video de Galería",
                icon = Icons.Default.MovieFilter,
                accentGradient = Brush.horizontalGradient(
                    listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
                ),
                onClick = {
                    videoPickerLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.VideoOnly
                        )
                    )
                },
                testTag = "import_video_to_music_btn"
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Opción 2: Seleccionar archivos
        item {
            ImportActionCard(
                title = "Elegir Archivos de Audio",
                description = "Selecciona una o varias canciones (MP3, FLAC, WAV, M4A, OGG) desde tu almacenamiento.",
                buttonText = "Seleccionar Archivos",
                icon = Icons.Default.AudioFile,
                accentGradient = Brush.horizontalGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                ),
                onClick = {
                    filePickerLauncher.launch(
                        arrayOf(
                            "audio/*",
                            "application/ogg"
                        )
                    )
                },
                testTag = "import_files_btn"
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Opción 2: Seleccionar carpeta
        item {
            ImportActionCard(
                title = "Elegir Carpeta de Música",
                description = "Elige una carpeta entera (ej: Music, Descargas). Se importarán todos los audios encontrados dentro.",
                buttonText = "Seleccionar Carpeta",
                icon = Icons.Default.FolderOpen,
                accentGradient = Brush.horizontalGradient(
                    listOf(Color(0xFF06B6D4), Color(0xFF10B981))
                ),
                onClick = { folderPickerLauncher.launch(null) },
                testTag = "import_folder_btn"
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Opción 3: Probar con canciones demo
        item {
            ImportActionCard(
                title = "Cargar Canciones Demo Synthwave",
                description = "Añade pistas de demostración con audio sintetizado para probar el reproductor y el ecualizador ahora mismo.",
                buttonText = "Añadir Demos",
                icon = Icons.Default.ElectricBolt,
                accentGradient = Brush.horizontalGradient(
                    listOf(Color(0xFFF97316), Color(0xFFF43F5E))
                ),
                onClick = onSeedDemoTracks,
                testTag = "import_demos_btn"
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Resumen de la biblioteca actual y opción de reinicio
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = CardBorder.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Estado de tu Colección",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Canciones importadas:", color = TextSecondary)
                        Text("${allTracks.size}", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ubicación:", color = TextSecondary)
                        Text("Almacenamiento Local", fontWeight = FontWeight.Medium, color = TextPrimary)
                    }

                    if (allTracks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { showClearConfirmation = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("clear_library_btn")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Limpiar Biblioteca Local")
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("¿Reiniciar biblioteca de música?") },
            text = { Text("Se desvincularán todas las canciones importadas de Aura Music. Tus archivos físicos originales no se borrarán de tu teléfono.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearLibrary()
                        showClearConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reiniciar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (pendingAudioUris != null || pendingFolderUri != null) {
        val isFolder = pendingFolderUri != null
        val itemCount = pendingAudioUris?.size ?: 1
        AlertDialog(
            onDismissRequest = {
                pendingAudioUris = null
                pendingFolderUri = null
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
                                onCheckedChange = { trimSilenceSelection = it },
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
                        val uris = pendingAudioUris
                        val folder = pendingFolderUri
                        val shouldTrim = trimSilenceSelection
                        pendingAudioUris = null
                        pendingFolderUri = null
                        if (uris != null) {
                            onImportUris(uris, shouldTrim)
                        } else if (folder != null) {
                            onImportFolder(folder, shouldTrim)
                        }
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
                        pendingAudioUris = null
                        pendingFolderUri = null
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
            onDismiss = { selectedVideoForConversion = null },
            onConfirm = { title, artist, album, attachAsCanvas, forceLoop, trimSilence ->
                onImportVideoAsMusic(selectedVideoForConversion!!, title, artist, album, attachAsCanvas, forceLoop, trimSilence)
                selectedVideoForConversion = null
            }
        )
    }

    // Cierre automático del diálogo al finalizar la descarga exitosamente
    LaunchedEffect(downloadProgress.isDownloading) {
        if (!downloadProgress.isDownloading && showDownloadFromLinkDialog && isImporting) {
            showDownloadFromLinkDialog = false
        }
    }

    if (showDownloadFromLinkDialog) {
        com.example.ui.components.DownloadFromLinkDialog(
            initialMode = downloadDialogMode,
            downloadProgress = downloadProgress,
            onDismiss = { showDownloadFromLinkDialog = false },
            onConfirmDownload = { info, title, artist, attachCanvas, trimSilence ->
                onDownloadFromLink(info, title, artist, attachCanvas, trimSilence)
            }
        )
    }
}

@Composable
private fun ImportActionCard(
    title: String,
    description: String,
    buttonText: String,
    icon: ImageVector,
    accentGradient: Brush,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = CardBorder.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 18.sp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(buttonText, fontWeight = FontWeight.Bold)
            }
        }
    }
}
