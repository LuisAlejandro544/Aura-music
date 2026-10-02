package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.Track
import java.io.File

/**
 * Componente para renderizar la portada de una pista de música.
 * Prioriza la carátula incrustada en el archivo; de no existir, usa el arte abstracto
 * generado de alta calidad o un gradiente estético con el icono musical.
 */
@Composable
fun ArtworkImage(
    track: Track?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp
) {
    val context = LocalContext.current
    val artPath = track?.albumArtPath

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (!artPath.isNullOrEmpty() && File(artPath).exists()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(artPath))
                    .crossfade(true)
                    .build(),
                contentDescription = "Carátula de ${track?.title}",
                contentScale = ContentScale.Crop,
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
