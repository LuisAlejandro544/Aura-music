package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.Track
import com.example.ui.components.BottomNavBar
import com.example.ui.components.MiniPlayer
import com.example.ui.navigation.NavScreen
import com.example.ui.theme.BackgroundDark
import com.example.viewmodel.MusicViewModel

/**
 * Barra inferior global (MiniPlayer flotante + navegación).
 * Rol arquitectónico: recolecta su propio estado de reproducción y navegación.
 */
@Composable
fun AppBottomBarHost(
    viewModel: MusicViewModel,
    effectiveTrack: Track?,
    animatedMiniPrimary: Color,
    animatedMiniSecondary: Color
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val hasVisitedSettings by viewModel.hasVisitedSettings.collectAsStateWithLifecycle()
    val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val isMiniPlayerVideoEnabled by viewModel.isMiniPlayerVideoEnabled.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val isAudioBuffering by viewModel.isAudioBuffering.collectAsStateWithLifecycle()
    val isNetworkStalled by viewModel.isNetworkStalled.collectAsStateWithLifecycle()
    val isResolvingStreamGlobal by viewModel.isResolvingStream.collectAsStateWithLifecycle()
    val isVideoBufferingGlobal by com.example.ui.components.BackgroundVideoLoadingState.isVideoBufferingFlow.collectAsStateWithLifecycle()
    val isVideoNetworkStalledGlobal by com.example.ui.components.BackgroundVideoLoadingState.isVideoNetworkStalledFlow.collectAsStateWithLifecycle()

                    if (currentScreen !is NavScreen.Onboarding) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BackgroundDark)
                        ) {
                            AnimatedVisibility(
                                visible = effectiveTrack != null && !isNowPlayingExpanded,
                                enter = fadeIn(animationSpec = tween(220)) + slideInVertically(
                                    initialOffsetY = { it / 2 },
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                ),
                                exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(
                                    targetOffsetY = { it / 2 },
                                    animationSpec = tween(180)
                                )
                            ) {
                                MiniPlayer(
                                    currentTrack = effectiveTrack,
                                    isPlaying = isPlaying,
                                    currentPositionFlow = viewModel.currentPosition,
                                    durationMs = duration,
                                    onTogglePlayPause = { viewModel.togglePlayPause() },
                                    onSkipNext = { viewModel.playNext() },
                                    onSkipPrevious = { viewModel.playPrevious() },
                                    onClick = { viewModel.setNowPlayingExpanded(true) },
                                    dynamicPrimary = animatedMiniPrimary,
                                    dynamicSecondary = animatedMiniSecondary,
                                    isMiniPlayerVideoEnabled = isMiniPlayerVideoEnabled && !isNowPlayingExpanded,
                                    playbackSpeed = playbackSpeed,
                                    isLoadingOrBuffering = effectiveTrack?.isStreamingTrack == true && (isAudioBuffering || isResolvingStreamGlobal),
                                    isVideoLoading = effectiveTrack?.isStreamingTrack == true && isMiniPlayerVideoEnabled && !effectiveTrack?.videoUri.isNullOrEmpty() && isVideoBufferingGlobal,
                                    isWaitingForNetwork = effectiveTrack?.isStreamingTrack == true && (isNetworkStalled || (isMiniPlayerVideoEnabled && !effectiveTrack?.videoUri.isNullOrEmpty() && isVideoNetworkStalledGlobal))
                                )
                            }

                            BottomNavBar(
                                currentScreen = currentScreen,
                                onNavigate = { viewModel.navigateTo(it) },
                                showSettingsBadge = !hasVisitedSettings
                            )
                        }
                    }
}
