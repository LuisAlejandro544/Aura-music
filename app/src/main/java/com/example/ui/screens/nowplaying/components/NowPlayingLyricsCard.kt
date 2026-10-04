package com.example.ui.screens.nowplaying.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LyricsState
import com.example.model.Track
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Tarjeta interactiva de Letras Sincronizadas (.LRC) estilo Karaoke.
 *
 * Características:
 * - Desplazamiento automático al compás de la música.
 * - Resaltado dinámico con color primario/neón y tipografía ampliada para la frase actual.
 * - Salto en la canción al tocar cualquier frase (Seek al timestamp).
 * - Búsqueda y descarga automática desde la base de datos libre LRCLIB.
 * - Importador de archivos de letras (.lrc y .txt) directamente desde el almacenamiento del celular.
 * - Diálogo para ingresar o editar letras manualmente.
 */
@Composable
fun NowPlayingLyricsCard(
    currentTrack: Track,
    lyricsState: LyricsState,
    currentPositionMs: Long,
    animatedPrimary: Color,
    onSeekTo: (Long) -> Unit,
    onFetchOnlineLyrics: () -> Unit,
    onSaveCustomLyrics: (String) -> Unit,
    onImportLyricsUri: (android.net.Uri) -> Unit = {},
    onOpenSearchLyrics: () -> Unit = {},
    onCloseLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showPasteDialog by remember { mutableStateOf(false) }

    // Launcher del sistema para seleccionar archivos .lrc o .txt locales
    val importLyricsFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            onImportLyricsUri(uri)
        }
    }

    // Determinar la línea activa en función de la posición actual de reproducción
    val activeIndex = remember(currentPositionMs, lyricsState.lines) {
        if (lyricsState.lines.isEmpty()) -1
        else {
            val idx = lyricsState.lines.indexOfLast { it.timeMs <= currentPositionMs }
            if (idx >= 0) idx else 0
        }
    }

    // Desplazamiento automático para centrar la frase que suena
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && lyricsState.lines.isNotEmpty()) {
            val targetScroll = (activeIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.88f)),
        border = BorderStroke(1.2.dp, animatedPrimary.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth(0.92f)
            .aspectRatio(0.95f)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = animatedPrimary,
                spotColor = animatedPrimary
            )
            .testTag("now_playing_lyrics_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Barra superior de la tarjeta de letras
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = animatedPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LETRAS KARAOKE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = animatedPrimary
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Botón para buscar por nombre y elegir versión
                    IconButton(
                        onClick = onOpenSearchLyrics,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar y elegir versión de letras",
                            tint = animatedPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Botón para importar archivo .LRC / .TXT desde el celular
                    IconButton(
                        onClick = {
                            importLyricsFileLauncher.launch(
                                arrayOf("text/*", "application/octet-stream", "*/*")
                            )
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Importar archivo .LRC o .TXT del celular",
                            tint = TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Botón para buscar en línea rápido
                    IconButton(
                        onClick = onFetchOnlineLyrics,
                        enabled = !lyricsState.isLoading,
                        modifier = Modifier.size(34.dp)
                    ) {
                        if (lyricsState.isLoading) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = animatedPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Descargar automáticamente",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Botón para pegar / editar texto
                    IconButton(
                        onClick = { showPasteDialog = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar letra",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Botón para volver a la carátula
                    IconButton(
                        onClick = onCloseLyrics,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Volver a carátula",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Contenido principal de letras
            when {
                lyricsState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = animatedPrimary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Buscando letras sincronizadas...",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                    }
                }

                lyricsState.lines.isNotEmpty() -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 20.dp)
                    ) {
                        itemsIndexed(lyricsState.lines) { index, line ->
                            val isActive = index == activeIndex
                            val scale by animateFloatAsState(
                                targetValue = if (isActive) 1.05f else 0.95f,
                                animationSpec = tween(200),
                                label = "lyric_scale"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSeekTo(line.timeMs) }
                                    .padding(vertical = 4.dp, horizontal = 6.dp)
                            ) {
                                Text(
                                    text = line.text.ifBlank { "♪ ♪ ♪" },
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = if (isActive) 19.sp else 14.sp,
                                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isActive) animatedPrimary else TextSecondary.copy(alpha = 0.55f),
                                        textAlign = TextAlign.Start,
                                        lineHeight = if (isActive) 26.sp else 20.sp
                                    )
                                )
                            }
                        }
                    }
                }

                lyricsState.plainLyrics.isNotBlank() -> {
                    // Letra plana no sincronizada
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            item {
                                Text(
                                    text = lyricsState.plainLyrics,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = TextPrimary.copy(alpha = 0.85f),
                                        lineHeight = 22.sp
                                    )
                                )
                            }
                        }
                    }
                }

                else -> {
                    // Estado vacío compacto, armónico y con desplazamiento suave sin recortes ni desbordamientos
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = animatedPrimary.copy(alpha = 0.12f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Lyrics,
                                        contentDescription = null,
                                        tint = animatedPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Sin letras para esta canción",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                ),
                                maxLines = 1
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = "Descarga la letra sincronizada o escribe tu versión.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Botón principal: Buscar por nombre y elegir versión
                            Button(
                                onClick = onOpenSearchLyrics,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = animatedPrimary),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.95f)
                                    .height(42.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                    tint = Color.Black
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Buscar Letras en Línea",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Fila horizontal equilibrada: Importar archivo y Pegar manual (sin ocupar espacio vertical excesivo)
                            Row(
                                modifier = Modifier.fillMaxWidth(0.95f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        importLyricsFileLauncher.launch(
                                            arrayOf("text/*", "application/octet-stream", "*/*")
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CardBorder),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = animatedPrimary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Importar",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    onClick = { showPasteDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CardBorder),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Escribir",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo para pegar o editar letra
    if (showPasteDialog) {
        var inputLyrics by remember { mutableStateOf(lyricsState.plainLyrics) }
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = {
                Text("Letras de ${currentTrack.title}", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "Puedes pegar letras sincronizadas [.lrc] con marcas de tiempo o texto plano.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = inputLyrics,
                        onValueChange = { inputLyrics = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        placeholder = { Text("[00:15.50] Primera frase...") },
                        maxLines = 15
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveCustomLyrics(inputLyrics)
                        showPasteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = animatedPrimary)
                ) {
                    Text("Guardar Letra")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
