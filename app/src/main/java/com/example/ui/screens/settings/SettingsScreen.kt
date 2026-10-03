package com.example.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AuraTheme
import com.example.model.CrossfeedStrengthPreset
import com.example.model.HeadphoneConfig
import com.example.model.HeadsetButtonAction
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Pantalla de Temas y Ajustes para Aura Music.
 * Incluye pestañas principales para:
 * 1. Apariencia & Temas (paletas de color OLED, Aura dinámica de carátula, datos técnicos).
 * 2. Auriculares / Audífonos (con apartados para Acústica DSP Crossfeed C++20, Balance Estéreo L/R,
 *    Protección contra desconexiones, Fade-In suave, Memoria de volumen y Controles de botones físicos).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: AuraTheme,
    onSelectTheme: (AuraTheme) -> Unit,
    isDynamicArtworkColorEnabled: Boolean = true,
    onToggleDynamicArtworkColor: (Boolean) -> Unit = {},
    // Configuración de Auriculares
    headphoneConfig: HeadphoneConfig = HeadphoneConfig(),
    onUpdateHeadphoneConfig: (HeadphoneConfig) -> Unit = {},
    onSetCrossfeedEnabled: (Boolean) -> Unit = {},
    onSetCrossfeedStrength: (Int) -> Unit = {},
    onSetBalanceControlEnabled: (Boolean) -> Unit = {},
    onSetStereoBalance: (Float) -> Unit = {},
    onSetBecomingNoisyGuardEnabled: (Boolean) -> Unit = {},
    onSetFadeInOnResumeEnabled: (Boolean) -> Unit = {},
    onSetDedicatedVolumeMemoryEnabled: (Boolean) -> Unit = {},
    onSetHeadsetControlsEnabled: (Boolean) -> Unit = {},
    onSetHeadsetAction: (Int, HeadsetButtonAction) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var mainSettingsTab by remember { mutableIntStateOf(0) }
    var headphoneSectionTab by remember { mutableIntStateOf(0) }

    // Diálogo para configurar acción de botones de auriculares
    var editingClickType by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Cabecera Principal
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Configuración & Ajustes",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Personaliza la apariencia visual, acústica DSP y experiencia con audífonos.",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Selector de Pestaña Principal (Temas vs Auriculares)
            TabRow(
                selectedTabIndex = mainSettingsTab,
                containerColor = SurfaceCard,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
            ) {
                Tab(
                    selected = mainSettingsTab == 0,
                    onClick = { mainSettingsTab = 0 },
                    text = { Text("Apariencia & Temas", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    modifier = Modifier.testTag("settings_tab_appearance")
                )
                Tab(
                    selected = mainSettingsTab == 1,
                    onClick = { mainSettingsTab = 1 },
                    text = { Text("Auriculares", fontWeight = FontWeight.SemiBold) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (headphoneConfig.isHeadphoneConnected) {
                                    Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                                        Text("ON", fontSize = 9.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    },
                    modifier = Modifier.testTag("settings_tab_headphones")
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ==========================================
        // PESTAÑA 0: APARIENCIA & TEMAS
        // ==========================================
        if (mainSettingsTab == 0) {
            // Opción Avanzada: Aura Dinámica de Carátula
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    border = CardBorder.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dynamic_artwork_color_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Aura Dinámica de Carátula",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Adapta el resplandor de neón, la barra de progreso y los acentos visuales a los tonos de cada portada o video.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Switch(
                            checked = isDynamicArtworkColorEnabled,
                            onCheckedChange = onToggleDynamicArtworkColor,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("dynamic_color_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Selector de Temas Visuales
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Paleta Base Predeterminada",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Se utiliza en la biblioteca, menús y cuando una pista no tiene colores específicos.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            items(AuraTheme.values().size) { index ->
                val theme = AuraTheme.values()[index]
                val isSelected = theme == currentTheme

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    border = if (isSelected) {
                        androidx.compose.foundation.BorderStroke(2.dp, theme.primaryColor)
                    } else {
                        CardBorder.let { androidx.compose.foundation.BorderStroke(1.dp, it) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelectTheme(theme) }
                        .testTag("theme_card_${theme.name}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Círculos de color del tema
                        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(theme.primaryColor)
                                    .border(2.dp, SurfaceCard, CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(theme.secondaryColor)
                                    .border(2.dp, SurfaceCard, CircleShape)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = theme.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = theme.description,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }

                        if (isSelected) {
                            Surface(
                                shape = CircleShape,
                                color = theme.primaryColor,
                                modifier = Modifier.size(26.dp)
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

            // Información de Privacidad y Rendimiento Local
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Arquitectura y Privacidad",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    border = CardBorder.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingDetailRow("Motor de Reproducción", "Jetpack Media3 ExoPlayer")
                        SettingDetailRow("Motor DSP Nativo", "ISO C++20 con filtros Biquad 64-bit")
                        SettingDetailRow("Persistencia Local", "Room Database SQLite")
                        SettingDetailRow("Acceso a Archivos", "SAF (Storage Access Framework)")
                        SettingDetailRow("Escaneo Automático", "Desactivado (100% bajo control del usuario)")
                        SettingDetailRow("Versión", "1.1 (Aura Music Edition)")
                    }
                }
            }
        }

        // ==========================================
        // PESTAÑA 1: AURICULARES & AUDÍFONOS
        // ==========================================
        if (mainSettingsTab == 1) {
            // Tarjeta de Estado de Audífonos en Vivo
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (headphoneConfig.isHeadphoneConnected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            SurfaceCard
                        }
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (headphoneConfig.isHeadphoneConnected) MaterialTheme.colorScheme.primary else CardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("headphone_status_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (headphoneConfig.isHeadphoneConnected) MaterialTheme.colorScheme.primary else SurfaceCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (headphoneConfig.isHeadphoneConnected) Icons.Default.HeadsetMic else Icons.Default.Speaker,
                                    contentDescription = null,
                                    tint = if (headphoneConfig.isHeadphoneConnected) Color.White else TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (headphoneConfig.isHeadphoneConnected) "Audífonos Conectados" else "Altavoz del Dispositivo",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (headphoneConfig.isHeadphoneConnected) Color(0xFF22C55E) else Color(0xFF94A3B8))
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = headphoneConfig.connectedDeviceName,
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                            if (headphoneConfig.isCrossfeedEnabled) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (headphoneConfig.isHeadphoneConnected) {
                                        "✨ Filtro Crossfeed C++20 activo en música"
                                    } else {
                                        "⏸️ Crossfeed en espera (inactivo en altavoces)"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (headphoneConfig.isHeadphoneConnected) MaterialTheme.colorScheme.secondary else TextMuted
                                    )
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Sub-Apartados de Auriculares
            item {
                ScrollableTabRow(
                    selectedTabIndex = headphoneSectionTab,
                    containerColor = SurfaceCard,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = headphoneSectionTab == 0,
                        onClick = { headphoneSectionTab = 0 },
                        text = { Text("1. Acústica & DSP", style = MaterialTheme.typography.labelMedium) },
                        icon = { Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("headphone_subtab_dsp")
                    )
                    Tab(
                        selected = headphoneSectionTab == 1,
                        onClick = { headphoneSectionTab = 1 },
                        text = { Text("2. Seguridad & Conexión", style = MaterialTheme.typography.labelMedium) },
                        icon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("headphone_subtab_safety")
                    )
                    Tab(
                        selected = headphoneSectionTab == 2,
                        onClick = { headphoneSectionTab = 2 },
                        text = { Text("3. Botones y Gestos", style = MaterialTheme.typography.labelMedium) },
                        icon = { Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("headphone_subtab_gestures")
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // SUB-APARTADO 0: ACÚSTICA & DSP (Crossfeed C++20 y Balance Estéreo L/R)
            if (headphoneSectionTab == 0) {
                // Tarjeta Filtro Crossfeed C++20
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("crossfeed_card")
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Hearing,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Filtro Crossfeed Acústico",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                        )
                                        Text(
                                            text = "Algoritmo Bauer / Chu Moy en C++20",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }

                                Switch(
                                    checked = headphoneConfig.isCrossfeedEnabled,
                                    onCheckedChange = { onSetCrossfeedEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("crossfeed_switch")
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Elimina la fatiga auditiva mezclando sutilmente una porción de audio con filtro paso-bajos (~700 Hz) y retardo de microsegundos en el oído opuesto, emulando la escucha natural de altavoces en una sala.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "🔒 Se activará en la música EXCLUSIVAMENTE cuando se detecte un auricular conectado, para no alterar la respuesta de altavoces.",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )

                            AnimatedVisibility(visible = headphoneConfig.isCrossfeedEnabled) {
                                Column(modifier = Modifier.padding(top = 16.dp)) {
                                    Text(
                                        text = "Intensidad Acústica del Crossfeed:",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    CrossfeedStrengthPreset.values().forEach { preset ->
                                        val isPresetSelected = headphoneConfig.crossfeedStrength == preset.mode
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isPresetSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isPresetSelected) MaterialTheme.colorScheme.primary else CardBorder
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { onSetCrossfeedStrength(preset.mode) }
                                                .testTag("crossfeed_preset_${preset.mode}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = isPresetSelected,
                                                    onClick = { onSetCrossfeedStrength(preset.mode) },
                                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = preset.title,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = TextPrimary
                                                        )
                                                    )
                                                    Text(
                                                        text = preset.subtitle,
                                                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Tarjeta Balance Estéreo Fino (L/R)
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stereo_balance_card")
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
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
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Balance Estéreo Fino (L / R)",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                        )
                                        Text(
                                            text = "Control Izquierda / Derecha en tiempo real",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }

                                Switch(
                                    checked = headphoneConfig.isBalanceControlEnabled,
                                    onCheckedChange = { onSetBalanceControlEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    modifier = Modifier.testTag("balance_control_switch")
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Permite compensar asimetrías auditivas o audífonos con diferente presión. Al activarse, podrás ajustarlo aquí y directamente en el reproductor completo Now Playing en tiempo real.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )

                            AnimatedVisibility(visible = headphoneConfig.isBalanceControlEnabled) {
                                Column(modifier = Modifier.padding(top = 16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Canal Izquierdo (L)",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (headphoneConfig.stereoBalance < -0.05f) MaterialTheme.colorScheme.secondary else TextSecondary,
                                                fontWeight = if (headphoneConfig.stereoBalance < -0.05f) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                        Text(
                                            text = headphoneConfig.formattedBalance,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        )
                                        Text(
                                            text = "Canal Derecho (R)",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (headphoneConfig.stereoBalance > 0.05f) MaterialTheme.colorScheme.secondary else TextSecondary,
                                                fontWeight = if (headphoneConfig.stereoBalance > 0.05f) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Slider(
                                        value = headphoneConfig.stereoBalance,
                                        onValueChange = { onSetStereoBalance(it) },
                                        valueRange = -1.0f..1.0f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.secondary,
                                            activeTrackColor = MaterialTheme.colorScheme.secondary,
                                            inactiveTrackColor = SurfaceCard
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("settings_stereo_balance_slider")
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        OutlinedButton(
                                            onClick = { onSetStereoBalance(0.0f) },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                                            modifier = Modifier.testTag("reset_balance_btn")
                                        ) {
                                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Restablecer al Centro (0%)", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SUB-APARTADO 1: SEGURIDAD & COMPORTAMIENTO
            if (headphoneSectionTab == 1) {
                // 1. Becoming Noisy Guard
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("becoming_noisy_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeOff,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Protección contra Desconexiones",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Becoming Noisy Guard: Pausa la música de inmediato al desconectar auriculares cableados o apagarse el Bluetooth para evitar que suene en altavoz en público.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Switch(
                                checked = headphoneConfig.isBecomingNoisyGuardEnabled,
                                onCheckedChange = { onSetBecomingNoisyGuardEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFFEF4444)
                                ),
                                modifier = Modifier.testTag("becoming_noisy_switch")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // 2. Reanudación con Fade-In Suave
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fade_in_resume_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Reanudación con Fade-In Suave",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Aumenta el volumen de forma progresiva (~1s) al reanudar la reproducción con audífonos, protegiendo tus oídos de impactos repentinos de volumen.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Switch(
                                checked = headphoneConfig.isFadeInOnResumeEnabled,
                                onCheckedChange = { onSetFadeInOnResumeEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("fade_in_resume_switch")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // 3. Memoria de Volumen Dedicada
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dedicated_volume_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Memoria de Volumen Dedicada",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Recuerda de forma independiente el volumen que usas con auriculares frente al volumen de los altavoces de tu teléfono.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Switch(
                                checked = headphoneConfig.isDedicatedVolumeMemoryEnabled,
                                onCheckedChange = { onSetDedicatedVolumeMemoryEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier.testTag("dedicated_volume_switch")
                            )
                        }
                    }
                }
            }

            // SUB-APARTADO 2: BOTONES Y GESTOS (Headset Controls)
            if (headphoneSectionTab == 2) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("headset_controls_master_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Controles Físicos de Auriculares",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Intercepta y personaliza las pulsaciones del botón físico o panel táctil de tus audífonos.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }

                            Switch(
                                checked = headphoneConfig.isHeadsetControlsEnabled,
                                onCheckedChange = { onSetHeadsetControlsEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("headset_controls_switch")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (headphoneConfig.isHeadsetControlsEnabled) {
                    item {
                        Text(
                            text = "Acciones Configurables por Pulsación:",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 1 Clic
                    item {
                        HeadsetGestureRow(
                            title = "1 Pulsación (Simple)",
                            description = "Pulsar una vez el botón de los audífonos",
                            currentAction = headphoneConfig.singleClickAction,
                            onClick = { editingClickType = 1 }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 2 Clics
                    item {
                        HeadsetGestureRow(
                            title = "2 Pulsaciones (Doble clic)",
                            description = "Pulsar dos veces seguidas",
                            currentAction = headphoneConfig.doubleClickAction,
                            onClick = { editingClickType = 2 }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 3 Clics
                    item {
                        HeadsetGestureRow(
                            title = "3 Pulsaciones (Triple clic)",
                            description = "Pulsar tres veces seguidas",
                            currentAction = headphoneConfig.tripleClickAction,
                            onClick = { editingClickType = 3 }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Clic Largo
                    item {
                        HeadsetGestureRow(
                            title = "Pulsación Prolongada (Hold)",
                            description = "Mantener presionado más de 0.7 segundos",
                            currentAction = headphoneConfig.longClickAction,
                            onClick = { editingClickType = 4 }
                        )
                    }
                }
            }
        }
    }

    // Diálogo Modal para seleccionar acción de botón de audífonos
    if (editingClickType != null) {
        val clickType = editingClickType!!
        val typeTitle = when (clickType) {
            1 -> "1 Pulsación (Simple)"
            2 -> "2 Pulsaciones (Doble)"
            3 -> "3 Pulsaciones (Triple)"
            else -> "Pulsación Prolongada (Hold)"
        }
        val currentSelection = when (clickType) {
            1 -> headphoneConfig.singleClickAction
            2 -> headphoneConfig.doubleClickAction
            3 -> headphoneConfig.tripleClickAction
            else -> headphoneConfig.longClickAction
        }

        AlertDialog(
            onDismissRequest = { editingClickType = null },
            title = {
                Text(
                    text = "Configurar $typeTitle",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Elige qué acción debe ejecutarse:",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    HeadsetButtonAction.values().forEach { action ->
                        val isSelected = action == currentSelection
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetHeadsetAction(clickType, action)
                                    editingClickType = null
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onSetHeadsetAction(clickType, action)
                                        editingClickType = null
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = action.label,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = TextPrimary
                                        )
                                    )
                                    Text(
                                        text = action.description,
                                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingClickType = null }) {
                    Text("Cerrar", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun HeadsetGestureRow(
    title: String,
    description: String,
    currentAction: HeadsetButtonAction,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentAction.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}
