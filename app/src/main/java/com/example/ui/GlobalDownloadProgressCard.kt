package com.example.ui
import com.example.core.model.LyricSearchResult

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.data.updater.AppReleaseUpdater
import com.example.core.model.AppUpdateState
import com.example.core.model.DownloadProgress
import com.example.core.model.HeadphoneConfig
import com.example.core.model.SleepTimerState
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.AudioEffectsBottomSheet
import com.example.ui.components.DownloadFromLinkDialog
import com.example.ui.components.SearchLyricsDialog
import com.example.ui.components.VideoToMusicDialog
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.MusicViewModel

/**
 * Tarjeta flotante de progreso en segundo plano.
 * Rol arquitectónico: anfitrión de diálogo global (fachada pública intacta).
 */
@Composable
fun GlobalDownloadProgressCard(
    downloadProgress: com.example.core.model.DownloadProgress,
    isWebLinkActive: Boolean
) {
    // 6. Tarjeta flotante de progreso de descarga en segundo plano
    if (downloadProgress.isDownloading && !isWebLinkActive) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 84.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            progress = { if (downloadProgress.totalBytes > 0) downloadProgress.progressFraction else 0f },
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = CardBorder,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = downloadProgress.phase.ifBlank { "Descargando..." },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            ),
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = downloadProgress.formattedSpeed,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { if (downloadProgress.totalBytes > 0) downloadProgress.progressFraction else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = CardBorder
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = downloadProgress.formattedProgress,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                }
            }
        }
    }
}
