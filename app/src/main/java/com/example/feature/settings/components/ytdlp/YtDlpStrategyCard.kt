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
 * Tarjeta de estrategia natural de yt-dlp (player_client).
 * Rol arquitectónico: selector Default/MWeb+Web con chips filtrantes.
 */
@Composable
fun YtDlpStrategyCard(
    currentStrategy: String,
    onSelectStrategy: (String) -> Unit
) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Estrategia Natural de yt-dlp",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Configura cómo interactúa yt-dlp con los servidores de YouTube. La estrategia natural ('default') utiliza la rotación interna del motor para evitar bloqueos de clientes deprecados.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = currentStrategy == YtDlpCookieManager.STRATEGY_DEFAULT,
                            onClick = {
                                onSelectStrategy(YtDlpCookieManager.STRATEGY_DEFAULT)
                            },
                            label = { Text("Default (Natural)", fontWeight = FontWeight.SemiBold) },
                            leadingIcon = if (currentStrategy == YtDlpCookieManager.STRATEGY_DEFAULT) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = currentStrategy == YtDlpCookieManager.STRATEGY_MWEB_AND_WEB,
                            onClick = {
                                onSelectStrategy(YtDlpCookieManager.STRATEGY_MWEB_AND_WEB)
                            },
                            label = { Text("MWeb + Web", fontWeight = FontWeight.SemiBold) },
                            leadingIcon = if (currentStrategy == YtDlpCookieManager.STRATEGY_MWEB_AND_WEB) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }
}
