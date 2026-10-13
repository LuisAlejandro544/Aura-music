package com.example.core.data.importer

import android.content.Context
import android.util.Base64
import com.example.debug.AuraDebugManager
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.zip.ZipFile
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Aura Music - Aprovisionador y Configuración del Entorno Nativo de Python y yt-dlp (< 400 líneas)
 *
 * Responsabilidades:
 * - Detección e inicialización de runtime CPython ('libpython.so') en [nativeLibraryDir].
 * - Vinculación de módulos C nativos ('libpymod_*.so') mediante symlinks de 0 bytes.
 * - Consolidación de certificados CA X.509 de Android 14+ (Conscrypt APEX / KeyStore) en 'cert.pem'.
 * - Detección de ejecutables de QuickJS ('libqjs.so') y FFmpeg.
 * - Normalización de URLs de YouTube Music y construcción de extractor-args.
 */
object YtDlpEnvironmentSetup {

    private const val TAG = "YtDlpEnvSetup"

    @Volatile
    private var isInitialized = false

    /**
     * Inicializa el entorno nativo de Python y el script de yt-dlp si aún no están listos.
     */
    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return

        try {
            val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
            val pythonZip = File(nativeLibDir, "libpython.zip.so")
            val pythonEnvDir = File(context.filesDir, "env/python")
            val versionMarker = File(pythonEnvDir, ".version")

            val pymodFiles = nativeLibDir.listFiles()?.filter {
                it.isFile && it.name.startsWith("libpymod_") && it.name.endsWith(".so")
            }.orEmpty()

            val currentSignature = buildString {
                append("direct_symlink_v4_")
                if (pythonZip.exists()) append("${pythonZip.length()}_${pythonZip.lastModified()}_")
                append("mods:${pymodFiles.size}")
            }

            if (pythonZip.exists() || pymodFiles.isNotEmpty()) {
                val needSetup = !pythonEnvDir.exists() ||
                        !versionMarker.exists() ||
                        versionMarker.readText().trim() != currentSignature

                if (needSetup) {
                    AuraDebugManager.logInfo(TAG, "Vinculando módulos C nativos de Python desde nativeLibraryDir...")
                    if (pythonEnvDir.exists()) {
                        pythonEnvDir.deleteRecursively()
                    }
                    pythonEnvDir.mkdirs()

                    linkNativeModulesFromLibDir(nativeLibDir, pythonZip, pythonEnvDir)
                    versionMarker.writeText(currentSignature)
                    AuraDebugManager.logInfo(TAG, "Módulos C de Python vinculados exitosamente.")
                } else {
                    File(pythonEnvDir, "stdlib.zip").takeIf { it.exists() }?.delete()
                    File(pythonEnvDir, "stdlib").takeIf { it.exists() }?.deleteRecursively()
                }
            }

            val nativeYtdlpSo = File(nativeLibDir, "libytdlp.zip.so").takeIf { it.exists() }
                ?: File(nativeLibDir, "libytdlp.so")
            val otaYtdlpFile = File(context.filesDir, "bin/yt-dlp")
            val versionFile = File(context.filesDir, "bin/ytdlp_version.txt")

            if (nativeYtdlpSo.exists() && nativeYtdlpSo.length() > YTDLP_MIN_PACKAGE_BYTES) {
                val recordedVer = if (versionFile.exists()) runCatching { versionFile.readText().trim() }.getOrDefault("") else ""
                if (otaYtdlpFile.exists() && (recordedVer.isEmpty() || recordedVer.contains("base", ignoreCase = true))) {
                    runCatching { otaYtdlpFile.delete() }
                }
                if (!versionFile.exists()) {
                    versionFile.parentFile?.mkdirs()
                    versionFile.writeText("v.base (nativo)")
                }
            } else if (!otaYtdlpFile.exists() || otaYtdlpFile.length() < 1000L) {
                try {
                    context.assets.open("bin/yt-dlp").use { input ->
                        otaYtdlpFile.parentFile?.mkdirs()
                        FileOutputStream(otaYtdlpFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    otaYtdlpFile.setExecutable(true, false)
                    versionFile.writeText("v.base (empaquetado)")
                } catch (_: Exception) {}
            }

            val binDir = File(context.filesDir, "bin")
            if (binDir.exists()) {
                listOf("python3", "python").forEach { aliasName ->
                    val aliasFile = File(binDir, aliasName)
                    if (aliasFile.exists()) {
                        runCatching { aliasFile.delete() }
                    }
                }
            }

            ensureTlsCertificates(pythonEnvDir)
            isInitialized = true
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Fallo al inicializar entorno Python yt-dlp: ${e.message}")
        }
    }

    /**
     * Localiza el binario ejecutable de Python disponible para el proceso de la app.
     * Solo nativeLibraryDir es camino vivo: init() elimina los alias obsoletos de files/bin.
     */
    fun getPythonExecutable(context: Context): File? {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val pythonInNative = File(nativeDir, "libpython.so")
        if (pythonInNative.exists() && pythonInNative.length() > 1000L && pythonInNative.canExecute()) {
            return pythonInNative
        }

        val python3InNative = File(nativeDir, "libpython3.so")
        if (python3InNative.exists() && python3InNative.length() > 1000L && python3InNative.canExecute()) {
            return python3InNative
        }

        return null
    }

    /**
     * Localiza el binario ejecutable de QuickJS C99 real (Bellard ECMAScript Engine > 200 KB)
     * para resolución de firmas JS (n-sig / cipher) en yt-dlp.
     */
    fun getQuickJsExecutable(context: Context): File? {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val qjsInNative = File(nativeDir, "libqjs.so")
        if (qjsInNative.exists() && qjsInNative.length() > 200_000L && qjsInNative.canExecute()) {
            return qjsInNative
        }
        val binDir = File(context.filesDir, "bin")
        val qjsInBin = File(binDir, "qjs")
        if (qjsInBin.exists() && qjsInBin.length() > 200_000L && qjsInBin.canExecute()) {
            return qjsInBin
        }
        return null
    }

    /**
     * Consolida los certificados TLS/CA de Android 14+ y KeyStore hacia 'usr/etc/tls/cert.pem'.
     */
    fun ensureTlsCertificates(pythonEnvDir: File, forceRebuild: Boolean = false) {
        val certFile = File(pythonEnvDir, "usr/etc/tls/cert.pem")
        if (!forceRebuild && certFile.exists() && certFile.length() > 5000L) {
            return
        }

        try {
            certFile.parentFile?.mkdirs()
            val pemBuilder = StringBuilder()

            try {
                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                tmf.init(null as KeyStore?)
                val issuers = tmf.trustManagers
                    .filterIsInstance<X509TrustManager>()
                    .flatMap { it.acceptedIssuers.toList() }

                for (cert in issuers) {
                    val encoded = Base64.encodeToString(cert.encoded, Base64.DEFAULT)
                    pemBuilder.append("-----BEGIN CERTIFICATE-----\n")
                    pemBuilder.append(encoded)
                    if (!encoded.endsWith("\n")) pemBuilder.append("\n")
                    pemBuilder.append("-----END CERTIFICATE-----\n\n")
                }
            } catch (e: Exception) {
                AuraDebugManager.logWarning(TAG, "Aviso leyendo certificados de TrustManagerFactory: ${e.message}")
            }

            val certDirs = listOf(
                File("/apex/com.android.conscrypt/cacerts"),
                File("/system/etc/security/cacerts")
            )
            for (dir in certDirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.take(250)?.forEach { cf ->
                        if (cf.isFile && cf.length() in 300..50000) {
                            try {
                                val text = cf.readText()
                                if (text.contains("-----BEGIN CERTIFICATE-----")) {
                                    val startIdx = text.indexOf("-----BEGIN CERTIFICATE-----")
                                    val endIdx = text.indexOf("-----END CERTIFICATE-----")
                                    if (startIdx != -1 && endIdx != -1) {
                                        pemBuilder.append(text.substring(startIdx, endIdx + 25)).append("\n\n")
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            if (pemBuilder.length > 500) {
                certFile.writeText(pemBuilder.toString())
                AuraDebugManager.logInfo(TAG, "Bundle TLS consolidado generado en ${certFile.absolutePath}")
            }
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Fallo al generar bundle TLS: ${e.message}")
        }
    }

    /**
     * Vincula los módulos C nativos de Python desde [nativeLibraryDir] ('libpymod_<nombre>.so')
     * hacia 'files/env/python/modules/<nombre>.so' usando symlinks de 0 bytes.
     */
    private fun linkNativeModulesFromLibDir(nativeLibDir: File, zipFile: File, destDir: File) {
        val canonicalDestDir = destDir.canonicalFile
        val modulesDir = File(destDir, "modules").apply { mkdirs() }
        val canonicalModulesDir = modulesDir.canonicalFile

        val pymodFiles = nativeLibDir.listFiles()?.filter {
            it.isFile && it.name.startsWith("libpymod_") && it.name.endsWith(".so")
        }.orEmpty()

        if (pymodFiles.isNotEmpty()) {
            for (srcSo in pymodFiles) {
                val originalModuleName = srcSo.name.removePrefix("libpymod_")
                if (originalModuleName.isBlank() || originalModuleName.contains("/") || originalModuleName.contains("..")) continue
                val targetLink = File(modulesDir, originalModuleName)
                val canonicalTarget = targetLink.canonicalFile
                if (!canonicalTarget.toPath().startsWith(canonicalModulesDir.toPath())) {
                    throw SecurityException("Violación Path Traversal al vincular módulo Python: '$originalModuleName'")
                }
                if (targetLink.exists()) targetLink.delete()
                try {
                    android.system.Os.symlink(srcSo.absolutePath, targetLink.absolutePath)
                } catch (_: Throwable) {
                    srcSo.copyTo(targetLink, overwrite = true)
                    targetLink.setExecutable(true, false)
                }
            }
            return
        }

        if (!zipFile.exists()) return
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = entry.name.trimStart('/')
                val isNativeModule = name.startsWith("modules/") && name.endsWith(".so")
                if (!isNativeModule) continue

                val entryFile = File(destDir, name)
                val canonicalEntryFile = entryFile.canonicalFile
                if (!canonicalEntryFile.toPath().startsWith(canonicalDestDir.toPath())) {
                    throw SecurityException("Violación Zip Slip: '${entry.name}'")
                }

                entryFile.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(entryFile).use { output ->
                        input.copyTo(output)
                    }
                }
                entryFile.setExecutable(true, false)
            }
        }
    }

    /**
     * Normaliza cualquier URL de YouTube Music a formato YouTube canónico para yt-dlp.
     */
    fun normalizeYouTubeMusicUrlForYtDlp(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (!trimmed.contains("music.youtube.com", ignoreCase = true)) {
            return trimmed
        }
        val videoIdRegex = Regex("(?:v=|youtu\\.be/|shorts/)([a-zA-Z0-9_-]{11})")
        val match = videoIdRegex.find(trimmed)
        if (match != null) {
            val videoId = match.groupValues[1]
            return "https://www.youtube.com/watch?v=$videoId"
        }
        return trimmed.replace(Regex("https?://(www\\.)?music\\.youtube\\.com", RegexOption.IGNORE_CASE), "https://www.youtube.com")
    }

    /**
     * Construye argumentos de extracción para YouTube (estrategia y PO-Token).
     */
    suspend fun buildYouTubeExtractorArgs(context: Context): String {
        val strategy = YtDlpCookieManager.getPlayerClientStrategy(context)
        val poTokenPair: Pair<String, String>? = PoTokenGenerator.getOrGeneratePoToken(context)
        val builder = StringBuilder("youtube:player_client=$strategy")
        if (poTokenPair != null) {
            val (poToken, visitorData) = poTokenPair
            if (poToken.isNotBlank()) {
                builder.append(";po_token=web+$poToken")
            }
            if (visitorData.isNotBlank()) {
                builder.append(";visitor_data=$visitorData")
            }
        }
        return builder.toString()
    }
}
