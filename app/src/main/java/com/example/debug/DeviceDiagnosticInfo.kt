package com.example.debug

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs

/**
 * Ficha técnica y estado del teléfono móvil en el momento en que se captura un evento o error.
 * Facilita diagnosticar incidencias reportadas por usuarios en dispositivos reales (Android 8.0+).
 */
data class DeviceDiagnosticInfo(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val androidVersion: String,
    val sdkInt: Int,
    val supportedAbis: String,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val availableStorageMb: Long,
    val threadName: String
) {
    companion object {
        fun capture(context: Context?, thread: Thread = Thread.currentThread()): DeviceDiagnosticInfo {
            var totalRam = -1L
            var availRam = -1L
            var availStorage = -1L

            try {
                if (context != null) {
                    val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    val memInfo = ActivityManager.MemoryInfo()
                    actManager?.getMemoryInfo(memInfo)
                    totalRam = memInfo.totalMem / (1024 * 1024)
                    availRam = memInfo.availMem / (1024 * 1024)

                    val stat = StatFs(context.filesDir.absolutePath)
                    availStorage = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
                }
            } catch (_: Throwable) {}

            return DeviceDiagnosticInfo(
                manufacturer = Build.MANUFACTURER ?: "Desconocido",
                model = Build.MODEL ?: "Desconocido",
                brand = Build.BRAND ?: "Desconocido",
                androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                sdkInt = Build.VERSION.SDK_INT,
                supportedAbis = Build.SUPPORTED_ABIS?.joinToString(", ") ?: "No disponible",
                totalRamMb = totalRam,
                availableRamMb = availRam,
                availableStorageMb = availStorage,
                threadName = thread.name
            )
        }
    }
}
