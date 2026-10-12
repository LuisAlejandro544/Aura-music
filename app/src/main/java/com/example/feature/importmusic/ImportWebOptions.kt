package com.example.feature.importmusic

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.data.importer.FFmpegNativeEngine
import com.example.core.data.importer.OnlineVideoAudioImporter
import com.example.core.model.DownloadProgress
import com.example.core.model.Track
import com.example.ui.components.DownloadFromLinkDialog
import com.example.ui.components.DownloadSourceMode
import com.example.ui.components.VideoToMusicDialog
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Opciones de descarga web (TikTok, YouTube y playlists externas).
 * Rol arquitectónico: accesos a importar desde enlaces y listas públicas.
 */
fun LazyListScope.importWebOptionsItems(
    onOpenTikTokLink: () -> Unit,
    onOpenYouTubeLink: () -> Unit,
    onOpenPlaylistImportDialog: () -> Unit
) {
            // Opción 1: Descargar desde TikTok (Música, Carátula y Video Canvas)
            item {
                ImportActionCard(
                    title = "Descargar desde TikTok",
                    description = "Pega un enlace de video de TikTok: descarga la música en alta fidelidad de cualquier duración, con carátula oficial y Video Canvas de fondo.",
                    buttonText = "Pegar Enlace de TikTok",
                    icon = Icons.Default.MusicNote,
                    accentGradient = Brush.horizontalGradient(
                        listOf(Color(0xFF00F2FE), Color(0xFF4FACFE), Color(0xFFFF007F))
                    ),
                    onClick = { onOpenTikTokLink() },
                    testTag = "import_tiktok_link_btn"
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Opción 2: Descargar desde YouTube / Enlace Web
            item {
                ImportActionCard(
                    title = "Descargar desde YouTube / Web",
                    description = "Pega un enlace de YouTube o video web: extrae el audio de alta fidelidad con resolución rápida y optimizada, carátula en WebP y Video Canvas sincronizado.",
                    buttonText = "Pegar Enlace de Video",
                    icon = Icons.Default.SmartDisplay,
                    accentGradient = Brush.horizontalGradient(
                        listOf(Color(0xFFFF0033), Color(0xFFFF3366), Color(0xFFFF8800))
                    ),
                    onClick = { onOpenYouTubeLink() },
                    testTag = "import_youtube_link_btn"
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Opción 3: Importador Universal de Playlists (Spotify y YouTube Music / YouTube)
            item {
                ImportActionCard(
                    title = "Importar Playlist (Spotify / YT Music)",
                    description = "Pega el enlace de cualquier Playlist pública de Spotify o YouTube Music: impórtala en segundos en Modo Streaming o descarga todas sus canciones offline.",
                    buttonText = "Importar Playlist",
                    icon = Icons.Default.PlaylistAdd,
                    accentGradient = Brush.horizontalGradient(
                        listOf(Color(0xFF1DB954), Color(0xFF10B981), Color(0xFFFF0033))
                    ),
                    onClick = onOpenPlaylistImportDialog,
                    testTag = "import_external_playlist_card_btn"
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
}
