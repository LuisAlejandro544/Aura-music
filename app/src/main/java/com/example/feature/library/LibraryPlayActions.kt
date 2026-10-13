package com.example.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.Playlist
import com.example.core.model.Track
import com.example.ui.components.TrackListItem
import com.example.ui.navigation.LibraryTab
import com.example.ui.navigation.NavScreen
import com.example.feature.library.components.*
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary


/**
 * Botones de reproducir todo y aleatorio.
 * Rol arquitectónico: sección LazyColumn de LibraryScreen (fachada intacta).
 */
fun LazyListScope.libraryPlayActions(
    filteredTracks: List<com.example.core.model.Track>,
    favoriteTracks: List<com.example.core.model.Track>,
    selectedTab: com.example.ui.navigation.LibraryTab,
    onTrackClick: (com.example.core.model.Track, List<com.example.core.model.Track>) -> Unit,
    canAutoRemix: Boolean,
    onOpenAutoRemix: () -> Unit
) {
        // Botones de Reproducir Todo / Aleatorio
        if (filteredTracks.isNotEmpty() && (selectedTab == LibraryTab.SONGS || selectedTab == LibraryTab.FAVORITES)) {
            val listToPlay = if (selectedTab == LibraryTab.FAVORITES) favoriteTracks else filteredTracks
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (listToPlay.isNotEmpty()) onTrackClick(listToPlay.first(), listToPlay)
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("play_all_btn")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reproducir", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            if (listToPlay.isNotEmpty()) {
                                val shuffled = listToPlay.shuffled()
                                onTrackClick(shuffled.first(), shuffled)
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shuffle_all_btn")
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Aleatorio", fontWeight = FontWeight.Bold)
                    }
                }
                if (canAutoRemix) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = { onOpenAutoRemix() },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("library_autoremix_btn")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Auto-Remix & Mashup con 2 Canciones (Local)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
}
