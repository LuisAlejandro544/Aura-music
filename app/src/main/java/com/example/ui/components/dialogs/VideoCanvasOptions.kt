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
 * Vinculación Canvas, encuadre, loop FFmpeg y recorte de silencios.
 * Rol arquitectónico: sección de VideoToMusicDialog (fachada pública intacta).
 */
@Composable
fun VideoCanvasOptions(
    attachAsCanvas: Boolean,
    onAttachChange: (Boolean) -> Unit,
    loopMode: Boolean?,
    onLoopModeChange: (Boolean?) -> Unit,
    loopStyle: com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle,
    onLoopStyleChange: (com.example.core.data.importer.FFmpegNativeEngine.CanvasLoopStyle) -> Unit,
    trimSilence: Boolean,
    onTrimChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
                    // Opción para vincular como Video Canvas
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Vincular Video Canvas",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                    Text(
                                        text = "Reproduce el video de fondo en Now Playing sincronizado.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                    )
                                }
                                Switch(
                                    checked = attachAsCanvas,
                                    onCheckedChange = { onAttachChange(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("video_attach_canvas_switch")
                                )
                            }

                            if (attachAsCanvas) {
                                val appPrefs = remember { context.getSharedPreferences("aura_music_ui_prefs", android.content.Context.MODE_PRIVATE) }
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
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
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
                                    text = "Modo de Video Canvas:",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
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
                                        label = { Text("Sincronizado", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }

                                if (loopMode != false) {
                                    Text(
                                        text = "Efecto de bucle FFmpeg (para Loops ≤20s):",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
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

                            HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))

                            // Opción de eliminación inteligente de silencios al inicio y final
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Eliminar silencios al inicio y final",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                    Text(
                                        text = "Recorta automáticamente espacios en silencio antes y después de la canción.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                    )
                                }
                                Switch(
                                    checked = trimSilence,
                                    onCheckedChange = { onTrimChange(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF10B981)
                                    ),
                                    modifier = Modifier.testTag("video_trim_silence_switch")
                                )
                            }
                        }
                    }
}
