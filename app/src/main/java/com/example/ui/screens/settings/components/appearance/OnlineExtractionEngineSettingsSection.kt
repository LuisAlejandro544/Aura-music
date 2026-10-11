package com.example.ui.screens.settings.components.appearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.importer.YoutubeExtractionEngine
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Apartado independiente de Ajustes para seleccionar el Motor de Descarga y Reproducción Online (Streaming).
 *
 * Responsabilidad arquitectónica (MVVM):
 * - Presenta en una pantalla dedicada las opciones de motores de extracción para escuchar en streaming y descargar videos/canciones locales:
 *   1. `yt-dlp Nativo` (Por defecto, limpio sin cookies).
 *   2. `InnerTube + yt-dlp` (Modo Híbrido: InnerTube busca e intercepta la URL en <300ms y se apoya en yt-dlp).
 *   3. `Piped API` (Open Source Proxy sin anuncios).
 *   4. `InnerTube + Invidious` (Clientes directos Android VR / iOS / TV).
 *   5. `Invidious Proxy` (Instancias con túnel local=true).
 *   6. `WebView Real` (Navegador móvil en segundo plano).
 * - Informa el protocolo de diagnóstico activo según la compilación:
 *   - APK Debug: registra el código crudo exacto en Aura Monitor cuando falla el motor elegido.
 *   - APK Beta: muestra un aviso directo en pantalla al usuario.
 */
fun LazyListScope.onlineExtractionEngineSettingsSection(
    selectedEngine: YoutubeExtractionEngine,
    onSelectEngine: (YoutubeExtractionEngine) -> Unit
) {
    // Banner de estado del protocolo de diagnóstico y motor predeterminado
    item {
        val isDebugApk = BuildConfig.ENABLE_DEBUG_MONITOR
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("online_engine_info_banner")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isDebugApk) Icons.Default.BugReport else Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isDebugApk) {
                            "Modo Estricto Activo • Diagnóstico Crudo en Aura Monitor (APK Debug)"
                        } else {
                            "Modo Estricto Activo • Aviso en Pantalla (APK Beta)"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isDebugApk) {
                        "Al escuchar en streaming o descargar videos locales se utilizará el motor que elijas aquí (yt-dlp Nativo o el híbrido InnerTube + yt-dlp). Si el motor seleccionado llega a fallar, se enviará el código crudo exacto a Aura Monitor."
                    } else {
                        "Al escuchar en streaming o descargar videos locales se utilizará el motor que elijas aquí (yt-dlp Nativo o el híbrido InnerTube + yt-dlp). Si ocurre algún fallo con el motor seleccionado, aparecerá un aviso directo en pantalla."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
    }

    // Tarjetas de selección para cada motor disponible
    items(YoutubeExtractionEngine.entries.size) { index ->
        val engine = YoutubeExtractionEngine.entries[index]
        val isSelected = engine == selectedEngine
        val isDefaultEngine = engine == YoutubeExtractionEngine.YTDLP

        OnlineEngineOptionCard(
            engine = engine,
            isSelected = isSelected,
            isRecommendedDefault = isDefaultEngine,
            onClick = { onSelectEngine(engine) }
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun OnlineEngineOptionCard(
    engine: YoutubeExtractionEngine,
    isSelected: Boolean,
    isRecommendedDefault: Boolean,
    onClick: () -> Unit
) {
    val accentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val icon: ImageVector = when (engine) {
        YoutubeExtractionEngine.YTDLP -> Icons.Default.Terminal
        YoutubeExtractionEngine.INNERTUBE_YTDLP -> Icons.Default.Speed
        YoutubeExtractionEngine.PIPED -> Icons.Default.Security
        YoutubeExtractionEngine.INNERTUBE -> Icons.Default.Speed
        YoutubeExtractionEngine.INVIDIOUS -> Icons.Default.CloudDownload
        YoutubeExtractionEngine.WEBVIEW -> Icons.Default.Public
    }

    val detailedSubtitle = when (engine) {
        YoutubeExtractionEngine.YTDLP ->
            "Predeterminado y más fiable. Ejecuta yt-dlp nativo 100% limpio (sin cookies) con Python 3.11 + QuickJS para descargar y reproducir sin bloqueos."
        YoutubeExtractionEngine.INNERTUBE_YTDLP ->
            "Opcional Híbrido para Streaming y Descarga Local: InnerTube busca e intercepta la URL al instante (<300ms) y se la pasa a yt-dlp cuando requiere descifrado o descarga."
        YoutubeExtractionEngine.PIPED ->
            "Red abierta de código libre sin anuncios con proxy anti-403 (pipedproxy) para YouTube y YouTube Music."
        YoutubeExtractionEngine.INNERTUBE ->
            "API interna multi-cliente (ANDROID_VR Quest 3, iOS y TVHTML5) con verificación de flujos HTTP 206."
        YoutubeExtractionEngine.INVIDIOUS ->
            "Instancias públicas de Invidious con auto-descubrimiento y túnel proxy local=true (/latest_version)."
        YoutubeExtractionEngine.WEBVIEW ->
            "Navegador móvil sobre m.youtube.com que intercepta flujos directos googlevideo."
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, CardBorder)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("online_engine_card_${engine.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = engine.label,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    if (isRecommendedDefault) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "POR DEFECTO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 9.sp
                                ),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = detailedSubtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Seleccionado",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
