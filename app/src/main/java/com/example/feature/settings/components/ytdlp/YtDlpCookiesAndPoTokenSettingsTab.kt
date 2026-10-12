package com.example.feature.settings.components.ytdlp

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.data.importer.YtDlpAutoUpdater
import com.example.core.data.importer.PoTokenGenerator
import com.example.core.data.importer.YtDlpCookieManager
import com.example.feature.settings.components.SettingDetailRow
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Pantalla Dedicada e Independiente de Descargas, Cookies de YouTube (Estilo YTDLnis),
 * PO Token Web y Control Máximo de yt-dlp OTA (< 500 líneas).
 *
 * Arquitectura:
 * 1. Estrategia Natural de yt-dlp: Usa `player_client=default` por defecto sin forzar clientes bloqueados.
 * 2. Soporte Opcional de Cookies: Permite importar o pegar `cookies.txt` (Netscape) para sortear bot-checks.
 * 3. Generador Ligero de PO Token Web: Sintetiza tokens de origen vinculados a Visitor Data.
 * 4. Control Máximo de Actualizador OTA: Búsqueda forzada, verificación de ejecución CLI y auto-check.
 */
fun LazyListScope.ytDlpCookiesAndPoTokenSettingsContent() {
    item {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        var cookieSummary by remember { mutableStateOf(YtDlpCookieManager.getCookieSummary(context)) }
        var currentStrategy by remember { mutableStateOf(YtDlpCookieManager.getPlayerClientStrategy(context)) }
        var isPoTokenEnabled by remember { mutableStateOf(YtDlpCookieManager.isPoTokenEnabled(context)) }
        var isAutoCheckStartup by remember { mutableStateOf(YtDlpCookieManager.isAutoCheckOtaStartupEnabled(context)) }

        var showPasteCookieDialog by remember { mutableStateOf(false) }
        var showManualPoTokenDialog by remember { mutableStateOf(false) }
        var showHowToExportCookiesGuide by remember { mutableStateOf(false) }

        var isTestingCli by remember { mutableStateOf(false) }
        var isGeneratingPoToken by remember { mutableStateOf(false) }
        var isUpdatingYtDlp by remember { mutableStateOf(false) }

        var statusFeedbackMessage by remember { mutableStateOf<String?>(null) }
        var statusFeedbackIsError by remember { mutableStateOf(false) }

        // Selector SAF para importar cookies.txt desde el almacenamiento
        val cookieFilePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                coroutineScope.launch {
                    val result = YtDlpCookieManager.saveCookiesFromUri(context, uri)
                    cookieSummary = YtDlpCookieManager.getCookieSummary(context)
                    if (result.isSuccess) {
                        statusFeedbackMessage = "¡Se importaron ${result.getOrNull()} cookies correctamente!"
                        statusFeedbackIsError = false
                    } else {
                        statusFeedbackMessage = "Error importando cookies: ${result.exceptionOrNull()?.message}"
                        statusFeedbackIsError = true
                    }
                }
            }
        }

        // Mensaje de feedback reactivo
        if (statusFeedbackMessage != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (statusFeedbackIsError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, if (statusFeedbackIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (statusFeedbackIsError) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (statusFeedbackIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = statusFeedbackMessage!!,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (statusFeedbackIsError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { statusFeedbackMessage = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }


        YtDlpStrategyCard(
            currentStrategy = currentStrategy,
            onSelectStrategy = {
                currentStrategy = it
                YtDlpCookieManager.setPlayerClientStrategy(context, it)
            }
        )

        Spacer(modifier = Modifier.height(18.dp))

        YtDlpCookiesCard(
            cookieSummary = cookieSummary,
            showGuide = showHowToExportCookiesGuide,
            onToggleEnabled = { enabled ->
                YtDlpCookieManager.setCookiesEnabled(context, enabled)
                cookieSummary = YtDlpCookieManager.getCookieSummary(context)
            },
            onImportClick = { cookieFilePickerLauncher.launch(arrayOf("text/plain", "*/*")) },
            onPasteClick = { showPasteCookieDialog = true },
            onClearCookies = {
                YtDlpCookieManager.clearCookies(context)
                cookieSummary = YtDlpCookieManager.getCookieSummary(context)
                statusFeedbackMessage = "Cookies eliminadas correctamente."
                statusFeedbackIsError = false
            },
            onToggleGuide = { showHowToExportCookiesGuide = !showHowToExportCookiesGuide }
        )

        Spacer(modifier = Modifier.height(18.dp))

        YtDlpPoTokenCard(
            isPoTokenEnabled = isPoTokenEnabled,
            isGeneratingPoToken = isGeneratingPoToken,
            onTogglePoToken = { enabled ->
                isPoTokenEnabled = enabled
                YtDlpCookieManager.setPoTokenEnabled(context, enabled)
            },
            onGeneratePoToken = {
                if (!isGeneratingPoToken) {
                    isGeneratingPoToken = true
                    coroutineScope.launch {
                        val gen = PoTokenGenerator.generateWebPoToken(context)
                        isGeneratingPoToken = false
                        isPoTokenEnabled = true
                        statusFeedbackMessage = gen.message
                        statusFeedbackIsError = !gen.success
                    }
                }
            },
            onManualPoToken = { showManualPoTokenDialog = true }
        )

        Spacer(modifier = Modifier.height(18.dp))

        YtDlpOtaControlCard(
            isAutoCheckStartup = isAutoCheckStartup,
            isUpdatingYtDlp = isUpdatingYtDlp,
            isTestingCli = isTestingCli,
            onToggleAutoCheck = { enabled ->
                isAutoCheckStartup = enabled
                YtDlpCookieManager.setAutoCheckOtaStartupEnabled(context, enabled)
            },
            onCheckUpdates = {
                if (!isUpdatingYtDlp) {
                    isUpdatingYtDlp = true
                    coroutineScope.launch {
                        val res = YtDlpAutoUpdater.checkAndUpdate(context, forceDownload = false)
                        isUpdatingYtDlp = false
                        when (res) {
                            is YtDlpAutoUpdater.UpdateResult.Updated -> {
                                statusFeedbackMessage = "¡yt-dlp actualizado con éxito a ${res.newVersion}!"
                                statusFeedbackIsError = false
                            }
                            is YtDlpAutoUpdater.UpdateResult.AlreadyUpToDate -> {
                                statusFeedbackMessage = "yt-dlp ya está en su última versión (${res.currentVersion})."
                                statusFeedbackIsError = false
                            }
                            is YtDlpAutoUpdater.UpdateResult.Error -> {
                                statusFeedbackMessage = "Error actualizando: ${res.message}"
                                statusFeedbackIsError = true
                            }
                        }
                    }
                }
            },
            onTestCli = {
                if (!isTestingCli) {
                    isTestingCli = true
                    coroutineScope.launch {
                        val (success, msg) = YtDlpAutoUpdater.testYtDlpCliExecution(context)
                        isTestingCli = false
                        statusFeedbackMessage = msg
                        statusFeedbackIsError = !success
                    }
                }
            },
            onForceDownload = {
                if (!isUpdatingYtDlp) {
                    isUpdatingYtDlp = true
                    coroutineScope.launch {
                        val res = YtDlpAutoUpdater.checkAndUpdate(context, forceDownload = true)
                        isUpdatingYtDlp = false
                        when (res) {
                            is YtDlpAutoUpdater.UpdateResult.Updated -> {
                                statusFeedbackMessage = "¡Reinstalación completada (${res.newVersion})!"
                                statusFeedbackIsError = false
                            }
                            else -> {
                                statusFeedbackMessage = "Resultado forzado: ${(res as? YtDlpAutoUpdater.UpdateResult.Error)?.message ?: "Completado"}"
                                statusFeedbackIsError = res is YtDlpAutoUpdater.UpdateResult.Error
                            }
                        }
                    }
                }
            }
        )

        YtDlpSettingsDialogs(
            showPasteDialog = showPasteCookieDialog,
            showManualPoTokenDialog = showManualPoTokenDialog,
            onDismissPaste = { showPasteCookieDialog = false },
            onDismissManual = { showManualPoTokenDialog = false },
            onSaveCookiesText = { pastedText ->
                showPasteCookieDialog = false
                coroutineScope.launch {
                    val res = YtDlpCookieManager.saveCookiesFromText(context, pastedText)
                    cookieSummary = YtDlpCookieManager.getCookieSummary(context)
                    if (res.isSuccess) {
                        statusFeedbackMessage = "¡Se guardaron ${res.getOrNull()} cookies!"
                        statusFeedbackIsError = false
                    } else {
                        statusFeedbackMessage = "Error: ${res.exceptionOrNull()?.message}"
                        statusFeedbackIsError = true
                    }
                }
            },
            onSavePoToken = { manualPoToken, manualVisitorData ->
                showManualPoTokenDialog = false
                if (manualPoToken.isNotBlank()) {
                    YtDlpCookieManager.savePoTokenData(context, manualPoToken, manualVisitorData)
                    YtDlpCookieManager.setPoTokenEnabled(context, true)
                    isPoTokenEnabled = true
                    statusFeedbackMessage = "PO Token manual guardado correctamente."
                    statusFeedbackIsError = false
                }
            }
        )
    }
}
