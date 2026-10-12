package com.example.core.data.importer

import android.content.Context
import com.example.core.model.DownloadProgress
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Aura Music - Descargador Directo de Flujos Multimedia con yt-dlp Nativo (< 400 líneas)
 *
 * Responsabilidades:
 * - Descarga directa hacia disco mediante CPython y el motor interno de yt-dlp.
 * - Captura y parseo en vivo de stdout con '--newline' para emitir métricas reales de progreso
 *   (porcentaje, velocidad en bytes/s, bytes descargados y total estimado).
 * - Soporte independiente para audio de alta fidelidad (.m4a) y Video Canvas en 480p (.mp4).
 */
object YtDlpStreamDownloader {

    private const val TAG = "YtDlpStreamDownloader"

    // Regex para parsear la línea estándar de yt-dlp:
    // [download]  45.2% of ~  12.50MiB at    2.40MiB/s ETA 00:03
    // [download]  100% of 12.50MiB in 00:05
    private val PROGRESS_REGEX = Pattern.compile(
        """\[download\]\s+([\d.]+)%\s+of\s+~?\s*([\d.]+)\s*([KMG]i?B)(?:\s+at\s+([\d.]+)\s*([KMG]i?B/s))?""",
        Pattern.CASE_INSENSITIVE
    )

    suspend fun downloadToFile(
        context: Context,
        url: String,
        targetFile: File,
        isAudioOnly: Boolean,
        onProgressUpdate: (DownloadProgress) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (YtDlpAutoUpdater.isYtDlpTemporarilyBlocked()) return@withContext false
            YtDlpEnvironmentSetup.init(context)
            FFmpegNativeEngine.init(context)

            val ytdlpFile = YtDlpAutoUpdater.getYtDlpFile(context)
            val activePython = YtDlpEnvironmentSetup.getPythonExecutable(context) ?: return@withContext false
            if (!ytdlpFile.exists()) return@withContext false

            val rawTrimmedUrl = url.trim()
            if (!rawTrimmedUrl.startsWith("http://", ignoreCase = true) && !rawTrimmedUrl.startsWith("https://", ignoreCase = true)) {
                return@withContext false
            }
            val cleanUrl = YtDlpEnvironmentSetup.normalizeYouTubeMusicUrlForYtDlp(rawTrimmedUrl)

            val pythonEnvDir = File(context.filesDir, "env/python")
            YtDlpEnvironmentSetup.ensureTlsCertificates(pythonEnvDir)
            val localCert = File(pythonEnvDir, "usr/etc/tls/cert.pem")
            val qjsBin = YtDlpEnvironmentSetup.getQuickJsExecutable(context)
            val ffmpegBin = FFmpegNativeEngine.getBinaryFile(context)

            if (targetFile.exists()) {
                targetFile.delete()
            }
            targetFile.parentFile?.mkdirs()

            val formatSelector = if (isAudioOnly) {
                "bestaudio[ext=m4a]/bestaudio/best[ext=mp4]/best"
            } else {
                "bestvideo[height<=480][ext=mp4]/bestvideo[height<=480]/best[height<=480][ext=mp4]/best[height<=480]/best"
            }

            val extractorArgs = YtDlpEnvironmentSetup.buildYouTubeExtractorArgs(context)
            val activeCookies = YtDlpCookieManager.getActiveCookiesFileIfEnabled(context)

            val cmdList = mutableListOf<String>().apply {
                add(activePython.absolutePath)
                add("-I")
                add(ytdlpFile.absolutePath)
                add("--no-config")
                add("--no-warnings")
                add("--newline") // Clave para emitir progreso línea a línea sin buffering
                add("--no-part")
                add("--no-playlist")
                add("--extractor-args")
                add(extractorArgs)
                add("--format")
                add(formatSelector)
                if (activeCookies != null && activeCookies.exists()) {
                    add("--cookies")
                    add(activeCookies.absolutePath)
                }
                if (qjsBin != null && qjsBin.exists()) {
                    add("--js-runtimes")
                    add("quickjs:${qjsBin.absolutePath}")
                }
                if (ffmpegBin != null && ffmpegBin.exists()) {
                    add("--ffmpeg-location")
                    add(ffmpegBin.absolutePath)
                }
                add("-o")
                add(targetFile.absolutePath)
                add("--")
                add(cleanUrl)
            }

            val nativeDir = context.applicationInfo.nativeLibraryDir
            val directPythonZipSo = File(nativeDir, "libpython.zip.so")
            val stdlibZip = File(pythonEnvDir, "stdlib.zip")
            val stdlibDir = File(pythonEnvDir, "stdlib")
            val modulesDir = File(pythonEnvDir, "modules")
            val ffmpegEnvDir = File(context.filesDir, "env/ffmpeg")

            val pb = ProcessBuilder(cmdList)
                .directory(File(nativeDir))
                .redirectErrorStream(true)

            pb.environment().apply {
                this["PYTHONHOME"] = pythonEnvDir.absolutePath
                this["PYTHONPATH"] = "${directPythonZipSo.absolutePath}:${stdlibZip.absolutePath}:${stdlibDir.absolutePath}:${modulesDir.absolutePath}:${pythonEnvDir.absolutePath}:${pythonEnvDir.absolutePath}/usr/lib/python3.11:${context.filesDir.absolutePath}/bin"
                this["PYTHONNOUSERSITE"] = "1"
                this["HOME"] = pythonEnvDir.absolutePath
                this["LD_LIBRARY_PATH"] = "$nativeDir:${modulesDir.absolutePath}:${pythonEnvDir.absolutePath}/usr/lib:${ffmpegEnvDir.absolutePath}/usr/lib"
                if (localCert.exists() && localCert.length() > 0) {
                    this["SSL_CERT_FILE"] = localCert.absolutePath
                    this["CURL_CA_BUNDLE"] = localCert.absolutePath
                    this["REQUESTS_CA_BUNDLE"] = localCert.absolutePath
                }
                this["PATH"] = "$nativeDir:${context.filesDir.absolutePath}/bin:${System.getenv("PATH") ?: ""}"
                this["TMPDIR"] = context.cacheDir.absolutePath
            }

            val phaseTitle = if (isAudioOnly) "Descargando audio de alta fidelidad (yt-dlp)..." else "Descargando Video Canvas 480p (yt-dlp)..."
            AuraDebugManager.logInfo(TAG, "Iniciando descarga directa con yt-dlp (${if (isAudioOnly) "Audio" else "Video 480p"}) para: $cleanUrl")

            onProgressUpdate(
                DownloadProgress(
                    isDownloading = true,
                    phase = phaseTitle,
                    bytesDownloaded = 0L,
                    totalBytes = 0L,
                    bytesPerSecond = 0L,
                    progressFraction = 0.05f
                )
            )

            val process = pb.start()

            val stdoutThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val l = line?.trim() ?: continue
                            parseAndEmitProgress(l, phaseTitle, onProgressUpdate)
                        }
                    }
                } catch (_: Throwable) {}
            }
            stdoutThread.start()

            val completed = process.waitFor(120, TimeUnit.SECONDS)
            if (!completed) {
                try { process.destroy() } catch (_: Throwable) {}
                AuraDebugManager.logWarning(TAG, "Descarga directa de yt-dlp excedió timeout de 120s.")
            }
            val exitCode = if (completed) process.exitValue() else -1
            stdoutThread.join(1000)

            val ok = exitCode == 0 && targetFile.exists() && targetFile.length() > 1024L
            if (ok) {
                AuraDebugManager.logInfo(TAG, "¡Descarga directa con yt-dlp completada con éxito (${targetFile.length()} bytes)!")
                onProgressUpdate(
                    DownloadProgress(
                        isDownloading = true,
                        phase = "Completando archivo...",
                        bytesDownloaded = targetFile.length(),
                        totalBytes = targetFile.length(),
                        bytesPerSecond = 0L,
                        progressFraction = 1f
                    )
                )
            } else {
                AuraDebugManager.logWarning(TAG, "Descarga directa con yt-dlp finalizó con código $exitCode.")
            }
            ok
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Excepción en descarga directa con yt-dlp: ${e.message}")
            false
        }
    }

    private fun parseAndEmitProgress(
        line: String,
        phase: String,
        onProgressUpdate: (DownloadProgress) -> Unit
    ) {
        if (!line.startsWith("[download]")) return
        val matcher = PROGRESS_REGEX.matcher(line)
        if (matcher.find()) {
            val percentStr = matcher.group(1) ?: "0"
            val totalSizeVal = matcher.group(2)?.toDoubleOrNull() ?: 0.0
            val totalSizeUnit = matcher.group(3) ?: "MiB"
            val speedVal = matcher.group(4)?.toDoubleOrNull() ?: 0.0
            val speedUnit = matcher.group(5) ?: "MiB/s"

            val percent = percentStr.toFloatOrNull() ?: 0f
            val fraction = (percent / 100f).coerceIn(0f, 1f)

            val totalBytes = toBytes(totalSizeVal, totalSizeUnit)
            val bytesDownloaded = (totalBytes * fraction).toLong()
            val bytesPerSecond = toBytesPerSecond(speedVal, speedUnit)

            onProgressUpdate(
                DownloadProgress(
                    isDownloading = true,
                    phase = phase,
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = totalBytes,
                    bytesPerSecond = bytesPerSecond,
                    progressFraction = fraction
                )
            )
        }
    }

    private fun toBytes(value: Double, unit: String): Long {
        val multiplier = when (unit.uppercase()) {
            "GIB", "GB" -> 1024L * 1024L * 1024L
            "MIB", "MB" -> 1024L * 1024L
            "KIB", "KB" -> 1024L
            else -> 1L
        }
        return (value * multiplier).toLong()
    }

    private fun toBytesPerSecond(value: Double, unit: String): Long {
        val cleanUnit = unit.removeSuffix("/s").removeSuffix("/S")
        return toBytes(value, cleanUnit)
    }
}
