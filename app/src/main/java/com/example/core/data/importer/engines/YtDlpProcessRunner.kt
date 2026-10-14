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
 * Resolución del lanzador CPython.
 *
 * Causa raíz del error Beta "Cannot run program .../files/bin/python ... error=13,
 * Permission denied" (ver captura): desde Android 10 (API 29, targetSdk 34) SELinux
 * aplica W^X y deniega exec() de archivos en directorios escribibles (filesDir con
 * contexto app_data_file). La vía soportada es ejecutar directamente el PIE
 * empaquetado como `lib*.so` desde nativeLibraryDir (contexto apk_data_file con
 * permiso exec + nombre .so conservado). Copiar `libpython.so` a `files/bin/python`
 * (sin extensión .so y en ubicación no-exec) rompe además la resolución
 * `self_dir` del lanzador (`native_python_launcher.c`), que busca
 * `libpython3.11.so` junto a argv[0].
 * Por eso se prefiere nativeLibraryDir y se elimina la copia obsoleta en files/bin.
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

/** Elimina copias obsoletas en files/bin que siempre fallan con EACCES en Beta. */
internal fun cleanupLegacyPythonBin(context: Context) {
    try {
        val binDir = File(context.filesDir, "bin")
        listOf(PYTHON_BIN_NAME, "python3", "$PYTHON_BIN_NAME.tmp").forEach { name ->
            runCatching { File(binDir, name).takeIf { it.exists() }?.delete() }
        }
    } catch (_: Throwable) {}
}

/** Copia atómica nativeLibraryDir/libpython.so -> files/bin/python con chmod. Idempotente.
 * @deprecated No usar en API 29+: SELinux deniega exec en filesDir (error=13).
 * Se conserva solo como último recurso si el .so nativo faltara. */
@Deprecated("SELinux W^X deniega exec en filesDir desde API 29. Usar binario nativo directo.")
internal fun ensurePythonExecutable(context: Context): File? {
    // Vía correcta: binario nativo directo (conserva nombre .so y self_dir).
    resolveNativePythonFile(context)?.let { return it }
    // Último recurso legado (casi siempre fallará con EACCES, pero se intenta).
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
        if (!isValidPythonFile(tmp)) {
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
        cachedPythonBin = finalFile
        return finalFile
    } catch (_: Exception) {
        return null
    }
}

/** Localiza el PIE nativo en nativeLibraryDir sin copiar ni renombrar. */
internal fun resolveNativePythonFile(context: Context): File? {
    return try {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val launcher = File(nativeDir, "libpython.so").takeIf { isValidPythonFile(it) }
            ?: File(nativeDir, "libpython3.so").takeIf { isValidPythonFile(it) }
            ?: return null
        // FIX EACCES temprano: el .so debe tener bit exec (extractNativeLibs=true).
        // Sin esto el fallo aparecía tarde en ProcessBuilder.start() sin telemetría.
        if (!launcher.canExecute()) {
            AuraDebugManager.logWarning("YtDlpProcessRunner", "libpython nativo sin bit exec, se usa fallback InnerTube.")
            return null
        }
        // El motor real debe existir junto al lanzador (self_dir/dlopen).
        val hasEngine = File(nativeDir, "libpython3.11.so").takeIf { it.length() >= 500_000L } != null ||
                File(nativeDir, "libpython3.so").takeIf { it.length() >= 500_000L } != null
        if (!hasEngine) return null
        cachedPythonBin = launcher
        launcher
    } catch (_: Exception) {
        null
    }
}

/**
 * Vía única de resolución del intérprete Python:
 * 1. Caché en memoria (nativa), 2. nativeLibraryDir/libpython.so directo,
 * 3. copia legada en files/bin solo si ya existe y es ejecutable.
 * Nunca se crea una copia nueva en files/bin (W^X la deniega en Beta).
 */
internal fun resolvePythonExecutable(context: Context): File? {
    // FIX: la caché solo vale si sigue ejecutable (OEM puede revocar exec).
    cachedPythonBin?.takeIf { isValidPythonFile(it) && it.canExecute() }?.let { return it }
    cleanupLegacyPythonBin(context)
    resolveNativePythonFile(context)?.let { return it }
    // Compatibilidad: reutilizar copia legada solo si el sistema la permite.
    val binPython = File(File(context.filesDir, "bin"), PYTHON_BIN_NAME)
    if (isValidPythonFile(binPython) && binPython.canExecute()) {
        cachedPythonBin = binPython
        return binPython
    }
    return null
}

/** Mensaje amigable para fallos de exec (EACCES/error=13) en lugar del path crudo. */
internal fun friendlyYtDlpExecError(raw: String?): String {
    val r = raw.orEmpty()
    if (r.contains("Cannot run program") || r.contains("Permission denied") ||
        r.contains("error=13") || r.contains("EACCES") || r.contains("/files/bin/python")
    ) {
        return "El motor yt-dlp no pudo ejecutarse en este dispositivo (permiso del sistema denegado). " +
                "Se continuará automáticamente con el motor rápido InnerTube. " +
                "Si el problema persiste, elige InnerTube o WebView en el selector de motor."
    }
    return r.ifBlank { "No se pudo ejecutar yt-dlp en este dispositivo." }
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
