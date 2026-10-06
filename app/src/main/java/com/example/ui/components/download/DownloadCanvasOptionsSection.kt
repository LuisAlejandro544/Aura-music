package com.example.ui.components.download

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.importer.FFmpegNativeEngine
import com.example.model.VideoDisplayMode
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Aura Music - Componente Modular de Opciones de Video Canvas y Recorte de Silencios
 *
 * Configura si se vincula el Video Canvas en 480p, el encuadre (Rellenar pantalla vs Adaptado),
 * el estilo de bucle corto FFmpeg (Crossfade xfade vs Boomerang Ping-Pong) y la eliminación de silencios.
 */
@Composable
fun DownloadCanvasOptionsSection(
    attachAsCanvas: Boolean,
    onAttachAsCanvasChanged: (Boolean) -> Unit,
    selectedFramingMode: VideoDisplayMode,
    onFramingModeChanged: (VideoDisplayMode) -> Unit,
    selectedLoopStyle: FFmpegNativeEngine.CanvasLoopStyle,
    onLoopStyleChanged: (FFmpegNativeEngine.CanvasLoopStyle) -> Unit,
    trimSilence: Boolean,
    onTrimSilenceChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Switch para Video Canvas de fondo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Vincular Video Canvas de fondo (480p)",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "Descarga por defecto en 480p de alta fluidez y lo reproduce detrás de Now Playing o en recuadro",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
            Switch(
                checked = attachAsCanvas,
                onCheckedChange = onAttachAsCanvasChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        if (attachAsCanvas) {
            Spacer(modifier = Modifier.height(8.dp))
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
                    selected = selectedFramingMode == VideoDisplayMode.FULLSCREEN_BACKGROUND,
                    onClick = { onFramingModeChanged(VideoDisplayMode.FULLSCREEN_BACKGROUND) },
                    label = { Text("📱 Rellenar pantalla (Recortar)", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = selectedFramingMode == VideoDisplayMode.FULLSCREEN_ADAPTED,
                    onClick = { onFramingModeChanged(VideoDisplayMode.FULLSCREEN_ADAPTED) },
                    label = { Text("🎬 Adaptado horizontal", style = MaterialTheme.typography.labelSmall) }
                )
            }
            Text(
                text = if (selectedFramingMode == VideoDisplayMode.FULLSCREEN_BACKGROUND) {
                    "El video ocupará el 100% de la pantalla de arriba a abajo aunque se recorten caras o bordes."
                } else {
                    "Muestra el video horizontal completo centrado sin recortar caras."
                },
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp)
            )

            Spacer(modifier = Modifier.height(8.dp))
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
                    selected = selectedLoopStyle == FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE,
                    onClick = { onLoopStyleChanged(FFmpegNativeEngine.CanvasLoopStyle.CROSSFADE) },
                    label = { Text("✨ Crossfade (xfade)", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = selectedLoopStyle == FFmpegNativeEngine.CanvasLoopStyle.BOOMERANG,
                    onClick = { onLoopStyleChanged(FFmpegNativeEngine.CanvasLoopStyle.BOOMERANG) },
                    label = { Text("🪃 Boomerang (Ping-Pong)", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Switch para Eliminación Inteligente de Silencios
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Eliminar silencios al inicio y final",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "Recorta inteligentemente espacios vacíos o silenciosos antes y después de la canción",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
            Switch(
                checked = trimSilence,
                onCheckedChange = onTrimSilenceChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF10B981)
                ),
                modifier = Modifier.testTag("download_trim_silence_switch")
            )
        }
    }
}
