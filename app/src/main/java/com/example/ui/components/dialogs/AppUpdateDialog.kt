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
 * Diálogo interactivo para el Actualizador Automático de APK (OTA App Updater).
 *
 * Rol arquitectónico:
 * - Se presenta cuando [AppReleaseUpdater] detecta un nuevo Pre-Release `-beta` (o Release) en GitHub
 *   con un tag superior al instalado (`BuildConfig.VERSION_NAME`), sin importar cómo haya cambiado el tag.
 * - Muestra el nuevo Tag, el Codename (`Nebula`), la arquitectura exacta detectada en el teléfono
 *   (`arm64-v8a`, `armeabi-v7a` o `universal`), el tamaño del APK y las notas completas de `chanelog-beta.md`.
 * - Permite descargar el APK con barra de progreso en tiempo real, verificación SHA-256 y lanzar
 *   el instalador nativo de Android conservando intactos los datos en `Android/data/com.auramusic.beta/`.
 */
@Composable
fun AppUpdateDialog(
    updateState: AppUpdateState,
    onStartDownload: () -> Unit,
    onDismiss: (rememberTag: String?) -> Unit
) {
    val context = LocalContext.current

    val releaseInfo = when (updateState) {
        is AppUpdateState.UpdateAvailable -> updateState.releaseInfo
        is AppUpdateState.Downloading -> updateState.releaseInfo
        is AppUpdateState.ReadyToInstall -> updateState.releaseInfo
        else -> return
    }

    Dialog(
        onDismissRequest = {
            if (updateState !is AppUpdateState.Downloading) {
                onDismiss(null)
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = updateState !is AppUpdateState.Downloading,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 24.dp)
                .widthIn(max = 520.dp)
                .testTag("app_update_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Cabecera con insignia Nebula / Canal Beta
                AppUpdateHeader(releaseInfo = releaseInfo)

                // Chips informativos de Arquitectura y Tamaño
                AppUpdateChips(releaseInfo = releaseInfo)

                // Aviso de preservación de datos
                AppUpdateDataNotice()

                // Notas del Pre-Release (chanelog-beta.md)
                AppUpdateChangelog(releaseInfo = releaseInfo)

                // Estado dinámico: disponible, descargando o listo para instalar
                AppUpdateStatus(
                    updateState = updateState,
                    releaseInfo = releaseInfo,
                    onStartDownload = onStartDownload,
                    onDismiss = onDismiss
                )
            }
        }
    }
}
