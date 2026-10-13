package com.example.ui.theme

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager

/**
 * Intervalo adaptativo por batería y publicación de color en vivo.
 * Rol arquitectónico: extensiones internas de ArtworkColorExtractor; la API pública no cambia.
 */
/**
 * Determina el intervalo de muestreo cromático en tiempo real según el estado de batería y ahorro de energía:
 * - 1000 ms (1 segundo) si la batería es > 15% y el Modo Ahorro de Batería está desactivado.
 * - 1500 ms (1.5 segundos) si la batería es <= 15% o el Modo Ahorro de Batería está activado.
 */
internal fun ArtworkColorExtractor.getAdaptiveSampleIntervalMsInternal(context: Context): Long {
    return try {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = powerManager?.isPowerSaveMode == true
        if (isPowerSave) {
            return INTERVAL_BATTERY_SAVER_MS
        }

        val batteryStatus: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        }

        if (batteryPct in 0..15) {
            INTERVAL_BATTERY_SAVER_MS
        } else {
            INTERVAL_NORMAL_MS
        }
    } catch (_: Throwable) {
        INTERVAL_NORMAL_MS
    }
}

/**
 * Publica una nueva instantánea de colores extraída en vivo desde el reproductor de video.
 */
internal fun ArtworkColorExtractor.publishLiveVideoColorsInternal(
    trackId: Long?,
    videoUri: String,
    colors: ExtractedArtworkColors,
    sampleIntervalMs: Long
) {
    if (trackId != null) {
        lastVideoTrackId = trackId
    }
    lastVideoUri = videoUri
    lastVideoColors = colors
    _liveVideoColorsFlow.value = LiveVideoColorSnapshot(
        videoUri = videoUri,
        colors = colors,
        sampleIntervalMs = sampleIntervalMs
    )
}
