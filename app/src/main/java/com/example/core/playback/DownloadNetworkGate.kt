package com.example.core.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.delay

/**
 * Puerta de red para descargas 1-en-1 (Fase B).
 *
 * Rol arquitectónico: pausa cooperativa. Cuando se pierde la conectividad a mitad
 * de un lote, el orquestador y el servicio SUSPENDEN la espera en lugar de marcar
 * las pistas como fallidas (`awaitOnline` retorna solo con red disponible).
 *
 * Implementación por sondeo cada 2s (sin `NetworkCallback` permanente) para no
 * filtrar contextos ni mantener el proceso vivo innecesariamente.
 */
object DownloadNetworkGate {

    private const val TAG = "DownloadNetworkGate"
    private const val POLL_MS = 2_000L

    /** true si hay una red con capacidad de INTERNET validada por el sistema. */
    fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Suspende hasta que haya red.
     *
     * @param isActive chequeo de cancelación (ej. lote cancelado por el usuario).
     * @param onWaiting callback con `true` al entrar en pausa y `false` al recuperar la red,
     *   para que la UI muestre "Sin conexión — en pausa" (hilo de llamada: IO).
     */
    suspend fun awaitOnline(
        context: Context,
        isActive: () -> Boolean = { true },
        onWaiting: (Boolean) -> Unit = {}
    ) {
        if (isOnline(context)) return
        AuraDebugManager.logWarning(TAG, "Sin red: pausando lote hasta recuperar conectividad.")
        onWaiting(true)
        try {
            while (isActive() && !isOnline(context)) {
                delay(POLL_MS)
            }
        } finally {
            onWaiting(false)
        }
    }
}
