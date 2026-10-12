package com.example.feature.nowplaying

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TextSecondary

/**
 * Fila de atajos inferiores de Now Playing (EQ FX, Letras/Carátula y Cola).
 * Rol arquitectónico: accesos directos con área táctil de 44-48dp y texto en una línea.
 */
@Composable
fun NowPlayingShortcutRow(
    animatedPrimary: androidx.compose.ui.graphics.Color,
    animatedSecondary: androidx.compose.ui.graphics.Color,
    showLyrics: Boolean,
    queueSize: Int,
    onOpenEq: (Int) -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenQueue: () -> Unit
) {
                // Atajos Inferiores: Ecualizador, Letras Karaoke y Cola de Reproducción
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ecualizador
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onOpenEq(0)
                            }
                            .testTag("now_playing_eq_shortcut")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = animatedSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "EQ FX",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = animatedSecondary,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    // Letras / Carátula
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onToggleLyrics() }
                            .testTag("now_playing_lyrics_shortcut")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (showLyrics) Icons.Default.Album else Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (showLyrics) animatedPrimary else TextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (showLyrics) "Carátula" else "Letras",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (showLyrics) FontWeight.Bold else FontWeight.Medium,
                                    color = if (showLyrics) animatedPrimary else TextSecondary,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    // Cola de Reproducción
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenQueue() }
                            .testTag("now_playing_queue_btn")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Cola (${queueSize})",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
}
