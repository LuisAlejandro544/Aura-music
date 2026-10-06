package com.example.ui.screens.settings.components.media

/**
 * Utilidades de formato de almacenamiento para medios de Aura Music.
 */
object StoredMediaFormatUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format("%.1f MB", mb)
        } else {
            String.format("%.0f KB", kb)
        }
    }
}
