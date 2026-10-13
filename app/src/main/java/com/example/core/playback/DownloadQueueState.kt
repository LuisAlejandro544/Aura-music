package com.example.core.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Estado reactivo global de la cola FIFO de descargas (Fase B).
 *
 * Rol arquitectónico: semáforo visible de 1 permiso. `AuraDownloadService` procesa
 * de a 1 tarea (el paralelismo vive dentro de cada archivo con bloques HTTP Range);
 * este objeto expone cuántas tareas esperan turno para que la notificación y la UI
 * muestren la posición (`3/25 en cola`) sin acoplar Compose al Service.
 *
 * Hilo: todos los contadores son atómicos; los flujos se actualizan desde
 * `Dispatchers.IO` (nunca bloquean el hilo principal).
 */
object DownloadQueueState {

    private val queuedCount = AtomicInteger(0)

    private val _queuedCount = MutableStateFlow(0)
    /** Tareas esperando turno (sin contar la activa). */
    val queuedCountFlow: StateFlow<Int> = _queuedCount.asStateFlow()

    private val _activeTitle = MutableStateFlow<String?>(null)
    /** Título de la pista que se está descargando ahora mismo (null si la cola está vacía). */
    val activeTitle: StateFlow<String?> = _activeTitle.asStateFlow()

    /** Texto corto de posición para notificación y tarjetas (`"En cola: 3"` o null si no hay espera). */
    fun queueLabel(): String? {
        val n = queuedCount.get()
        return if (n > 0) "En cola: $n" else null
    }

    fun onEnqueued() {
        _queuedCount.value = queuedCount.incrementAndGet()
    }

    fun onDequeued() {
        val n = queuedCount.decrementAndGet().coerceAtLeast(0)
        queuedCount.set(n)
        _queuedCount.value = n
    }

    fun setActiveTitle(title: String?) {
        _activeTitle.value = title
    }

    fun reset() {
        queuedCount.set(0)
        _queuedCount.value = 0
        _activeTitle.value = null
    }
}
