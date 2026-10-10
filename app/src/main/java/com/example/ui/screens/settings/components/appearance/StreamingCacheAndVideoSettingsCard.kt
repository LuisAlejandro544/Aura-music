package com.example.ui.screens.settings.components.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.StreamingCacheConfig
import com.example.model.StreamingVideoNetworkPolicy
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Tarjeta modular de configuración para el Modo Streaming en Ajustes:
 * 1. Política de Red para Video Canvas en Streaming:
 *    - "Videos solo con Wi-Fi"
 *    - "Siempre (Wi-Fi y Datos Móviles)"
 *    - "Desactivado en Streaming"
 *    (Exclusivo para el streaming; no toca nada de la reproducción local).
 * 2. Caché Temporal Inteligente (Audio + Video Canvas):
 *    - Configurable entre 50 MB (por defecto) y 500 MB (máximo).
 *    - Expiración automática tras 30 minutos sin usarse y desalojo LRU de elementos más antiguos.
 */
@Composable
fun StreamingCacheAndVideoSettingsCard(
    cacheConfig: StreamingCacheConfig,
    onSelectVideoPolicy: (StreamingVideoNetworkPolicy) -> Unit,
    onChangeMaxCacheMb: (Int) -> Unit,
    onClearStreamingCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = modifier
            .fillMaxWidth()
            .testTag("streaming_cache_and_video_settings_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CloudQueue,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Streaming: Video Canvas & Caché Inteligente",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Configuración exclusiva para el modo Streaming (tus canciones y videos locales no se ven afectados)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Videos Canvas en Streaming",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            StreamingVideoNetworkPolicy.values().forEach { policy ->
                val isSelected = cacheConfig.videoNetworkPolicy == policy
                val icon = when (policy) {
                    StreamingVideoNetworkPolicy.WIFI_ONLY -> Icons.Filled.Wifi
                    StreamingVideoNetworkPolicy.ALWAYS -> Icons.Filled.SignalCellularAlt
                    StreamingVideoNetworkPolicy.DISABLED -> Icons.Filled.VideocamOff
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else SurfaceVariantDark.copy(alpha = 0.45f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectVideoPolicy(policy) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("streaming_video_policy_${policy.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = policy.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = policy.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectVideoPolicy(policy) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary,
                            unselectedColor = TextMuted
                        )
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = SurfaceVariantDark
            )

            // Sección de Caché Temporal (50 MB por defecto, máximo 500 MB, 30 min TTL)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Caché Temporal (Audio + Video)",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${cacheConfig.maxCacheSizeMb} MB máx",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Pre-descarga por adelantado los minutos de la canción y su Video Canvas para reproducir más rápido al volver atrás o repetir en bucle. Se elimina automáticamente tras 30 minutos sin usarse o al llenarse (eliminando lo más viejo primero).",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Slider(
                value = cacheConfig.maxCacheSizeMb.toFloat(),
                onValueChange = { raw ->
                    val stepped = ((raw / 25f).roundToInt() * 25)
                        .coerceIn(StreamingCacheConfig.MIN_CACHE_SIZE_MB, StreamingCacheConfig.MAX_CACHE_SIZE_MB)
                    onChangeMaxCacheMb(stepped)
                },
                valueRange = StreamingCacheConfig.MIN_CACHE_SIZE_MB.toFloat()..StreamingCacheConfig.MAX_CACHE_SIZE_MB.toFloat(),
                steps = 17,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = SurfaceVariantDark
                ),
                modifier = Modifier.testTag("streaming_cache_size_slider")
            )

            // Chips rápidos de tamaño (50 MB defecto, 100 MB, 250 MB, 500 MB máximo)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(50, 100, 250, 500).forEach { presetMb ->
                    val selected = cacheConfig.maxCacheSizeMb == presetMb
                    FilterChip(
                        selected = selected,
                        onClick = { onChangeMaxCacheMb(presetMb) },
                        label = {
                            Text(
                                text = if (presetMb == 50) "50 MB (Defecto)" else "$presetMb MB",
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            containerColor = SurfaceVariantDark,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("streaming_cache_preset_$presetMb")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Barra de uso actual y botón de vaciado manual
            LinearProgressIndicator(
                progress = { cacheConfig.usageFraction },
                color = MaterialTheme.colorScheme.primary,
                trackColor = SurfaceVariantDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "En uso: ${cacheConfig.usedMbFormatted} (${cacheConfig.cachedAudioCount} audios • ${cacheConfig.cachedVideoCount} videos)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )

                OutlinedButton(
                    onClick = onClearStreamingCache,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("clear_streaming_cache_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Vaciar caché")
                }
            }
        }
    }
}
