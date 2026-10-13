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
 * Insignia LRC y botones: buscar, importar, editar, fullscreen y cerrar.
 * Rol arquitectónico: pieza de NowPlayingLyricsCard (fachada pública intacta).
 */
@Composable
fun LyricsCardTopBar(
    animatedPrimary: Color,
    onOpenSearchLyrics: () -> Unit,
    onPickFile: () -> Unit,
    onEditClick: () -> Unit,
    onOpenFullScreen: () -> Unit,
    onCloseLyrics: () -> Unit
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
                        onClick = { onPickFile() },
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
                        onClick = { onEditClick() },
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
}
