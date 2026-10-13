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
                    if (onStdoutLine != null) {
                        try {
                            onStdoutLine(text)
                        } catch (_: Throwable) {
                            // Un callback roto no debe matar el drenaje del proceso.
                        }
                    } else {
                        stdout.append(text)
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
