package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.importer.OnlineVideoAudioImporter
import com.example.data.importer.WebStreamExtractor
import com.example.data.importer.YoutubeExtractionEngine
import com.example.model.DownloadProgress
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
 * Diálogo modal para descargar música y Video Canvas directamente desde enlaces de TikTok o videos web.
 *
 * Funcionalidades:
 * - Pegar enlace con un toque desde el portapapeles.
 * - Resolución previa de información (título, artista/creador, carátula y duración).
 * - Personalización de título y creador antes de guardar.
 * - Opción de vincular como Video Canvas de fondo continuo o sincronizado.
 * - Retroalimentación en tiempo real del progreso de descarga y extracción.
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
    ) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

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
    var trimSilence by remember { mutableStateOf(true) }

    // Auto-resolución si se recibe una URL inicial compartida desde otra app
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

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = TextSecondary
                        )
                    }
                }

                if (downloadProgress.isDownloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { if (downloadProgress.totalBytes > 0) downloadProgress.progressFraction else 0f },
                                modifier = Modifier.size(56.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = CardBorder,
                                strokeWidth = 4.dp
                            )
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = editableTitle.ifBlank { resolvedInfo?.suggestedTitle ?: "Descargando música..." },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            ),
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Text(
                            text = downloadProgress.phase.ifBlank { "Procesando flujo multimedia..." },
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        if (downloadProgress.totalBytes > 0) {
                            LinearProgressIndicator(
                                progress = { downloadProgress.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = CardBorder
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = CardBorder
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Métricas numéricas de Peso descargado / total y Velocidad de internet
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = downloadProgress.formattedProgress,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = downloadProgress.formattedSpeed,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Continuar en segundo plano")
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(14.dp))

                    // Selector rápido de fuente
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedMode == DownloadSourceMode.TIKTOK,
                        onClick = {
                            selectedMode = DownloadSourceMode.TIKTOK
                            resolveError = null
                        },
                        label = { Text("TikTok", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    FilterChip(
                        selected = selectedMode == DownloadSourceMode.YOUTUBE_WEB,
                        onClick = {
                            selectedMode = DownloadSourceMode.YOUTUBE_WEB
                            resolveError = null
                        },
                        label = { Text("YouTube / Web", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.SmartDisplay, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }

                // Selector de motor para YouTube / Web
                if (selectedMode == DownloadSourceMode.YOUTUBE_WEB) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Motor de extracción:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedEngine == YoutubeExtractionEngine.YTDLP,
                            onClick = {
                                selectedEngine = YoutubeExtractionEngine.YTDLP
                                resolveError = null
                            },
                            label = { Text("⚡ yt-dlp + FFmpeg", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedEngine == YoutubeExtractionEngine.INNERTUBE,
                            onClick = {
                                selectedEngine = YoutubeExtractionEngine.INNERTUBE
                                resolveError = null
                            },
                            label = { Text("InnerTube", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedEngine == YoutubeExtractionEngine.WEBVIEW,
                            onClick = {
                                selectedEngine = YoutubeExtractionEngine.WEBVIEW
                                resolveError = null
                            },
                            label = { Text("WebView", fontSize = 11.sp) }
                        )
                    }
                    Text(
                        text = when (selectedEngine) {
                            YoutubeExtractionEngine.YTDLP -> "Extractor local avanzado con soporte para sortear firmas n-sig y protección de bots."
                            YoutubeExtractionEngine.INNERTUBE -> "API nativa directa de YouTube Music. Rápida, gratis y sin consumo de batería."
                            YoutubeExtractionEngine.WEBVIEW -> "Navegador efímero en segundo plano que ejecuta el reproductor en memoria."
                        },
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
                        modifier = Modifier.padding(top = 2.dp, start = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Campo para ingresar la URL con botón de pegar
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
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Pegar desde portapapeles",
                                tint = MaterialTheme.colorScheme.primary
                            )
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

                // Botón para resolver enlace si aún no está resuelto
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("resolve_link_btn")
                    ) {
                        if (isResolving) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Inspeccionando enlace...")
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Inspeccionar Video", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Vista previa del contenido resuelto
                    val currentInfo = resolvedInfo!!
                    val ytId = WebStreamExtractor.extractVideoId(currentInfo.originalUrl)
                    val previewCoverUrl = when {
                        !currentInfo.coverUrl.isNullOrBlank() -> {
                            val raw = currentInfo.coverUrl
                            if (raw.startsWith("//")) "https:$raw" else raw
                        }
                        ytId != null -> "https://i.ytimg.com/vi/$ytId/hqdefault.jpg"
                        else -> null
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!previewCoverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = previewCoverUrl,
                                    contentDescription = "Carátula",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF222222)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentInfo.suggestedTitle,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    ),
                                    maxLines = 1
                                )
                                Text(
                                    text = currentInfo.suggestedArtist,
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                    maxLines = 1
                                )
                                if (currentInfo.durationSeconds > 0) {
                                    Text(
                                        text = "Duración: ${currentInfo.durationSeconds}s",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Campos de edición de título y artista
                    OutlinedTextField(
                        value = editableTitle,
                        onValueChange = { editableTitle = it },
                        label = { Text("Título de la canción") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = CardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editableArtist,
                        onValueChange = { editableArtist = it },
                        label = { Text("Artista / Creador") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = CardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Switch para Video Canvas de fondo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Vincular Video Canvas de fondo (480p)",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Descarga por defecto en 480p de alta fluidez y lo reproduce detrás de Now Playing o en recuadro",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                        Switch(
                            checked = attachAsCanvas,
                            onCheckedChange = { attachAsCanvas = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Switch para Eliminación Inteligente de Silencios al inicio y final
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Eliminar silencios al inicio y final",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Recorta inteligentemente espacios vacíos o silenciosos antes y después de la canción",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                        Switch(
                            checked = trimSilence,
                            onCheckedChange = { trimSilence = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981)
                            ),
                            modifier = Modifier.testTag("download_trim_silence_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Botones de acción
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
                                onConfirmDownload(
                                    currentInfo,
                                    editableTitle.ifBlank { currentInfo.suggestedTitle },
                                    editableArtist.ifBlank { currentInfo.suggestedArtist },
                                    attachAsCanvas,
                                    trimSilence
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
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
