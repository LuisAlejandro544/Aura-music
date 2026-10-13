package com.example.feature.nowplaying.components
import com.example.core.model.RepeatMode

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import com.example.core.model.ABLoopState
import com.example.core.model.Track
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
/**
 * Título con marquesina, artista, etiqueta de mixtape y favorito.
 * Rol arquitectónico: pieza de NowPlayingPlaybackControls (fachada pública intacta).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingTrackHeader(
    currentTrack: Track,
    activeMixtapeChapter: com.example.core.model.MixtapeChapter?,
    mixtapeChapterIndex: Int,
    mixtapeTotalChapters: Int,
    animatedPrimary: Color,
    onToggleFavorite: (Track) -> Unit
) {
        // Título con Marquesina fluida, Artista, Etiqueta de formato y botón de Favorito
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .clipToBounds()
            ) {
                Text(
                    text = currentTrack.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            repeatDelayMillis = 1600,
                            initialDelayMillis = 1200,
                            velocity = 34.dp
                        )
                        .testTag("now_playing_marquee_title")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                    if (activeMixtapeChapter != null && mixtapeTotalChapters > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.22f),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "MIX ${mixtapeChapterIndex + 1}/$mixtapeTotalChapters",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF10B981)
                                ),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = animatedPrimary.copy(alpha = 0.16f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = currentTrack.formatBadge(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = animatedPrimary
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = currentTrack.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .weight(1f)
                            .clipToBounds()
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                repeatDelayMillis = 2000,
                                initialDelayMillis = 1600,
                                velocity = 28.dp
                            )
                    )
                }
            }

            IconButton(
                onClick = { onToggleFavorite(currentTrack) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("now_playing_fav_btn")
            ) {
                Icon(
                    imageVector = if (currentTrack.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorito",
                    tint = if (currentTrack.isFavorite) Color(0xFFEF4444) else TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
}
