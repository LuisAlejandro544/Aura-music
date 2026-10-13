package com.example.ui.components
import com.example.core.model.VideoDisplayMode

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.data.importer.VideoAudioExtractor
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Campos de título, artista y álbum sugeridos.
 * Rol arquitectónico: sección de VideoToMusicDialog (fachada pública intacta).
 */
@Composable
fun VideoMetadataFields(
    title: String,
    onTitleChange: (String) -> Unit,
    artist: String,
    onArtistChange: (String) -> Unit,
    album: String,
    onAlbumChange: (String) -> Unit
) {
                    // Campos de texto para metadatos
                    OutlinedTextField(
                        value = title,
                        onValueChange = { onTitleChange(it) },
                        label = { Text("Título de la canción") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_title_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = artist,
                        onValueChange = { onArtistChange(it) },
                        label = { Text("Artista / Intérprete") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_artist_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = album,
                        onValueChange = { onAlbumChange(it) },
                        label = { Text("Álbum o Colección") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_album_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
}
