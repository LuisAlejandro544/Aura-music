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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainActivity
import com.example.ui.theme.AuraMusicTheme
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Actividad independiente de Diagnóstico y Monitor de Errores de Aura Music.
 * Accesible directamente desde el cajón de aplicaciones del teléfono con su propio icono.
 * Permite auditar en tiempo real:
 * - Cierres inesperados (Crashes) y trazas de pila (Stack Trace en crudo).
 * - Errores críticos de Media3 / ExoPlayer / JNI C++20 y anomalías de memoria.
 * - Ficha de telemetría del teléfono móvil (RAM, CPU ABI, Versión de Android).
 * - Exportación / Copia del reporte completo de diagnóstico.
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
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Historial de Diagnóstico (${filteredLogs.size})",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )

                        if (allLogs.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    AuraDebugManager.clearLogs()
                                    Toast.makeText(context, "Historial de logs limpiado", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Limpiar Todo", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChipItem(
                                label = "Todos (${allLogs.size})",
                                isSelected = selectedFilter == null,
                                color = MaterialTheme.colorScheme.primary,
                                onClick = { selectedFilter = null }
                            )
                        }
                        item {
                            FilterChipItem(
                                label = "Crash ($crashCount)",
                                isSelected = selectedFilter == DebugSeverity.CRASH,
                                color = DebugSeverity.CRASH.color,
                                onClick = { selectedFilter = DebugSeverity.CRASH }
                            )
                        }
                        item {
                            FilterChipItem(
                                label = "Crítico ($criticalCount)",
                                isSelected = selectedFilter == DebugSeverity.CRITICAL,
                                color = DebugSeverity.CRITICAL.color,
                                onClick = { selectedFilter = DebugSeverity.CRITICAL }
                            )
                        }
                        item {
                            FilterChipItem(
                                label = "Error ($errorCount)",
                                isSelected = selectedFilter == DebugSeverity.ERROR,
                                color = DebugSeverity.ERROR.color,
                                onClick = { selectedFilter = DebugSeverity.ERROR }
                            )
                        }
                        item {
                            FilterChipItem(
                                label = "Warning ($warningCount)",
                                isSelected = selectedFilter == DebugSeverity.WARNING,
                                color = DebugSeverity.WARNING.color,
                                onClick = { selectedFilter = DebugSeverity.WARNING }
                            )
                        }
                        item {
                            FilterChipItem(
                                label = "Info ($infoCount)",
                                isSelected = selectedFilter == DebugSeverity.INFO,
                                color = DebugSeverity.INFO.color,
                                onClick = { selectedFilter = DebugSeverity.INFO }
                            )
                        }
                    }
                }
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
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary
                                ),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredLogs, key = { it.id }) { logItem ->
                    LogEntryCard(
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
        AlertDialog(
            onDismissRequest = { selectedEntryForDetail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = entry.severity.color.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, entry.severity.color)
                    ) {
                        Text(
                            text = entry.severity.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = entry.severity.color
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = entry.tag,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Hora: ${entry.dateTimeFormatted}",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                    Text(
                        text = "Hilo: ${entry.deviceInfo.threadName}",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                    Text(
                        text = "Dispositivo: ${entry.deviceInfo.manufacturer} ${entry.deviceInfo.model} (${entry.deviceInfo.androidVersion})",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Mensaje:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                    )
                    Text(
                        text = entry.message,
                        style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
                    )

                    if (!entry.rawStackTrace.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Stack Trace en Crudo:",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = entry.severity.color
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0F111A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2E3D)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = entry.rawStackTrace,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFFFFB4A9)
                                ),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
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
                    }
                ) {
                    Text("Copiar Todo", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedEntryForDetail = null }) {
                    Text("Cerrar", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard
        )
    }

    // Menú de generación de eventos de prueba
    if (showTestMenu) {
        AlertDialog(
            onDismissRequest = { showTestMenu = false },
            title = {
                Text("Simular Eventos de Diagnóstico", style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Genera logs de prueba para comprobar que el monitor captura errores en crudo, advertencias y telemetría correctamente:",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )

                    Button(
                        onClick = {
                            AuraDebugManager.logWarning("TestSimulated", "Advertencia de prueba: Buffer underrun de audio controlado")
                            showTestMenu = false
                            Toast.makeText(context, "Warning de prueba registrado", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DebugSeverity.WARNING.color.copy(alpha = 0.8f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Simular WARNING (Ámbar)", color = Color.Black)
                    }

                    Button(
                        onClick = {
                            val fakeException = IllegalStateException("Excepción simulada: Error al leer metadatos de audio en SAF")
                            AuraDebugManager.logError("TestSimulated", "Fallo simulado en importador de música", fakeException)
                            showTestMenu = false
                            Toast.makeText(context, "Error con stacktrace registrado", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DebugSeverity.ERROR.color),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Simular ERROR (Carmín)", color = Color.White)
                    }

                    Button(
                        onClick = {
                            val criticalEx = RuntimeException("Fallo crítico simulado: Buffer PCM desbordado en C++20 DSP Core")
                            AuraDebugManager.logCritical("TestSimulated", "Incidencia crítica en motor de audio nativo", criticalEx)
                            showTestMenu = false
                            Toast.makeText(context, "Incidencia crítica registrada", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DebugSeverity.CRITICAL.color),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Simular CRÍTICO (Naranja)", color = Color.White)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTestMenu = false }) {
                    Text("Cerrar", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard
        )
    }
}

@Composable
private fun HardwareTelemetryCard(deviceInfo: DeviceDiagnosticInfo) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Datos del Teléfono",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = deviceInfo.androidVersion,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Modelo", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                    Text(
                        text = "${deviceInfo.manufacturer} ${deviceInfo.model}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text("Arquitectura CPU", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                    Text(
                        text = deviceInfo.supportedAbis.split(",").firstOrNull()?.trim() ?: "N/A",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Memoria RAM", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                    Text(
                        text = if (deviceInfo.totalRamMb > 0) "${deviceInfo.availableRamMb} MB / ${deviceInfo.totalRamMb} MB" else "No disponible",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text("Almacenamiento Libre", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                    Text(
                        text = if (deviceInfo.availableStorageMb > 0) "${deviceInfo.availableStorageMb} MB" else "No disponible",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) color.copy(alpha = 0.25f) else SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isSelected) color else Color(0xFF2A2E3D)
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) color else TextSecondary
            ),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun LogEntryCard(
    entry: DebugLogEntry,
    onClick: () -> Unit,
    onCopyEntry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = entry.severity.color.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("debug_log_card_${entry.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = entry.severity.color.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = entry.severity.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = entry.severity.color
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = entry.tag,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                Text(
                    text = entry.dateTimeFormatted.substringAfter(" "),
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!entry.rawStackTrace.isNullOrBlank()) {
                    Text(
                        text = "Stacktrace disponible (Toca para ver)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = entry.severity.color,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                } else {
                    Text(
                        text = "Hilo: ${entry.deviceInfo.threadName}",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                    )
                }

                IconButton(
                    onClick = onCopyEntry,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copiar entrada",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
