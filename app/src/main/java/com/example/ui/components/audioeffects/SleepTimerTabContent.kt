package com.example.ui.components.audioeffects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.SleepTimerState
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pestaña 2 de Efectos de Audio: Temporizador de Apagado (Sleep Timer con Fade-Out de 10s).
 * Arquitectura: Componente modular de UI que presenta:
 * - Vista de temporizador activo con contador regresivo `mm:ss`, aviso de fade-out, botón +5 min y Cancelar.
 * - Vista inactiva con chips de selección rápida (15m, 30m, 45m, 60m) y campo de texto para minutos libres.
 */
@Composable
fun SleepTimerTabContent(
    timerState: SleepTimerState,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onAddMinutes: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var customMinutesInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Temporizador de Apagado",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Text(
                text = "Detiene la música suavemente con una atenuación progresiva de 10s para no interrumpir tu descanso.",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (timerState.isActive) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (timerState.isFadingOut) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = BorderStroke(
                        1.dp,
                        if (timerState.isFadingOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = timerState.formattedRemaining,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = if (timerState.isFadingOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = if (timerState.isFadingOut) "Atenuando volumen (Fade-out)..." else "Tiempo restante para pausar",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { onAddMinutes(5) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+5 min")
                            }

                            Button(
                                onClick = onCancelTimer,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancelar", color = Color.White)
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Selecciona una duración:",
                    style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BackgroundDark,
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    keyboardController?.hide()
                                    onStartTimer(mins)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$mins m",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customMinutesInput,
                        onValueChange = { customMinutesInput = it.filter { char -> char.isDigit() }.take(3) },
                        label = { Text("Minutos libres") },
                        placeholder = { Text("Ej: 20") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val mins = customMinutesInput.toIntOrNull() ?: 0
                                if (mins > 0) {
                                    keyboardController?.hide()
                                    onStartTimer(mins)
                                    customMinutesInput = ""
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sleep_timer_custom_input")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val mins = customMinutesInput.toIntOrNull() ?: 0
                            if (mins > 0) {
                                keyboardController?.hide()
                                onStartTimer(mins)
                                customMinutesInput = ""
                            }
                        },
                        enabled = (customMinutesInput.toIntOrNull() ?: 0) > 0,
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("Iniciar")
                    }
                }
            }
        }
    }
}
