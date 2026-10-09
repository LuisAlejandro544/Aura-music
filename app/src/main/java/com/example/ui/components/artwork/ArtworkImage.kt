package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import com.example.model.Track
import java.io.File

/**
 * Transformación de Coil que recorta automáticamente las bandas negras horizontales (letterbox)
 * en carátulas 4:3 ya almacenadas (por ejemplo miniaturas hqdefault de YouTube).
 */
private class DeletterboxTransformation : Transformation {
    override val cacheKey: String = "aura_deletterbox_v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        if (w < 80 || h < 80) return input

        fun isRowDark(y: Int): Boolean {
            val sampleXs = intArrayOf(
                (w * 0.15f).toInt(),
                (w * 0.35f).toInt(),
                (w * 0.50f).toInt(),
                (w * 0.65f).toInt(),
                (w * 0.85f).toInt()
            )
            var totalLuma = 0
            for (x in sampleXs) {
                val pixel = input.getPixel(x.coerceIn(0, w - 1), y.coerceIn(0, h - 1))
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                totalLuma += (r + g + b) / 3
            }
            return (totalLuma / sampleXs.size) < 20
        }

        if (!isRowDark((h * 0.02f).toInt()) || !isRowDark((h * 0.98f).toInt())) {
            return input
        }

        val maxCrop = (h * 0.18f).toInt()
        val step = (h / 100).coerceAtLeast(1)
        var topCrop = 0
        var yTop = step
        while (yTop <= maxCrop && isRowDark(yTop)) {
            topCrop = yTop
            yTop += step
        }

        var bottomCrop = 0
        var yBottom = h - 1 - step
        while (yBottom >= h - maxCrop && isRowDark(yBottom)) {
            bottomCrop = (h - 1) - yBottom
            yBottom -= step
        }

        val symCrop = minOf(topCrop, bottomCrop)
        if (symCrop < (h * 0.05f).toInt()) return input
        val finalCrop = (symCrop + 2).coerceAtMost(maxCrop)
        val newHeight = h - (finalCrop * 2)
        if (newHeight <= h / 2) return input

        return try {
            Bitmap.createBitmap(input, 0, finalCrop, w, newHeight)
        } catch (_: Exception) {
            input
        }
    }
}

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
    val deletterboxTransformation = remember { DeletterboxTransformation() }

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
                    .transformations(deletterboxTransformation)
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
