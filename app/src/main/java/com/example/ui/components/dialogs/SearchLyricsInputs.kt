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
 * Campos de título y artista con acciones de teclado.
 * Rol arquitectónico: sección de SearchLyricsDialog (fachada pública intacta).
 */
@Composable
fun SearchLyricsInputs(
    titleInput: String,
    onTitleChange: (String) -> Unit,
    artistInput: String,
    onArtistChange: (String) -> Unit,
    accentColor: Color,
    onSubmitSearch: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
                // Campos de entrada: Título de la canción
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { onTitleChange(it) },
                    label = { Text("Nombre de la canción") },
                    placeholder = { Text("Ej: Bohemian Rhapsody") },
                    leadingIcon = {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = accentColor)
                    },
                    trailingIcon = {
                        if (titleInput.isNotEmpty()) {
                            IconButton(onClick = { onTitleChange("") }) {
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
                    onValueChange = { onArtistChange(it) },
                    label = { Text("Artista o banda (opcional)") },
                    placeholder = { Text("Ej: Queen") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary)
                    },
                    trailingIcon = {
                        if (artistInput.isNotEmpty()) {
                            IconButton(onClick = { onArtistChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            onSubmitSearch()
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
}
