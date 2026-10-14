package com.example.core.data.importer

import android.content.Context
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.coroutines.resume

/**
 * Ejecutor compartido de subprocesos yt-dlp / Python nativo.
 * Rol arquitectónico: unifica el entorno (antes triplicado con deriva), impone timeout
 * siempre, usa hilos daemon y honra la cancelación de la corrutina que lo invoca.
 */
internal data class YtDlpProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean
)

/**
 * Entorno base idéntico para todos los subprocesos (resolución, descarga, playlists y
 * diagnóstico): PYTHONHOME/PYTHONPATH herméticos, sin user-site, sin bytecode, E/S UTF-8,
 * certificados TLS propios si existen y TMPDIR configurable.
 */
internal fun ytdlpProcessEnv(
    context: Context,
    pythonEnvDir: File,
    nativeDir: File,
    tmpDir: File = context.cacheDir
): Map<String, String> {
    val directPythonZipSo = File(nativeDir, "libpython.zip.so")
    val stdlibZip = File(pythonEnvDir, "stdlib.zip")
    val stdlibDir = File(pythonEnvDir, "stdlib")
    val modulesDir = File(pythonEnvDir, "modules")
    val ffmpegEnvDir = File(context.filesDir, "env/ffmpeg")
    val certFile = File(pythonEnvDir, "usr/etc/tls/cert.pem")
    val env = HashMap<String, String>()
    env["PYTHONHOME"] = pythonEnvDir.absolutePath
    env["PYTHONPATH"] = listOf(
        directPythonZipSo.absolutePath,
        stdlibZip.absolutePath,
        stdlibDir.absolutePath,
        modulesDir.absolutePath,
        pythonEnvDir.absolutePath,
        pythonEnvDir.absolutePath + "/usr/lib/python3.11",
        context.filesDir.absolutePath + "/bin"
    ).joinToString(":")
    env["PYTHONNOUSERSITE"] = "1"
    env["PYTHONDONTWRITEBYTECODE"] = "1"
    env["PYTHONIOENCODING"] = "utf-8"
    env["HOME"] = pythonEnvDir.absolutePath
    env["LD_LIBRARY_PATH"] = "$nativeDir:${modulesDir.absolutePath}:${pythonEnvDir.absolutePath}/usr/lib:${ffmpegEnvDir.absolutePath}/usr/lib"
    if (certFile.exists() && certFile.length() > 0) {
        env["SSL_CERT_FILE"] = certFile.absolutePath
        env["CURL_CA_BUNDLE"] = certFile.absolutePath
        env["REQUESTS_CA_BUNDLE"] = certFile.absolutePath
    }
    env["PATH"] = "$nativeDir:${context.filesDir.absolutePath}/bin:" + (System.getenv("PATH") ?: "/system/bin")
    env["TMPDIR"] = tmpDir.absolutePath
    return env
}

/**
 * Normalización del lanzador CPython a files/bin (paridad con QuickJS).
 * En APK Beta (isDebuggable=false, targetSdk 34+) la ejecución directa desde
 * nativeLibraryDir/libpython.so falla con EACCES por SELinux / noexec, mientras
 * en Debug (debuggable) el dominio SELinux lo permite. Por eso Debug funciona
 * y Beta no, aunque R8 ya está desactivado (minify=false) en ambos.
 * Se copia a files/bin/python con chmod 0700 y se reutiliza en caliente.
 */
internal const val PYTHON_BIN_NAME = "python"
internal const val PYTHON_MIN_PACKAGE_BYTES = 5_000L

@Volatile
internal var cachedPythonBin: File? = null

internal fun isValidPythonFile(f: File): Boolean {
    return try {
        f.exists() && f.isFile && f.length() >= PYTHON_MIN_PACKAGE_BYTES
    } catch (_: Exception) {
        false
    }
}

internal fun applyPythonExecutablePermission(f: File) {
    try {
        android.system.Os.chmod(f.absolutePath, 448) // 0700
    } catch (_: Throwable) {
    }
    runCatching { f.setExecutable(true, false) }
}

/** Copia atómica nativeLibraryDir/libpython.so -> files/bin/python con chmod. Idempotente. */
internal fun ensurePythonExecutable(context: Context): File? {
    try {
        val binDir = File(context.filesDir, "bin").apply { mkdirs() }
        val target = File(binDir, PYTHON_BIN_NAME)
        if (isValidPythonFile(target) && target.canExecute()) {
            cachedPythonBin = target
            return target
        }
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val src = File(nativeDir, "libpython.so").takeIf { isValidPythonFile(it) }
            ?: File(nativeDir, "libpython3.so").takeIf { isValidPythonFile(it) }
            ?: return null
        val tmp = File(binDir, "$PYTHON_BIN_NAME.tmp")
        src.copyTo(tmp, overwrite = true)
        applyPythonExecutablePermission(tmp)
        if (!isValidPythonFile(tmp) || !tmp.canExecute()) {
            runCatching { tmp.delete() }
            return null
        }
        if (target.exists()) runCatching { target.delete() }
        val moved = runCatching { tmp.renameTo(target) }.getOrDefault(false)
        val finalFile = if (moved) target else {
            tmp.copyTo(target, overwrite = true)
            runCatching { tmp.delete() }
            applyPythonExecutablePermission(target)
            target
        }
        if (!finalFile.canExecute()) return null
        cachedPythonBin = finalFile
        return finalFile
    } catch (_: Exception) {
        return null
    }
}

/**
 * Vía única de resolución del intérprete Python:
 * 1. Caché en memoria, 2. files/bin/python, 3. nativeLibraryDir ejecutable,
 * 4. normalización (copia + chmod) como último recurso.
 */
internal fun resolvePythonExecutable(context: Context): File? {
    cachedPythonBin?.takeIf { isValidPythonFile(it) && it.canExecute() }?.let { return it }
    val binPython = File(File(context.filesDir, "bin"), PYTHON_BIN_NAME)
    if (isValidPythonFile(binPython) && binPython.canExecute()) {
        cachedPythonBin = binPython
        return binPython
    }
    val nativeDir = File(context.applicationInfo.nativeLibraryDir)
    val nativePython = File(nativeDir, "libpython.so").takeIf { isValidPythonFile(it) && it.canExecute() }
        ?: File(nativeDir, "libpython3.so").takeIf { isValidPythonFile(it) && it.canExecute() }
    if (nativePython != null) {
        // Normalizar a files/bin para ejecuciones futuras garantizadas en Beta.
        return ensurePythonExecutable(context) ?: nativePython.also { cachedPythonBin = it }
    }
    // El .so existe pero sin bit exec (caso Beta SELinux): intentar normalizar.
    if (File(nativeDir, "libpython.so").let { isValidPythonFile(it) }) {
        ensurePythonExecutable(context)?.let { return it }
    }
    return null
}

/**
 * Lanza el proceso, drena sus flujos en hilos daemon y espera con timeout.
 * Si la corrutina se cancela, el proceso se destruye de inmediato.
 * Si expira el timeout, se destruye forzosamente y se reporta [YtDlpProcessResult.timedOut].
 */
internal suspend fun runYtDlpProcess(
    cmd: List<String>,
    workDir: File,
    env: Map<String, String>,
    timeoutSeconds: Long,
    tag: String,
    mergeStderr: Boolean = false,
    onStdoutLine: ((String) -> Unit)? = null
): YtDlpProcessResult {
    val processBuilder = ProcessBuilder(cmd)
        .directory(workDir)
        .redirectErrorStream(mergeStderr)
    processBuilder.environment().putAll(env)
    val process = processBuilder.start()

    val stdout = StringBuilder()
    val stderr = StringBuilder()

    val stdoutThread = thread(isDaemon = true, name = "ytdlp-stdout") {
        try {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val text = line ?: continue
                    // FIX: siempre acumular en stdout (con salto de línea) ADEMÁS del
                    // callback. Antes, con onStdoutLine != null se descartaba el buffer
                    // y sin callback se concatenaba sin "\n", rompiendo el parseo
                    // lineSequence() de flat-playlist y el JSONObject de resolveStream.
                    stdout.append(text).append("\n")
                    if (onStdoutLine != null) {
                        try {
                            onStdoutLine(text)
                        } catch (_: Throwable) {
                            // Un callback roto no debe matar el drenaje del proceso.
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // Flujo cerrado al destruir el proceso: salida normal del hilo.
        }
    }
    val stderrThread = if (!mergeStderr) {
        thread(isDaemon = true, name = "ytdlp-stderr") {
            try {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stderr.append(line).append("\n")
                    }
                }
            } catch (_: Throwable) {
                // Flujo cerrado al destruir el proceso: salida normal del hilo.
            }
        }
    } else {
        null
    }

    val completed = suspendCancellableCoroutine { cont ->
        thread(isDaemon = true, name = "ytdlp-wait") {
            val ok = try {
                process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            } catch (_: Throwable) {
                false
            }
            cont.resume(ok)
        }
        cont.invokeOnCancellation {
            try {
                process.destroyForcibly()
            } catch (_: Throwable) {
            }
        }
    }

    if (!completed) {
        AuraDebugManager.logWarning(tag, "yt-dlp excedió el tiempo límite de ${timeoutSeconds}s; proceso destruido forzosamente.")
        try {
            process.destroyForcibly()
        } catch (_: Throwable) {
        }
    }
    // Cerrar los flujos despierta a los hilos lectores para que no queden colgados.
    try {
        process.inputStream.close()
    } catch (_: Throwable) {
    }
    try {
        process.errorStream.close()
    } catch (_: Throwable) {
    }
    try {
        process.outputStream.close()
    } catch (_: Throwable) {
    }
    stdoutThread.join(1500)
    stderrThread?.join(1500)

    val exitCode = try {
        if (!completed) -1 else process.exitValue()
    } catch (_: Throwable) {
        -1
    }
    return YtDlpProcessResult(exitCode, stdout.toString(), stderr.toString(), !completed)
}
