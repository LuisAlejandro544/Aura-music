package com.example.feature.settings.components.appearance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.model.StreamingAudioQuality
import com.example.core.model.StreamingCacheConfig
import com.example.core.model.StreamingVideoNetworkPolicy

/**
 * Tarjeta combinada de streaming (compatibilidad pública).
 * Rol arquitectónico: fachada que reutiliza las 4 sub-tarjetas independientes
 * ([StreamingVideoPolicyCard], [StreamingAudioQualityCard], [StreamingDataSaverCard] y
 * [StreamingCacheUsageCard]) para no duplicar lógica. Las pantallas nuevas usan cada
 * tarjeta por separado en su propia pantalla.
 */
@Composable
fun StreamingCacheAndVideoSettingsCard(
    cacheConfig: StreamingCacheConfig,
    onSelectVideoPolicy: (StreamingVideoNetworkPolicy) -> Unit,
    onChangeMaxCacheMb: (Int) -> Unit,
    onClearStreamingCache: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectAudioQuality: (StreamingAudioQuality) -> Unit = {},
    onToggleDataSaver: (Boolean) -> Unit = {}
) {
    Column(
        modifier = modifier.testTag("streaming_cache_and_video_settings_card")
    ) {
        StreamingVideoPolicyCard(
            cacheConfig = cacheConfig,
            onSelectVideoPolicy = onSelectVideoPolicy
        )
        Spacer(modifier = Modifier.height(12.dp))
        StreamingAudioQualityCard(
            cacheConfig = cacheConfig,
            onSelectAudioQuality = onSelectAudioQuality
        )
        Spacer(modifier = Modifier.height(12.dp))
        StreamingDataSaverCard(
            cacheConfig = cacheConfig,
            onToggleDataSaver = onToggleDataSaver
        )
        Spacer(modifier = Modifier.height(12.dp))
        StreamingCacheUsageCard(
            cacheConfig = cacheConfig,
            onChangeMaxCacheMb = onChangeMaxCacheMb,
            onClearStreamingCache = onClearStreamingCache
        )
    }
}
