package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.importer.mixtape.SmartAutoRemixEngine
import com.example.model.AutoRemixStyle
import com.example.model.Track
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Diálogo unificado para crear:
 * 1. Mixtape Continuo de toda la lista con Crossfade en FFmpeg.
 * 2. Auto-Remix / Mashup de 2 Canciones Locales (elegidas por el usuario o por Selección Inteligente Auto-Match),
 *    soportando tanto Mashup (Voz A + Base B) como Remix Alternado DJ con sincronización de ritmo.
 */
@Composable
fun CreateMixtapeDialog(
    initialTitle: String,
    tracks: List<Track>,
    isCreating: Boolean,
    progress: Float,
    statusMessage: String?,
    onDismissRequest: () -> Unit,
    onCreateMixtape: (title: String, crossfadeSeconds: Int) -> Unit,
    onCreateAutoRemix: ((trackA: Track, trackB: Track, title: String, style: AutoRemixStyle, crossfadeSeconds: Int) -> Unit)? = null,
    initialModeAutoRemix: Boolean = false,
    createdTrack: Track? = null,
    onPlayCreatedTrack: (Track) -> Unit = {}
) {
    val localTracks = remember(tracks) { SmartAutoRemixEngine.filterLocalTracks(tracks) }
    var isAutoRemixMode by remember(initialModeAutoRemix) { mutableStateOf(initialModeAutoRemix) }

    var mixtapeTitle by remember(initialTitle) { mutableStateOf(initialTitle) }
    var crossfadeSec by remember { mutableFloatStateOf(6f) }

    // Estado del modo Auto-Remix de 2 canciones locales
    var autoMatchSeed by remember { mutableIntStateOf(0) }
    val initialPair = remember(localTracks) { SmartAutoRemixEngine.findBestMatchingPair(localTracks, 0) }
    var selectedTrackA by remember {
        mutableStateOf(initialPair?.trackA ?: localTracks.getOrNull(0))
    }
    var selectedTrackB by remember {
        mutableStateOf(initialPair?.trackB ?: localTracks.getOrNull(1) ?: localTracks.getOrNull(0))
    }
    var autoMatchReason by remember {
        mutableStateOf(initialPair?.reason ?: "Selecciona 2 canciones locales o usa el botón de Selección Inteligente.")
    }

    // Si las pistas locales se cargan o actualizan asíncronamente desde Room, rellenar automáticamente las ranuras vacías
    LaunchedEffect(localTracks, initialPair) {
        if (localTracks.isNotEmpty()) {
            if (selectedTrackA == null || localTracks.none { it.id == selectedTrackA?.id }) {
                selectedTrackA = initialPair?.trackA ?: localTracks.getOrNull(0)
            }
            if (selectedTrackB == null || localTracks.none { it.id == selectedTrackB?.id } ||
                (localTracks.size >= 2 && selectedTrackB?.id == selectedTrackA?.id)
            ) {
                selectedTrackB = initialPair?.trackB
                    ?: localTracks.firstOrNull { it.id != selectedTrackA?.id }
                    ?: localTracks.getOrNull(0)
            }
            if (initialPair != null) {
                autoMatchReason = initialPair.reason
            }
        }
    }

    var selectedRemixStyle by remember { mutableStateOf(AutoRemixStyle.VOCAL_MASHUP) }
    var remixTitle by remember(selectedTrackA, selectedTrackB, selectedRemixStyle) {
        val aTitle = selectedTrackA?.title ?: "Canción A"
        val bTitle = selectedTrackB?.title ?: "Canción B"
        val suffix = if (selectedRemixStyle == AutoRemixStyle.VOCAL_MASHUP) "Mashup" else "DJ Remix"
        mutableStateOf("$aTitle × $bTitle ($suffix)")
    }

    // Selector emergente para elegir Canción A (1) o Canción B (2)
    var pickingTrackSlot by remember { mutableIntStateOf(0) }
    var pickerSearchQuery by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = { if (!isCreating) onDismissRequest() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF141722),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Encabezado
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAutoRemixMode) Icons.Default.AutoAwesome else Icons.Default.AllInclusive,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAutoRemixMode) "Auto-Remix & Mashup (2 Pistas)" else "Crear Mixtape Continuo",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = if (isAutoRemixMode) {
                                "Música Local • Selección Manual o Inteligente"
                            } else {
                                "Fusión DJ sin pausas • ${localTracks.size} pistas locales"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                    if (!isCreating) {
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selector de Modo: Mixtape Continuo vs Auto-Remix de 2 Canciones
                if (!isCreating && createdTrack == null && onCreateAutoRemix != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceCard)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isMixSelected = !isAutoRemixMode
                        Surface(
                            onClick = { isAutoRemixMode = false },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isMixSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mode_mixtape_tab")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AllInclusive,
                                    contentDescription = null,
                                    tint = if (isMixSelected) Color.White else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Mixtape Lista",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMixSelected) Color.White else TextSecondary
                                    )
                                )
                            }
                        }

                        val isRemixSelected = isAutoRemixMode
                        Surface(
                            onClick = { isAutoRemixMode = true },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isRemixSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mode_autoremix_tab")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (isRemixSelected) Color.White else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Auto-Remix (2)",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isRemixSelected) Color.White else TextSecondary
                                    )
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Aviso de estado o error si la creación previa reportó algún problema
                if (!isCreating && createdTrack == null && !statusMessage.isNullOrBlank()) {
                    val isErrorMsg = statusMessage.contains("No se pudo", ignoreCase = true) ||
                        statusMessage.contains("Error", ignoreCase = true) ||
                        statusMessage.contains("Verifica", ignoreCase = true) ||
                        statusMessage.contains("solo para", ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isErrorMsg) Color(0xFFEF4444).copy(alpha = 0.14f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        border = BorderStroke(
                            1.dp,
                            if (isErrorMsg) Color(0xFFEF4444).copy(alpha = 0.45f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mixtape_status_alert_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isErrorMsg) Icons.Default.ErrorOutline else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isErrorMsg) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusMessage,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (createdTrack != null && !isCreating) {
                    // Estado completado
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isAutoRemixMode) "¡Auto-Remix Listo!" else "¡Mixtape Continuo Listo!",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "'${createdTrack.title}' se guardó en tu biblioteca con carátula en collage WebP, letras sincronizadas y capítulos reactivos.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismissRequest,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cerrar")
                        }
                        Button(
                            onClick = { onPlayCreatedTrack(createdTrack) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reproducir Ahora", fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (isCreating) {
                    // Progreso en vivo
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            progress = { progress.coerceIn(0.05f, 1f) },
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "${(progress * 100).roundToInt()}%",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = statusMessage ?: "Procesando mezcla de estudio con FFmpeg...",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0.02f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = SurfaceCard
                        )
                    }
                } else if (isAutoRemixMode) {
                    // Configuración del Modo Auto-Remix / Mashup de 2 Canciones Locales
                    AutoRemixConfigSection(
                        localTracks = localTracks,
                        selectedTrackA = selectedTrackA,
                        selectedTrackB = selectedTrackB,
                        autoMatchReason = autoMatchReason,
                        selectedRemixStyle = selectedRemixStyle,
                        remixTitle = remixTitle,
                        crossfadeSec = crossfadeSec,
                        onRemixTitleChange = { remixTitle = it },
                        onSelectStyle = { selectedRemixStyle = it },
                        onCrossfadeChange = { crossfadeSec = it },
                        onPickTrackA = {
                            pickerSearchQuery = ""
                            pickingTrackSlot = 1
                        },
                        onPickTrackB = {
                            pickerSearchQuery = ""
                            pickingTrackSlot = 2
                        },
                        onSwapTracks = {
                            val tmp = selectedTrackA
                            selectedTrackA = selectedTrackB
                            selectedTrackB = tmp
                        },
                        onAutoMatchPair = {
                            autoMatchSeed++
                            val pair = SmartAutoRemixEngine.findBestMatchingPair(localTracks, autoMatchSeed)
                            if (pair != null) {
                                selectedTrackA = pair.trackA
                                selectedTrackB = pair.trackB
                                autoMatchReason = pair.reason
                            }
                        },
                        onDismissRequest = onDismissRequest,
                        onCreateAutoRemix = {
                            val a = selectedTrackA
                            val b = selectedTrackB
                            if (a != null && b != null) {
                                onCreateAutoRemix?.invoke(
                                    a,
                                    b,
                                    remixTitle.ifBlank { "${a.title} × ${b.title}" },
                                    selectedRemixStyle,
                                    crossfadeSec.roundToInt()
                                )
                            }
                        }
                    )
                } else {
                    // Configuración de Mixtape Continuo de Lista Completa
                    OutlinedTextField(
                        value = mixtapeTitle,
                        onValueChange = { mixtapeTitle = it },
                        label = { Text("Nombre del Mixtape") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mixtape_title_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Duración del Crossfade (Fundido):",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "${crossfadeSec.roundToInt()} seg",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Slider(
                        value = crossfadeSec,
                        onValueChange = { crossfadeSec = it },
                        valueRange = 2f..12f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceCard,
                        border = BorderStroke(0.8.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "✨ Incluye automáticamente:",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Fusión real de audio en 1 sola pista con curva DJ sin silencios.\n" +
                                    "• Portada en Collage WebP con hasta 4 carátulas de la lista.\n" +
                                    "• Cambio reactivo de carátula y Video Canvas en vivo según el minuto.\n" +
                                    "• Letras .LRC combinadas automáticamente para modo Karaoke.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancelar", color = TextSecondary)
                        }
                        Button(
                            onClick = {
                                onCreateMixtape(
                                    mixtapeTitle.ifBlank { "Aura Continuous Mix" },
                                    crossfadeSec.roundToInt()
                                )
                            },
                            enabled = localTracks.size >= 2,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1.4f)
                                .testTag("confirm_create_mixtape_btn")
                        ) {
                            Icon(Icons.Default.AllInclusive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fusionar Pistas", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Sub-diálogo para elegir manualmente la Canción A o la Canción B
    if (pickingTrackSlot != 0) {
        val filteredPickerTracks = remember(localTracks, pickerSearchQuery) {
            if (pickerSearchQuery.isBlank()) localTracks
            else localTracks.filter {
                it.title.contains(pickerSearchQuery, ignoreCase = true) ||
                    it.artist.contains(pickerSearchQuery, ignoreCase = true)
            }
        }
        AlertDialog(
            onDismissRequest = { pickingTrackSlot = 0 },
            title = {
                Text(
                    text = if (pickingTrackSlot == 1) "Elegir Canción A (Voz / Base 1)" else "Elegir Canción B (Base / Drop 2)",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    OutlinedTextField(
                        value = pickerSearchQuery,
                        onValueChange = { pickerSearchQuery = it },
                        placeholder = { Text("Buscar canción local...", color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        items(filteredPickerTracks, key = { it.id }) { trk ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceCard,
                                border = BorderStroke(0.8.dp, CardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (pickingTrackSlot == 1) selectedTrackA = trk else selectedTrackB = trk
                                        pickingTrackSlot = 0
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = trk.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = trk.artist,
                                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.CheckCircleOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingTrackSlot = 0 }) {
                    Text("Cerrar")
                }
            }
        )
    }
}
