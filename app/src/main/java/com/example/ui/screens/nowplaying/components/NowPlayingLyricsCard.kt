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
    onOpenFullScreen: () -> Unit = {},
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
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D12).copy(alpha = 0.95f)),
        border = BorderStroke(1.2.dp, animatedPrimary.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth(0.86f)
            .aspectRatio(1f)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = animatedPrimary,
                spotColor = animatedPrimary
            )
            .testTag("now_playing_lyrics_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            // Barra superior de la tarjeta de letras
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = animatedPrimary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, animatedPrimary.copy(alpha = 0.35f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = animatedPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "KARAOKE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = animatedPrimary,
                                fontSize = 10.5.sp
                            ),
                            maxLines = 1
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Botón para buscar en línea y elegir versiones
                    IconButton(
                        onClick = onOpenSearchLyrics,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar y elegir versión de letras",
                            tint = animatedPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Botón para importar archivo .LRC / .TXT desde el celular
                    IconButton(
                        onClick = {
                            importLyricsFileLauncher.launch(
                                arrayOf("text/*", "application/octet-stream", "*/*")
                            )
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Importar archivo .LRC o .TXT del celular",
                            tint = TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Botón para pegar / editar texto manualmente
                    IconButton(
                        onClick = { showPasteDialog = true },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar letra",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Botón para modo Karaoke a pantalla completa
                    IconButton(
                        onClick = onOpenFullScreen,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Pantalla completa Karaoke",
                            tint = animatedPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Botón para volver a la carátula
                    IconButton(
                        onClick = onCloseLyrics,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Volver a carátula",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Contenido principal de letras
            when {
                lyricsState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = animatedPrimary, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Buscando letras...",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 12.sp)
                            )
                        }
                    }
                }

                lyricsState.lines.isNotEmpty() -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        itemsIndexed(lyricsState.lines) { index, line ->
                            val isActive = index == activeIndex

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isActive) animatedPrimary.copy(alpha = 0.16f) else Color.Transparent
                                    )
                                    .clickable { onSeekTo(line.timeMs) }
                                    .padding(vertical = 5.dp, horizontal = 8.dp)
                            ) {
                                Text(
                                    text = line.text.ifBlank { "♪ ♪ ♪" },
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = if (isActive) 16.5.sp else 13.5.sp,
                                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isActive) animatedPrimary else TextSecondary.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Start,
                                        lineHeight = if (isActive) 22.sp else 18.sp
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
                            contentPadding = PaddingValues(6.dp)
                        ) {
                            item {
                                Text(
                                    text = lyricsState.plainLyrics,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = TextPrimary.copy(alpha = 0.85f),
                                        lineHeight = 20.sp,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    }
                }

                else -> {
                    // Estado vacío perfectamente ajustado sin desbordamientos
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = animatedPrimary.copy(alpha = 0.14f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Lyrics,
                                        contentDescription = null,
                                        tint = animatedPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Sin letras para esta pista",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 13.5.sp
                                ),
                                maxLines = 1
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "Descarga la letra sincronizada o escribe tu versión.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    fontSize = 10.5.sp
                                ),
                                maxLines = 1
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Botón principal: Buscar por nombre y elegir versión
                            Button(
                                onClick = onOpenSearchLyrics,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = animatedPrimary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .height(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.Black
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Buscar Letras en Línea",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    fontSize = 12.5.sp,
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Fila horizontal equilibrada: Importar archivo y Pegar manual
                            Row(
                                modifier = Modifier.fillMaxWidth(0.92f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        importLyricsFileLauncher.launch(
                                            arrayOf("text/*", "application/octet-stream", "*/*")
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, CardBorder),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = animatedPrimary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Importar",
                                        color = TextPrimary,
                                        fontSize = 11.5.sp,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    onClick = { showPasteDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, CardBorder),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Escribir",
                                        color = TextPrimary,
                                        fontSize = 11.5.sp,
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
