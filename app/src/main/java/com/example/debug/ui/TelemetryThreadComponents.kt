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
 * Chip de categoría y tarjeta de hilo con traza.
 * Rol arquitectónico: átomos del inspector de hilos.
 */
@Composable
internal fun CategoryChip(
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
internal fun ThreadCardItem(
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
