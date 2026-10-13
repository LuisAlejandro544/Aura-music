package com.example.core.data.importer

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP compartido para LRCLIB con User-Agent de Aura Music.
 * Rol arquitectónico: submódulo de LyricsManager (fachada pública intacta).
 */
internal object LyricsHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    const val APP_USER_AGENT: String = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 (AuraMusic/1.0)"
}
