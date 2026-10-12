package com.example.feature.importmusic

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import com.example.core.model.Track
import com.example.ui.components.DownloadSourceMode
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
    downloadProgress: com.example.core.model.DownloadProgress = com.example.core.model.DownloadProgress(),
    onImportVideoAsMusic: (videoUri: Uri, title: String, artist: String, album: String, attachAsCanvas: Boolean, forceLoop: Boolean?, trimSilence: Boolean) -> Unit = { _, _, _, _, _, _, _ -> },
    onImportVideoAsMusicWithLoopStyle: (videoUri: Uri, title: String, artist: String, album: String, attachAsCanvas: Boolean, forceLoop: Boolean?, trimSilence: Boolean, loopStyle: com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle) -> Unit = { uri, title, artist, album, attach, forceLoop, trim, _ ->
        onImportVideoAsMusic(uri, title, artist, album, attach, forceLoop, trim)
    },
    onDownloadFromLink: (resolvedInfo: com.example.core.data.importer.OnlineVideoAudioImporter.ResolvedMediaInfo, customTitle: String, customArtist: String, attachAsCanvas: Boolean, trimSilence: Boolean) -> Unit = { _, _, _, _, _ -> },
    onDownloadFromLinkWithLoopStyle: (resolvedInfo: com.example.core.data.importer.OnlineVideoAudioImporter.ResolvedMediaInfo, customTitle: String, customArtist: String, attachAsCanvas: Boolean, trimSilence: Boolean, loopStyle: com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle) -> Unit = { info, title, artist, attach, trim, _ ->
        onDownloadFromLink(info, title, artist, attach, trim)
    },
    onOpenPlaylistImportDialog: () -> Unit = {},
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
                ImportStatusBanner(
                    importStatusMessage = importStatusMessage,
                    onDismissStatusMessage = onDismissStatusMessage
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Indicador de carga activa
        if (isImporting) {
            item {
                ImportLoadingCard()
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Tarjeta de Privacidad y Consentimiento
        item {
            ImportPrivacyCard()
            Spacer(modifier = Modifier.height(8.dp))
        }

        importWebOptionsItems(
            onOpenTikTokLink = {
                downloadDialogMode = DownloadSourceMode.TIKTOK
                showDownloadFromLinkDialog = true
            },
            onOpenYouTubeLink = {
                downloadDialogMode = DownloadSourceMode.YOUTUBE_WEB
                showDownloadFromLinkDialog = true
            },
            onOpenPlaylistImportDialog = onOpenPlaylistImportDialog
        )

        importLocalOptionsItems(
            onPickVideo = {
                videoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            onPickFiles = {
                filePickerLauncher.launch(arrayOf("audio/*", "application/ogg"))
            },
            onPickFolder = { folderPickerLauncher.launch(null) },
            onSeedDemoTracks = onSeedDemoTracks
        )

        item {
            ImportLibrarySummaryCard(
                trackCount = allTracks.size,
                onClearClick = { showClearConfirmation = true }
            )
        }
    }

    if (showClearConfirmation) {
        ImportClearConfirmDialog(
            onConfirmClear = {
                onClearLibrary()
                showClearConfirmation = false
            },
            onDismiss = { showClearConfirmation = false }
        )
    }



    // Cierre garantizado del diálogo únicamente cuando finaliza la descarga (o si el usuario lo cierra manualmente con la X)
    var wasDownloadingInModal by remember { mutableStateOf(false) }
    LaunchedEffect(downloadProgress.isDownloading, allTracks.size) {
        if (downloadProgress.isDownloading && showDownloadFromLinkDialog) {
            wasDownloadingInModal = true
        } else if (!downloadProgress.isDownloading && wasDownloadingInModal) {
            showDownloadFromLinkDialog = false
            wasDownloadingInModal = false
        }
    }

    ImportPendingDialogs(
        pendingAudioUris = pendingAudioUris,
        pendingFolderUri = pendingFolderUri,
        trimSilenceSelection = trimSilenceSelection,
        onTrimSilenceChange = { trimSilenceSelection = it },
        onDismissPending = {
            pendingAudioUris = null
            pendingFolderUri = null
        },
        onConfirmPending = {
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
        selectedVideoForConversion = selectedVideoForConversion,
        onDismissVideo = { selectedVideoForConversion = null },
        onImportVideoAsMusic = { title, artist, album, attachAsCanvas, forceLoop, trimSilence ->
            val videoUri = selectedVideoForConversion
            if (videoUri != null) {
                onImportVideoAsMusic(videoUri, title, artist, album, attachAsCanvas, forceLoop, trimSilence)
                selectedVideoForConversion = null
            }
        },
        onImportVideoAsMusicWithLoopStyle = { title, artist, album, attachAsCanvas, forceLoop, trimSilence, loopStyle ->
            val videoUri = selectedVideoForConversion
            if (videoUri != null) {
                onImportVideoAsMusicWithLoopStyle(videoUri, title, artist, album, attachAsCanvas, forceLoop, trimSilence, loopStyle)
                selectedVideoForConversion = null
            }
        },
        showDownloadFromLinkDialog = showDownloadFromLinkDialog,
        downloadProgress = downloadProgress,
        downloadDialogMode = downloadDialogMode,
        onDismissDownload = {
            showDownloadFromLinkDialog = false
            wasDownloadingInModal = false
        },
        onDownloadFromLink = { info, title, artist, attachCanvas, trimSilence ->
            wasDownloadingInModal = true
            onDownloadFromLink(info, title, artist, attachCanvas, trimSilence)
        },
        onDownloadFromLinkWithLoopStyle = { info, title, artist, attachCanvas, trimSilence, loopStyle ->
            wasDownloadingInModal = true
            onDownloadFromLinkWithLoopStyle(info, title, artist, attachCanvas, trimSilence, loopStyle)
        }
    )
}

@Composable
internal fun ImportActionCard(
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
