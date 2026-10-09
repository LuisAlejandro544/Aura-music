package com.example.ui.screens.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.HeadphoneConfig
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Tarjeta de estado en tiempo real de audífonos (conectado vs altavoz, nombre de dispositivo y estado Crossfeed).
 */
@Composable
fun HeadphoneStatusCard(
    headphoneConfig: HeadphoneConfig,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (headphoneConfig.isHeadphoneConnected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            } else {
                SurfaceCard
            }
        ),
        border = BorderStroke(
            1.dp,
            if (headphoneConfig.isHeadphoneConnected) MaterialTheme.colorScheme.primary else CardBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("headphone_status_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (headphoneConfig.isHeadphoneConnected) MaterialTheme.colorScheme.primary else SurfaceCard,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (headphoneConfig.isHeadphoneConnected) Icons.Default.HeadsetMic else Icons.Default.Speaker,
                        contentDescription = null,
                        tint = if (headphoneConfig.isHeadphoneConnected) Color.White else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (headphoneConfig.isHeadphoneConnected) "Audífonos Conectados" else "Altavoz del Dispositivo",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (headphoneConfig.isHeadphoneConnected) Color(0xFF22C55E) else Color(0xFF94A3B8))
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = headphoneConfig.connectedDeviceName,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                if (headphoneConfig.isCrossfeedEnabled) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (headphoneConfig.isHeadphoneConnected) {
                            "✨ Filtro Crossfeed C++20 activo en música"
                        } else {
                            "⏸️ Crossfeed en espera (inactivo en altavoces)"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (headphoneConfig.isHeadphoneConnected) MaterialTheme.colorScheme.secondary else TextMuted
                        )
                    )
                }
            }
        }
    }
}
