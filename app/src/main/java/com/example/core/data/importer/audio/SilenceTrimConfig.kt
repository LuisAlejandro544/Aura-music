package com.example.core.data.importer



/**
 * Umbrales dBFS, ventanas de escaneo y nombre del JSON de offsets.
 * Rol arquitectónico: submódulo de AudioSilenceTrimmer (fachada pública intacta).
 */
internal object SilenceTrimConfig {
    const val SILENCE_THRESHOLD_DB: Double = -42.0
    const val MAX_SCAN_EDGE_US: Long = 15_000_000L
    const val MIN_TRIM_START_MS: Long = 180L
    const val MIN_TRIM_END_MS: Long = 220L
    const val ATTACK_GUARD_US: Long = 50_000L
    const val RELEASE_GUARD_US: Long = 140_000L
    const val OFFSETS_FILE_NAME: String = "aura_silence_trim_offsets.json"
}
