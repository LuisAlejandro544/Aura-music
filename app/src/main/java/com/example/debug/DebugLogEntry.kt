package com.example.debug

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Representa una entrada de registro de diagnóstico generada por Aura Music.
 * Incluye timestamp de precisión, etiqueta de severidad, mensaje, traza de error en crudo y estado de hardware.
 */
data class DebugLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val dateTimeFormatted: String = formatTimestamp(timestamp),
    val severity: DebugSeverity,
    val tag: String,
    val message: String,
    val rawStackTrace: String? = null,
    val deviceInfo: DeviceDiagnosticInfo
) {
    companion object {
        fun formatTimestamp(timeMs: Long): String {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                sdf.format(Date(timeMs))
            } catch (_: Throwable) {
                timeMs.toString()
            }
        }
    }
}
