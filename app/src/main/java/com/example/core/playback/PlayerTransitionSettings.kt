package com.example.core.playback


/**
 * Persistencia de crossfade, automix, EQ y gapless.
 * Rol arquitectónico: extensiones internas de AuraAudioPlayer; la API pública no cambia.
 */
internal fun AuraAudioPlayer.setCrossfadeSecondsInternal(seconds: Int) {
    val clamped = seconds.coerceIn(0, 15)
    _crossfadeSeconds.value = clamped
    try {
        transitionPrefs.edit().putInt("crossfade_seconds", clamped).apply()
    } catch (_: Throwable) {}
}

internal fun AuraAudioPlayer.setDjAutomixEnabledInternal(enabled: Boolean) {
    _isDjAutomixEnabled.value = enabled
    try {
        transitionPrefs.edit().putBoolean("dj_automix_enabled", enabled).apply()
    } catch (_: Throwable) {}
    if (!enabled) {
        effectManager.setDjAutomixTransition(false, 0.0f)
    }
}

internal fun AuraAudioPlayer.setDjEqCurveEnabledInternal(enabled: Boolean) {
    _isDjEqCurveEnabled.value = enabled
    try {
        transitionPrefs.edit().putBoolean("dj_eq_curve_enabled", enabled).apply()
    } catch (_: Throwable) {}
    if (!enabled) {
        effectManager.setDjAutomixTransition(false, 0.0f)
    }
}

internal fun AuraAudioPlayer.setGaplessEnabledInternal(enabled: Boolean) {
    _isGaplessEnabled.value = enabled
    try {
        transitionPrefs.edit().putBoolean("gapless_enabled", enabled).apply()
    } catch (_: Throwable) {}
}
