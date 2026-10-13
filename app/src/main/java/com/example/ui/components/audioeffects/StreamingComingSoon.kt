package com.example.ui.components.audioeffects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Insignia "Próximamente" para efectos aún sin soporte en Modo Streaming.
 * Fases 1-3 activas; Fase 4 (Reverb, VocalClarity, Crossfeed/Balance, DJ, Bit-Perfect)
 * se muestra bloqueada sin ocultar el control.
 */
@Composable
fun StreamingComingSoonBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFFFB020).copy(alpha = 0.16f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LockClock,
                contentDescription = null,
                tint = Color(0xFFFFB020),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "PRÓXIMAMENTE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFB020)
                )
            )
        }
    }
}

/**
 * Envuelve un bloque aún no soportado en streaming: lo atenúa, bloquea
 * la interacción y muestra la insignia junto al título.
 */
@Composable
fun StreamingComingSoonBlock(
    title: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!isStreaming) {
        Box(modifier = modifier) { content() }
        return
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.02f),
        modifier = modifier.fillMaxWidth()
    ) {
        Box {
            Box(modifier = Modifier.alpha(0.45f)) { content() }
            // Velo bloqueador: intercepta toques sin ocultar el control.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(enabled = true, onClick = {})
            ) {}
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StreamingComingSoonBadge()
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.75f)
                    )
                )
            }
        }
    }
}

/**
 * Aviso superior del sheet cuando suena una pista en streaming:
 * resume qué fases están activas y qué queda próximamente.
 */
@Composable
fun StreamingEffectsBanner(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF00E5FF).copy(alpha = 0.08f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StreamingComingSoonBadge()
            Text(
                text = "En Explorar ya funcionan EQ, 8D/16D, velocidad, crossfade, A-B y temporizador. Reverb, voces HD, Crossfeed y DJ llegan próximamente.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.85f)
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
