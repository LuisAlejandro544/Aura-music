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
import com.example.model.Track
import com.example.ui.theme.ArtworkColorExtractor
import com.example.ui.theme.CardBorder
import com.example.ui.theme.ExtractedArtworkColors
import android.view.TextureView
import android.view.View
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import kotlin.math.abs

/**
 * Busca recursivamente el [TextureView] interno dentro de la jerarquía de [PlayerView].
 */
private fun findInternalTextureView(view: View?): TextureView? {
    if (view == null) return null
    if (view is TextureView) return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val found = findInternalTextureView(view.getChildAt(i))
            if (found != null) return found
        }
    }
    return null
}

/**
 * Estado reactivo compartido del reproductor de Video Canvas para indicar en Now Playing y MiniPlayer
 * cuando el video de fondo está cargando buffer o esperando conexión a internet.
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

/**
 * Componente de reproducción de Video de Fondo (Canvas / Video Sincronizado) con aceleración GPU sin colisiones
 * y muestreo cromático en tiempo real adaptativo por batería.
 *
 * Arquitectura Anti-Lag para Grabación de Pantalla y Multitarea:
 * 1. Usa TextureView (`app:surface_type="texture_view"`) en lugar de SurfaceView para integrarse directamente
 *    en el árbol HWUI/Skia de Jetpack Compose, evitando que SurfaceFlinger y el VirtualDisplay del grabador
 *    de pantalla sufran bloqueos de BufferQueue al aplicar recortes (clip) o transiciones de opacidad (alpha),
 *    y permitiendo extraer micro-bitmaps (24x24) en <1ms sin abrir un segundo MediaCodec.
 * 2. Desactiva por completo el renderizador de audio (`C.TRACK_TYPE_AUDIO`) en el reproductor de video de fondo
 *    para que jamás instancie un segundo decodificador de audio ni compita con el motor DSP C++20 principal.
 * 3. Muestreo Cromático Adaptativo en Tiempo Real (Cada 1 Segundo):
 *    - **1000 ms (1s)** cuando el teléfono tiene **> 15% de batería** y el modo ahorro de energía está desactivado.
 *    - **1500 ms (1.5s)** cuando el teléfono tiene **<= 15% de batería** o el modo ahorro de energía está activado.
 * 4. Suspensión Inteligente en Segundo Plano (Modo Juego):
 *    - Cuando la aplicación pasa a segundo plano mientras el usuario juega, pausa el decodificador de video
 *      por hardware y detiene el muestreo del TextureView para dejar el 100% de la GPU libre al juego,
 *      sincronizando y reanudando al instante cuando el usuario vuelve a abrir la app.
 */
@OptIn(UnstableApi::class)
@Composable
fun BackgroundVideoPlayer(
    videoUriString: String,
    isVideoLoop: Boolean,
    isPlaying: Boolean,
    currentPositionMs: Long = 0L,
    currentPositionFlow: kotlinx.coroutines.flow.StateFlow<Long>? = null,
    playbackSpeed: Float = 1.0f,
    placeholderTrack: Track? = null,
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 24.dp,
    fitHorizontalInFullscreen: Boolean = false,
    enableLiveColorSampling: Boolean = true,
    onLiveColorsExtracted: ((ExtractedArtworkColors, Long) -> Unit)? = null,
    onVideoLoadingStateChanged: ((isBuffering: Boolean, isNetworkStalled: Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val fallbackPrimary = MaterialTheme.colorScheme.primary
    val fallbackSecondary = MaterialTheme.colorScheme.secondary
    val isAppInForeground by com.example.AuraApplication.isAppInForegroundFlow.collectAsState()
    val shouldPlayVideoNow = isPlaying && (isAppInForeground || !com.example.AuraApplication.isBackgroundGameModeEnabled)

    val isRemoteStream = remember(videoUriString) {
        videoUriString.startsWith("http://", ignoreCase = true) ||
            videoUriString.startsWith("https://", ignoreCase = true)
    }

    var isFirstFrameRendered by remember(videoUriString) { mutableStateOf(false) }
    var isVideoBuffering by remember(videoUriString) { mutableStateOf(isRemoteStream) }
    var isVideoNetworkStalled by remember(videoUriString) { mutableStateOf(false) }
    var videoDimensions by remember(videoUriString) { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(isVideoBuffering, isVideoNetworkStalled) {
        BackgroundVideoLoadingState.update(isVideoBuffering, isVideoNetworkStalled)
        onVideoLoadingStateChanged?.invoke(isVideoBuffering, isVideoNetworkStalled)
    }

    // Detección reactiva de si el video es horizontal (16:9 / panorámico) o vertical (9:16)
    val isHorizontalVideo = remember(videoDimensions) {
        val (w, h) = videoDimensions ?: return@remember false
        w > (h * 1.15f)
    }

    // Modo de redimensionado elegible por el usuario:
    // - Si fitHorizontalInFullscreen es false (Modo Rellenar / Recortar): usa RESIZE_MODE_ZOOM para que el video
    //   ocupe el 100% de la pantalla de arriba a abajo sin franjas ni cortes horizontales, tomando la región central.
    // - Si fitHorizontalInFullscreen es true (Modo Adaptado): usa RESIZE_MODE_FIT en videos horizontales para
    //   mostrar todo el fotograma sin recortar rostros ni laterales.
    val targetResizeMode = when {
        cornerRadius == 0.dp && isHorizontalVideo && fitHorizontalInFullscreen -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        else -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }

    // Listener recordado para poder desregistrarlo limpiamente al liberar el reproductor
    val videoListener = remember(videoUriString) {
        object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
                isVideoBuffering = false
                isVideoNetworkStalled = false
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        if (isRemoteStream) {
                            isVideoBuffering = true
                        }
                    }
                    Player.STATE_READY -> {
                        isVideoBuffering = false
                        isVideoNetworkStalled = false
                    }
                    Player.STATE_ENDED, Player.STATE_IDLE -> {
                        if (isFirstFrameRendered || !isRemoteStream) {
                            isVideoBuffering = false
                        }
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (isRemoteStream) {
                    isVideoBuffering = true
                    isVideoNetworkStalled = true
                } else {
                    isVideoBuffering = false
                    isVideoNetworkStalled = false
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoDimensions = Pair(videoSize.width, videoSize.height)
                }
            }
        }
    }

    var playerViewRef by remember(videoUriString) { mutableStateOf<PlayerView?>(null) }

    // Instancia de ExoPlayer optimizada exclusivamente para renderizado visual por GPU sin colisiones con Screen Recorder
    val videoPlayer = remember(videoUriString) {
        val appCtx = context.applicationContext

        val renderersFactory = DefaultRenderersFactory(appCtx)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .experimentalSetMediaCodecAsyncCryptoFlagEnabled(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ if (isRemoteStream) 2500 else 1500,
                /* maxBufferMs = */ if (isRemoteStream) 8000 else 5000,
                /* bufferForPlaybackMs = */ if (isRemoteStream) 600 else 250,
                /* bufferForPlaybackAfterRebufferMs = */ if (isRemoteStream) 1200 else 500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
            com.example.data.streaming.StreamingHttpDataSourceConfig.buildExoPlayerDataSourceFactory(appCtx)
        )

        ExoPlayer.Builder(appCtx, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                // Desactivar por completo las pistas de audio y texto en el reproductor de video
                // y limitar en streaming la resolución/framerate (máx 480p @ 30fps) para enfriar GPU/CPU.
                trackSelectionParameters = TrackSelectionParameters.Builder(appCtx)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .apply {
                        if (isRemoteStream) {
                            setMaxVideoSize(854, 480)
                            setMaxVideoFrameRate(30)
                            setMaxVideoBitrate(1_200_000)
                        }
                    }
                    .build()

                volume = 0f
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
                repeatMode = if (isVideoLoop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF

                val mediaItem = if (
                    videoUriString.startsWith("http://", ignoreCase = true) ||
                    videoUriString.startsWith("https://", ignoreCase = true) ||
                    videoUriString.startsWith("content://", ignoreCase = true) ||
                    videoUriString.startsWith("file://", ignoreCase = true)
                ) {
                    MediaItem.fromUri(Uri.parse(videoUriString))
                } else {
                    MediaItem.fromUri(Uri.fromFile(File(videoUriString)))
                }
                setMediaItem(mediaItem)
                setPlaybackSpeed(playbackSpeed)

                // Sincronizar posición inicial exacta antes de prepare() para evitar el re-buffering en negro
                val initialPos = currentPositionFlow?.value ?: currentPositionMs
                if (!isVideoLoop && initialPos > 0L) {
                    seekTo(initialPos)
                }

                addListener(videoListener)

                prepare()
                if (shouldPlayVideoNow) {
                    play()
                }
            }
    }

    // Auto-recuperación en segundo plano cuando el video online sufre micro-cortes de internet
    LaunchedEffect(isVideoNetworkStalled, isRemoteStream, shouldPlayVideoNow, videoPlayer) {
        if (isRemoteStream && isVideoNetworkStalled && shouldPlayVideoNow) {
            while (isActive && isVideoNetworkStalled) {
                delay(2500L)
                if (videoPlayer.playbackState == Player.STATE_IDLE) {
                    try {
                        val pos = currentPositionFlow?.value ?: currentPositionMs
                        if (!isVideoLoop && pos > 0L) {
                            videoPlayer.seekTo(pos)
                        }
                        videoPlayer.prepare()
                        videoPlayer.play()
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    // Sincronizar velocidad de reproducción en tiempo real con la velocidad de la música
    LaunchedEffect(playbackSpeed, videoPlayer) {
        videoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    // Gestionar play / pause según el estado del reproductor principal y si la app está en primer plano:
    // Al ir a segundo plano mientras juegas, el video se pausa liberando GPU; al volver a primer plano,
    // sincroniza el segundo actual con la canción y continúa reproduciendo sin cortes.
    LaunchedEffect(shouldPlayVideoNow, isAppInForeground, videoPlayer) {
        if (shouldPlayVideoNow) {
            if (!isVideoLoop) {
                val targetPos = currentPositionFlow?.value ?: currentPositionMs
                if (targetPos > 0L && abs(videoPlayer.currentPosition - targetPos) > 1200L) {
                    videoPlayer.seekTo(targetPos)
                }
            }
            videoPlayer.play()
        } else {
            videoPlayer.pause()
        }
    }

    // Muestreo cromático en tiempo real desde el TextureView cada 1 segundo (1000 ms):
    // - 1000 ms cuando la batería es > 15% y no está activo el ahorro de batería
    // - 1500 ms cuando la batería es <= 15% o está activo el ahorro de batería
    // - Se suspende por completo cuando la app está en segundo plano para no consumir GPU/CPU mientras juegas
    LaunchedEffect(videoUriString, isFirstFrameRendered, shouldPlayVideoNow, isAppInForeground, enableLiveColorSampling) {
        val canSampleInCurrentState = isAppInForeground || !com.example.AuraApplication.isBackgroundGameModeEnabled
        if (!enableLiveColorSampling || !isFirstFrameRendered || !canSampleInCurrentState) return@LaunchedEffect

        var sampledOnceWhenPaused = false
        while (isActive) {
            val intervalMs = ArtworkColorExtractor.getAdaptiveSampleIntervalMs(context)
            if (shouldPlayVideoNow || !sampledOnceWhenPaused) {
                val textureView = (playerViewRef?.videoSurfaceView as? TextureView)
                    ?: findInternalTextureView(playerViewRef)
                if (textureView != null && textureView.isAvailable && textureView.width > 0 && textureView.height > 0) {
                    val microBitmap = try {
                        textureView.getBitmap(24, 24)
                    } catch (_: Throwable) {
                        null
                    }
                    if (microBitmap != null) {
                        val extracted = ArtworkColorExtractor.extractColorsFromVideoFrame(
                            frameBitmap = microBitmap,
                            trackId = placeholderTrack?.id,
                            videoUri = videoUriString,
                            fallbackPrimary = fallbackPrimary,
                            fallbackSecondary = fallbackSecondary,
                            sampleIntervalMs = intervalMs
                        )
                        if (extracted != null) {
                            onLiveColorsExtracted?.invoke(extracted, intervalMs)
                            sampledOnceWhenPaused = true
                        }
                    }
                }
            }
            delay(intervalMs)
        }
    }

    // Gestionar sincronización temporal para videos largos (> 10s) únicamente cuando el video está activo en primer plano
    var lastSyncSeekWallClockMs by remember(videoUriString) { mutableLongStateOf(0L) }

    if (currentPositionFlow != null) {
        LaunchedEffect(currentPositionFlow, isVideoLoop, shouldPlayVideoNow, videoPlayer) {
            if (!isVideoLoop && shouldPlayVideoNow) {
                currentPositionFlow.collect { posMs ->
                    val now = android.os.SystemClock.elapsedRealtime()
                    val playerPos = videoPlayer.currentPosition
                    val driftMs = abs(playerPos - posMs)
                    if (driftMs > 1850L && (now - lastSyncSeekWallClockMs) > 1500L && videoPlayer.playbackState == Player.STATE_READY) {
                        lastSyncSeekWallClockMs = now
                        videoPlayer.seekTo(posMs)
                    }
                }
            }
        }
    } else {
        LaunchedEffect(currentPositionMs, isVideoLoop, shouldPlayVideoNow, videoPlayer) {
            if (!isVideoLoop && shouldPlayVideoNow) {
                val now = android.os.SystemClock.elapsedRealtime()
                val playerPos = videoPlayer.currentPosition
                val driftMs = abs(playerPos - currentPositionMs)
                if (driftMs > 1850L && (now - lastSyncSeekWallClockMs) > 1500L && videoPlayer.playbackState == Player.STATE_READY) {
                    lastSyncSeekWallClockMs = now
                    videoPlayer.seekTo(currentPositionMs)
                }
            }
        }
    }

    // Liberación estricta de recursos de códec, SurfaceTexture y referencias de vista al cambiar de video o desmontar
    DisposableEffect(videoPlayer) {
        onDispose {
            try {
                playerViewRef?.player = null
                playerViewRef = null
                videoPlayer.removeListener(videoListener)
                videoPlayer.clearVideoSurface()
                videoPlayer.stop()
                videoPlayer.clearMediaItems()
                videoPlayer.release()
            } catch (_: Throwable) {}
        }
    }

    val videoAlpha by animateFloatAsState(
        targetValue = if (isFirstFrameRendered) 1f else 0f,
        animationSpec = tween(240),
        label = "VideoFadeInAlpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .border(if (cornerRadius > 0.dp) 1.dp else 0.dp, CardBorder, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        // Capa 1: Carátula que se muestra únicamente mientras el decodificador prepara el primer cuadro
        // (desaparece suavemente al renderizar el video para no generar cortes horizontales detrás del video)
        if (placeholderTrack != null && videoAlpha < 0.99f) {
            ArtworkImage(
                track = placeholderTrack,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (1f - videoAlpha).coerceIn(0f, 1f) },
                cornerRadius = cornerRadius
            )
        } else if (placeholderTrack == null && videoAlpha < 0.99f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F16))
            )
        }

        // Capa 2: Reproductor de video sobre TextureView acelerado por GPU, sin perforar SurfaceFlinger
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.aura_background_video_player, null, false) as PlayerView).apply {
                    playerViewRef = this
                    player = videoPlayer
                    useController = false
                    resizeMode = targetResizeMode
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerViewRef = playerView
                if (playerView.player != videoPlayer) {
                    playerView.player = videoPlayer
                }
                if (playerView.resizeMode != targetResizeMode) {
                    playerView.resizeMode = targetResizeMode
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = videoAlpha }
        )

        // Capa 3: Degradado sutil oscuro para garantizar contraste con controles flotantes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = if (cornerRadius == 0.dp && isHorizontalVideo && fitHorizontalInFullscreen) 0.45f else 0.25f),
                            Color.Transparent,
                            Color.Black.copy(alpha = if (cornerRadius == 0.dp) 0.65f else 0.45f)
                        )
                    )
                )
        )
    }
}
