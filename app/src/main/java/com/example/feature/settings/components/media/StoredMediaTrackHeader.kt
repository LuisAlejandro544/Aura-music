package com.example.feature.settings.components.media

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

/**
 * Carátula, título y artista de la pista.
 * Rol arquitectónico: sección de StoredMediaTrackCard (fachada pública intacta).
 */
@Composable
fun StoredMediaTrackHeader(track: Track) {
            // Fila principal: Carátula, Título y Artista
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ArtworkImage(
                    track = track,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    cornerRadius = 10.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds()
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
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
                                repeatDelayMillis = 1800,
                                initialDelayMillis = 1000,
                                velocity = 28.dp
                            )
                    )
                    Text(
                        text = "${track.artist} • ${track.album}",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clipToBounds()
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                repeatDelayMillis = 1800,
                                initialDelayMillis = 1000,
                                velocity = 28.dp
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = CardBorder.copy(alpha = 0.5f), thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(10.dp))
}
