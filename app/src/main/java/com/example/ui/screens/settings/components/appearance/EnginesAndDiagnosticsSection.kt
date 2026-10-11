package com.example.ui.screens.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Pantalla dedicada de Motores Nativos, Actualizador OTA de APK / yt-dlp y Diagnóstico (< 500 líneas).
 * Extraída modularmente para mantener cada archivo de Ajustes limpio y bajo el límite de 500 líneas.
 */
fun LazyListScope.enginesAndDiagnosticsSettingsContent(
    onOpenOnboarding: () -> Unit = {}
) {
    item {
        val context = LocalContext.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Arquitectura y Privacidad",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingDetailRow("Motor de Reproducción", "Jetpack Media3 ExoPlayer")
                SettingDetailRow("Motor DSP Nativo", "ISO C++20 con filtros Biquad 64-bit")
                SettingDetailRow("Persistencia Local", "Room Database SQLite")
                SettingDetailRow("Acceso a Archivos", "SAF (Storage Access Framework)")
                SettingDetailRow("Escaneo Automático", "Desactivado (100% bajo control del usuario)")
                SettingDetailRow("Motor Multimedia", if (com.example.data.importer.FFmpegNativeEngine.isAvailable(context)) "FFmpeg puro nativo (Activo)" else "FFmpeg puro nativo (Fallback MediaMuxer)")
                SettingDetailRow("Versión", "${com.example.BuildConfig.VERSION_NAME} (${com.example.BuildConfig.APP_CODENAME})")
                SettingDetailRow("Código de Build", "${com.example.BuildConfig.APP_BUILD_CODE} (#${com.example.BuildConfig.VERSION_CODE})")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Tarjeta del Actualizador Automático de Versiones APK (GitHub Releases / Pre-Releases -beta)
        AppReleaseUpdateSettingsCard()

        Spacer(modifier = Modifier.height(20.dp))

        // Tarjeta de Motores Multimedia: FFmpeg Puro & yt-dlp OTA (Activa en Debug, Beta y Release)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Motores de Extracción & yt-dlp OTA",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Aura Music utiliza FFmpeg puro sin wrapper para procesar y transcodificar audio, con soporte de actualización en caliente de yt-dlp para sortear parches de YouTube sin requerir un nuevo APK.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(12.dp))

                var ytdlpVersion by remember { mutableStateOf(com.example.data.importer.YtDlpAutoUpdater.getInstalledVersion(context)) }
                var isUpdating by remember { mutableStateOf(false) }
                var updateMessage by remember { mutableStateOf<String?>(null) }
                val coroutineScope = rememberCoroutineScope()

                SettingDetailRow(
                    "FFmpeg Puro",
                    if (com.example.data.importer.FFmpegNativeEngine.isAvailable(context)) "Listo en dispositivo" else "Habilitado (CLI / NDK)"
                )
                SettingDetailRow("Versión yt-dlp", ytdlpVersion)

                if (updateMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = updateMessage!!,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = {
                        if (!isUpdating) {
                            isUpdating = true
                            updateMessage = "Consultando versión más reciente en GitHub..."
                            coroutineScope.launch {
                                val result = com.example.data.importer.YtDlpAutoUpdater.checkAndUpdate(context, forceDownload = false)
                                isUpdating = false
                                when (result) {
                                    is com.example.data.importer.YtDlpAutoUpdater.UpdateResult.Updated -> {
                                        ytdlpVersion = result.newVersion
                                        updateMessage = "¡Actualizado con éxito a ${result.newVersion}!"
                                    }
                                    is com.example.data.importer.YtDlpAutoUpdater.UpdateResult.AlreadyUpToDate -> {
                                        ytdlpVersion = result.currentVersion
                                        updateMessage = "yt-dlp ya está al día (${result.currentVersion})."
                                    }
                                    is com.example.data.importer.YtDlpAutoUpdater.UpdateResult.Error -> {
                                        updateMessage = "Estado: ${result.message}"
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Comprobando...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buscar Actualizaciones de yt-dlp (OTA)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Acceso directo a Aura Monitor: EXCLUSIVO de compilación Debug (Eliminado en APK Beta y Release)
        if (com.example.BuildConfig.ENABLE_DEBUG_MONITOR) {
            Spacer(modifier = Modifier.height(20.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Aura Monitor (Telemetría & Diagnóstico)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Inspecciona el consumo de RAM en tiempo real (Java, C++ Nativo, PSS), la carga de procesador, los hilos de ejecución activos y el registro detallado de incidentes.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            val intent = android.content.Intent(context, com.example.debug.DebugMonitorActivity::class.java)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_aura_monitor_btn")
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Abrir Aura Monitor", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
