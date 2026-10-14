package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.model.AppUpdateState
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard

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
 *
 * v0.2.1-beta.1f (fix de diseño de la captura):
 * - Antes TODO el diálogo hacía `verticalScroll` y la caja de novedades también:
 *   doble scroll anidado que en pantallas de 720px recortaba los botones
 *   ("Más tarde" cortado, "Actualizar APK" fuera de vista) y dibujaba una
 *   píldora gris fantasma (thumb del scroll interno estirado).
 * - Ahora la estructura es fija: cabecera + chips + aviso (fijos arriba),
 *   novedades con scroll propio y altura acotada en el centro (weight 1f),
 *   y botones/estado fijos abajo. Sin scroll padre, sin píldora fantasma.
 */
@Composable
fun AppUpdateDialog(
    updateState: AppUpdateState,
    onStartDownload: () -> Unit,
    onDismiss: (rememberTag: String?) -> Unit
) {
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
                .heightIn(max = 620.dp)
                .testTag("app_update_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Cabecera con insignia Nebula / Canal Beta (fija)
                AppUpdateHeader(releaseInfo = releaseInfo)

                // Chips informativos de Arquitectura y Tamaño (fijos)
                AppUpdateChips(releaseInfo = releaseInfo)

                // Aviso de preservación de datos (fijo)
                AppUpdateDataNotice()

                // Notas del Pre-Release: única zona con scroll, ocupa el espacio libre.
                AppUpdateChangelog(
                    releaseInfo = releaseInfo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Estado dinámico fijo abajo: disponible, descargando o listo para instalar.
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
