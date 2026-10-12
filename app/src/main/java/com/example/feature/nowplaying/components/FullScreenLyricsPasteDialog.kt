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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Diálogo para ingresar o editar la letra manualmente.
 * Rol arquitectónico: editor de texto LRC/plano con estado local.
 */
@Composable
fun FullScreenLyricsPasteDialog(
    initialText: String,
    onSaveLyrics: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Diálogo para ingresar o editar letra manualmente
    var rawText by remember { mutableStateOf(initialText) }
    AlertDialog(
                    onDismissRequest = { onDismiss() },
                    title = { Text("Editar o Pegar Letra") },
                    text = {
                        Column {
                            Text(
                                text = "Pega aquí la letra sincronizada (.LRC) o texto plano:",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = rawText,
                                onValueChange = { rawText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                placeholder = { Text("[00:12.50] Tu letra aquí...") }
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                onSaveLyrics(rawText)
                            }
                        ) {
                            Text("Guardar")
                        }
                    },
                        dismissButton = {
                            TextButton(onClick = { onDismiss() }) {
                                Text("Cancelar")
                            }
                        }
                    )
}
