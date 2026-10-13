package com.example.core.data.importer.mixtape

import android.content.Context
import com.example.core.data.storage.AppStorageManager
import com.example.debug.AuraDebugManager
import com.example.core.model.MixtapeChapter
import com.example.core.model.MixtapeMetadata
import org.json.JSONObject
import java.io.File

/**
 * Persistencia JSON de capítulos en metadata/.
 * Rol arquitectónico: pieza de MixtapeEngine (fachada pública intacta).
 */
internal object MixtapeMetadataStore {
    private const val TAG = "MixtapeEngine"

    fun saveMetadata(storageManager: AppStorageManager, metadata: MixtapeMetadata) {
        try {
            val file = File(storageManager.metadataDir, "mixtape_${metadata.mixtapeId}.json")
            file.writeText(metadata.toJsonObject().toString(2))
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Error guardando metadatos JSON del mixtape: ${e.message}")
        }
    }

    fun readMetadata(context: Context, trackId: Long): MixtapeMetadata? {
        val storageManager = AppStorageManager(context)
        val file = File(storageManager.metadataDir, "mixtape_${trackId}.json")
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            MixtapeMetadata.fromJsonObject(json)
        } catch (e: Exception) {
            null
        }
    }
}
