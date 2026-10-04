package com.example.debug

/**
 * Modelos de datos para telemetría de rendimiento, consumo de memoria e inspección de hilos.
 *
 * Rol Arquitectónico:
 * Permite a los desarrolladores y usuarios móviles sin computadora auditar en tiempo real
 * la asignación de RAM (Java Heap vs Native Heap C++20 vs Gráficos), uso de CPU/GPU
 * y la ejecución de hilos activos de ExoPlayer, C++ DSP, Compose y corrutinas.
 */

data class MemoryTelemetryInfo(
    val totalPssMb: Float,
    val javaHeapMb: Float,
    val javaHeapMaxMb: Float,
    val nativeHeapMb: Float,
    val graphicsMb: Float,
    val codeMb: Float,
    val otherPssMb: Float,
    val deviceFreeRamMb: Long,
    val deviceTotalRamMb: Long,
    val ramUsagePercentage: Float
)

data class CpuGpuTelemetryInfo(
    val processCpuUsagePercent: Float,
    val activeThreadCount: Int,
    val availableCores: Int,
    val estimatedGpuFps: Int,
    val thermalState: String
)

enum class ThreadCategory(val label: String) {
    AUDIO_MEDIA3("Audio & Media3"),
    UI_RENDER("Interfaz & Render"),
    COROUTINE_IO("Corrutinas & IO"),
    C_PLUS_PLUS("C++20 & JNI"),
    SYSTEM("Sistema Android")
}

data class ThreadDiagnosticItem(
    val id: Long,
    val name: String,
    val state: Thread.State,
    val priority: Int,
    val isDaemon: Boolean,
    val isAlive: Boolean,
    val category: ThreadCategory,
    val topStackTrace: String,
    val fullStackTrace: String
)

data class FullPerformanceSnapshot(
    val memory: MemoryTelemetryInfo,
    val cpuGpu: CpuGpuTelemetryInfo,
    val threads: List<ThreadDiagnosticItem>,
    val timestamp: Long = System.currentTimeMillis()
)
