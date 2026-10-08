package com.example.ui.screens.nowplaying.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HeadphoneConfig
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import kotlin.math.abs

/**
 * Barra de Balance Estéreo Fino L/R en Tiempo Real integrada en Now Playing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingBalanceBar(
    headphoneConfig: HeadphoneConfig,
    onSetStereoBalance: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!headphoneConfig.isBalanceControlEnabled) return

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .testTag("now_playing_balance_control_bar")
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Balance Estéreo L/R:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }
                Text(
                    text = headphoneConfig.formattedBalance,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                )
                if (abs(headphoneConfig.stereoBalance) > 0.02f) {
                    TextButton(
                        onClick = { onSetStereoBalance(0.0f) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(
                            text = "Centrar",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                }
            }
            Slider(
                value = headphoneConfig.stereoBalance,
                onValueChange = onSetStereoBalance,
                valueRange = -1.0f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.secondary,
                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                    inactiveTrackColor = Color(0xFF232736)
                ),
                thumb = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(18.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.30f), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .shadow(elevation = 2.dp, shape = CircleShape)
                                .background(Color.White, CircleShape)
                        )
                    }
                },
                track = {
                    val fraction = ((headphoneConfig.stereoBalance + 1f) / 2f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF232736))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.secondary)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .testTag("now_playing_live_balance_slider")
            )
        }
    }
}
