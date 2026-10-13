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
import com.example.core.data.importer.mixtape.SmartAutoRemixEngine
import com.example.core.model.AutoRemixStyle
import com.example.core.model.Track
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
                // Encabezado
                MixtapeDialogHeader(
                    isAutoRemixMode = isAutoRemixMode,
                    localTrackCount = localTracks.size,
                    isCreating = isCreating,
                    onDismissRequest = onDismissRequest
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Selector de Modo: Mixtape Continuo vs Auto-Remix de 2 Canciones
                MixtapeModeSelector(
                    isAutoRemixMode = isAutoRemixMode,
                    isCreating = isCreating,
                    createdTrack = createdTrack,
                    hasAutoRemix = onCreateAutoRemix != null,
                    onSelectMode = { isAutoRemixMode = it }
                )

                // Aviso de estado o error si la creación previa reportó algún problema
                if (!isCreating && createdTrack == null && !statusMessage.isNullOrBlank()) {
                    MixtapeStatusAlert(statusMessage = statusMessage)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (createdTrack != null && !isCreating) {
                    // Estado completado
                    MixtapeCompletedCard(
                        createdTrack = createdTrack,
                        isAutoRemixMode = isAutoRemixMode,
                        onDismissRequest = onDismissRequest,
                        onPlay = { onPlayCreatedTrack(it) }
                    )
                } else if (isCreating) {
                    // Progreso en vivo
                    MixtapeProgressView(
                        progress = progress,
                        statusMessage = statusMessage
                    )
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
                        onPickTrackA = { pickingTrackSlot = 1 },
                        onPickTrackB = { pickingTrackSlot = 2 },
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
                    MixtapeListConfig(
                        mixtapeTitle = mixtapeTitle,
                        onTitleChange = { mixtapeTitle = it },
                        crossfadeSec = crossfadeSec,
                        onCrossfadeChange = { crossfadeSec = it },
                        localTrackCount = localTracks.size,
                        onDismissRequest = onDismissRequest,
                        onConfirm = { title, crossfade ->
                            onCreateMixtape(
                                title.ifBlank { "Aura Continuous Mix" },
                                crossfade.roundToInt()
                            )
                        }
                    )
                }
            }
        }
    }

    // Sub-diálogo para elegir manualmente la Canción A o la Canción B
    if (pickingTrackSlot != 0) {
        MixtapeTrackPickerDialog(
            slot = pickingTrackSlot,
            localTracks = localTracks,
            onPick = { trk ->
                if (pickingTrackSlot == 1) selectedTrackA = trk else selectedTrackB = trk
                pickingTrackSlot = 0
            },
            onDismiss = { pickingTrackSlot = 0 }
        )
    }
}
