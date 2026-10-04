package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.LyricSearchResult
import com.example.model.Track
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Diálogo interactivo para buscar letras personalizadas en LRCLIB.
 *
 * Permite al usuario:
 * 1. Escribir o editar el nombre de la canción y el artista libremente.
 * 2. Visualizar y comparar las letras encontradas en tiempo real.
 * 3. Recomendar en primer lugar la Lírica Oficial canónica con insignia destacada.
 * 4. Seleccionar cualquier versión para aplicarla de inmediato a la canción.
 */
@Composable
fun SearchLyricsDialog(
    currentTrack: Track,
    isSearching: Boolean,
    searchResults: List<LyricSearchResult>,
    searchError: String?,
    accentColor: Color,
    onDismissRequest: () -> Unit,
    onSearch: (title: String, artist: String) -> Unit,
    onSelectResult: (LyricSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    var titleInput by remember(currentTrack.id) { mutableStateOf(currentTrack.title) }
    var artistInput by remember(currentTrack.id) { mutableStateOf(currentTrack.artist) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundDark.copy(alpha = 0.96f)),
            border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.5f)),
            modifier = modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 12.dp)
                .testTag("search_lyrics_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Cabecera del diálogo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = accentColor.copy(alpha = 0.16f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Buscar Letras",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Elige entre las versiones disponibles",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Campos de entrada: Título de la canción
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Nombre de la canción") },
                    placeholder = { Text("Ej: Bohemian Rhapsody") },
                    leadingIcon = {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = accentColor)
                    },
                    trailingIcon = {
                        if (titleInput.isNotEmpty()) {
                            IconButton(onClick = { titleInput = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = CardBorder,
                        focusedLabelColor = accentColor,
                        cursorColor = accentColor
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lyrics_search_title_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Campo de entrada: Artista (opcional)
                OutlinedTextField(
                    value = artistInput,
                    onValueChange = { artistInput = it },
                    label = { Text("Artista o banda (opcional)") },
                    placeholder = { Text("Ej: Queen") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary)
                    },
                    trailingIcon = {
                        if (artistInput.isNotEmpty()) {
                            IconButton(onClick = { artistInput = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            if (titleInput.isNotBlank()) {
                                onSearch(titleInput.trim(), artistInput.trim())
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = CardBorder,
                        focusedLabelColor = accentColor,
                        cursorColor = accentColor
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lyrics_search_artist_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Botón de búsqueda principal
                Button(
                    onClick = {
                        keyboardController?.hide()
                        if (titleInput.isNotBlank()) {
                            onSearch(titleInput.trim(), artistInput.trim())
                        }
                    },
                    enabled = titleInput.isNotBlank() && !isSearching,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("lyrics_search_submit_btn")
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            strokeWidth = 2.5.dp,
                            color = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Buscando en LRCLIB...", fontWeight = FontWeight.Bold, color = Color.Black)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black, modifier = Modifier.size(19.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buscar Letras Disponibles", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Contenido dinámico de resultados
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        isSearching -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = accentColor)
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Consultando catálogo de letras y versión oficial...",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                                )
                            }
                        }

                        searchError != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = searchError,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = TextSecondary,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }

                        searchResults.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lyrics,
                                    contentDescription = null,
                                    tint = TextSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Escribe el nombre y pulsa 'Buscar'",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Se mostrará primero la lírica oficial recomendada y luego las versiones alternativas.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 8.dp)
                            ) {
                                item {
                                    Text(
                                        text = "${searchResults.size} resultado(s) encontrado(s)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TextSecondary,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                                    )
                                }

                                items(searchResults, key = { "${it.id}_${it.trackName}_${it.isSynced}_${it.isOfficialRecommended}" }) { result ->
                                    LyricResultItemCard(
                                        result = result,
                                        accentColor = accentColor,
                                        onSelect = { onSelectResult(result) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta individual para un resultado de letra en la lista.
 * Si es oficial/recomendada, resalta con borde de neón e insignia estelar destacada.
 */
@Composable
private fun LyricResultItemCard(
    result: LyricSearchResult,
    accentColor: Color,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOfficial = result.isOfficialRecommended

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOfficial) accentColor.copy(alpha = 0.12f) else SurfaceCard.copy(alpha = 0.7f)
        ),
        border = BorderStroke(
            width = if (isOfficial) 1.6.dp else 1.dp,
            color = if (isOfficial) accentColor else CardBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect() }
            .testTag(if (isOfficial) "lyric_official_card" else "lyric_option_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Fila superior: Insignia Oficial y Tipo de Letra
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isOfficial) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor,
                        modifier = Modifier.height(24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "OFICIAL / RECOMENDADA",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp,
                                    color = Color.Black,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.height(22.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            Text(
                                text = "VERSIÓN ALTERNATIVA",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    color = TextSecondary
                                )
                            )
                        }
                    }
                }

                // Chip de formato: Sincronizada o Texto plano
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (result.isSynced) Color(0xFF10B981).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(
                        0.8.dp,
                        if (result.isSynced) Color(0xFF10B981).copy(alpha = 0.6f) else Color.Transparent
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (result.isSynced) Icons.Default.Sync else Icons.Default.Notes,
                            contentDescription = null,
                            tint = if (result.isSynced) Color(0xFF10B981) else TextSecondary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (result.isSynced) "Sincronizada (Karaoke)" else "Texto Plano",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (result.isSynced) Color(0xFF10B981) else TextSecondary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Título de la pista
            Text(
                text = result.trackName.ifBlank { "Sin título" },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isOfficial) accentColor else TextPrimary,
                    fontSize = 15.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Artista y álbum
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = result.artistName.ifBlank { "Artista desconocido" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary.copy(alpha = 0.85f),
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (result.albumName.isNotBlank()) {
                    Text(
                        text = " • ${result.albumName}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (result.durationSeconds > 0) {
                    val m = result.durationSeconds / 60
                    val s = result.durationSeconds % 60
                    Text(
                        text = " (${String.format("%02d:%02d", m, s)})",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // Extracto de versos para previsualización
            if (result.previewSnippet.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = result.previewSnippet,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary.copy(alpha = 0.9f),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            ),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Botón de selección táctil
            Button(
                onClick = onSelect,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOfficial) accentColor else Color.White.copy(alpha = 0.12f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Text(
                    text = if (isOfficial) "Elegir Lírica Oficial" else "Elegir esta versión",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isOfficial) Color.Black else TextPrimary
                    )
                )
            }
        }
    }
}
