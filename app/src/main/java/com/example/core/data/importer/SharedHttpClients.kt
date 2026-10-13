package com.example.core.data.importer

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import java.util.concurrent.TimeUnit

/**
 * Pool de conexiones y dispatcher compartidos por los clientes HTTP de importación.
 *
 * Rol arquitectónico: evita que cada motor (LRCLIB, InnerTube, Invidious, Chunked)
 * mantenga su propio pool de conexiones y sus hilos de dispatcher.
 */
internal object SharedHttpClients {
    val pool: ConnectionPool = ConnectionPool(6, 5L, TimeUnit.MINUTES)
    val dispatcher: Dispatcher = Dispatcher().apply {
        maxRequests = 32
        maxRequestsPerHost = 8
    }
}
