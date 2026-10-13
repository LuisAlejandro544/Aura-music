package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.core.data.updater.AppReleaseUpdater
import com.example.core.model.AppUpdateState
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File


/**
 * Estado dinámico: disponible, descargando o listo para instalar.
 * Rol arquitectónico: sección de AppUpdateDialog (fachada pública intacta).
 */
@Composable
fun AppUpdateStatus(
    updateState: com.example.core.model.AppUpdateState,
    releaseInfo: com.example.core.model.AppUpdateReleaseInfo,
    onStartDownload: () -> Unit,
    onDismiss: (String?) -> Unit
) {
    val context = LocalContext.current
                // Estado dinámico: disponible, descargando o listo para instalar
                when (updateState) {
                    is AppUpdateState.Downloading -> {
                        val fraction = (updateState.progressPercent / 100f).coerceIn(0f, 1f)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = updateState.statusMessage,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${updateState.progressPercent}%",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = CardBorder
                            )
                        }
                    }

                    is AppUpdateState.ReadyToInstall -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (updateState.sha256Verified) {
                                        "APK descargado y verificado con SHA-256. Listo para instalar."
                                    } else {
                                        "APK descargado correctamente. Pulsa para abrir el instalador."
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onDismiss(null) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                ) {
                                    Text("Cerrar", color = TextSecondary)
                                }
                                Button(
                                    onClick = {
                                        AppReleaseUpdater.promptAndroidPackageInstaller(
                                            context = context,
                                            apkFile = File(updateState.apkFilePath)
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("install_downloaded_apk_btn")
                                ) {
                                    Text("Instalar APK Ahora", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onDismiss(releaseInfo.tagName) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Omitir esta versión", color = TextSecondary, fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            OutlinedButton(
                                onClick = { onDismiss(null) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Más tarde", color = TextSecondary)
                            }

                            Button(
                                onClick = onStartDownload,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("download_and_install_apk_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Actualizar APK", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
}
