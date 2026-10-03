package com.example.debug.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.debug.DebugLogEntry
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Diálogo modal para inspección completa de traza de pila (Stack Trace en crudo) y copia de diagnóstico.
 */
@Composable
fun DebugLogDetailDialog(
    entry: DebugLogEntry,
    onCopyAll: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = entry.severity.color.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, entry.severity.color)
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
                        border = BorderStroke(1.dp, Color(0xFF2A2E3D)),
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
            TextButton(onClick = onCopyAll) {
                Text("Copiar Todo", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cerrar", color = TextSecondary)
            }
        },
        containerColor = SurfaceCard
    )
}
