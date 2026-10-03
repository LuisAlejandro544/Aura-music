package com.example.debug.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.debug.DebugLogEntry
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Tarjeta de presentación individual para cada entrada en el registro de Aura Monitor.
 */
@Composable
fun DebugLogEntryCard(
    entry: DebugLogEntry,
    onClick: () -> Unit,
    onCopyEntry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceCard,
        border = BorderStroke(
            width = 1.dp,
            color = entry.severity.color.copy(alpha = 0.4f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("debug_log_card_${entry.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = entry.severity.color.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = entry.severity.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = entry.severity.color
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = entry.tag,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                Text(
                    text = entry.dateTimeFormatted.substringAfter(" "),
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!entry.rawStackTrace.isNullOrBlank()) {
                    Text(
                        text = "Stacktrace disponible (Toca para ver)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = entry.severity.color,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                } else {
                    Text(
                        text = "Hilo: ${entry.deviceInfo.threadName}",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                    )
                }

                IconButton(
                    onClick = onCopyEntry,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copiar entrada",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
