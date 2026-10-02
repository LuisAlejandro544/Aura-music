package com.example.debug

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

/**
 * Gestor Central de Diagnóstico, Depuración y Monitor de Crashes para Aura Music.
 * - Captura excepciones no controladas mediante [Thread.UncaughtExceptionHandler] (CRASH).
 * - Registra anomalías en reproducción de audio, JNI C++20, almacenamiento y memoria.
 * - Persiste los registros en JSON en el almacenamiento privado de la app.
 * - Conecta con la actividad autónoma [DebugMonitorActivity] para inspección en el móvil.
 */
object AuraDebugManager {

    private const val TAG = "AuraDebugManager"
    private const val LOGS_FILE_NAME = "aura_debug_logs.json"
    private const val MAX_LOGS = 250

    private var appContext: Context? = null
    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null

    private val _logs = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val logs: StateFlow<List<DebugLogEntry>> = _logs.asStateFlow()

    private val lock = Any()

    fun init(context: Context) {
        synchronized(lock) {
            if (appContext != null) return
            appContext = context.applicationContext

            // Cargar registros persistidos previamente
            loadLogsFromDisk()

            // Instalar capturador global de cierres inesperados (Crashes)
            defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    logCrash(throwable, thread)
                } catch (e: Throwable) {
                    Log.e(TAG, "Error crítico dentro del capturador de excepciones: ${e.message}", e)
                } finally {
                    // Ceder el control al manejador estándar del sistema
                    defaultExceptionHandler?.uncaughtException(thread, throwable)
                }
            }

            logInfo("Init", "AuraDebugManager inicializado con éxito. Persistencia activa.")
        }
    }

    fun logCrash(throwable: Throwable, thread: Thread = Thread.currentThread()) {
        val entry = DebugLogEntry(
            severity = DebugSeverity.CRASH,
            tag = "UncaughtCrash",
            message = throwable.message ?: "Cierre inesperado no controlado (${throwable.javaClass.simpleName})",
            rawStackTrace = throwable.stackTraceToString(),
            deviceInfo = DeviceDiagnosticInfo.capture(appContext, thread)
        )
        addEntry(entry, immediateSync = true)
        Log.e(TAG, "CRASH DETECTADO: ${entry.message}\n${entry.rawStackTrace}")
    }

    fun logCritical(tag: String, message: String, throwable: Throwable? = null) {
        val entry = DebugLogEntry(
            severity = DebugSeverity.CRITICAL,
            tag = tag,
            message = message,
            rawStackTrace = throwable?.stackTraceToString(),
            deviceInfo = DeviceDiagnosticInfo.capture(appContext)
        )
        addEntry(entry, immediateSync = true)
        Log.e(TAG, "CRITICAL: [$tag] $message", throwable)
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val entry = DebugLogEntry(
            severity = DebugSeverity.ERROR,
            tag = tag,
            message = message,
            rawStackTrace = throwable?.stackTraceToString(),
            deviceInfo = DeviceDiagnosticInfo.capture(appContext)
        )
        addEntry(entry)
        Log.e(TAG, "ERROR: [$tag] $message", throwable)
    }

    fun logWarning(tag: String, message: String, throwable: Throwable? = null) {
        val entry = DebugLogEntry(
            severity = DebugSeverity.WARNING,
            tag = tag,
            message = message,
            rawStackTrace = throwable?.stackTraceToString(),
            deviceInfo = DeviceDiagnosticInfo.capture(appContext)
        )
        addEntry(entry)
        Log.w(TAG, "WARNING: [$tag] $message", throwable)
    }

    fun logInfo(tag: String, message: String) {
        val entry = DebugLogEntry(
            severity = DebugSeverity.INFO,
            tag = tag,
            message = message,
            rawStackTrace = null,
            deviceInfo = DeviceDiagnosticInfo.capture(appContext)
        )
        addEntry(entry)
        Log.i(TAG, "INFO: [$tag] $message")
    }

    fun clearLogs() {
        synchronized(lock) {
            _logs.value = emptyList()
            saveLogsToDisk()
        }
    }

    private fun addEntry(entry: DebugLogEntry, immediateSync: Boolean = false) {
        synchronized(lock) {
            val currentList = _logs.value.toMutableList()
            // Insertar al inicio para que los más recientes aparezcan primero
            currentList.add(0, entry)
            if (currentList.size > MAX_LOGS) {
                currentList.removeAt(currentList.lastIndex)
            }
            _logs.value = currentList
            saveLogsToDisk(immediateSync)
        }
    }

    private fun getLogFile(): File? {
        val context = appContext ?: return null
        return File(context.filesDir, LOGS_FILE_NAME)
    }

    private fun saveLogsToDisk(syncImmediately: Boolean = false) {
        try {
            val file = getLogFile() ?: return
            val jsonArray = JSONArray()
            _logs.value.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("timestamp", item.timestamp)
                    put("dateTimeFormatted", item.dateTimeFormatted)
                    put("severity", item.severity.name)
                    put("tag", item.tag)
                    put("message", item.message)
                    put("rawStackTrace", item.rawStackTrace ?: "")
                    put("device_model", item.deviceInfo.model)
                    put("device_manufacturer", item.deviceInfo.manufacturer)
                    put("device_android", item.deviceInfo.androidVersion)
                    put("device_abis", item.deviceInfo.supportedAbis)
                    put("device_ram_total", item.deviceInfo.totalRamMb)
                    put("device_ram_avail", item.deviceInfo.availableRamMb)
                    put("device_storage_avail", item.deviceInfo.availableStorageMb)
                    put("device_thread", item.deviceInfo.threadName)
                }
                jsonArray.put(obj)
            }

            FileWriter(file, false).use { writer ->
                writer.write(jsonArray.toString())
                if (syncImmediately) {
                    writer.flush()
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error al guardar logs de diagnóstico en disco: ${e.message}")
        }
    }

    private fun loadLogsFromDisk() {
        try {
            val file = getLogFile() ?: return
            if (!file.exists()) return

            val content = file.readText()
            if (content.isBlank()) return

            val jsonArray = JSONArray(content)
            val loadedList = mutableListOf<DebugLogEntry>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val severityStr = obj.optString("severity", "INFO")
                val severity = try {
                    DebugSeverity.valueOf(severityStr)
                } catch (_: Throwable) {
                    DebugSeverity.INFO
                }

                val stack = obj.optString("rawStackTrace", "").takeIf { it.isNotBlank() }

                val deviceInfo = DeviceDiagnosticInfo(
                    manufacturer = obj.optString("device_manufacturer", "Desconocido"),
                    model = obj.optString("device_model", "Desconocido"),
                    brand = "Desconocido",
                    androidVersion = obj.optString("device_android", "Desconocido"),
                    sdkInt = 0,
                    supportedAbis = obj.optString("device_abis", "No disponible"),
                    totalRamMb = obj.optLong("device_ram_total", -1L),
                    availableRamMb = obj.optLong("device_ram_avail", -1L),
                    availableStorageMb = obj.optLong("device_storage_avail", -1L),
                    threadName = obj.optString("device_thread", "main")
                )

                val entry = DebugLogEntry(
                    id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    dateTimeFormatted = obj.optString("dateTimeFormatted", ""),
                    severity = severity,
                    tag = obj.optString("tag", "General"),
                    message = obj.optString("message", ""),
                    rawStackTrace = stack,
                    deviceInfo = deviceInfo
                )
                loadedList.add(entry)
            }
            _logs.value = loadedList
        } catch (e: Throwable) {
            Log.e(TAG, "Error al cargar logs de diagnóstico previos: ${e.message}")
        }
    }

    fun generateFullReportText(context: Context?): String {
        val sb = StringBuilder()
        val device = DeviceDiagnosticInfo.capture(context ?: appContext)
        sb.appendLine("==================================================")
        sb.appendLine("🛠️ REPORTE DE DIAGNÓSTICO Y ERRORES - AURA MUSIC")
        sb.appendLine("==================================================")
        sb.appendLine("Generado: ${DebugLogEntry.formatTimestamp(System.currentTimeMillis())}")
        sb.appendLine("Dispositivo: ${device.manufacturer} ${device.model}")
        sb.appendLine("Versión OS: ${device.androidVersion}")
        sb.appendLine("Arquitectura CPU: ${device.supportedAbis}")
        sb.appendLine("Memoria RAM: ${device.availableRamMb} MB libres de ${device.totalRamMb} MB")
        sb.appendLine("Almacenamiento: ${device.availableStorageMb} MB disponibles")
        sb.appendLine("Total de Eventos Registrados: ${_logs.value.size}")
        sb.appendLine("==================================================")
        sb.appendLine()

        if (_logs.value.isEmpty()) {
            sb.appendLine("No se han registrado errores ni incidentes. El sistema está 100% estable.")
            return sb.toString()
        }

        _logs.value.forEachIndexed { index, entry ->
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("#${index + 1} [${entry.severity.label}] ${entry.dateTimeFormatted} | Tag: ${entry.tag}")
            sb.appendLine("Hilo: ${entry.deviceInfo.threadName}")
            sb.appendLine("Mensaje: ${entry.message}")
            if (!entry.rawStackTrace.isNullOrBlank()) {
                sb.appendLine("--- STACK TRACE EN CRUDO ---")
                sb.appendLine(entry.rawStackTrace)
            }
            sb.appendLine()
        }

        sb.appendLine("================ FIN DEL REPORTE ================")
        return sb.toString()
    }
}
