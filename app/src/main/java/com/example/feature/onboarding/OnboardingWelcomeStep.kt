package com.example.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Paso 1: bienvenida e introducción a Aura Music.
 * Rol arquitectónico: paso/átomo del onboarding (mismo paquete, API intacta).
 */
@Composable
internal fun OnboardingWelcomeStep() {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Halo de Neón con Icono Central
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = SurfaceCard,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Aura Music Icono",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Bienvenido a Aura Music",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                letterSpacing = 0.5.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "El reproductor de música local de alta fidelidad diseñado para quienes aman el sonido puro, el diseño estético Dark Luxury y la libertad absoluta.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = TextSecondary,
                lineHeight = 22.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Puntos Clave
        WelcomePillCard(
            icon = Icons.Default.CloudOff,
            title = "100% Fuera de Línea",
            description = "Tu música permanece en tu teléfono. Sin cuentas, sin registros obligatorios ni suscripciones."
        )

        Spacer(modifier = Modifier.height(12.dp))

        WelcomePillCard(
            icon = Icons.Default.Security,
            title = "Privacidad y Seguridad",
            description = "Sin escaneo ciego de tu disco. Importa tus archivos o carpetas selectivamente con SAF y permisos protegidos."
        )

        Spacer(modifier = Modifier.height(12.dp))

        WelcomePillCard(
            icon = Icons.Default.Tune,
            title = "Audiofilia en C++20",
            description = "Ecualización nativa ISO de 10 bandas, Audio 8D orbital y Crossfeed acústico sin intermediarios."
        )
    }
}
