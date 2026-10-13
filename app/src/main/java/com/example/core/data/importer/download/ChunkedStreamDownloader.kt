package com.example.core.data.importer.download

import android.net.Uri
import com.example.debug.AuraDebugManager
import com.example.core.model.DownloadProgress
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Aura Music - Acelerador y Descargador de Flujos Multimedia por Rangos HTTP
 *
 * Responsabilidades:
 * - Aceleración mediante fragmentación por bloques HTTP Range (Chunked Range Download),
 *   eliminando por completo la limitación artificial de ~63 KB/s de YouTube.
 * - Descarga lineal continua de respaldo con reintentos limpios en HTTP 403.
 * - Sonda de tamaño total y emisión fluida de métricas de progreso (bytes/s, porcentaje, fase).
 * - Protección anti-SSRF / anti-DNS Rebinding y validación estricta de dominios para cookies.
 */
object ChunkedStreamDownloader {

    private val ssrfGuardInterceptor = Interceptor { chain ->
        val request = chain.request()
        val urlHost = request.url.host.lowercase()
        if (ChunkedUrlGuards.isDisallowedHost(urlHost)) {
            throw IOException("Bloqueado acceso de red hacia host local o privado no autorizado: $urlHost")
        }
        val resolvedAddresses = try {
            InetAddress.getAllByName(urlHost)
        } catch (_: Exception) {
            emptyArray()
        }
        for (addr in resolvedAddresses) {
            if (addr.isLoopbackAddress ||
                addr.isSiteLocalAddress ||
                addr.isLinkLocalAddress ||
                addr.isAnyLocalAddress ||
                addr.isMulticastAddress
            ) {
                throw IOException("Bloqueado intento de conexión SSRF / DNS Rebinding hacia IP interna: ${addr.hostAddress}")
            }
        }
        chain.proceed(request)
    }

    val defaultHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .connectionPool(com.example.core.data.importer.SharedHttpClients.pool)
        .dispatcher(com.example.core.data.importer.SharedHttpClients.dispatcher)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(ssrfGuardInterceptor)
        .addNetworkInterceptor(ssrfGuardInterceptor)
        .build()

    fun isSafePublicUrl(rawUrl: String): Boolean =
        ChunkedUrlGuards.isSafePublicUrl(rawUrl)

    fun downloadUrlToFile(
        url: String,
        targetFile: File,
        phase: String,
        customHeaders: Map<String, String> = emptyMap(),
        httpClient: OkHttpClient = defaultHttpClient,
        onProgress: (DownloadProgress) -> Unit = {}
    ): Boolean = ChunkedDownloadRouter.downloadUrlToFile(
        url, targetFile, phase, customHeaders, httpClient, onProgress
    )
}
