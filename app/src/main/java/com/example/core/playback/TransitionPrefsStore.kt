package com.example.core.playback

import android.content.Context
import android.content.SharedPreferences

/**
 * Almacén persistente de transiciones para AuraAudioPlayer.
 * Rol arquitectónico: encapsula `aura_transitions_prefs`
 * (crossfade 0-15s, gapless, DJ Automix y curva EQ-X) para que la
 * fachada no manipule SharedPreferences directamente.
 */
internal class TransitionPrefsStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("aura_transitions_prefs", Context.MODE_PRIVATE)
    }

    data class TransitionSettings(
        val crossfadeSeconds: Int = 0,
        val isGaplessEnabled: Boolean = true,
        val isDjAutomixEnabled: Boolean = false,
        val isDjEqCurveEnabled: Boolean = true
    )

    fun load(): TransitionSettings {
        return try {
            TransitionSettings(
                crossfadeSeconds = prefs.getInt("crossfade_seconds", 0).coerceIn(0, 15),
                isGaplessEnabled = prefs.getBoolean("gapless_enabled", true),
                isDjAutomixEnabled = prefs.getBoolean("dj_automix_enabled", false),
                isDjEqCurveEnabled = prefs.getBoolean("dj_eq_curve_enabled", true)
            )
        } catch (_: Throwable) {
            TransitionSettings()
        }
    }

    fun saveCrossfadeSeconds(seconds: Int) {
        try {
            prefs.edit().putInt("crossfade_seconds", seconds.coerceIn(0, 15)).apply()
        } catch (_: Throwable) {}
    }

    fun saveGaplessEnabled(enabled: Boolean) {
        try {
            prefs.edit().putBoolean("gapless_enabled", enabled).apply()
        } catch (_: Throwable) {}
    }

    fun saveDjAutomixEnabled(enabled: Boolean) {
        try {
            prefs.edit().putBoolean("dj_automix_enabled", enabled).apply()
        } catch (_: Throwable) {}
    }

    fun saveDjEqCurveEnabled(enabled: Boolean) {
        try {
            prefs.edit().putBoolean("dj_eq_curve_enabled", enabled).apply()
        } catch (_: Throwable) {}
    }
}
