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

        // ==========================================
        // 1. ESTRATEGIA NATURAL DE YT-DLP (PLAYER_CLIENT)
        // ==========================================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Estrategia Natural de yt-dlp",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Configura cómo interactúa yt-dlp con los servidores de YouTube. La estrategia natural ('default') utiliza la rotación interna del motor para evitar bloqueos de clientes deprecados.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = currentStrategy == YtDlpCookieManager.STRATEGY_DEFAULT,
                        onClick = {
                            currentStrategy = YtDlpCookieManager.STRATEGY_DEFAULT
                            YtDlpCookieManager.setPlayerClientStrategy(context, YtDlpCookieManager.STRATEGY_DEFAULT)
                        },
                        label = { Text("Default (Natural)", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = if (currentStrategy == YtDlpCookieManager.STRATEGY_DEFAULT) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = currentStrategy == YtDlpCookieManager.STRATEGY_MWEB_AND_WEB,
                        onClick = {
                            currentStrategy = YtDlpCookieManager.STRATEGY_MWEB_AND_WEB
                            YtDlpCookieManager.setPlayerClientStrategy(context, YtDlpCookieManager.STRATEGY_MWEB_AND_WEB)
                        },
                        label = { Text("MWeb + Web", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = if (currentStrategy == YtDlpCookieManager.STRATEGY_MWEB_AND_WEB) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ==========================================
        // 2. SOPORTE OPCIONAL DE COOKIES (ESTILO YTDLNIS)
        // ==========================================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cookies de YouTube (Estilo YTDLnis)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                    Switch(
                        checked = cookieSummary.isEnabled,
                        onCheckedChange = { enabled ->
                            YtDlpCookieManager.setCookiesEnabled(context, enabled)
                            cookieSummary = YtDlpCookieManager.getCookieSummary(context)
                        },
                        modifier = Modifier.testTag("ytdlp_cookies_toggle")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Opcional. Permite usar un archivo 'cookies.txt' exportado desde tu navegador para descargar videos con restricción de edad o sortear verificaciones de bots sin requerir iniciar sesión en la app.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (cookieSummary.fileExists && cookieSummary.isEnabled) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (cookieSummary.fileExists && cookieSummary.isEnabled) MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f) else CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (cookieSummary.fileExists) Icons.Default.Description else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (cookieSummary.fileExists) MaterialTheme.colorScheme.secondary else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (cookieSummary.fileExists) "Archivo cookies.txt cargado" else "Sin archivo de cookies",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }
                        if (cookieSummary.fileExists) {
                            Text(
                                text = "Tamaño: ${cookieSummary.sizeBytes / 1024} KB • ${cookieSummary.validCookieCount} cookies detectadas",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                            Text(
                                text = "Última actualización: ${cookieSummary.lastModifiedFormatted}",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Botones de acción para Cookies
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { cookieFilePickerLauncher.launch(arrayOf("text/plain", "*/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Importar Archivo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { showPasteCookieDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pegar Texto", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (cookieSummary.fileExists) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            YtDlpCookieManager.clearCookies(context)
                            cookieSummary = YtDlpCookieManager.getCookieSummary(context)
                            statusFeedbackMessage = "Cookies eliminadas correctamente."
                            statusFeedbackIsError = false
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Eliminar Cookies Guardadas", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = { showHowToExportCookiesGuide = !showHowToExportCookiesGuide },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(
                        imageVector = if (showHowToExportCookiesGuide) Icons.Default.ExpandLess else Icons.Default.HelpOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showHowToExportCookiesGuide) "Ocultar guía" else "¿Cómo exportar cookies desde el celular?",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                AnimatedVisibility(visible = showHowToExportCookiesGuide) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Instrucciones paso a paso:",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )
                            Text(
                                text = "1. Usa Kiwi Browser o Firefox en tu teléfono con la extensión 'Get cookies.txt LOCALLY'.\n2. Inicia sesión en YouTube desde dicho navegador.\n3. Abre la extensión y descarga 'cookies.txt'.\n4. Toca 'Importar Archivo' aquí y selecciónalo.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 12.sp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ==========================================
        // 3. GENERADOR LIGERO DE PO TOKEN WEB
        // ==========================================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generador Ligero de PO Token Web",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                    Switch(
                        checked = isPoTokenEnabled,
                        onCheckedChange = { enabled ->
                            isPoTokenEnabled = enabled
                            YtDlpCookieManager.setPoTokenEnabled(context, enabled)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Obtiene y sintetiza un Proof of Origin Token (PO Token) vinculado a YouTube Web para que yt-dlp consulte los flujos legítimamente.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )

                Spacer(modifier = Modifier.height(12.dp))

                val savedPo = YtDlpCookieManager.getSavedPoToken(context)
                val visitorData = YtDlpCookieManager.getSavedVisitorData(context)
                val lastGen = YtDlpCookieManager.getPoTokenLastGeneratedFormatted(context)

                SettingDetailRow("Estado PO Token", if (savedPo != null && isPoTokenEnabled) "Activo (Web GVS)" else "Sin generar")
                SettingDetailRow("Última Generación", lastGen)
                if (visitorData != null && visitorData.isNotBlank()) {
                    SettingDetailRow("Visitor Data", visitorData.take(18) + "...")
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isGeneratingPoToken) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generando...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generar PO Token", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = { showManualPoTokenDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PO Token Manual", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ==========================================
        // 4. CONTROL MÁXIMO DE YT-DLP OTA
        // ==========================================
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
                        text = "Actualizador OTA de yt-dlp al Máximo",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Aura Music actualiza yt-dlp en caliente sin requerir un nuevo APK. Mantén tu motor al día para recibir inmediatamente las correcciones de YouTube.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )

                Spacer(modifier = Modifier.height(12.dp))

                var currentVersion by remember { mutableStateOf(YtDlpAutoUpdater.getInstalledVersion(context)) }
                SettingDetailRow("Versión Instalada", currentVersion)

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Verificar actualizaciones al iniciar",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    )
                    Switch(
                        checked = isAutoCheckStartup,
                        onCheckedChange = { enabled ->
                            isAutoCheckStartup = enabled
                            YtDlpCookieManager.setAutoCheckOtaStartupEnabled(context, enabled)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (!isUpdatingYtDlp) {
                                isUpdatingYtDlp = true
                                coroutineScope.launch {
                                    val res = YtDlpAutoUpdater.checkAndUpdate(context, forceDownload = false)
                                    isUpdatingYtDlp = false
                                    when (res) {
                                        is YtDlpAutoUpdater.UpdateResult.Updated -> {
                                            currentVersion = res.newVersion
                                            statusFeedbackMessage = "¡yt-dlp actualizado con éxito a ${res.newVersion}!"
                                            statusFeedbackIsError = false
                                        }
                                        is YtDlpAutoUpdater.UpdateResult.AlreadyUpToDate -> {
                                            currentVersion = res.currentVersion
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isUpdatingYtDlp) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Comprobando en GitHub...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buscar Actualizaciones OTA en GitHub", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
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
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isTestingCli) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Probar CLI", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                if (!isUpdatingYtDlp) {
                                    isUpdatingYtDlp = true
                                    coroutineScope.launch {
                                        val res = YtDlpAutoUpdater.checkAndUpdate(context, forceDownload = true)
                                        isUpdatingYtDlp = false
                                        when (res) {
                                            is YtDlpAutoUpdater.UpdateResult.Updated -> {
                                                currentVersion = res.newVersion
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
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Forzar Descarga", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Diálogo para pegar Cookies de texto
        if (showPasteCookieDialog) {
            var pastedText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showPasteCookieDialog = false },
                title = { Text("Pegar Cookies de YouTube", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Pega aquí el contenido en formato Netscape de tu archivo cookies.txt o el header HTTP Cookie:",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                        OutlinedTextField(
                            value = pastedText,
                            onValueChange = { pastedText = it },
                            placeholder = { Text("# Netscape HTTP Cookie File...\n.youtube.com\tTRUE\t/\tTRUE...", fontSize = 11.sp) },
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
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
                        }
                    ) {
                        Text("Guardar Cookies")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasteCookieDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Diálogo para PO Token Manual
        if (showManualPoTokenDialog) {
            var manualPoToken by remember { mutableStateOf(YtDlpCookieManager.getSavedPoToken(context) ?: "") }
            var manualVisitorData by remember { mutableStateOf(YtDlpCookieManager.getSavedVisitorData(context) ?: "") }
            AlertDialog(
                onDismissRequest = { showManualPoTokenDialog = false },
                title = { Text("PO Token Manual", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Si generaste un PO Token en tu PC o navegador, ingrésalo aquí:",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                        OutlinedTextField(
                            value = manualPoToken,
                            onValueChange = { manualPoToken = it },
                            label = { Text("PO Token") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = manualVisitorData,
                            onValueChange = { manualVisitorData = it },
                            label = { Text("Visitor Data (Opcional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showManualPoTokenDialog = false
                            if (manualPoToken.isNotBlank()) {
                                YtDlpCookieManager.savePoTokenData(context, manualPoToken, manualVisitorData)
                                YtDlpCookieManager.setPoTokenEnabled(context, true)
                                isPoTokenEnabled = true
                                statusFeedbackMessage = "PO Token manual guardado correctamente."
                                statusFeedbackIsError = false
                            }
                        }
                    ) {
                        Text("Guardar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualPoTokenDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
