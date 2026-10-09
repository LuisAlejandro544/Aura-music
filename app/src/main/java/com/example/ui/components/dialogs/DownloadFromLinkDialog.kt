package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.importer.FFmpegNativeEngine
import com.example.data.importer.OnlineVideoAudioImporter
import com.example.data.importer.YoutubeExtractionEngine
import com.example.data.importer.YtDlpAutoUpdater
import com.example.model.DownloadProgress
import com.example.model.VideoDisplayMode
import com.example.ui.components.download.DownloadCanvasOptionsSection
import com.example.ui.components.download.DownloadProgressStatusCard
import com.example.ui.components.download.DownloadResolvedMediaCard
import com.example.ui.components.download.DownloadSourceAndEngineSelector
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class DownloadSourceMode(val label: String) {
    TIKTOK("TikTok"),
    YOUTUBE_WEB("YouTube / Web")
}

/**
 * Aura Music - Diálogo Modular de Descarga desde Enlaces Web (TikTok, YouTube) (< 500 líneas)
 *
 * Delega en componentes modulares especializados:
 * - [DownloadSourceAndEngineSelector]: Selector de fuentes y motores (yt-dlp, InnerTube, WebView).
 * - [DownloadProgressStatusCard]: Métricas en vivo de progreso, velocidad y persistencia en segundo plano.
 * - [DownloadResolvedMediaCard]: Vista previa oficial y campos de edición libre.
 * - [DownloadCanvasOptionsSection]: Configuración de Video Canvas 480p, encuadre y recorte de silencios.
 */
@Composable
fun DownloadFromLinkDialog(
    initialMode: DownloadSourceMode = DownloadSourceMode.TIKTOK,
    initialUrl: String = "",
    downloadProgress: DownloadProgress = DownloadProgress(),
    onDismiss: () -> Unit,
    onConfirmDownload: (
        resolvedInfo: OnlineVideoAudioImporter.ResolvedMediaInfo,
        customTitle: String,
        customArtist: String,
        attachAsCanvas: Boolean,
        trimSilence: Boolean
    ) -> Unit,
    onConfirmDownloadWithLoopStyle: ((
        resolvedInfo: OnlineVideoAudioImporter.ResolvedMediaInfo,
        customTitle: String,
        customArtist: String,
        attachAsCanvas: Boolean,
        trimSilence: Boolean,
        loopStyle: FFmpegNativeEngine.CanvasLoopStyle
    ) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var selectedMode by remember {
        mutableStateOf(
            if (initialUrl.contains("youtu", ignoreCase = true)) DownloadSourceMode.YOUTUBE_WEB else initialMode
        )
    }
    var selectedEngine by remember { mutableStateOf(YoutubeExtractionEngine.YTDLP) }
    var linkUrl by remember { mutableStateOf(initialUrl) }
    var isResolving by remember { mutableStateOf(false) }
    var resolveError by remember { mutableStateOf<String?>(null) }
    var resolvedInfo by remember { mutableStateOf<OnlineVideoAudioImporter.ResolvedMediaInfo?>(null) }

    var editableTitle by remember { mutableStateOf("") }
    var editableArtist by remember { mutableStateOf("") }
    var attachAsCanvas by remember { mutableStateOf(true) }
    var selectedLoopStyle by remember { mutableStateOf(FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE) }

    val appPrefs = remember { context.getSharedPreferences("aura_music_ui_prefs", android.content.Context.MODE_PRIVATE) }
    var selectedFramingMode by remember {
        val saved = appPrefs.getString("pref_video_display_mode", VideoDisplayMode.FULLSCREEN_BACKGROUND.name)
        val initial = if (saved == VideoDisplayMode.FULLSCREEN_ADAPTED.name) VideoDisplayMode.FULLSCREEN_ADAPTED else VideoDisplayMode.FULLSCREEN_BACKGROUND
        mutableStateOf(initial)
    }
    var trimSilence by remember { mutableStateOf(true) }
    val packageUpdateState by YtDlpAutoUpdater.packageUpdateState.collectAsState()
    val isYtDlpBlocked = selectedMode == DownloadSourceMode.YOUTUBE_WEB &&
            selectedEngine == YoutubeExtractionEngine.YTDLP &&
            packageUpdateState.isYtDlpTemporarilyLocked

    // Auto-resolución al abrir con URL predeterminada
    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank() && resolvedInfo == null) {
            isResolving = true
            resolveError = null
            val result = OnlineVideoAudioImporter.resolveMediaLink(
                linkUrl = initialUrl.trim(),
                context = context,
                engine = selectedEngine
            )
            isResolving = false
            result.onSuccess { info ->
                resolvedInfo = info
                editableTitle = info.suggestedTitle
                editableArtist = info.suggestedArtist
            }.onFailure { err ->
                resolveError = err.message ?: "No se pudo obtener información del enlace."
            }
        }
    }

    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 16.dp)
                .testTag("download_link_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(20.dp)
            ) {
                val isYoutube = linkUrl.contains("youtu", ignoreCase = true) || selectedMode == DownloadSourceMode.YOUTUBE_WEB
                val headerGradient = if (isYoutube) {
                    Brush.linearGradient(listOf(Color(0xFFFF0033), Color(0xFFFF3366), Color(0xFFFF8800)))
                } else {
                    Brush.linearGradient(listOf(Color(0xFF00F2FE), Color(0xFF4FACFE), Color(0xFFFF007F)))
                }

                // Cabecera con degradado
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(headerGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isYoutube) Icons.Default.SmartDisplay else Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isYoutube) "Descargar desde YouTube / Web" else "Descargar desde TikTok",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Música, carátula y Video Canvas",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextSecondary)
                    }
                }

                if (downloadProgress.isDownloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    DownloadProgressStatusCard(
                        downloadProgress = downloadProgress,
                        titleText = editableTitle.ifBlank { resolvedInfo?.suggestedTitle ?: "Descargando música..." },
                        onDismiss = onDismiss
                    )
                } else {
                    Spacer(modifier = Modifier.height(14.dp))

                    DownloadSourceAndEngineSelector(
                        selectedMode = selectedMode,
                        onModeSelected = {
                            selectedMode = it
                            resolveError = null
                        },
                        selectedEngine = selectedEngine,
                        onEngineSelected = {
                            selectedEngine = it
                            resolveError = null
                        },
                        packageUpdateState = packageUpdateState,
                        isYtDlpBlocked = isYtDlpBlocked,
                        context = context
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Campo URL y botón pegar
                    OutlinedTextField(
                        value = linkUrl,
                        onValueChange = {
                            linkUrl = it
                            resolveError = null
                        },
                        label = { Text("Enlace del video (TikTok o Web)") },
                        placeholder = { Text("https://vm.tiktok.com/... o enlace web") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = CardBorder,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    clipboardManager.getText()?.text?.let { clipText ->
                                        linkUrl = clipText.trim()
                                        resolveError = null
                                    }
                                },
                                modifier = Modifier.testTag("paste_clipboard_btn")
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Pegar desde portapapeles", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("link_url_input")
                    )

                    if (resolveError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = resolveError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (resolvedInfo == null) {
                        Button(
                            onClick = {
                                if (linkUrl.isBlank()) {
                                    resolveError = "Por favor pega un enlace antes de continuar"
                                    return@Button
                                }
                                isResolving = true
                                resolveError = null
                                coroutineScope.launch {
                                    val result = OnlineVideoAudioImporter.resolveMediaLink(linkUrl, context, selectedEngine)
                                    isResolving = false
                                    result.onSuccess { info ->
                                        resolvedInfo = info
                                        editableTitle = info.suggestedTitle
                                        editableArtist = info.suggestedArtist
                                    }.onFailure { error ->
                                        resolveError = error.message ?: "No se pudo obtener el video. Verifica tu conexión y el enlace."
                                    }
                                }
                            },
                            enabled = !isResolving && linkUrl.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("resolve_link_btn")
                        ) {
                            if (isResolving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Inspeccionando enlace...")
                            } else if (isYtDlpBlocked) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Inspeccionar Video (Vía Rápida)", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Inspeccionar Video", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        val currentInfo = resolvedInfo!!

                        DownloadResolvedMediaCard(
                            resolvedInfo = currentInfo,
                            editableTitle = editableTitle,
                            onTitleChanged = { editableTitle = it },
                            editableArtist = editableArtist,
                            onArtistChanged = { editableArtist = it }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        DownloadCanvasOptionsSection(
                            attachAsCanvas = attachAsCanvas,
                            onAttachAsCanvasChanged = { attachAsCanvas = it },
                            selectedFramingMode = selectedFramingMode,
                            onFramingModeChanged = { mode ->
                                selectedFramingMode = mode
                                appPrefs.edit().putString("pref_video_display_mode", mode.name).apply()
                            },
                            selectedLoopStyle = selectedLoopStyle,
                            onLoopStyleChanged = { selectedLoopStyle = it },
                            trimSilence = trimSilence,
                            onTrimSilenceChanged = { trimSilence = it }
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { resolvedInfo = null },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("Cambiar")
                            }

                            Button(
                                onClick = {
                                    if (attachAsCanvas) {
                                        appPrefs.edit().putString("pref_video_display_mode", selectedFramingMode.name).apply()
                                    }
                                    val finalTitle = editableTitle.ifBlank { currentInfo.suggestedTitle }
                                    val finalArtist = editableArtist.ifBlank { currentInfo.suggestedArtist }
                                    if (onConfirmDownloadWithLoopStyle != null) {
                                        onConfirmDownloadWithLoopStyle(
                                            currentInfo,
                                            finalTitle,
                                            finalArtist,
                                            attachAsCanvas,
                                            trimSilence,
                                            selectedLoopStyle
                                        )
                                    } else {
                                        onConfirmDownload(
                                            currentInfo,
                                            finalTitle,
                                            finalArtist,
                                            attachAsCanvas,
                                            trimSilence
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(2f)
                                    .height(48.dp)
                                    .testTag("start_download_btn")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Descargar y Reproducir", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
