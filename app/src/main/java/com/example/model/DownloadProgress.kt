package com.example.model

/**
 * Modelo de datos reactivo que encapsula las métricas en tiempo real de una descarga multimedia.
 * Proporciona:
 * - Peso descargado y peso total (bytes / MB).
 * - Porcentaje de avance (0.0f a 1.0f).
 * - Velocidad de descarga de internet en tiempo real (MB/s o KB/s).
 * - Fase descriptiva del proceso (ej: "Descargando audio", "Descargando Video Canvas", "Generando carátula").
 */
data class DownloadProgress(
    val isDownloading: Boolean = false,
    val phase: String = "",
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val bytesPerSecond: Long = 0L,
    val progressFraction: Float = 0f
) {
    /**
     * Velocidad formateada con unidades inteligentes (KB/s o MB/s).
     */
    val formattedSpeed: String
        get() {
            if (bytesPerSecond <= 0) return "-- KB/s"
            val kb = bytesPerSecond / 1024.0
            return if (kb >= 1024.0) {
                String.format(java.util.Locale.US, "%.1f MB/s", kb / 1024.0)
            } else {
                String.format(java.util.Locale.US, "%.0f KB/s", kb)
            }
        }

    /**
     * Contador numérico formateado de peso descargado / peso total y porcentaje.
     * Ejemplos: "12.4 MB / 45.0 MB (28%)" o "8.3 MB descargados" si el servidor no envió Content-Length.
     */
    val formattedProgress: String
        get() {
            val curMb = bytesDownloaded / (1024.0 * 1024.0)
            return if (totalBytes > 0) {
                val totMb = totalBytes / (1024.0 * 1024.0)
                val pct = (progressFraction * 100).toInt().coerceIn(0, 100)
                String.format(java.util.Locale.US, "%.1f MB / %.1f MB (%d%%)", curMb, totMb, pct)
            } else {
                String.format(java.util.Locale.US, "%.1f MB descargados", curMb)
            }
        }
}
