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
 * Paso 2: características principales y capacidades.
 * Rol arquitectónico: paso/átomo del onboarding (mismo paquete, API intacta).
 */
@Composable
internal fun OnboardingFeaturesStep() {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Lo Que Te Ofrecemos",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Una suite audiovisual completa para tus pistas locales:",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        FeatureDetailCard(
            icon = Icons.Default.GraphicEq,
            iconTint = Color(0xFF00E5FF),
            title = "Motor DSP en C++20 de 10 Bandas",
            description = "Ecualizador paramétrico con etiquetas claras (Subgraves, Voces, Claridad, Brillo, Aire), Audio 8D Espacial y suite de reverberación en tiempo real."
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureDetailCard(
            icon = Icons.Default.VideoLibrary,
            iconTint = Color(0xFFFF007F),
            title = "Video Canvas Audiovisual",
            description = "Vincula loops cinemáticos en bucle continuo (Seamless Loop xfade o Boomerang) o videos sincronizados como fondo vivo mientras suena la música."
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureDetailCard(
            icon = Icons.Default.Mic,
            iconTint = Color(0xFF00E676),
            title = "Letras Sincronizadas (.LRC) & Karaoke",
            description = "Descarga automática libre desde LRCLIB, recomendación de lírica oficial (#1), salto al compás de cada verso e importador de archivos .lrc/.txt."
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureDetailCard(
            icon = Icons.Default.Download,
            iconTint = Color(0xFFFFD600),
            title = "Video a Música 3 en 1 & Extracción Web",
            description = "Convierte videos de tu galería en música .m4a en 1 segundo con carátula WebP, o descarga desde TikTok, YouTube y enlaces con acelerador de red."
        )
    }
}
