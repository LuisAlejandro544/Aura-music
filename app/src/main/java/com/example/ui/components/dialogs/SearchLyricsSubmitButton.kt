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
 * Botón principal de búsqueda con estado de carga.
 * Rol arquitectónico: sección de SearchLyricsDialog (fachada pública intacta).
 */
@Composable
fun SearchLyricsSubmitButton(
    titleInput: String,
    artistInput: String,
    isSearching: Boolean,
    accentColor: Color,
    onSearch: (String, String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
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
}
