package com.example.feature.nowplaying.components

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
import com.example.core.model.LyricsState
import com.example.core.model.Track
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
/**
 * Karaoke sincronizado, texto plano o estado vacío con acciones.
 * Rol arquitectónico: pieza de NowPlayingLyricsCard (fachada pública intacta).
 */
@Composable
fun LyricsCardContent(
    lyricsState: LyricsState,
    activeIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    animatedPrimary: Color,
    onSeekTo: (Long) -> Unit,
    onFetchOnlineLyrics: () -> Unit,
    onOpenSearchLyrics: () -> Unit,
    onPickFile: () -> Unit,
    onEditClick: () -> Unit
) {
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
                        itemsIndexed(lyricsState.lines, key = { _, line -> line.timeMs }) { index, line ->
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
                                    onClick = { onPickFile() },
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
                                    onClick = { onEditClick() },
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
