package com.example.ui.components.download

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.importer.OnlineVideoAudioImporter
import com.example.data.importer.WebStreamExtractor
import com.example.ui.theme.CardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Aura Music - Componente Modular de Vista Previa y Edición de Metadatos
 *
 * Muestra la miniatura oficial del video inspeccionado, duración y campos
 * de edición libre para título de la canción y nombre de artista/creador.
 */
@Composable
fun DownloadResolvedMediaCard(
    resolvedInfo: OnlineVideoAudioImporter.ResolvedMediaInfo,
    editableTitle: String,
    onTitleChanged: (String) -> Unit,
    editableArtist: String,
    onArtistChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ytId = WebStreamExtractor.extractVideoId(resolvedInfo.originalUrl)
    val previewCoverUrl = when {
        !resolvedInfo.coverUrl.isNullOrBlank() -> {
            val raw = resolvedInfo.coverUrl
            if (raw.startsWith("//")) "https:$raw" else raw
        }
        ytId != null -> "https://i.ytimg.com/vi/$ytId/hqdefault.jpg"
        else -> null
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!previewCoverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = previewCoverUrl,
                        contentDescription = "Carátula",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF222222)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = resolvedInfo.suggestedTitle,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = resolvedInfo.suggestedArtist,
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        maxLines = 1
                    )
                    if (resolvedInfo.durationSeconds > 0) {
                        Text(
                            text = "Duración: ${resolvedInfo.durationSeconds}s",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Campos de edición de título y artista
        OutlinedTextField(
            value = editableTitle,
            onValueChange = onTitleChanged,
            label = { Text("Título de la canción") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = CardBorder
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = editableArtist,
            onValueChange = onArtistChanged,
            label = { Text("Artista / Creador") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = CardBorder
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
