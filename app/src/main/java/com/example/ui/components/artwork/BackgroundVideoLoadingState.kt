package com.example.ui.components

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.core.model.Track
import com.example.ui.theme.ArtworkColorExtractor
import com.example.ui.theme.CardBorder
import com.example.ui.theme.ExtractedArtworkColors
import android.view.TextureView
import android.view.View
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
/**
 * Estado reactivo compartido de buffering del Video Canvas.
 * Rol arquitectónico: pieza de BackgroundVideoPlayer (API intacta).
 */
object BackgroundVideoLoadingState {
    private val _isVideoBufferingFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isVideoBufferingFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _isVideoBufferingFlow

    private val _isVideoNetworkStalledFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isVideoNetworkStalledFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _isVideoNetworkStalledFlow

    fun update(isBuffering: Boolean, isNetworkStalled: Boolean) {
        _isVideoBufferingFlow.value = isBuffering
        _isVideoNetworkStalledFlow.value = isNetworkStalled
    }

    fun reset() {
        _isVideoBufferingFlow.value = false
        _isVideoNetworkStalledFlow.value = false
    }
}
