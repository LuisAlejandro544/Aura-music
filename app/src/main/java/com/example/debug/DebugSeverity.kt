package com.example.debug

import androidx.compose.ui.graphics.Color

/**
 * Niveles de severidad y clasificación de eventos para el monitor de diagnóstico de Aura Music.
 * Permite categorizar desde cierres inesperados (Crash) hasta advertencias y telemetría de rendimiento.
 */
enum class DebugSeverity(
    val label: String,
    val hexColor: Long
) {
    CRASH("CRASH", 0xFFFF3B30),         // Rojo crítico: Cierres inesperados y excepciones fatales no atrapadas
    CRITICAL("CRÍTICO", 0xFFFF9500),    // Naranja: Fallos graves en JNI C++, Media3 o Base de Datos
    ERROR("ERROR", 0xFFE91E63),         // Rosa/Carmín: Excepciones controladas (fallos de lectura SAF, WebP, etc.)
    WARNING("WARNING", 0xFFFFCC00),     // Ámbar: Memoria baja (onLowMemory), underrun de audio, degradación
    INFO("INFO", 0xFF00E5FF);           // Cyan: Arranque de servicios, carga de C++20, transiciones clave

    val color: Color
        get() = Color(hexColor)
}
