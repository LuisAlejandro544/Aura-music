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
 * Tarjeta de CPU, núcleos y estado térmico.
 * Rol arquitectónico: sección del LazyColumn de telemetría.
 */
fun LazyListScope.telemetryCpuCard(cpu: com.example.debug.CpuGpuTelemetryInfo) {
            // Tarjeta de CPU, Núcleos y Rendimiento
            item {
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
}
