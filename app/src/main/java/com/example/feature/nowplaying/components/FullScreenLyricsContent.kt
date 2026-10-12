package com.example.feature.nowplaying.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.example.core.model.LyricsState
import com.example.core.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Contenedor principal de letras (carga, lista karaoke, vacío y botón de sincronía).
 * Rol arquitectónico: cuerpo desplazable con resaltado neón de la frase activa.
 */
@Composable
fun FullScreenLyricsContent(
    listState: LazyListState,
    lyricsState: LyricsState,
    activeIndex: Int,
    animatedPrimary: Color,
    userScrolledManually: Boolean,
    coroutineScope: CoroutineScope,
    onSeekTo: (Long) -> Unit,
    onOpenSearchLyrics: () -> Unit,
    onImportClick: () -> Unit,
    onUserScrollConsumed: () -> Unit
) {
                // Contenedor principal de letras
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        lyricsState.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = animatedPrimary, modifier = Modifier.size(42.dp))
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Sincronizando letras para karaoke...",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                                    )
                                }
                            }
                        }

                        lyricsState.lines.isNotEmpty() -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(top = 28.dp, bottom = 120.dp)
                            ) {
                                itemsIndexed(lyricsState.lines) { index, line ->
                                    val isCurrent = (index == activeIndex)
                                    val textScale by animateFloatAsState(
                                        targetValue = if (isCurrent) 1.05f else 1.0f,
                                        animationSpec = tween(280),
                                        label = "LyricsLineScale"
                                    )

                                    val lineAlpha by animateFloatAsState(
                                        targetValue = when {
                                            isCurrent -> 1.0f
                                            index < activeIndex -> 0.35f
                                            else -> 0.65f
                                        },
                                        animationSpec = tween(280),
                                        label = "LyricsLineAlpha"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onSeekTo(line.timeMs) }
                                            .padding(vertical = 6.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = line.text,
                                            style = if (isCurrent) {
                                                MaterialTheme.typography.headlineSmall.copy(
                                                    fontSize = 24.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    lineHeight = 32.sp,
                                                    color = animatedPrimary
                                                )
                                            } else {
                                                MaterialTheme.typography.titleLarge.copy(
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    lineHeight = 28.sp,
                                                    color = Color.White.copy(alpha = lineAlpha)
                                                )
                                            },
                                            modifier = Modifier.scale(textScale)
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            // Vista vacía cuando no hay letras disponibles
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = animatedPrimary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "No hay letras disponibles para esta canción",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            textAlign = TextAlign.Center
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Busca en la base de datos libre LRCLIB o importa tu archivo .LRC",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = onOpenSearchLyrics,
                                            colors = ButtonDefaults.buttonColors(containerColor = animatedPrimary)
                                        ) {
                                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Buscar Letras")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onImportClick()
                                            }
                                        ) {
                                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Importar .LRC")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Botón flotante "Volver a la canción" cuando el usuario hace scroll manual
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = userScrolledManually && activeIndex >= 0 && lyricsState.lines.isNotEmpty(),
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(200))
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = animatedPrimary,
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .clickable {
                                        userScrolledManually = false
                                        coroutineScope.launch {
                                            val targetScroll = (activeIndex - 2).coerceAtLeast(0)
                                            listState.animateScrollToItem(targetScroll)
                                        }
                                    }
                                    .testTag("full_screen_lyrics_sync_btn")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Sincronizar con audio",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
}
