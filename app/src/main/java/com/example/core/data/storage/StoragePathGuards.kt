package com.example.core.data.storage

import java.io.File

/**
 * Resolución segura anti-Zip-Slip y validación canónica de rutas.
 * Rol arquitectónico: pieza de AppStorageManager (fachada pública intacta).
 */
internal object StoragePathGuards {
    fun resolveChild(parentDir: File, rawFileName: String): File {
        val cleanName = rawFileName
            .replace("/", "_")
            .replace("\\", "_")
            .replace("..", "_")
            .trim()
            .ifBlank { "file_${System.currentTimeMillis()}" }

        val targetFile = File(parentDir, cleanName)
        val canonicalParent = parentDir.canonicalFile.toPath()
        val canonicalTarget = targetFile.canonicalFile.toPath()
        if (!canonicalTarget.startsWith(canonicalParent)) {
            throw SecurityException("Intento de Path Traversal bloqueado: '$rawFileName' escapa de '${parentDir.name}'")
        }
        return targetFile
    }

    fun isInside(file: File, allowedDir: File): Boolean {
        return try {
            file.canonicalFile.toPath().startsWith(allowedDir.canonicalFile.toPath())
        } catch (_: Exception) {
            false
        }
    }
}
