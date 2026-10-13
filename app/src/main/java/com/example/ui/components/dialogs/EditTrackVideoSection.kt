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
 * Video Canvas: estado, encuadre, modo loop/estilo FFmpeg y botones.
 * Rol arquitectónico: sección de EditTrackDialog (fachada pública intacta).
 */
@Composable
fun EditTrackVideoSection(
    track: Track,
    videoUri: Uri?,
    removeVideo: Boolean,
    loopMode: Boolean?,
    onLoopModeChange: (Boolean?) -> Unit,
    loopStyle: com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle,
    onLoopStyleChange: (com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle) -> Unit,
    onPickVideo: () -> Unit,
    onRemoveVideo: () -> Unit
) {
                // Sección de Video Canvas / Video de Fondo
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Video Canvas de Fondo",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }

                        val hasVideoAttached = (track.videoUri != null || videoUri != null) && !removeVideo
                        val videoStatusText = when {
                            videoUri != null -> "🎬 Nuevo video seleccionado (se analizará duración al guardar)"
                            removeVideo -> "❌ Se eliminará el video de fondo"
                            track.videoUri != null -> if (track.isVideoLoop) "🔁 Loop Canvas activo (≤ 20s)" else "⏱️ Video largo sincronizado activo"
                            else -> "Sin video de fondo. Los videos cortos (≤ 20s) se repiten en loop y los largos se sincronizan con la música."
                        }

                        Text(
                            text = videoStatusText,
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )

                        if (hasVideoAttached) {
                            val editContext = androidx.compose.ui.platform.LocalContext.current
                            val appPrefs = remember { editContext.getSharedPreferences("aura_music_ui_prefs", android.content.Context.MODE_PRIVATE) }
                            var selectedFramingMode by remember {
                                val saved = appPrefs.getString("pref_video_display_mode", com.example.core.model.VideoDisplayMode.FULLSCREEN_BACKGROUND.name)
                                val initial = if (saved == com.example.core.model.VideoDisplayMode.FULLSCREEN_ADAPTED.name) {
                                    com.example.core.model.VideoDisplayMode.FULLSCREEN_ADAPTED
                                } else {
                                    com.example.core.model.VideoDisplayMode.FULLSCREEN_BACKGROUND
                                }
                                mutableStateOf(initial)
                            }

                            Text(
                                text = "Encuadre del Video de Fondo:",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = selectedFramingMode == com.example.core.model.VideoDisplayMode.FULLSCREEN_BACKGROUND,
                                    onClick = {
                                        selectedFramingMode = com.example.core.model.VideoDisplayMode.FULLSCREEN_BACKGROUND
                                        appPrefs.edit().putString("pref_video_display_mode", selectedFramingMode.name).apply()
                                    },
                                    label = { Text("📱 Rellenar (Recortar)", style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = selectedFramingMode == com.example.core.model.VideoDisplayMode.FULLSCREEN_ADAPTED,
                                    onClick = {
                                        selectedFramingMode = com.example.core.model.VideoDisplayMode.FULLSCREEN_ADAPTED
                                        appPrefs.edit().putString("pref_video_display_mode", selectedFramingMode.name).apply()
                                    },
                                    label = { Text("🎬 Adaptado horizontal", style = MaterialTheme.typography.labelSmall) }
                                )
                            }

                            Text(
                                text = "Modo de reproducción de video:",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = loopMode == null,
                                    onClick = { onLoopModeChange(null) },
                                    label = { Text("Auto (≤20s)", style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = loopMode == true,
                                    onClick = { onLoopModeChange(true) },
                                    label = { Text("Loop", style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = loopMode == false,
                                    onClick = { onLoopModeChange(false) },
                                    label = { Text("Sync", style = MaterialTheme.typography.labelSmall) }
                                )
                            }

                            if (loopMode != false) {
                                Text(
                                    text = "Efecto de bucle FFmpeg (para Loops ≤20s):",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = loopStyle == com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE,
                                        onClick = {
                                            onLoopStyleChange(com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE)
                                        },
                                        label = { Text("✨ Crossfade (xfade)", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    FilterChip(
                                        selected = loopStyle == com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle.BOOMERANG,
                                        onClick = {
                                            onLoopStyleChange(com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle.BOOMERANG)
                                            onLoopModeChange(true)
                                        },
                                        label = { Text("🪃 Boomerang (Ping-Pong)", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onPickVideo() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.MovieFilter, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (hasVideoAttached) "Cambiar Video" else "Elegir Video")
                            }

                            if (hasVideoAttached) {
                                OutlinedButton(
                                    onClick = { onRemoveVideo() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
}
