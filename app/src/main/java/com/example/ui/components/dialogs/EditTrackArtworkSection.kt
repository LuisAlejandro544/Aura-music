package com.example.ui.components
import com.example.core.model.VideoDisplayMode

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.model.Track
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

/**
 * Previsualización de carátula y botones de galería/procedural.
 * Rol arquitectónico: sección de EditTrackDialog (fachada pública intacta).
 */
@Composable
fun EditTrackArtworkSection(
    track: Track,
    title: String,
    artist: String,
    artUri: Uri?,
    removeArtwork: Boolean,
    onPickArtwork: () -> Unit,
    onRemoveArtwork: () -> Unit
) {
                // Sección de Carátula (Previsualización y controles de galería)
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .clickable { onPickArtwork() },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        // 1. Imagen recién seleccionada de la galería
                        artUri != null -> {
                            AsyncImage(
                                model = artUri,
                                contentDescription = "Nueva carátula seleccionada",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // 2. Se solicitó quitar la carátula -> mostrar arte procedural
                        removeArtwork -> {
                            ProceduralArtwork(
                                title = title.ifBlank { "Aura" },
                                artist = artist.ifBlank { "Music" },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // 3. Carátula existente de la pista si existe
                        !track.albumArtPath.isNullOrEmpty() && File(track.albumArtPath).exists() -> {
                            AsyncImage(
                                model = File(track.albumArtPath),
                                contentDescription = "Carátula actual",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // 4. Arte procedural predeterminado
                        else -> {
                            ProceduralArtwork(
                                title = track.title,
                                artist = track.artist,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Overlay con botón de cámara / galería para mayor claridad táctil
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Seleccionar de galería",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Botones para gestionar la carátula
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onPickArtwork() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("change_artwork_btn")
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Carátula", style = MaterialTheme.typography.labelMedium)
                    }

                    val hasArtwork = (!track.albumArtPath.isNullOrEmpty() || artUri != null) && !removeArtwork
                    if (hasArtwork) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { onRemoveArtwork() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("remove_artwork_btn")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Procedural", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
}
