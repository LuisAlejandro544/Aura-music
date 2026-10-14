package com.example.core.data.importer

import android.content.Context
import android.util.Base64
import com.example.debug.AuraDebugManager
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Aura Music - Aprovisionador del Entorno Nativo de Python y yt-dlp (< 500 líneas).
 * - Runtime CPython ('libpython.so'), symlinks 'libpymod_*.so' y bundle TLS CA.
 * - QuickJS C99 solo como CLI externo ('libqjs.so' -> 'files/bin/qjs', chmod 0700)
 *   vía `--js-runtimes` de yt-dlp (nunca JNI). Normalización de URLs YouTube Music.
 */
object YtDlpEnvironmentSetup {
    private const val TAG = "YtDlpEnvSetup"

    /** Nombres del .so PIE instalado y del binario normalizado en files/bin. */
    private const val QUICKJS_SO_NAME = "libqjs.so"
    private const val QUICKJS_BIN_NAME = "qjs"

    /** Pistas de stderr de yt-dlp que indican fallo por falta de motor JS. */
    private val JS_FAILURE_HINTS = listOf("no js runtime", "js-runtimes", "nsig", "n-sig", "throttling", "decrypt_signature", "signature decipher", "cipher", "base.js", "po token", "botguard", "sign in to confirm")

    @Volatile
    private var isInitialized = false

    @Volatile
    private var cachedQuickJs: File? = null

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
                append("direct_symlink_v5_")
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
                // Limpieza de copias legadas que provocaban error=13 en Beta (W^X).
                // No deben recrearse: el intérprete se ejecuta desde nativeLibraryDir.
                listOf("python3", "python", "python.tmp", "qjs", "qjs.tmp").forEach { aliasName ->
                    val aliasFile = File(binDir, aliasName)
                    if (aliasFile.exists()) {
                        runCatching { aliasFile.delete() }
                    }
                }
            }

            ensureTlsCertificates(pythonEnvDir)
            // Pre-resuelve rutas nativas (sin copiar a files/bin) para fallar rápido
            // en el primer uso y permitir fallback a InnerTube si faltan motores.
            runCatching { getQuickJsExecutable(context) }
            runCatching { resolvePythonExecutable(context) }
            isInitialized = true
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Fallo al inicializar entorno Python yt-dlp: ${e.message}")
        }
    }

    /**
     * Localiza el binario ejecutable de Python disponible para el proceso de la app.
     * Vía única [resolvePythonExecutable]: `nativeLibraryDir/libpython.so` directo
     * (única vía con exec permitido en API 29+). Nunca se copia a files/bin.
     */
    fun getPythonExecutable(context: Context): File? = resolvePythonExecutable(context)

    /**
     * Localiza el binario ejecutable de QuickJS C99 real (Bellard ECMAScript Engine).
     *
     * Vía soportada en API 29+ (W^X): ejecutar directamente
     * `nativeLibraryDir/libqjs.so` (apk_data_file, con permiso exec y nombre .so
     * conservado). Las copias en `files/bin/qjs` se deniegan con error=13 en Beta,
     * igual que ocurría con `files/bin/python`.
     *
     * Umbral unificado: [QUICKJS_MIN_PACKAGE_BYTES] (250 KB). Un stub menor nunca se
     * considera válido, evitando el estado falso "activo" con binarios rotos.
     * Uso exclusivo como CLI vía `--js-runtimes quickjs:<rutaAbsoluta>`, nunca JNI.
     */
    fun getQuickJsExecutable(context: Context): File? {
        // FIX: la caché solo vale si sigue ejecutable.
        cachedQuickJs?.takeIf { isValidQuickJsFile(it) && it.canExecute() }?.let { return it }

        // 1. Nativo directo (preferido, funciona en Debug y Beta).
        val nativeQjs = File(context.applicationInfo.nativeLibraryDir, QUICKJS_SO_NAME)
        if (isValidQuickJsFile(nativeQjs) && nativeQjs.canExecute()) {
            cachedQuickJs = nativeQjs
            // Limpieza de copia legada que provocaba EACCES en Beta.
            runCatching {
                File(File(context.filesDir, "bin"), QUICKJS_BIN_NAME)
                    .takeIf { it.exists() && it.absolutePath != nativeQjs.absolutePath }?.delete()
            }
            return nativeQjs
        }

        // 2. Compatibilidad: copia legada solo si ya es ejecutable en este dispositivo.
        val binQjs = File(File(context.filesDir, "bin"), QUICKJS_BIN_NAME)
        if (isValidQuickJsFile(binQjs) && binQjs.canExecute()) {
            cachedQuickJs = binQjs
            return binQjs
        }
        return null
    }

    /**
     * Normalización legada `nativeLibraryDir/libqjs.so` -> `files/bin/qjs`.
     * @deprecated SELinux W^X deniega exec en filesDir desde API 29 (error=13).
     * Se conserva por compatibilidad pero [getQuickJsExecutable] ya prefiere el .so nativo.
     */
    @Deprecated("Usar nativeLibraryDir/libqjs.so directo.")
    @Synchronized
    fun ensureQuickJsExecutable(context: Context): File? {
        // Vía correcta: nativo directo.
        getQuickJsExecutable(context)?.let { return it }
        try {
            val binDir = File(context.filesDir, "bin").apply { mkdirs() }
            val target = File(binDir, QUICKJS_BIN_NAME)
            if (isValidQuickJsFile(target) && target.canExecute()) {
                cachedQuickJs = target
                return target
            }
            return null
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Fallo al normalizar QuickJS a files/bin: ${e.message}")
            return null
        }
    }

    /** Valida tamaño mínimo unificado de un candidato QuickJS (sin exigir bit exec). */
    fun isValidQuickJsFile(f: File): Boolean {
        return try {
            f.exists() && f.isFile && f.length() >= QUICKJS_MIN_PACKAGE_BYTES
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Única vía para construir el flag `--js-runtimes` de yt-dlp.
     * Retorna lista vacía si no hay QuickJS ejecutable (el llamador debe registrar
     * "sin motor JS" en lugar de usar el fallback literal `quickjs`, que buscaría
     * un binario inexistente en PATH y produce errores genéricos).
     */
    fun buildJsRuntimeArgs(context: Context): List<String> {
        val qjs = getQuickJsExecutable(context)
        if (qjs == null || !qjs.exists() || !qjs.canExecute()) {
            AuraDebugManager.logWarning(TAG, "yt-dlp correrá SIN motor JS (QuickJS no ejecutable): n-sig/cipher pueden fallar con 403.")
            return emptyList()
        }
        return listOf("--js-runtimes", "quickjs:${qjs.absolutePath}")
    }

    /** Estados honestos del motor JS para UI y diagnóstico. */
    enum class QuickJsState { OK, MISSING, PRESENT_NOT_EXECUTABLE, TOO_SMALL }

    /** Clasifica el estado actual sin ejecutar procesos ni copiar archivos (apto para hilo UI). */
    fun quickJsState(context: Context): QuickJsState {
        // FIX: OK solo si hay bit exec real; antes el tamaño bastaba y mentía a la UI.
        cachedQuickJs?.takeIf { isValidQuickJsFile(it) && it.canExecute() }?.let { return QuickJsState.OK }
        // Nativo con tamaño válido + exec = OK (PackageManager otorga exec a lib*.so).
        val nativeQjs = File(context.applicationInfo.nativeLibraryDir, QUICKJS_SO_NAME)
        if (isValidQuickJsFile(nativeQjs) && nativeQjs.canExecute()) return QuickJsState.OK
        val binQjs = File(File(context.filesDir, "bin"), QUICKJS_BIN_NAME)
        if (isValidQuickJsFile(binQjs) && binQjs.canExecute()) return QuickJsState.OK
        if (binQjs.exists() || nativeQjs.exists()) {
            val biggest = if (binQjs.exists() && (!nativeQjs.exists() || binQjs.length() >= nativeQjs.length())) binQjs else nativeQjs
            if (biggest.length() < QUICKJS_MIN_PACKAGE_BYTES) return QuickJsState.TOO_SMALL
            return QuickJsState.PRESENT_NOT_EXECUTABLE
        }
        return QuickJsState.MISSING
    }

    /** Resultado del self-test real de ejecución JS (`qjs -e 'print(...)'`). */
    data class QuickJsSelfTest(val ok: Boolean, val detail: String)

    /**
     * Ejecuta `qjs -e 'print("qjs-ok")'` con timeout corto para confirmar que el
     * binario realmente corre JS en este dispositivo (no solo que existe en disco).
     */
    fun runQuickJsSelfTest(context: Context, timeoutSeconds: Long = 4L): QuickJsSelfTest {
        val qjs = getQuickJsExecutable(context)
            ?: return QuickJsSelfTest(false, "QuickJS no disponible (sin binario ejecutable).")
        return try {
            val proc = ProcessBuilder(qjs.absolutePath, "-e", "print('qjs-ok');")
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            val readerThread = Thread({
                try {
                    proc.inputStream.bufferedReader().use { r ->
                        var line: String?
                        while (r.readLine().also { line = it } != null) {
                            output.append(line).append('\n')
                        }
                    }
                } catch (_: Throwable) {
                }
            }, "qjs-selftest").apply { isDaemon = true; start() }
            val finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                runCatching { proc.destroyForcibly() }
                return QuickJsSelfTest(false, "QuickJS no respondió en ${timeoutSeconds}s (timeout).")
            }
            readerThread.join(1000)
            val code = proc.exitValue()
            val out = output.toString()
            if (code == 0 && out.contains("qjs-ok")) {
                QuickJsSelfTest(true, "QuickJS ejecuta JS correctamente (${qjs.length() / 1024} KB).")
            } else {
                QuickJsSelfTest(false, "QuickJS respondió código $code sin salida esperada: ${out.take(160)}")
            }
        } catch (e: Exception) {
            QuickJsSelfTest(false, "No se pudo ejecutar QuickJS: ${e.message}")
        }
    }

    /** Detecta en el stderr de yt-dlp pistas de fallo por falta de motor JS. */
    fun containsJsFailureHints(stderr: String): Boolean {
        if (stderr.isBlank()) return false
        val lower = stderr.lowercase()
        return JS_FAILURE_HINTS.any { lower.contains(it) }
    }

    private fun applyExecutablePermission(f: File) {
        try {
            android.system.Os.chmod(f.absolutePath, 448) // 0700
        } catch (_: Throwable) {
        }
        runCatching { f.setExecutable(true, false) }
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
