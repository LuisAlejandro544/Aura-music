package com.example.core.data.importer

import android.content.Context
import java.io.File
import org.json.JSONObject

/**
 * Persistencia de límites startMs/endMs por URI en filesDir.
 * Rol arquitectónico: submódulo de AudioSilenceTrimmer (fachada pública intacta).
 */
internal object ClippingBoundsStore {    @Synchronized
    fun save(context: Context, uriString: String, startMs: Long, endMs: Long) {
        try {
            val file = File(context.filesDir, SilenceTrimConfig.OFFSETS_FILE_NAME)
            val root = if (file.exists()) {
                JSONObject(file.readText())
            } else {
                JSONObject()
            }
            val entry = JSONObject().apply {
                put("startMs", startMs)
                put("endMs", endMs)
            }
            root.put(uriString, entry)
            file.writeText(root.toString())
        } catch (_: Throwable) {}
    }

    /**
     * Obtiene los límites de recorte de silencio registrados para una URI, o `null` si no aplica.
     */
    @Synchronized
    fun get(context: Context, uriString: String): AudioSilenceTrimmer.ClippingBounds? {
        return try {
            val file = File(context.filesDir, SilenceTrimConfig.OFFSETS_FILE_NAME)
            if (!file.exists()) return null
            val root = JSONObject(file.readText())
            if (!root.has(uriString)) return null
            val obj = root.getJSONObject(uriString)
            val startMs = obj.optLong("startMs", 0L)
            val endMs = obj.optLong("endMs", 0L)
            if (endMs > startMs) AudioSilenceTrimmer.ClippingBounds(startMs, endMs) else null
        } catch (_: Throwable) {
            null
        }
    }

    @Synchronized
    fun remove(context: Context, uriString: String) {
        try {
            val file = File(context.filesDir, SilenceTrimConfig.OFFSETS_FILE_NAME)
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            if (root.has(uriString)) {
                root.remove(uriString)
                file.writeText(root.toString())
            }
        } catch (_: Throwable) {}
    }
}
