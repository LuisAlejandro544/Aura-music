package com.example.feature.settings.components.ytdlp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.data.importer.YtDlpAutoUpdater
import com.example.core.data.importer.YtDlpCookieManager
import com.example.feature.settings.components.SettingDetailRow
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Diálogos de cookies pegadas y PO Token manual.
 * Rol arquitectónico: formularios con estado local y guardado delegado a la fachada.
 */
@Composable
fun YtDlpSettingsDialogs(
    showPasteDialog: Boolean,
    showManualPoTokenDialog: Boolean,
    onDismissPaste: () -> Unit,
    onDismissManual: () -> Unit,
    onSaveCookiesText: (String) -> Unit,
    onSavePoToken: (String, String) -> Unit
) {
    val context = LocalContext.current
            // Diálogo para pegar Cookies de texto
            if (showPasteDialog) {
                var pastedText by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { onDismissPaste() },
                    title = { Text("Pegar Cookies de YouTube", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Pega aquí el contenido en formato Netscape de tu archivo cookies.txt o el header HTTP Cookie:",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                            OutlinedTextField(
                                value = pastedText,
                                onValueChange = { pastedText = it },
                                placeholder = { Text("# Netscape HTTP Cookie File...\n.youtube.com\tTRUE\t/\tTRUE...", fontSize = 11.sp) },
                                maxLines = 8,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                onSaveCookiesText(pastedText)
                            }
                        ) {
                            Text("Guardar Cookies")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { onDismissPaste() }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            // Diálogo para PO Token Manual
            if (showManualPoTokenDialog) {
                var manualPoToken by remember { mutableStateOf(YtDlpCookieManager.getSavedPoToken(context) ?: "") }
                var manualVisitorData by remember { mutableStateOf(YtDlpCookieManager.getSavedVisitorData(context) ?: "") }
                AlertDialog(
                    onDismissRequest = { onDismissManual() },
                    title = { Text("PO Token Manual", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Si generaste un PO Token en tu PC o navegador, ingrésalo aquí:",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                            OutlinedTextField(
                                value = manualPoToken,
                                onValueChange = { manualPoToken = it },
                                label = { Text("PO Token") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = manualVisitorData,
                                onValueChange = { manualVisitorData = it },
                                label = { Text("Visitor Data (Opcional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                onSavePoToken(manualPoToken, manualVisitorData)
                            }
                        ) {
                            Text("Guardar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { onDismissManual() }) {
                            Text("Cancelar")
                        }
                    }
                )
            }
}
