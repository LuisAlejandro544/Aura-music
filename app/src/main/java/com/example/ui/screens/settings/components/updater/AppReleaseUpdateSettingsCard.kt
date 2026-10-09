package com.example.ui.screens.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.updater.AppReleaseUpdater
import com.example.model.AppUpdateState
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

/**
 * Tarjeta dedicada de Ajustes para el Actualizador de Versiones APK (GitHub Releases & Pre-Releases `-beta`).
 *
 * Rol arquitectónico:
 * - Permite al usuario configurar o verificar el repositorio de GitHub (`usuario/repositorio`),
 *   activar o desactivar la búsqueda automática al iniciar y buscar manualmente nuevas versiones APK
 *   sin importar cómo cambien los tags (`v0.1.0-beta.1a` -> `v0.1.0-beta.2d` -> `v0.2.0-beta.1m`).
 * - Muestra la versión instalada, el Codename (`Nebula`), el código de compilación (`NEBULA-00101A`)
 *   y la arquitectura detectada del teléfono (`arm64-v8a` / `armeabi-v7a`).
 */
@Composable
fun AppReleaseUpdateSettingsCard() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateState by AppReleaseUpdater.updateState.collectAsState()

    var repoSlugInput by remember { mutableStateOf(AppReleaseUpdater.getConfiguredRepositorySlug(context)) }
    var autoCheckEnabled by remember { mutableStateOf(AppReleaseUpdater.isAutoCheckOnStartEnabled(context)) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    val detectedAbi = remember { AppReleaseUpdater.getDevicePrimaryMobileAbi() }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_release_updater_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Actualizador de Versión APK (GitHub Releases)",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Busca automáticamente nuevos Pre-Releases (-beta) en GitHub aunque el tag cambie (ej: v0.1.0-beta.1a → v0.1.0-beta.2d), elige el APK exacto para tu procesador ($detectedAbi) e instala sin perder tus canciones ni listas.",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingDetailRow("Versión Instalada", "${BuildConfig.VERSION_NAME} (${BuildConfig.APP_CODENAME})")
            SettingDetailRow("Código Estratégico", "${BuildConfig.APP_BUILD_CODE} (#${BuildConfig.VERSION_CODE})")
            SettingDetailRow("Arquitectura Detectada", "$detectedAbi (Selección automática de APK)")

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = repoSlugInput,
                onValueChange = { newValue ->
                    repoSlugInput = newValue
                    AppReleaseUpdater.setConfiguredRepositorySlug(context, newValue)
                },
                label = { Text("Repositorio GitHub (usuario/repositorio)") },
                placeholder = { Text("LuisAlejandro544/Aura-music") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("github_repo_slug_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Buscar nueva versión APK al abrir la app",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "Revisa silenciosamente si hay un Pre-Release más reciente en segundo plano.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = autoCheckEnabled,
                    onCheckedChange = { enabled ->
                        autoCheckEnabled = enabled
                        AppReleaseUpdater.setAutoCheckOnStartEnabled(context, enabled)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // Mensaje de estado del actualizador
            val statusText = when (val st = updateState) {
                is AppUpdateState.Checking -> st.message
                is AppUpdateState.UpToDate -> st.message
                is AppUpdateState.UpdateAvailable -> "¡Nueva versión ${st.releaseInfo.tagName} (${st.releaseInfo.matchedAbi}) lista para descargar!"
                is AppUpdateState.Downloading -> st.statusMessage
                is AppUpdateState.ReadyToInstall -> "APK ${st.releaseInfo.tagName} descargado. Pulsa para instalar."
                is AppUpdateState.Error -> st.message
                else -> feedbackMessage
            }

            if (!statusText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                val statusColor = when (updateState) {
                    is AppUpdateState.Error -> MaterialTheme.colorScheme.error
                    is AppUpdateState.UpdateAvailable, is AppUpdateState.ReadyToInstall -> Color(0xFF10B981)
                    else -> MaterialTheme.colorScheme.primary
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        AppReleaseUpdater.setConfiguredRepositorySlug(context, repoSlugInput)
                        feedbackMessage = null
                        coroutineScope.launch {
                            AppReleaseUpdater.checkForUpdates(context, manualCheck = true)
                        }
                    },
                    enabled = updateState !is AppUpdateState.Checking && updateState !is AppUpdateState.Downloading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("check_apk_update_btn")
                ) {
                    if (updateState is AppUpdateState.Checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buscando...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buscar Nueva Versión APK", fontWeight = FontWeight.Bold)
                    }
                }

                if (updateState is AppUpdateState.ReadyToInstall) {
                    val ready = updateState as AppUpdateState.ReadyToInstall
                    OutlinedButton(
                        onClick = {
                            AppReleaseUpdater.promptAndroidPackageInstaller(context, File(ready.apkFilePath))
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Instalar", fontWeight = FontWeight.Bold)
                    }
                } else if (updateState is AppUpdateState.UpdateAvailable) {
                    val avail = updateState as AppUpdateState.UpdateAvailable
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                AppReleaseUpdater.downloadAndInstallUpdate(context, avail.releaseInfo)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Descargar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
