package com.example.core.playback

/**
 * Soporte de efectos en Modo Streaming (Explorar).
 * Fases 1-3 activas, resto "Próximamente".
 * Rol arquitectónico: tabla central de capacidades para UI + DSP.
 *
 * - Fase 1 (riesgo cero, nivel player): Velocidad/Pitch, Crossfade manual + fade-in,
 *   Repetidor A-B, Sleep Timer, Becoming-Noisy, visualizador 28 bandas.
 * - Fase 2 (DSP estático): Ecualizador 10 bandas + BassBoost + presets + alcance,
 *   Normalización LUFS (-18/-14/-11).
 * - Fase 3 (DSP espacial): Audio 8D / 16D.
 * - Próximamente bloqueado: Reverb, Vocal Clarity HD, Crossfeed/Balance,
 *   Smart AutoMix DJ + curva X, Bit-Perfect/AAudio/USB DAC, trim de silencios.
 */
object StreamingEffectsSupport {
    // Fase 1: ya funcionaban, solo gating de UI.
    const val SPEED_PITCH_SUPPORTED = true
    const val CROSSFADE_MANUAL_SUPPORTED = true
    const val AB_LOOP_SUPPORTED = true
    const val SLEEP_TIMER_SUPPORTED = true
    const val GAPLESS_SUPPORTED = true

    // Fase 2: primer desbloqueo DSP.
    const val EQ_SUPPORTED = true
    const val NORMALIZATION_SUPPORTED = true

    // Fase 3: espaciales.
    const val SPATIAL_8D_16D_SUPPORTED = true

    // Fase 4+: bloqueados con insignia "Próximamente".
    const val REVERB_SUPPORTED = false
    const val VOCAL_CLARITY_SUPPORTED = false
    const val CROSSFEED_SUPPORTED = false
    const val BALANCE_SUPPORTED = false
    const val DJ_AUTOMIX_SUPPORTED = false
    const val DJ_EQ_CURVE_SUPPORTED = false
    const val BIT_PERFECT_SUPPORTED = false
    const val SILENCE_TRIM_SUPPORTED = false
}
