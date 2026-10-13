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
 * Pegado/edición manual de letra con guardado.
 * Rol arquitectónico: pieza de NowPlayingLyricsCard (fachada pública intacta).
 */
@Composable
fun LyricsPasteDialog(
    trackTitle: String,
    initialText: String,
    animatedPrimary: Color,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {

    // Diálogo para pegar o editar letra
        var inputLyrics by remember { mutableStateOf(initialText) }
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = {
                Text("Letras de ${trackTitle}", fontWeight = FontWeight.Bold)
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
                        onSave(inputLyrics)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = animatedPrimary)
                ) {
                    Text("Guardar Letra")
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismiss() }) {
                    Text("Cancelar")
                }
            }
        )
}
