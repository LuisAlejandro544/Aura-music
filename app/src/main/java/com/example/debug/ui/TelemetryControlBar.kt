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
 * Barra de control de actualización en vivo.
 * Rol arquitectónico: sección del LazyColumn de telemetría.
 */
fun LazyListScope.telemetryControlBar(
    autoRefresh: Boolean,
    onToggleRefresh: (Boolean) -> Unit,
    onRefreshNow: () -> Unit
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
                                onClick = { onRefreshNow() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Actualizar ahora", tint = MaterialTheme.colorScheme.primary)
                            }
                            Switch(
                                checked = autoRefresh,
                                onCheckedChange = { onToggleRefresh(it) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }
}
