package com.example.core.data.streaming

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.core.model.StreamingVideoNetworkPolicy

/**
 * Puerta de red para Video Canvas: Wi-Fi / siempre / desactivado.
 * Rol arquitectónico: pieza de StreamingCacheManager (fachada pública intacta).
 */
internal class StreamingNetworkPolicy(private val s: StreamingCacheState) {
    fun isStreamingVideoAllowedByNetwork(): Boolean {
        return when (s.config.value.videoNetworkPolicy) {
            StreamingVideoNetworkPolicy.DISABLED -> false
            StreamingVideoNetworkPolicy.ALWAYS -> isNetworkAvailable()
            StreamingVideoNetworkPolicy.WIFI_ONLY -> isWifiConnected()
        }
    }

    fun isWifiConnected(): Boolean {
        return try {
            val cm = s.context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (_: Exception) {
            false
        }
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = s.context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }
}
