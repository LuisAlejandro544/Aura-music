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
import androidx.compose.foundation.lazy.LazyListScope
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
 * Lista de hilos con expansión y copiado.
 * Rol arquitectónico: sección del LazyColumn de telemetría.
 */
fun LazyListScope.telemetryThreadList(
    threads: List<com.example.debug.ThreadDiagnosticItem>,
    expandedThreadId: Long?,
    onToggleExpand: (Long) -> Unit,
    onCopyTrace: (com.example.debug.ThreadDiagnosticItem) -> Unit
) {
            // Lista de hilos
            if (threads.isEmpty()) {
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
                items(threads, key = { it.id }) { threadItem ->
                    val isExpanded = expandedThreadId == threadItem.id
                    ThreadCardItem(
                        item = threadItem,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            onToggleExpand(threadItem.id)
                        },
                        onCopyTrace = {
                            onCopyTrace(threadItem)
                        }
                    )
                }
            }
}
