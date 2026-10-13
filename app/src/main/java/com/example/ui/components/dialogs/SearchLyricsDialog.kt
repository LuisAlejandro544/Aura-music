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
import com.example.core.model.LyricSearchResult
import com.example.core.model.Track
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
                SearchLyricsHeader(
                    accentColor = accentColor,
                    onDismissRequest = onDismissRequest
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Campos de entrada: título y artista
                SearchLyricsInputs(
                    titleInput = titleInput,
                    onTitleChange = { titleInput = it },
                    artistInput = artistInput,
                    onArtistChange = { artistInput = it },
                    accentColor = accentColor,
                    onSubmitSearch = {
                        if (titleInput.isNotBlank()) {
                            onSearch(titleInput.trim(), artistInput.trim())
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Botón de búsqueda principal
                SearchLyricsSubmitButton(
                    titleInput = titleInput,
                    artistInput = artistInput,
                    isSearching = isSearching,
                    accentColor = accentColor,
                    onSearch = onSearch
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Contenido dinámico de resultados
                SearchLyricsResultsContent(
                    isSearching = isSearching,
                    searchError = searchError,
                    searchResults = searchResults,
                    accentColor = accentColor,
                    onSelectResult = onSelectResult,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Tarjeta individual para un resultado de letra en la lista.
 * Si es oficial/recomendada, resalta con borde de neón e insignia estelar destacada.
 */
