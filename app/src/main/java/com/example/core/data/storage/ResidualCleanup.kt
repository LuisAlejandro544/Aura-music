package com.example.core.data.storage

import android.content.Context
import java.io.File

/**
 * Purga de residuos temporales, APKs viejos y duplicados nativos.
 * Rol arquitectónico: pieza de AppStorageManager (fachada pública intacta).
 */
    internal object ResidualCleanup {
        /**
         * Purga automáticamente al iniciar la aplicación cualquier residuo de archivos temporales,
         * APKs de actualización antiguos ya instalados, copias duplicadas de ejecutables o carpetas
         * legadas en `filesDir` y `getExternalFilesDir` para garantizar cero residuos de peso.
         */
        fun cleanupResidualFiles(context: Context) {
            try {
                val baseExternal = context.getExternalFilesDir(null) ?: context.filesDir
                val internalFiles = context.filesDir
                val cacheDir = context.cacheDir

                // 1. Limpiar APKs de actualización descargados previamente en updates/
                listOf(File(baseExternal, "updates"), File(internalFiles, "updates"), File(cacheDir, "updates")).forEach { updatesDir ->
                    if (updatesDir.exists() && updatesDir.isDirectory) {
                        updatesDir.listFiles()?.forEach { file ->
                            try { file.deleteRecursively() } catch (_: Exception) {}
                        }
                    }
                }

                // 2. Eliminar carpeta duplicada antigua env/ffmpeg (~90 MB liberados si existía de versiones previas)
                val legacyFfmpegEnv = File(internalFiles, "env/ffmpeg")
                if (legacyFfmpegEnv.exists()) {
                    try { legacyFfmpegEnv.deleteRecursively() } catch (_: Exception) {}
                }

                // 3. Eliminar archivos sueltos duplicados de stdlib/ en env/python si stdlib.zip ya está presente
                val pythonEnv = File(internalFiles, "env/python")
                val stdlibZip = File(pythonEnv, "stdlib.zip")
                val stdlibExpandedDir = File(pythonEnv, "stdlib")
                if (stdlibZip.exists() && stdlibExpandedDir.exists() && stdlibExpandedDir.isDirectory) {
                    stdlibExpandedDir.listFiles()?.forEach { f ->
                        if (f.name != "site.py") {
                            try { f.deleteRecursively() } catch (_: Exception) {}
                        }
                    }
                }

                // 4. Eliminar módulos .so duplicados con sufijo .cpython-311.so o .chaquopy.so si ya existe su versión normalizada
                val pyModulesDir = File(pythonEnv, "modules")
                if (pyModulesDir.exists() && pyModulesDir.isDirectory) {
                    pyModulesDir.listFiles()?.forEach { f ->
                        if (f.name.contains(".cpython-") || f.name.contains(".chaquopy.so")) {
                            val normalizedName = f.name
                                .replace(".chaquopy.so", ".so")
                                .replace(Regex("\\.cpython-\\d+.*\\.so$"), ".so")
                            if (normalizedName != f.name && File(pyModulesDir, normalizedName).exists()) {
                                try { f.delete() } catch (_: Exception) {}
                            }
                        }
                    }
                }

                // 5. Eliminar copias duplicadas de python / python3 y archivos .tmp/.bak en files/bin
                val binDir = File(internalFiles, "bin")
                if (binDir.exists() && binDir.isDirectory) {
                    binDir.listFiles()?.forEach { f ->
                        if (f.name == "python" || f.name == "python3" || f.name.endsWith(".tmp") || f.name.endsWith(".bak")) {
                            try { f.delete() } catch (_: Exception) {}
                        }
                    }
                }

                // 6. Eliminar archivos temporales huérfanos (.tmp, .part) en videos/, songs/, images/ y cacheDir
                listOf(
                    File(baseExternal, "videos"),
                    File(baseExternal, "songs"),
                    File(baseExternal, "images"),
                    cacheDir
                ).forEach { dir ->
                    if (dir.exists() && dir.isDirectory) {
                        dir.listFiles()?.forEach { f ->
                            if (f.isFile && (f.name.endsWith(".tmp") || f.name.endsWith(".part") || f.name.startsWith("mixtape_norm_") || f.name.startsWith("canvas_raw_"))) {
                                try { f.delete() } catch (_: Exception) {}
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
}
