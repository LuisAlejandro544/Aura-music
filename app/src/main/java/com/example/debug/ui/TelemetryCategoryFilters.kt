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
 * Filtros por categoría de hilo.
 * Rol arquitectónico: sección del LazyColumn de telemetría.
 */
fun LazyListScope.telemetryCategoryFilters(
    selected: com.example.debug.ThreadCategory?,
    onSelect: (com.example.debug.ThreadCategory?) -> Unit
) {
            // Filtros por Categoría
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryChip(label = "Todos", isSelected = selected == null, onClick = { onSelect(null) })
                    CategoryChip(label = "Audio", isSelected = selected == ThreadCategory.AUDIO_MEDIA3, onClick = { onSelect(ThreadCategory.AUDIO_MEDIA3) })
                    CategoryChip(label = "C++ DSP", isSelected = selected == ThreadCategory.C_PLUS_PLUS, onClick = { onSelect(ThreadCategory.C_PLUS_PLUS) })
                    CategoryChip(label = "UI", isSelected = selected == ThreadCategory.UI_RENDER, onClick = { onSelect(ThreadCategory.UI_RENDER) })
                    CategoryChip(label = "Corrutinas", isSelected = selected == ThreadCategory.COROUTINE_IO, onClick = { onSelect(ThreadCategory.COROUTINE_IO) })
                }
            }
}
