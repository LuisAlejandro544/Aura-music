package com.example.debug.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.debug.DebugSeverity
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Diálogo modal para simulación de incidencias sintéticas (Warning, Error, Crítico) para comprobar la captura de telemetría.
 */
@Composable
fun DebugSyntheticTestDialog(
    onSimulateWarning: () -> Unit,
    onSimulateError: () -> Unit,
    onSimulateCritical: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text("Simular Eventos de Diagnóstico", style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Genera logs de prueba para comprobar que el monitor captura errores en crudo, advertencias y telemetría correctamente:",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )

                Button(
                    onClick = onSimulateWarning,
                    colors = ButtonDefaults.buttonColors(containerColor = DebugSeverity.WARNING.color.copy(alpha = 0.8f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Simular WARNING (Ámbar)", color = Color.Black)
                }

                Button(
                    onClick = onSimulateError,
                    colors = ButtonDefaults.buttonColors(containerColor = DebugSeverity.ERROR.color),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Simular ERROR (Carmín)", color = Color.White)
                }

                Button(
                    onClick = onSimulateCritical,
                    colors = ButtonDefaults.buttonColors(containerColor = DebugSeverity.CRITICAL.color),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Simular CRÍTICO (Naranja)", color = Color.White)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cerrar", color = TextSecondary)
            }
        },
        containerColor = SurfaceCard
    )
}
