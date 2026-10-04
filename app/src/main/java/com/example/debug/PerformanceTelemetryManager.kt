package com.example.debug

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock

/**
 * Gestor y analizador de telemetría de rendimiento para Aura Music.
 *
 * Responsabilidades:
 * 1. Desglose detallado del consumo de memoria RAM: Java Heap, Native Heap (C++20),
 *    Graphics (shaders/imágenes) y PSS Total.
 * 2. Estimación de carga de CPU del proceso y estado térmico del hardware.
 * 3. Inspección y catalogación de hilos concurrentes activos (ExoPlayer, Compose, Corrutinas, JNI).
 */
object PerformanceTelemetryManager {

    private var lastCpuTimeMs: Long = 0L
    private var lastSampleTimeMs: Long = 0L
    private var lastCpuUsage: Float = 0f

    /**
     * Captura una instantánea completa del rendimiento y de todos los hilos de la aplicación.
     */
    fun captureSnapshot(context: Context): FullPerformanceSnapshot {
        val memory = captureMemory(context)
        val cpuGpu = captureCpuGpu(context)
        val threads = captureThreads()
        return FullPerformanceSnapshot(
            memory = memory,
            cpuGpu = cpuGpu,
            threads = threads
        )
    }

    private fun captureMemory(context: Context): MemoryTelemetryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val sysMemInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(sysMemInfo)

        val totalRamMb = (sysMemInfo.totalMem / (1024L * 1024L)).coerceAtLeast(1L)
        val freeRamMb = (sysMemInfo.availMem / (1024L * 1024L)).coerceAtLeast(0L)

        // Medición de memoria interna del proceso propio
        val debugMemInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(debugMemInfo)

        val totalPssMb = (debugMemInfo.totalPss / 1024f).coerceAtLeast(0f)

        // Java Heap
        val runtime = Runtime.getRuntime()
        val javaAllocatedMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)).coerceAtLeast(0f)
        val javaMaxMb = (runtime.maxMemory() / (1024f * 1024f)).coerceAtLeast(1f)

        // Native Heap (C++20 DSP Engine y decodificadores)
        val nativeAllocatedMb = (Debug.getNativeHeapAllocatedSize() / (1024f * 1024f)).coerceAtLeast(0f)

        // Gráficos y Código compilado
        val graphicsKb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val stats = debugMemInfo.memoryStats
            stats["summary.graphics"]?.toLongOrNull() ?: 0L
        } else 0L
        val graphicsMb = (graphicsKb / 1024f).coerceAtLeast(0f)

        val codeKb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val stats = debugMemInfo.memoryStats
            stats["summary.code"]?.toLongOrNull() ?: 0L
        } else 0L
        val codeMb = (codeKb / 1024f).coerceAtLeast(0f)

        val otherPss = (totalPssMb - (javaAllocatedMb + nativeAllocatedMb + graphicsMb)).coerceAtLeast(0f)
        val ramUsagePercent = ((totalPssMb / totalRamMb.toFloat()) * 100f).coerceIn(0f, 100f)

        return MemoryTelemetryInfo(
            totalPssMb = totalPssMb,
            javaHeapMb = javaAllocatedMb,
            javaHeapMaxMb = javaMaxMb,
            nativeHeapMb = nativeAllocatedMb,
            graphicsMb = graphicsMb,
            codeMb = codeMb,
            otherPssMb = otherPss,
            deviceFreeRamMb = freeRamMb,
            deviceTotalRamMb = totalRamMb,
            ramUsagePercentage = ramUsagePercent
        )
    }

    private fun captureCpuGpu(context: Context): CpuGpuTelemetryInfo {
        val now = SystemClock.elapsedRealtime()
        val currentCpuTime = Process.getElapsedCpuTime()

        if (lastSampleTimeMs > 0 && now > lastSampleTimeMs) {
            val timeDiff = (now - lastSampleTimeMs).coerceAtLeast(1L)
            val cpuDiff = (currentCpuTime - lastCpuTimeMs).coerceAtLeast(0L)
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            // Porcentaje normalizado por número de núcleos
            val usage = ((cpuDiff.toFloat() / (timeDiff * cores).toFloat()) * 100f).coerceIn(0f, 100f)
            lastCpuUsage = (lastCpuUsage * 0.4f) + (usage * 0.6f) // Suavizado EMA
        }

        lastCpuTimeMs = currentCpuTime
        lastSampleTimeMs = now

        val cores = Runtime.getRuntime().availableProcessors()
        val activeThreads = Thread.activeCount()

        val thermalState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            when (pm?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Normal (Frío)"
                PowerManager.THERMAL_STATUS_LIGHT -> "Ligero (Tibio)"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderado"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severo (Throttling)"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Crítico (Alta Temp)"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergencia"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Apagado por calor"
                else -> "Óptimo"
            }
        } else "Normal"

        return CpuGpuTelemetryInfo(
            processCpuUsagePercent = lastCpuUsage,
            activeThreadCount = activeThreads,
            availableCores = cores,
            estimatedGpuFps = 60,
            thermalState = thermalState
        )
    }

    private fun captureThreads(): List<ThreadDiagnosticItem> {
        val traces = Thread.getAllStackTraces()
        val result = mutableListOf<ThreadDiagnosticItem>()

        for ((thread, stack) in traces) {
            val name = thread.name
            val category = classifyThread(name)
            val topMethod = stack.firstOrNull { elem ->
                !elem.className.startsWith("java.lang") &&
                !elem.className.startsWith("android.os")
            }?.let { "${it.className.substringAfterLast(".")}.${it.methodName}:${it.lineNumber}" }
                ?: stack.firstOrNull()?.let { "${it.className.substringAfterLast(".")}.${it.methodName}:${it.lineNumber}" }
                ?: "En espera"

            val fullTrace = stack.joinToString("\n") { "  at $it" }

            result.add(
                ThreadDiagnosticItem(
                    id = thread.id,
                    name = name,
                    state = thread.state,
                    priority = thread.priority,
                    isDaemon = thread.isDaemon,
                    isAlive = thread.isAlive,
                    category = category,
                    topStackTrace = topMethod,
                    fullStackTrace = fullTrace.ifBlank { "Sin traza de pila activa" }
                )
            )
        }

        // Ordenar: primero los hilos activos RUNNABLE, luego por categoría
        return result.sortedWith(
            compareBy<ThreadDiagnosticItem> { it.state != Thread.State.RUNNABLE }
                .thenBy { it.category.ordinal }
                .thenBy { it.name }
        )
    }

    private fun classifyThread(name: String): ThreadCategory {
        val lower = name.lowercase()
        return when {
            lower.contains("exoplayer") || lower.contains("playback") || lower.contains("audiotrack") ||
            lower.contains("audio") || lower.contains("mediasession") || lower.contains("media3") ->
                ThreadCategory.AUDIO_MEDIA3

            lower.contains("main") || lower.contains("renderthread") || lower.contains("choreographer") ||
            lower.contains("compose") || lower.contains("viewroot") ->
                ThreadCategory.UI_RENDER

            lower.contains("dispatcher") || lower.contains("coroutine") || lower.contains("okhttp") ||
            lower.contains("retrofit") || lower.contains("pool") || lower.contains("io") ->
                ThreadCategory.COROUTINE_IO

            lower.contains("native") || lower.contains("dsp") || lower.contains("jni") ||
            lower.contains("c++") || lower.contains("auramusic") ->
                ThreadCategory.C_PLUS_PLUS

            else -> ThreadCategory.SYSTEM
        }
    }
}
