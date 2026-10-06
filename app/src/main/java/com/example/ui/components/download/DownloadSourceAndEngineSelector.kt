package com.example.ui.components.download

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.importer.YtDlpAutoUpdater
import com.example.data.importer.YoutubeExtractionEngine
import com.example.model.PackageUpdateState
import com.example.ui.components.DownloadSourceMode
import com.example.ui.theme.TextSecondary

/**
 * Aura Music - Componente Modular de Selector de Fuente y Motor de Extracción
 *
 * Permite alternar entre TikTok y YouTube / Web, seleccionar el motor (yt-dlp, InnerTube, WebView)
 * y muestra advertencias informativas si yt-dlp está en proceso de actualización de paquetes.
 */
@Composable
fun DownloadSourceAndEngineSelector(
    selectedMode: DownloadSourceMode,
    onModeSelected: (DownloadSourceMode) -> Unit,
    selectedEngine: YoutubeExtractionEngine,
    onEngineSelected: (YoutubeExtractionEngine) -> Unit,
    packageUpdateState: PackageUpdateState,
    isYtDlpBlocked: Boolean,
    context: Context,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Selector de fuente (TikTok vs YouTube / Web)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedMode == DownloadSourceMode.TIKTOK,
                onClick = { onModeSelected(DownloadSourceMode.TIKTOK) },
                label = { Text("TikTok", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
            FilterChip(
                selected = selectedMode == DownloadSourceMode.YOUTUBE_WEB,
                onClick = { onModeSelected(DownloadSourceMode.YOUTUBE_WEB) },
                label = { Text("YouTube / Web", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(Icons.Default.SmartDisplay, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
        }

        // Selector de motor para YouTube / Web
        if (selectedMode == DownloadSourceMode.YOUTUBE_WEB) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Motor de extracción:",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedEngine == YoutubeExtractionEngine.YTDLP,
                    onClick = { onEngineSelected(YoutubeExtractionEngine.YTDLP) },
                    label = { Text("⚡ yt-dlp + FFmpeg", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedEngine == YoutubeExtractionEngine.INNERTUBE,
                    onClick = { onEngineSelected(YoutubeExtractionEngine.INNERTUBE) },
                    label = { Text("InnerTube", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedEngine == YoutubeExtractionEngine.WEBVIEW,
                    onClick = { onEngineSelected(YoutubeExtractionEngine.WEBVIEW) },
                    label = { Text("WebView", fontSize = 11.sp) }
                )
            }
            Text(
                text = when (selectedEngine) {
                    YoutubeExtractionEngine.YTDLP -> "Extractor local avanzado con soporte para sortear firmas n-sig y protección de bots."
                    YoutubeExtractionEngine.INNERTUBE -> "API nativa directa de YouTube Music. Rápida, gratis y sin consumo de batería."
                    YoutubeExtractionEngine.WEBVIEW -> "Navegador efímero en segundo plano que ejecuta el reproductor en memoria."
                },
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
                modifier = Modifier.padding(top = 2.dp, start = 2.dp)
            )

            if (isYtDlpBlocked) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ytdlp_locked_warning_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        val lockTitle = when (val st = packageUpdateState) {
                            is PackageUpdateState.Downloading ->
                                "Descargando actualización de yt-dlp (${st.progressPercent}%)"
                            is PackageUpdateState.RestartRequired ->
                                "Paquete yt-dlp (${st.version}) listo para aplicar"
                            else -> "Actualizando paquetes de yt-dlp"
                        }
                        Text(
                            text = lockTitle,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "La descarga de videos por yt-dlp está bloqueada temporalmente hasta que se aplique la actualización. Sal de la app o actualiza ahora.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.88f)
                            )
                        )
                        if (packageUpdateState is PackageUpdateState.RestartRequired) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    YtDlpAutoUpdater.applyPendingUpdateAndRestart(context)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dialog_apply_ytdlp_restart_btn")
                            ) {
                                Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Actualizar y Reiniciar ahora", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
