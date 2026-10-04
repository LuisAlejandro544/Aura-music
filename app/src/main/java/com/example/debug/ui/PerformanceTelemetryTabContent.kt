package com.example.debug.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.debug.FullPerformanceSnapshot
import com.example.debug.PerformanceTelemetryManager
import com.example.debug.ThreadCategory
import com.example.debug.ThreadDiagnosticItem
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Pestaña modular de Telemetría de Rendimiento, Consumo de RAM segmentado e Inspector de Hilos.
 * Diseñada para permitir el diagnóstico en caliente en dispositivos móviles sin necesidad de PC.
 */
@Composable
fun PerformanceTelemetryTabContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(PerformanceTelemetryManager.captureSnapshot(context)) }
    var autoRefresh by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<ThreadCategory?>(null) }
    var onlyRunnable by remember { mutableStateOf(false) }
    var expandedThreadId by remember { mutableStateOf<Long?>(null) }

    // Auto-actualización periódica en vivo cada 2.5 segundos
    LaunchedEffect(autoRefresh) {
        while (isActive && autoRefresh) {
            snapshot = PerformanceTelemetryManager.captureSnapshot(context)
            delay(2500L)
        }
    }

    val filteredThreads = remember(snapshot.threads, searchQuery, selectedCategoryFilter, onlyRunnable) {
        snapshot.threads.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.topStackTrace.contains(searchQuery, ignoreCase = true)
            val matchesCat = selectedCategoryFilter == null || item.category == selectedCategoryFilter
            val matchesRunnable = !onlyRunnable || item.state == Thread.State.RUNNABLE
            matchesQuery && matchesCat && matchesRunnable
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp)
    ) {
        // Barra de control de actualización
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (autoRefresh) Color(0xFF00E676) else TextSecondary,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (autoRefresh) "Monitoreo en vivo activo (2.5s)" else "Monitoreo en pausa",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (autoRefresh) Color(0xFF00E676) else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                snapshot = PerformanceTelemetryManager.captureSnapshot(context)
                                Toast.makeText(context, "Métricas actualizadas", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualizar ahora", tint = MaterialTheme.colorScheme.primary)
                        }
                        Switch(
                            checked = autoRefresh,
                            onCheckedChange = { autoRefresh = it },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        }

        // Tarjeta de Consumo de Memoria RAM Detallado
        item {
            val mem = snapshot.memory
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CONSUMO DE MEMORIA RAM",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f MB (PSS)", mem.totalPssMb),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Barra segmentada de distribución de memoria
                    val totalTracked = (mem.javaHeapMb + mem.nativeHeapMb + mem.graphicsMb + mem.codeMb).coerceAtLeast(1f)
                    val javaFrac = (mem.javaHeapMb / totalTracked).coerceIn(0f, 1f)
                    val nativeFrac = (mem.nativeHeapMb / totalTracked).coerceIn(0f, 1f)
                    val gfxFrac = (mem.graphicsMb / totalTracked).coerceIn(0f, 1f)
                    val codeFrac = (mem.codeMb / totalTracked).coerceIn(0f, 1f)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        if (javaFrac > 0) {
                            Box(modifier = Modifier.fillMaxHeight().weight(javaFrac.coerceAtLeast(0.01f)).background(Color(0xFF00E676)))
                        }
                        if (nativeFrac > 0) {
                            Box(modifier = Modifier.fillMaxHeight().weight(nativeFrac.coerceAtLeast(0.01f)).background(Color(0xFF00E5FF)))
                        }
                        if (gfxFrac > 0) {
                            Box(modifier = Modifier.fillMaxHeight().weight(gfxFrac.coerceAtLeast(0.01f)).background(Color(0xFFFF007F)))
                        }
                        if (codeFrac > 0) {
                            Box(modifier = Modifier.fillMaxHeight().weight(codeFrac.coerceAtLeast(0.01f)).background(Color(0xFFFFD600)))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Cuadrícula de valores numéricos
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            MemoryMetricItem(color = Color(0xFF00E676), label = "Java Heap (VM)", value = String.format(java.util.Locale.US, "%.1f MB", mem.javaHeapMb), sub = "Límite: ${mem.javaHeapMaxMb.toInt()} MB")
                            Spacer(modifier = Modifier.height(8.dp))
                            MemoryMetricItem(color = Color(0xFFFF007F), label = "Gráficos / Shaders", value = String.format(java.util.Locale.US, "%.1f MB", mem.graphicsMb), sub = "Compose & WebP")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            MemoryMetricItem(color = Color(0xFF00E5FF), label = "Native C++20 DSP", value = String.format(java.util.Locale.US, "%.1f MB", mem.nativeHeapMb), sub = "Buffers PCM & CMake")
                            Spacer(modifier = Modifier.height(8.dp))
                            MemoryMetricItem(color = Color(0xFFFFD600), label = "Código & Mappings", value = String.format(java.util.Locale.US, "%.1f MB", mem.codeMb), sub = "DEX & Binarios .so")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("RAM física libre en teléfono: ${mem.deviceFreeRamMb} MB / ${mem.deviceTotalRamMb} MB", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                        Text(String.format(java.util.Locale.US, "Uso: %.1f%%", mem.ramUsagePercentage), style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        // Tarjeta de CPU, Núcleos y Rendimiento
        item {
            val cpu = snapshot.cpuGpu
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PROCESADOR, NÚCLEOS Y ESTADO TÉRMICO",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary,
                                letterSpacing = 1.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatTile(
                            modifier = Modifier.weight(1f),
                            label = "Carga de CPU",
                            value = String.format(java.util.Locale.US, "%.1f%%", cpu.processCpuUsagePercent),
                            accentColor = if (cpu.processCpuUsagePercent > 40f) Color(0xFFFF9100) else Color(0xFF00E676)
                        )
                        StatTile(
                            modifier = Modifier.weight(1f),
                            label = "Núcleos CPU",
                            value = "${cpu.availableCores} núcleos",
                            accentColor = MaterialTheme.colorScheme.secondary
                        )
                        StatTile(
                            modifier = Modifier.weight(1f),
                            label = "Hilos Activos",
                            value = "${cpu.activeThreadCount} hilos",
                            accentColor = Color(0xFF00E5FF)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Estado Térmico: ${cpu.thermalState}", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                        Text("FPS UI Estimado: ~${cpu.estimatedGpuFps} fps", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                    }
                }
            }
        }

        // Cabecera del Inspector de Hilos
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INSPECTOR DE HILOS (${filteredThreads.size}/${snapshot.threads.size})",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                )
                FilterChip(
                    selected = onlyRunnable,
                    onClick = { onlyRunnable = !onlyRunnable },
                    label = { Text("Solo Runnable") },
                    leadingIcon = if (onlyRunnable) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF00E676).copy(alpha = 0.2f),
                        selectedLabelColor = Color(0xFF00E676)
                    )
                )
            }
        }

        // Buscador de hilos
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar hilo (ej: ExoPlayer, Dispatcher, RenderThread)...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                trailingIcon = if (searchQuery.isNotBlank()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
        }

        // Filtros por Categoría
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategoryChip(label = "Todos", isSelected = selectedCategoryFilter == null, onClick = { selectedCategoryFilter = null })
                CategoryChip(label = "Audio", isSelected = selectedCategoryFilter == ThreadCategory.AUDIO_MEDIA3, onClick = { selectedCategoryFilter = ThreadCategory.AUDIO_MEDIA3 })
                CategoryChip(label = "C++ DSP", isSelected = selectedCategoryFilter == ThreadCategory.C_PLUS_PLUS, onClick = { selectedCategoryFilter = ThreadCategory.C_PLUS_PLUS })
                CategoryChip(label = "UI", isSelected = selectedCategoryFilter == ThreadCategory.UI_RENDER, onClick = { selectedCategoryFilter = ThreadCategory.UI_RENDER })
                CategoryChip(label = "Corrutinas", isSelected = selectedCategoryFilter == ThreadCategory.COROUTINE_IO, onClick = { selectedCategoryFilter = ThreadCategory.COROUTINE_IO })
            }
        }

        // Lista de hilos
        if (filteredThreads.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceCard,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No se encontraron hilos con los filtros aplicados", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(filteredThreads, key = { it.id }) { threadItem ->
                val isExpanded = expandedThreadId == threadItem.id
                ThreadCardItem(
                    item = threadItem,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        expandedThreadId = if (isExpanded) null else threadItem.id
                    },
                    onCopyTrace = {
                        val toCopy = """
                            Hilo: ${threadItem.name} (ID: ${threadItem.id})
                            Estado: ${threadItem.state}
                            Prioridad: ${threadItem.priority} (Daemon: ${threadItem.isDaemon})
                            Categoría: ${threadItem.category.label}
                            Método actual: ${threadItem.topStackTrace}
                            Traza completa:
                            ${threadItem.fullStackTrace}
                        """.trimIndent()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Thread ${threadItem.name}", toCopy))
                        Toast.makeText(context, "Traza del hilo copiada", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun MemoryMetricItem(
    color: Color,
    label: String,
    value: String,
    sub: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(10.dp)) {}
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp))
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
            Text(sub, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary.copy(alpha = 0.7f), fontSize = 10.sp))
        }
    }
}

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.04f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium.copy(color = accentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp))
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onClick() }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (isSelected) MaterialTheme.colorScheme.primary else TextSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        )
    }
}

@Composable
private fun ThreadCardItem(
    item: ThreadDiagnosticItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopyTrace: () -> Unit
) {
    val stateColor = when (item.state) {
        Thread.State.RUNNABLE -> Color(0xFF00E676)
        Thread.State.TIMED_WAITING -> Color(0xFFFFB300)
        Thread.State.WAITING -> Color(0xFF00E5FF)
        Thread.State.BLOCKED -> Color(0xFFFF1744)
        else -> TextSecondary
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else CardBorder),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onToggleExpand() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = stateColor, modifier = Modifier.size(8.dp)) {}
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        maxLines = 1
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = stateColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = item.state.name,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = stateColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.category.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                )
                Text(
                    text = "ID: ${item.id} • Pri: ${item.priority}${if (item.isDaemon) " • Daemon" else ""}",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.topStackTrace,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextPrimary.copy(alpha = 0.85f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    maxLines = if (isExpanded) 4 else 1
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Traza de pila completa:", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                        TextButton(
                            onClick = onCopyTrace,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copiar traza", fontSize = 11.sp)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item.fullStackTrace,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFF80D8FF),
                            modifier = Modifier.padding(10.dp),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}
