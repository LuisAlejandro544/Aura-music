package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.importer.OnlineVideoAudioImporter
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
    onDismiss: () -> Unit,
    onConfirmDownload: (
        resolvedInfo: OnlineVideoAudioImporter.ResolvedMediaInfo,
        customTitle: String,
        customArtist: String,
        attachAsCanvas: Boolean
    ) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedMode by remember { mutableStateOf(initialMode) }
    var linkUrl by remember { mutableStateOf("") }
    var isResolving by remember { mutableStateOf(false) }
    var resolveError by remember { mutableStateOf<String?>(null) }
    var resolvedInfo by remember { mutableStateOf<OnlineVideoAudioImporter.ResolvedMediaInfo?>(null) }

    var editableTitle by remember { mutableStateOf("") }
    var editableArtist by remember { mutableStateOf("") }
    var attachAsCanvas by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .testTag("download_link_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                                val result = OnlineVideoAudioImporter.resolveMediaLink(linkUrl, context)
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
                            if (!currentInfo.coverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = currentInfo.coverUrl,
                                    contentDescription = "Carátula de TikTok",
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
                                text = "Vincular Video Canvas de fondo",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Reproduce el video detrás de la pantalla Now Playing o en recuadro",
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

                    Spacer(modifier = Modifier.height(20.dp))

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
                                    attachAsCanvas
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
