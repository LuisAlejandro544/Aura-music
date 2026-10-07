package com.example.ui.screens.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NowPlayingDesignMode
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Contenido de la pantalla independiente a pantalla completa de "Diseño del Reproductor".
 * Permite al usuario elegir entre:
 * - Automático Inteligente (NowPlayingDesignMode.AUTO)
 * - Modo Cinemático Canvas estilo Spotify (NowPlayingDesignMode.CINEMATIC_CANVAS)
 * - Modo Clásico con Carátula 1:1 (NowPlayingDesignMode.CLASSIC)
 */
fun LazyListScope.playerDesignSettingsContent(
    currentDesignMode: NowPlayingDesignMode,
    onSetDesignMode: (NowPlayingDesignMode) -> Unit
) {
    item {
        Text(
            text = "Selecciona cómo deseas visualizar la pantalla completa del reproductor (Now Playing):",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
        )
        Spacer(modifier = Modifier.height(14.dp))
    }

    items(NowPlayingDesignMode.values().size) { index ->
        val mode = NowPlayingDesignMode.values()[index]
        val isSelected = (currentDesignMode == mode)
        val primaryColor = MaterialTheme.colorScheme.primary

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) primaryColor.copy(alpha = 0.14f) else SurfaceCard
            ),
            border = BorderStroke(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) primaryColor else CardBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable { onSetDesignMode(mode) }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (mode) {
                        NowPlayingDesignMode.AUTO -> Icons.Default.AutoAwesome
                        NowPlayingDesignMode.CINEMATIC_CANVAS -> Icons.Default.Smartphone
                        NowPlayingDesignMode.CLASSIC -> Icons.Default.PlayCircleOutline
                    },
                    contentDescription = null,
                    tint = if (isSelected) primaryColor else TextSecondary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) primaryColor else TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = mode.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                RadioButton(
                    selected = isSelected,
                    onClick = { onSetDesignMode(mode) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = primaryColor,
                        unselectedColor = TextSecondary
                    )
                )
            }
        }
    }
}
