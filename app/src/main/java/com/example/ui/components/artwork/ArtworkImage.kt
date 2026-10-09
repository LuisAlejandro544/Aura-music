package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.model.Track
import java.io.File

/**
 * Componente para renderizar la portada de una pista de música con rendimiento optimizado para 120 FPS:
 * - No ejecuta File.exists() síncrono en el hilo principal (UI Thread) durante el scroll de LazyColumn.
 * - No escanea píxeles en tiempo real (las franjas negras se recortan una única vez al guardar el WebP en disco).
 * - Memoriza el ImageRequest por ruta para aprovechar al 100% la caché en RAM de Coil.
 */
@Composable
fun ArtworkImage(
    track: Track?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp
) {
    val context = LocalContext.current
    val artPath = track?.albumArtPath
    var loadFailed by remember(artPath) { mutableStateOf(false) }

    val imageRequest = remember(artPath, context) {
        if (!artPath.isNullOrBlank()) {
            ImageRequest.Builder(context)
                .data(File(artPath))
                .memoryCacheKey(artPath)
                .diskCacheKey(artPath)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(false)
                .build()
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (imageRequest != null && !loadFailed) {
            AsyncImage(
                model = imageRequest,
                contentDescription = "Carátula de ${track?.title}",
                contentScale = ContentScale.Crop,
                onError = { loadFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            ProceduralArtwork(
                title = track?.title ?: "Aura",
                artist = track?.artist ?: "Música",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

