package com.example.debug.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.debug.DebugSeverity
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Fila de chips de filtro por severidad y contadores para Aura Monitor.
 */
@Composable
fun DebugFilterChips(
    totalCount: Int,
    filteredCount: Int,
    crashCount: Int,
    criticalCount: Int,
    errorCount: Int,
    warningCount: Int,
    infoCount: Int,
    selectedFilter: DebugSeverity?,
    onSelectFilter: (DebugSeverity?) -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Historial de Diagnóstico ($filteredCount)",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )

            if (totalCount > 0) {
                TextButton(onClick = onClearLogs) {
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
                    label = "Todos ($totalCount)",
                    isSelected = selectedFilter == null,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onSelectFilter(null) }
                )
            }
            item {
                FilterChipItem(
                    label = "Crash ($crashCount)",
                    isSelected = selectedFilter == DebugSeverity.CRASH,
                    color = DebugSeverity.CRASH.color,
                    onClick = { onSelectFilter(DebugSeverity.CRASH) }
                )
            }
            item {
                FilterChipItem(
                    label = "Crítico ($criticalCount)",
                    isSelected = selectedFilter == DebugSeverity.CRITICAL,
                    color = DebugSeverity.CRITICAL.color,
                    onClick = { onSelectFilter(DebugSeverity.CRITICAL) }
                )
            }
            item {
                FilterChipItem(
                    label = "Error ($errorCount)",
                    isSelected = selectedFilter == DebugSeverity.ERROR,
                    color = DebugSeverity.ERROR.color,
                    onClick = { onSelectFilter(DebugSeverity.ERROR) }
                )
            }
            item {
                FilterChipItem(
                    label = "Warning ($warningCount)",
                    isSelected = selectedFilter == DebugSeverity.WARNING,
                    color = DebugSeverity.WARNING.color,
                    onClick = { onSelectFilter(DebugSeverity.WARNING) }
                )
            }
            item {
                FilterChipItem(
                    label = "Info ($infoCount)",
                    isSelected = selectedFilter == DebugSeverity.INFO,
                    color = DebugSeverity.INFO.color,
                    onClick = { onSelectFilter(DebugSeverity.INFO) }
                )
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
        border = BorderStroke(
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
