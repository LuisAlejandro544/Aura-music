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
 * Tarjeta de consumo detallado de RAM.
 * Rol arquitectónico: sección del LazyColumn de telemetría.
 */
fun LazyListScope.telemetryMemoryCard(mem: com.example.debug.MemoryTelemetryInfo) {
            // Tarjeta de Consumo de Memoria RAM Detallado
            item {
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
}
