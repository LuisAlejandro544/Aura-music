package com.example.feature.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.Playlist
import com.example.core.model.Track
import com.example.ui.components.TrackListItem
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Portada/collage, insignia de colección y botón editar.
 * Rol arquitectónico: sección LazyColumn de PlaylistDetailScreen (fachada intacta).
 */
fun LazyListScope.playlistDetailHeader(
    playlist: com.example.core.model.Playlist,
    tracks: List<com.example.core.model.Track>,
    isFavoritesVirtual: Boolean,
    isAlbumVirtual: Boolean,
    isArtistVirtual: Boolean,
    isVirtualCollection: Boolean,
    onBack: () -> Unit,
    onRenameClick: () -> Unit
) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("playlist_detail_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )

                if (!isVirtualCollection) {
                    IconButton(
                        onClick = { onRenameClick() },
                        modifier = Modifier.testTag("rename_playlist_header_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar lista",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cabecera visual con Portada Personalizada o Collage de 1 a 4 canciones
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .then(
                            if (!isVirtualCollection) {
                                Modifier.clickable { onRenameClick() }
                            } else Modifier
                        )
                ) {
                    com.example.ui.components.PlaylistCoverCollage(
                        playlist = playlist,
                        tracksOverride = tracks.take(4),
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 18.dp
                    )
                    if (!isVirtualCollection) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Cambiar imagen de lista",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val collectionBadge = when {
                        isFavoritesVirtual -> "COLECCIÓN FAVORITOS"
                        isAlbumVirtual -> "ÁLBUM"
                        isArtistVirtual -> "ARTISTA"
                        else -> "PLAYLIST PERSONALIZADA"
                    }
                    Text(
                        text = collectionBadge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (playlist.description.isNotBlank()) {
                        Text(
                            text = playlist.description,
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = "${tracks.size} canción(es) disponible(s)",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                }
            }
        }
}
