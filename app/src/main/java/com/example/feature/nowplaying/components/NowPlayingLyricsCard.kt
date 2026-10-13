package com.example.feature.nowplaying.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.LyricsState
import com.example.core.model.Track
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Tarjeta interactiva de Letras Sincronizadas (.LRC) estilo Karaoke.
 *
 * Características:
 * - Desplazamiento automático al compás de la música.
 * - Resaltado dinámico con color primario/neón y tipografía ampliada para la frase actual.
 * - Salto en la canción al tocar cualquier frase (Seek al timestamp).
 * - Búsqueda y descarga automática desde la base de datos libre LRCLIB.
 * - Importador de archivos de letras (.lrc y .txt) directamente desde el almacenamiento del celular.
 * - Diálogo para ingresar o editar letras manualmente.
 */
@Composable
fun NowPlayingLyricsCard(
    currentTrack: Track,
    lyricsState: LyricsState,
    currentPositionMs: Long = 0L,
    currentPositionFlow: StateFlow<Long>? = null,
    animatedPrimary: Color,
    onSeekTo: (Long) -> Unit,
    onFetchOnlineLyrics: () -> Unit,
    onSaveCustomLyrics: (String) -> Unit,
    onImportLyricsUri: (android.net.Uri) -> Unit = {},
    onOpenSearchLyrics: () -> Unit = {},
    onOpenFullScreen: () -> Unit = {},
    onCloseLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var showPasteDialog by remember { mutableStateOf(false) }

    // Launcher del sistema para seleccionar archivos .lrc o .txt locales
    val importLyricsFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            onImportLyricsUri(uri)
        }
    }

    val collectedPosition = currentPositionFlow?.collectAsStateWithLifecycle()

    // Búsqueda binaria O(log N) + derivedStateOf: la LazyColumn solo se recompone cuando cambia la frase activa (cada 3-6s)
    val activeIndex by remember(lyricsState.lines, collectedPosition, currentPositionMs) {
        derivedStateOf {
            val posMs = collectedPosition?.value ?: currentPositionMs
            findActiveLyricIndexBinary(lyricsState.lines, posMs)
        }
    }

    // Desplazamiento automático para centrar la frase que suena
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && lyricsState.lines.isNotEmpty()) {
            val targetScroll = (activeIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D12).copy(alpha = 0.95f)),
        border = BorderStroke(1.2.dp, animatedPrimary.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth(0.86f)
            .aspectRatio(1f)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = animatedPrimary,
                spotColor = animatedPrimary
            )
            .testTag("now_playing_lyrics_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            // Barra superior de la tarjeta de letras
            LyricsCardTopBar(
                animatedPrimary = animatedPrimary,
                onOpenSearchLyrics = onOpenSearchLyrics,
                onPickFile = {
                    importLyricsFileLauncher.launch(
                        arrayOf("text/*", "application/octet-stream", "*/*")
                    )
                },
                onEditClick = { showPasteDialog = true },
                onOpenFullScreen = onOpenFullScreen,
                onCloseLyrics = onCloseLyrics
            )

            // Contenido principal de letras
            LyricsCardContent(
                lyricsState = lyricsState,
                activeIndex = activeIndex,
                listState = listState,
                animatedPrimary = animatedPrimary,
                onSeekTo = onSeekTo,
                onFetchOnlineLyrics = onFetchOnlineLyrics,
                onOpenSearchLyrics = onOpenSearchLyrics,
                onPickFile = {
                    importLyricsFileLauncher.launch(
                        arrayOf("text/*", "application/octet-stream", "*/*")
                    )
                },
                onEditClick = { showPasteDialog = true }
            )
        }
    // Diálogo para pegar o editar letra
    if (showPasteDialog) {
        LyricsPasteDialog(
            trackTitle = currentTrack.title,
            initialText = lyricsState.plainLyrics,
            animatedPrimary = animatedPrimary,
            onSave = { onSaveCustomLyrics(it) },
            onDismiss = { showPasteDialog = false }
        )
    }
}

}
