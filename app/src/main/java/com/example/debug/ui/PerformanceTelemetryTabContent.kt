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
        telemetryControlBar(
            autoRefresh = autoRefresh,
            onToggleRefresh = { autoRefresh = it },
            onRefreshNow = {
                snapshot = PerformanceTelemetryManager.captureSnapshot(context)
                Toast.makeText(context, "Métricas actualizadas", Toast.LENGTH_SHORT).show()
            }
        )

        // Tarjeta de Consumo de Memoria RAM Detallado
        telemetryMemoryCard(mem = snapshot.memory)

        // Tarjeta de CPU, Núcleos y Rendimiento
        telemetryCpuCard(cpu = snapshot.cpuGpu)

        // Cabecera del Inspector de Hilos
        telemetryThreadHeader(
            shownCount = filteredThreads.size,
            totalCount = snapshot.threads.size,
            onlyRunnable = onlyRunnable,
            onToggleRunnable = { onlyRunnable = !onlyRunnable }
        )

        // Buscador de hilos
        telemetryThreadSearch(
            searchQuery = searchQuery,
            onQueryChange = { searchQuery = it }
        )

        // Filtros por Categoría
        telemetryCategoryFilters(
            selected = selectedCategoryFilter,
            onSelect = { selectedCategoryFilter = it }
        )

        // Lista de hilos
        telemetryThreadList(
            threads = filteredThreads,
            expandedThreadId = expandedThreadId,
            onToggleExpand = { expandedThreadId = if (expandedThreadId == it) null else it },
            onCopyTrace = { threadItem ->
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

