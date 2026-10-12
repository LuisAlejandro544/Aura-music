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
 * Opciones locales (video a música, archivos, carpeta y demos).
 * Rol arquitectónico: accesos a importar desde el almacenamiento del teléfono.
 */
fun LazyListScope.importLocalOptionsItems(
    onPickVideo: () -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onSeedDemoTracks: () -> Unit
) {
            // Opción 2: Video a Música de Galería (Extraer Audio, Carátula y Video Canvas)
            item {
                ImportActionCard(
                    title = "Video a Música (Galería)",
                    description = "Elige un video de tu galería: extraerá el audio en alta fidelidad, capturará la portada en WebP y vinculará el Video Canvas automáticamente.",
                    buttonText = "Elegir Video de Galería",
                    icon = Icons.Default.MovieFilter,
                    accentGradient = Brush.horizontalGradient(
                        listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
                    ),
                    onClick = { onPickVideo() },
                    testTag = "import_video_to_music_btn"
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Opción 2: Seleccionar archivos
            item {
                ImportActionCard(
                    title = "Elegir Archivos de Audio",
                    description = "Selecciona una o varias canciones (MP3, FLAC, WAV, M4A, OGG) desde tu almacenamiento.",
                    buttonText = "Seleccionar Archivos",
                    icon = Icons.Default.AudioFile,
                    accentGradient = Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                    ),
                    onClick = { onPickFiles() },
                    testTag = "import_files_btn"
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Opción 2: Seleccionar carpeta
            item {
                ImportActionCard(
                    title = "Elegir Carpeta de Música",
                    description = "Elige una carpeta entera (ej: Music, Descargas). Se importarán todos los audios encontrados dentro.",
                    buttonText = "Seleccionar Carpeta",
                    icon = Icons.Default.FolderOpen,
                    accentGradient = Brush.horizontalGradient(
                        listOf(Color(0xFF06B6D4), Color(0xFF10B981))
                    ),
                    onClick = { onPickFolder() },
                    testTag = "import_folder_btn"
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Opción 3: Probar con canciones demo (Exclusivo en compilación Debug; oculto en APK Beta)
            if (com.example.BuildConfig.ENABLE_DEMO_TRACKS) {
                item {
                    ImportActionCard(
                        title = "Cargar Canciones Demo Synthwave",
                        description = "Añade pistas de demostración con audio sintetizado para probar el reproductor y el ecualizador ahora mismo.",
                        buttonText = "Añadir Demos",
                        icon = Icons.Default.ElectricBolt,
                        accentGradient = Brush.horizontalGradient(
                            listOf(Color(0xFFF97316), Color(0xFFF43F5E))
                        ),
                        onClick = onSeedDemoTracks,
                        testTag = "import_demos_btn"
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
}
