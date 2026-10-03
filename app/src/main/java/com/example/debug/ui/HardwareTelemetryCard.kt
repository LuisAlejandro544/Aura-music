package com.example.debug.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.debug.DeviceDiagnosticInfo
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Tarjeta de telemetría de hardware y entorno del teléfono móvil en Aura Monitor.
 */
@Composable
fun HardwareTelemetryCard(
    deviceInfo: DeviceDiagnosticInfo,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Datos del Teléfono",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = deviceInfo.androidVersion,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Modelo", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                    Text(
                        text = "${deviceInfo.manufacturer} ${deviceInfo.model}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text("Arquitectura CPU", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                    Text(
                        text = deviceInfo.supportedAbis.split(",").firstOrNull()?.trim() ?: "N/A",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Memoria RAM", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                    Text(
                        text = if (deviceInfo.totalRamMb > 0) "${deviceInfo.availableRamMb} MB / ${deviceInfo.totalRamMb} MB" else "No disponible",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text("Almacenamiento Libre", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                    Text(
                        text = if (deviceInfo.availableStorageMb > 0) "${deviceInfo.availableStorageMb} MB" else "No disponible",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    )
                }
            }
        }
    }
}
