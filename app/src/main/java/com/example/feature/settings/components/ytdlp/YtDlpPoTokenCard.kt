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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.data.importer.YtDlpCookieManager
import com.example.feature.settings.components.SettingDetailRow
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Tarjeta del generador ligero de PO Token Web.
 * Rol arquitectónico: estado, generación y acceso al ingreso manual.
 */
@Composable
fun YtDlpPoTokenCard(
    isPoTokenEnabled: Boolean,
    isGeneratingPoToken: Boolean,
    onTogglePoToken: (Boolean) -> Unit,
    onGeneratePoToken: () -> Unit,
    onManualPoToken: () -> Unit
) {
    val context = LocalContext.current
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Generador Ligero de PO Token Web",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }
                        Switch(
                            checked = isPoTokenEnabled,
                            onCheckedChange = { enabled ->
                                onTogglePoToken(enabled)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Obtiene y sintetiza un Proof of Origin Token (PO Token) vinculado a YouTube Web para que yt-dlp consulte los flujos legítimamente.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val savedPo = YtDlpCookieManager.getSavedPoToken(context)
                    val visitorData = YtDlpCookieManager.getSavedVisitorData(context)
                    val lastGen = YtDlpCookieManager.getPoTokenLastGeneratedFormatted(context)

                    SettingDetailRow("Estado PO Token", if (savedPo != null && isPoTokenEnabled) "Activo (Web GVS)" else "Sin generar")
                    SettingDetailRow("Última Generación", lastGen)
                    if (visitorData != null && visitorData.isNotBlank()) {
                        SettingDetailRow("Visitor Data", visitorData.take(18) + "...")
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onGeneratePoToken()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isGeneratingPoToken) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generando...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generar PO Token", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = { onManualPoToken() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PO Token Manual", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
}
