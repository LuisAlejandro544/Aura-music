package com.example.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainActivity
import com.example.debug.ui.*
import com.example.ui.theme.AuraMusicTheme
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Actividad independiente de Diagnóstico y Monitor de Errores de Aura Music.
 * Accesible directamente desde el cajón de aplicaciones del teléfono con su propio icono.
 * Arquitectura Modular:
 * Delega los componentes visuales en el paquete [com.example.debug.ui]:
 * - [HardwareTelemetryCard]: Ficha técnica de hardware y entorno del teléfono móvil.
 * - [DebugFilterChips]: Selector de filtros por severidad con contadores en vivo.
 * - [DebugLogEntryCard]: Tarjeta individual para cada registro de error o advertencia.
 * - [DebugLogDetailDialog]: Modal con visualizador del Stack Trace completo en crudo.
 * - [DebugSyntheticTestDialog]: Menú para emitir eventos sintéticos de prueba.
 */
class DebugMonitorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AuraMusicTheme {
                DebugMonitorScreen(
                    onOpenMusicApp = {
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                    },
                    onFinish = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugMonitorScreen(
    onOpenMusicApp: () -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val allLogs by AuraDebugManager.logs.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf<DebugSeverity?>(null) }
    var selectedEntryForDetail by remember { mutableStateOf<DebugLogEntry?>(null) }
    var showTestMenu by remember { mutableStateOf(false) }

    val deviceInfo = remember { DeviceDiagnosticInfo.capture(context) }

    val filteredLogs = remember(allLogs, selectedFilter) {
        if (selectedFilter == null) allLogs else allLogs.filter { it.severity == selectedFilter }
    }

    val crashCount = remember(allLogs) { allLogs.count { it.severity == DebugSeverity.CRASH } }
    val criticalCount = remember(allLogs) { allLogs.count { it.severity == DebugSeverity.CRITICAL } }
    val errorCount = remember(allLogs) { allLogs.count { it.severity == DebugSeverity.ERROR } }
    val warningCount = remember(allLogs) { allLogs.count { it.severity == DebugSeverity.WARNING } }
    val infoCount = remember(allLogs) { allLogs.count { it.severity == DebugSeverity.INFO } }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark),
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Aura Monitor",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = if (crashCount > 0 || criticalCount > 0) Color(0xFFFF3B30) else Color(0xFF00E676),
                                modifier = Modifier.size(8.dp)
                            ) {}
                        }
                        Text(
                            text = if (crashCount > 0) "$crashCount fallos críticos detectados" else "Sistema estable (${allLogs.size} eventos)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (crashCount > 0) Color(0xFFFF8A80) else TextSecondary
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onFinish) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
                    }
                },
                actions = {
                    // Botón para generar eventos de prueba
                    IconButton(
                        onClick = { showTestMenu = true },
                        modifier = Modifier.testTag("debug_test_events_btn")
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = "Eventos de Prueba", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Botón para copiar reporte completo
                    IconButton(
                        onClick = {
                            val report = AuraDebugManager.generateFullReportText(context)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Aura Music Diagnostic Report", report))
                            Toast.makeText(context, "Reporte completo copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("debug_copy_report_btn")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar Reporte", tint = TextPrimary)
                    }

                    // Botón para abrir la app de música
                    IconButton(
                        onClick = onOpenMusicApp,
                        modifier = Modifier.testTag("debug_open_music_btn")
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = "Abrir Reproductor", tint = MaterialTheme.colorScheme.secondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Tarjeta de Hardware y Entorno del Teléfono
            item {
                HardwareTelemetryCard(deviceInfo = deviceInfo)
            }

            // Barra de Filtros y Contadores
            item {
                DebugFilterChips(
                    totalCount = allLogs.size,
                    filteredCount = filteredLogs.size,
                    crashCount = crashCount,
                    criticalCount = criticalCount,
                    errorCount = errorCount,
                    warningCount = warningCount,
                    infoCount = infoCount,
                    selectedFilter = selectedFilter,
                    onSelectFilter = { selectedFilter = it },
                    onClearLogs = {
                        AuraDebugManager.clearLogs()
                        Toast.makeText(context, "Historial de logs limpiado", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (filteredLogs.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceCard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Sin incidencias registradas",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Aura Music está operando con total estabilidad. Si surge algún crash o advertencia, se registrará aquí automáticamente.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredLogs, key = { it.id }) { logItem ->
                    DebugLogEntryCard(
                        entry = logItem,
                        onClick = { selectedEntryForDetail = logItem },
                        onCopyEntry = {
                            val textToCopy = """
                                [${logItem.severity.label}] ${logItem.dateTimeFormatted}
                                Tag: ${logItem.tag}
                                Hilo: ${logItem.deviceInfo.threadName}
                                Dispositivo: ${logItem.deviceInfo.manufacturer} ${logItem.deviceInfo.model} (${logItem.deviceInfo.androidVersion})
                                Mensaje: ${logItem.message}
                                Stack Trace:
                                ${logItem.rawStackTrace ?: "N/A"}
                            """.trimIndent()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Log Entry ${logItem.id}", textToCopy))
                            Toast.makeText(context, "Detalle del log copiado", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // Modal de Detalle Completo de Stack Trace en crudo
    if (selectedEntryForDetail != null) {
        val entry = selectedEntryForDetail!!
        DebugLogDetailDialog(
            entry = entry,
            onCopyAll = {
                val textToCopy = """
                    [${entry.severity.label}] ${entry.dateTimeFormatted}
                    Tag: ${entry.tag}
                    Hilo: ${entry.deviceInfo.threadName}
                    Dispositivo: ${entry.deviceInfo.manufacturer} ${entry.deviceInfo.model} (${entry.deviceInfo.androidVersion})
                    Mensaje: ${entry.message}
                    Stack Trace:
                    ${entry.rawStackTrace ?: "N/A"}
                """.trimIndent()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("Log Detail", textToCopy))
                Toast.makeText(context, "Error copiado al portapapeles", Toast.LENGTH_SHORT).show()
            },
            onDismissRequest = { selectedEntryForDetail = null }
        )
    }

    // Menú de generación de eventos de prueba
    if (showTestMenu) {
        DebugSyntheticTestDialog(
            onSimulateWarning = {
                AuraDebugManager.logWarning("TestSimulated", "Advertencia de prueba: Buffer underrun de audio controlado")
                showTestMenu = false
                Toast.makeText(context, "Warning de prueba registrado", Toast.LENGTH_SHORT).show()
            },
            onSimulateError = {
                val fakeException = IllegalStateException("Excepción simulada: Error al leer metadatos de audio en SAF")
                AuraDebugManager.logError("TestSimulated", "Fallo simulado en importador de música", fakeException)
                showTestMenu = false
                Toast.makeText(context, "Error con stacktrace registrado", Toast.LENGTH_SHORT).show()
            },
            onSimulateCritical = {
                val criticalEx = RuntimeException("Fallo crítico simulado: Buffer PCM desbordado en C++20 DSP Core")
                AuraDebugManager.logCritical("TestSimulated", "Incidencia crítica en motor de audio nativo", criticalEx)
                showTestMenu = false
                Toast.makeText(context, "Incidencia crítica registrada", Toast.LENGTH_SHORT).show()
            },
            onDismissRequest = { showTestMenu = false }
        )
    }
}
