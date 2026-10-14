package com.example.core.data.importer

import android.content.Context
import com.example.core.model.DownloadProgress
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
        """\[download\]\s+([\d.]+)%\s+of\s+~?\s*([\d.]+)\s*([KMG]i?B|B)(?:\s+at\s+([\d.]+)\s*([KMG]i?B/s|B/s))?""",
        Pattern.CASE_INSENSITIVE
    )

    suspend fun downloadToFile(
        context: Context,
        url: String,
        targetFile: File,
        isAudioOnly: Boolean,
        onProgressUpdate: (DownloadProgress) -> Unit = {},
        audioQuality: com.example.core.model.StreamingAudioQuality =
            com.example.core.model.StreamingAudioQuality.AUTO_HIFI
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // v0.2.1-beta.1f: corrección automática de formato según el teléfono + blindaje de disco.
            val effectiveQuality = AudioDownloadFormatResolver.resolveEffectiveQuality(audioQuality, context)
            try { targetFile.parentFile?.mkdirs() } catch (_: Throwable) {}
            if (!AudioDownloadFormatResolver.hasEnoughFreeSpace(targetFile.parentFile, 30L * 1024L * 1024L)) {
                AuraDebugManager.logWarning(TAG, "Descarga abortada: sin espacio libre suficiente (~30 MB).")
                onProgressUpdate(DownloadProgress(isDownloading = false))
                return@withContext false
            }
            if (YtDlpAutoUpdater.isYtDlpTemporarilyBlocked()) {
                AuraDebugManager.logWarning(TAG, "Descarga directa omitida: yt-dlp bloqueado por actualización OTA en curso.")
                return@withContext false
            }
            YtDlpEnvironmentSetup.init(context)
            FFmpegNativeEngine.init(context)

            val ytdlpFile = YtDlpAutoUpdater.getYtDlpFile(context)
            val activePython = YtDlpEnvironmentSetup.getPythonExecutable(context)
            if (activePython == null || !ytdlpFile.exists()) {
                AuraDebugManager.logWarning(TAG, "Descarga directa omitida: entorno yt-dlp no disponible.")
                return@withContext false
            }

            val rawTrimmedUrl = url.trim()
            if (!rawTrimmedUrl.startsWith("http://", ignoreCase = true) && !rawTrimmedUrl.startsWith("https://", ignoreCase = true)) {
                AuraDebugManager.logWarning(TAG, "Descarga directa omitida: URL no válida ni segura.")
                return@withContext false
            }
            val cleanUrl = YtDlpEnvironmentSetup.normalizeYouTubeMusicUrlForYtDlp(rawTrimmedUrl)

            val pythonEnvDir = File(context.filesDir, "env/python")
            YtDlpEnvironmentSetup.ensureTlsCertificates(pythonEnvDir)
            val jsRuntimeArgs = YtDlpEnvironmentSetup.buildJsRuntimeArgs(context)
            val ffmpegBin = FFmpegNativeEngine.getBinaryFile(context)

            if (targetFile.exists()) {
                targetFile.delete()
            }
            targetFile.parentFile?.mkdirs()

            val formatSelector = if (isAudioOnly) {
                when (effectiveQuality) {
                    com.example.core.model.StreamingAudioQuality.AHORRO_OPUS_48K ->
                        "bestaudio[ext=webm][acodec=opus]/bestaudio[ext=webm]/bestaudio/best"
                    com.example.core.model.StreamingAudioQuality.ALTA_OPUS_160K ->
                        "bestaudio[ext=webm]/bestaudio[acodec=opus]/bestaudio/best"
                    com.example.core.model.StreamingAudioQuality.ESTANDAR_M4A_128K ->
                        "bestaudio[ext=m4a]/bestaudio/best[ext=mp4]/best"
                    com.example.core.model.StreamingAudioQuality.AUTO_HIFI ->
                        "bestaudio[ext=webm]/bestaudio[ext=m4a]/bestaudio/best"
                }
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
                addAll(jsRuntimeArgs)
                if (ffmpegBin != null && ffmpegBin.exists()) {
                    add("--ffmpeg-location")
                    add(ffmpegBin.absolutePath)
                }
                add("-o")
                add(targetFile.absolutePath)
                add("--")
                add(cleanUrl)
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

            val nativeDir = context.applicationInfo.nativeLibraryDir

            val procResult = runYtDlpProcess(
                cmd = cmdList,
                workDir = File(nativeDir),
                env = ytdlpProcessEnv(context, pythonEnvDir, File(nativeDir)),
                timeoutSeconds = 120,
                tag = TAG,
                mergeStderr = true,
                onStdoutLine = { rawLine ->
                    parseAndEmitProgress(rawLine.trim(), phaseTitle, onProgressUpdate)
                }
            )
            val completed = !procResult.timedOut
            if (!completed) {
                AuraDebugManager.logWarning(TAG, "Descarga directa de yt-dlp excedió timeout de 120s.")
            }
            val exitCode = procResult.exitCode

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
                val jsHint = if (jsRuntimeArgs.isEmpty()) " (sin motor JS: QuickJS ${YtDlpNativeEngine.getQuickJsStatusSummary(context)})" else ""
                AuraDebugManager.logWarning(TAG, "Descarga directa con yt-dlp finalizó con código $exitCode$jsHint.")
                // No dejar parciales corruptos ocupando disco; el próximo intento parte limpio.
                runCatching { if (targetFile.exists()) targetFile.delete() }
            }
            ok
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AuraDebugManager.logWarning(TAG, "Excepción en descarga directa con yt-dlp: ${e.message}")
            try { if (targetFile.exists() && targetFile.length() < 1024L) targetFile.delete() } catch (_: Throwable) {}
            false
        } catch (t: Throwable) {
            AuraDebugManager.logWarning(TAG, "Descarga yt-dlp abortada por falta de memoria: ${t.message}")
            try { if (targetFile.exists() && targetFile.length() < 1024L) targetFile.delete() } catch (_: Throwable) {}
            try { System.gc() } catch (_: Throwable) {}
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
