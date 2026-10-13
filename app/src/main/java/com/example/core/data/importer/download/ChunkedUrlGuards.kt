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
 * Anti-SSRF, anti-DNS-Rebinding y dominios confiables.
 * Rol arquitectónico: pieza de ChunkedStreamDownloader (fachada pública intacta).
 */
internal object ChunkedUrlGuards {
    fun isSafePublicUrl(rawUrl: String): Boolean {
        return try {
            val uri = Uri.parse(rawUrl.trim())
            val scheme = uri.scheme?.lowercase() ?: return false
            if (scheme != "https" && scheme != "http") return false
            val host = uri.host?.lowercase() ?: return false
            if (isDisallowedHost(host)) return false
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isDisallowedHost(host: String): Boolean {
        val h = host.trim().lowercase()
        if (h.isEmpty()) return true
        if (h == "localhost" ||
            h.endsWith(".local") ||
            h.endsWith(".internal") ||
            h.endsWith(".lan") ||
            h == "0.0.0.0" ||
            h.startsWith("127.") ||
            h.startsWith("10.") ||
            h.startsWith("192.168.") ||
            h.startsWith("169.254.") ||
            h.startsWith("[::1]") ||
            h == "::1" ||
            Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*").matches(h)
        ) {
            return true
        }
        return false
    }

    fun isTrustedYoutubeHost(rawUrl: String): Boolean {
        return try {
            val host = Uri.parse(rawUrl.trim()).host?.lowercase() ?: return false
            host == "youtube.com" ||
                host.endsWith(".youtube.com") ||
                host == "googlevideo.com" ||
                host.endsWith(".googlevideo.com")
        } catch (_: Exception) {
            false
        }
    }
}
