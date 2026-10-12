package com.example.core.data.importer

import android.content.Context
import com.example.core.data.streaming.StreamingHttpDataSourceConfig
import com.example.core.model.DownloadProgress
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Aura Music - Motor y Fachada Pública de yt-dlp con Entorno Python Optimizado (< 450 líneas)
 *
 * Responsabilidades:
 * - Fachada desacoplada delegando en [YtDlpEnvironmentSetup] y [YtDlpStreamDownloader].
 * - Inspección de flujos y metadatos JSON con soporte multi-cliente y selección de 480p.
 * - Resolución de listas planas (flat-playlist) para playlists de YouTube / SoundCloud.
 * - Descarga directa nativa respaldada por CPython con métricas de progreso en vivo.
 */
object YtDlpNativeEngine {

    private const val TAG = "YtDlpNativeEngine"

    fun init(context: Context) {
        YtDlpEnvironmentSetup.init(context)
    }

    fun isAvailable(context: Context): Boolean {
        val ytdlpFile = YtDlpAutoUpdater.getYtDlpFile(context)
        return ytdlpFile.exists() && ytdlpFile.length() > 50_000L && getPythonExecutable(context) != null
    }

    fun getPythonExecutable(context: Context): File? = YtDlpEnvironmentSetup.getPythonExecutable(context)

    fun getQuickJsExecutable(context: Context): File? = YtDlpEnvironmentSetup.getQuickJsExecutable(context)

    fun getQuickJsStatusSummary(context: Context): String {
        val qjs = getQuickJsExecutable(context)
        return if (qjs != null && qjs.exists() && qjs.length() > 50_000L) {
            "QuickJS C99 activo"
        } else {
            "Integrado en APK"
        }
    }

    fun ensureTlsCertificates(pythonEnvDir: File, forceRebuild: Boolean = false) {
        YtDlpEnvironmentSetup.ensureTlsCertificates(pythonEnvDir, forceRebuild)
    }

    fun normalizeYouTubeMusicUrlForYtDlp(rawUrl: String): String =
        YtDlpEnvironmentSetup.normalizeYouTubeMusicUrlForYtDlp(rawUrl)

    suspend fun buildYouTubeExtractorArgs(context: Context): String =
        YtDlpEnvironmentSetup.buildYouTubeExtractorArgs(context)

    /**
     * Descarga directamente el flujo de audio o video 480p hacia [targetFile] usando el motor HTTP/TLS
     * interno de `yt-dlp` (CPython + OpenSSL + QuickJS) con emisión de progreso en tiempo real.
     */
    suspend fun downloadStreamDirectToFile(
        context: Context,
        url: String,
        targetFile: File,
        isAudioOnly: Boolean,
        onProgressUpdate: (DownloadProgress) -> Unit = {}
    ): Boolean {
        return YtDlpStreamDownloader.downloadToFile(
            context = context,
            url = url,
            targetFile = targetFile,
            isAudioOnly = isAudioOnly,
            onProgressUpdate = onProgressUpdate
        )
    }

    /**
     * Resuelve los flujos de audio y video de YouTube utilizando yt-dlp y Python localmente.
     */
    suspend fun resolveStream(
        context: Context,
        url: String
    ): Result<OnlineVideoAudioImporter.ResolvedMediaInfo> = withContext(Dispatchers.IO) {
        if (YtDlpAutoUpdater.isYtDlpTemporarilyBlocked()) {
            val msg = "Descarga por yt-dlp bloqueada temporalmente hasta que se aplique la actualización de paquetes."
            AuraDebugManager.logWarning(TAG, msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        init(context)
        FFmpegNativeEngine.init(context)

        val ytdlpFile = YtDlpAutoUpdater.getYtDlpFile(context)
        val pythonBin = getPythonExecutable(context)

        if (!ytdlpFile.exists() || pythonBin == null) {
            AuraDebugManager.logInfo(TAG, "yt-dlp no inicializado. Iniciando auto-provisión OTA...")
            val updateRes = YtDlpAutoUpdater.checkAndUpdate(context, forceDownload = false)
            if (updateRes !is YtDlpAutoUpdater.UpdateResult.Updated && updateRes !is YtDlpAutoUpdater.UpdateResult.AlreadyUpToDate) {
                return@withContext Result.failure(
                    IllegalStateException("Motor yt-dlp no disponible. Requiere provisión de entorno.")
                )
            }
        }

        val activePython = getPythonExecutable(context)
        if (!ytdlpFile.exists() || activePython == null) {
            return@withContext Result.failure(
                IllegalStateException("No se encontró el ejecutable de Python para correr yt-dlp en el teléfono.")
            )
        }

        val ffmpegBin = FFmpegNativeEngine.getBinaryFile(context)
        val qjsBin = getQuickJsExecutable(context)

        val rawTrimmedUrl = url.trim()
        if (!rawTrimmedUrl.startsWith("http://", ignoreCase = true) && !rawTrimmedUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(
                IllegalArgumentException("URL inválida o insegura para resolver con yt-dlp: $url")
            )
        }
        val cleanUrl = normalizeYouTubeMusicUrlForYtDlp(rawTrimmedUrl)

        val pythonEnvDir = File(context.filesDir, "env/python")
        ensureTlsCertificates(pythonEnvDir)
        val localCert = File(pythonEnvDir, "usr/etc/tls/cert.pem")

        val extractorArgs = buildYouTubeExtractorArgs(context)
        val activeCookies = YtDlpCookieManager.getActiveCookiesFileIfEnabled(context)

        val cmdList = mutableListOf<String>().apply {
            add(activePython.absolutePath)
            add(ytdlpFile.absolutePath)
            add("--dump-single-json")
            add("--no-warnings")
            add("--no-playlist")
            add("--extractor-args")
            add(extractorArgs)
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
            if (!localCert.exists() || localCert.length() < 1000L) {
                ensureTlsCertificates(pythonEnvDir, forceRebuild = true)
            }
            add("--")
            add(cleanUrl)
        }

        val nativeDir = context.applicationInfo.nativeLibraryDir
        val directPythonZipSo = File(nativeDir, "libpython.zip.so")
        val stdlibZip = File(pythonEnvDir, "stdlib.zip")
        val stdlibDir = File(pythonEnvDir, "stdlib")
        val modulesDir = File(pythonEnvDir, "modules")
        val ffmpegEnvDir = File(context.filesDir, "env/ffmpeg")

        val processBuilder = ProcessBuilder(cmdList)
            .directory(File(nativeDir))
            .redirectErrorStream(false)

        processBuilder.environment().apply {
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

        AuraDebugManager.logInfo(TAG, "Ejecutando yt-dlp con Python nativo para resolver URL: $cleanUrl")

        val process = processBuilder.start()
        val jsonOutput = StringBuilder()
        val errorLog = StringBuilder()

        val stdoutThread = Thread {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        jsonOutput.append(line)
                    }
                }
            } catch (_: Exception) {}
        }
        stdoutThread.start()

        val stderrThread = Thread {
            try {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        errorLog.append(line).append("\n")
                    }
                }
            } catch (_: Exception) {}
        }
        stderrThread.start()

        val completedInTime = process.waitFor(20, TimeUnit.SECONDS)
        if (!completedInTime) {
            try { process.destroy() } catch (_: Throwable) {}
            AuraDebugManager.logWarning(TAG, "yt-dlp excedió el tiempo límite de 20s.")
        }
        val exitCode = if (completedInTime) process.exitValue() else -1
        stdoutThread.join(1000)
        stderrThread.join(1000)

        if (exitCode != 0 || jsonOutput.isBlank()) {
            AuraDebugManager.logWarning(TAG, "yt-dlp terminó con código $exitCode. Error: ${errorLog.takeLast(300)}")
            return@withContext Result.failure(
                Exception("yt-dlp finalizó con código $exitCode. Error: ${errorLog.takeLast(120).trim()}")
            )
        }

        try {
            val rootJson = JSONObject(jsonOutput.toString())
            val title = rootJson.optString("title", "Audio de YouTube").trim()
            val uploader = rootJson.optString("uploader", "").ifBlank {
                rootJson.optString("channel", "YouTube")
            }.trim()
            val duration = rootJson.optLong("duration", 0L)
            val thumbnail = rootJson.optString("thumbnail", "")

            var directAudioUrl: String? = null
            var directVideoUrl: String? = null
            var audioHeaders = mapOf<String, String>()
            var videoHeaders = mapOf<String, String>()

            fun extractHeaders(fmtObj: JSONObject): Map<String, String> {
                val map = LinkedHashMap<String, String>()
                rootJson.optJSONObject("http_headers")?.let { rootHeaders ->
                    val keys = rootHeaders.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        map[k] = rootHeaders.optString(k, "")
                    }
                }
                fmtObj.optJSONObject("http_headers")?.let { fmtHeaders ->
                    val keys = fmtHeaders.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        map[k] = fmtHeaders.optString(k, "")
                    }
                }
                return map
            }

            fun isDirectStream(u: String, proto: String, ext: String): Boolean {
                if (u.isBlank()) return false
                val lower = u.lowercase()
                val lp = proto.lowercase()
                if (lp.contains("m3u8") || lp.contains("dash") || lower.contains(".m3u8") || lower.contains(".mpd")) return false
                if (ext.equals("mhtml", ignoreCase = true)) return false
                return true
            }

            val formats = rootJson.optJSONArray("formats")
            if (formats != null) {
                var bestAudioScore = -1.0
                var bestVideoScore = -999999

                for (i in 0 until formats.length()) {
                    val fmt = formats.getJSONObject(i)
                    val fmtUrl = fmt.optString("url", "")
                    val protocol = fmt.optString("protocol", "https")
                    val ext = fmt.optString("ext", "").lowercase()
                    if (!isDirectStream(fmtUrl, protocol, ext)) continue

                    val vcodec = fmt.optString("vcodec", "none").lowercase()
                    val acodec = fmt.optString("acodec", "none").lowercase()
                    val height = fmt.optInt("height", 0)
                    val abr = fmt.optDouble("abr", 0.0).takeIf { it > 0 } ?: fmt.optDouble("tbr", 0.0)

                    if (acodec != "none" && vcodec == "none") {
                        val codecBonus = if (ext == "m4a" || acodec.contains("mp4a")) 500.0 else 0.0
                        val score = abr + codecBonus
                        if (score > bestAudioScore || directAudioUrl == null) {
                            bestAudioScore = score
                            directAudioUrl = fmtUrl
                            audioHeaders = extractHeaders(fmt)
                        }
                    }

                    if (vcodec != "none" && height > 0) {
                        val diff = kotlin.math.abs(height - 480)
                        var score = 10000 - (diff * 15)
                        if (height == 480) score += 5000
                        if (ext == "mp4") score += 3000
                        if (vcodec.startsWith("avc1") || vcodec.contains("h264")) score += 4000
                        if (score > bestVideoScore) {
                            bestVideoScore = score
                            directVideoUrl = fmtUrl
                            videoHeaders = extractHeaders(fmt)
                        }
                    }
                }
            }

            val finalVideoUrl = directVideoUrl ?: directAudioUrl ?: url
            val finalAudioHeaders = StreamingHttpDataSourceConfig.resolveHeadersForStream(
                videoId = null,
                rawUrl = directAudioUrl ?: finalVideoUrl,
                explicitHeaders = audioHeaders
            )
            val finalVideoHeaders = StreamingHttpDataSourceConfig.resolveHeadersForStream(
                videoId = null,
                rawUrl = finalVideoUrl,
                explicitHeaders = videoHeaders.ifEmpty { audioHeaders }
            )

            StreamingHttpDataSourceConfig.registerStreamHeaders(
                videoId = WebStreamExtractor.extractVideoId(url),
                audioUrl = directAudioUrl,
                videoUrl = finalVideoUrl,
                headers = finalAudioHeaders,
                audioHeaders = finalAudioHeaders,
                videoHeaders = finalVideoHeaders
            )

            val mediaInfo = OnlineVideoAudioImporter.ResolvedMediaInfo(
                originalUrl = url,
                suggestedTitle = title,
                suggestedArtist = uploader,
                videoUrl = finalVideoUrl,
                audioUrl = directAudioUrl,
                coverUrl = thumbnail.ifBlank { null },
                durationSeconds = duration,
                httpHeaders = finalAudioHeaders,
                audioHeaders = finalAudioHeaders,
                videoHeaders = finalVideoHeaders
            )

            AuraDebugManager.logInfo(TAG, "¡yt-dlp resolvió exitosamente: '$title' de $uploader ($duration s) con video 480p!")
            Result.success(mediaInfo)
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Fallo al procesar JSON de yt-dlp: ${e.message}")
            Result.failure(e)
        }
    }

    data class FlatPlaylistEntry(
        val id: String,
        val title: String,
        val uploader: String,
        val durationSeconds: Long,
        val thumbnailUrl: String,
        val url: String
    )

    data class FlatPlaylistDump(
        val title: String,
        val uploader: String,
        val thumbnailUrl: String,
        val entries: List<FlatPlaylistEntry>
    )

    suspend fun resolveFlatPlaylist(
        context: Context,
        targetUrlOrSearch: String
    ): Result<FlatPlaylistDump> = withContext(Dispatchers.IO) {
        try {
            val trimmedInput = targetUrlOrSearch.trim()
            if (trimmedInput.isBlank() || trimmedInput.startsWith("-")) {
                return@withContext Result.failure(SecurityException("Entrada no válida para yt-dlp."))
            }

            init(context)
            FFmpegNativeEngine.init(context)

            val ytdlpBin = YtDlpAutoUpdater.getYtDlpFile(context)
            val pythonBin = getPythonExecutable(context)
            if (!ytdlpBin.exists() || pythonBin == null) {
                YtDlpAutoUpdater.checkAndUpdate(context, forceDownload = false)
            }

            val activePython = getPythonExecutable(context)
            if (!ytdlpBin.exists() || activePython == null) {
                return@withContext Result.failure(IllegalStateException("Entorno nativo de yt-dlp no inicializado."))
            }

            val nativeDir = context.applicationInfo.nativeLibraryDir
            val directPythonZipSo = File(nativeDir, "libpython.zip.so")
            val quickJsExec = getQuickJsExecutable(context)
            val pythonEnvDir = File(context.filesDir, "env/python")
            val stdlibZip = File(pythonEnvDir, "stdlib.zip")
            val stdlibDir = File(pythonEnvDir, "stdlib")
            val modulesDir = File(pythonEnvDir, "modules")
            val ffmpegEnvDir = File(context.filesDir, "env/ffmpeg")

            ensureTlsCertificates(pythonEnvDir, forceRebuild = false)
            val certPemFile = File(pythonEnvDir, "usr/etc/tls/cert.pem")
            val tmpDir = File(context.cacheDir, "ytdlp_tmp").apply { mkdirs() }
            val jsRuntimeSpec = if (quickJsExec != null && quickJsExec.exists()) "quickjs:${quickJsExec.absolutePath}" else "quickjs"

            val normalizedInput = if (trimmedInput.startsWith("http", ignoreCase = true)) {
                normalizeYouTubeMusicUrlForYtDlp(trimmedInput)
            } else {
                trimmedInput
            }

            val extractorArgs = buildYouTubeExtractorArgs(context)
            val activeCookies = YtDlpCookieManager.getActiveCookiesFileIfEnabled(context)

            val command = mutableListOf(
                activePython.absolutePath,
                "-I",
                ytdlpBin.absolutePath,
                "--no-config",
                "--dump-single-json",
                "--flat-playlist",
                "--no-warnings",
                "--no-progress",
                "--socket-timeout", "14",
                "--extractor-args", extractorArgs,
                "--js-runtimes", jsRuntimeSpec
            ).apply {
                if (activeCookies != null && activeCookies.exists()) {
                    add("--cookies")
                    add(activeCookies.absolutePath)
                }
                add("--")
                add(normalizedInput)
            }

            val pb = ProcessBuilder(command)
                .directory(File(nativeDir))
                .redirectErrorStream(false)

            pb.environment().apply {
                this["PYTHONHOME"] = pythonEnvDir.absolutePath
                this["PYTHONPATH"] = "${directPythonZipSo.absolutePath}:${stdlibZip.absolutePath}:${stdlibDir.absolutePath}:${modulesDir.absolutePath}:${pythonEnvDir.absolutePath}:${pythonEnvDir.absolutePath}/usr/lib/python3.11:${context.filesDir.absolutePath}/bin"
                this["PYTHONNOUSERSITE"] = "1"
                this["PYTHONDONTWRITEBYTECODE"] = "1"
                this["PYTHONIOENCODING"] = "utf-8"
                this["TMPDIR"] = tmpDir.absolutePath
                this["HOME"] = pythonEnvDir.absolutePath
                this["LD_LIBRARY_PATH"] = "$nativeDir:${modulesDir.absolutePath}:${pythonEnvDir.absolutePath}/usr/lib:${ffmpegEnvDir.absolutePath}/usr/lib"
                this["PATH"] = "$nativeDir:${context.filesDir.absolutePath}/bin:${System.getenv("PATH") ?: "/system/bin"}"
                if (certPemFile.exists() && certPemFile.length() > 0) {
                    this["SSL_CERT_FILE"] = certPemFile.absolutePath
                    this["REQUESTS_CA_BUNDLE"] = certPemFile.absolutePath
                    this["CURL_CA_BUNDLE"] = certPemFile.absolutePath
                }
            }

            val proc = pb.start()
            val stdout = proc.inputStream.bufferedReader().use { it.readText() }
            val stderr = proc.errorStream.bufferedReader().use { it.readText() }
            val code = proc.waitFor()

            if (code != 0 || stdout.isBlank()) {
                return@withContext Result.failure(IllegalStateException("yt-dlp flat-playlist error ($code): ${stderr.take(200)}"))
            }

            val jsonLine = stdout.lineSequence().lastOrNull { it.trim().startsWith("{") } ?: stdout.trim()
            val root = JSONObject(jsonLine)
            val playlistTitle = root.optString("title", "Playlist Importada").trim()
            val playlistUploader = root.optString("uploader", "").ifBlank { root.optString("channel", "Creador") }.trim()

            val entriesArr = root.optJSONArray("entries") ?: JSONArray()
            val entries = mutableListOf<FlatPlaylistEntry>()
            for (i in 0 until entriesArr.length()) {
                val item = entriesArr.optJSONObject(i) ?: continue
                val id = item.optString("id", "").trim()
                val entryTitle = item.optString("title", "").trim()
                if (entryTitle.isBlank() || entryTitle.equals("[Deleted video]", ignoreCase = true)) continue

                val uploader = item.optString("uploader", "").ifBlank { item.optString("channel", "") }.ifBlank { playlistUploader }.replace(" - Topic", "").trim()
                val duration = item.optDouble("duration", 0.0).toLong().coerceAtLeast(0L)
                val entryUrl = item.optString("url", "").ifBlank { item.optString("webpage_url", "") }.trim()

                entries.add(
                    FlatPlaylistEntry(
                        id = id.ifBlank { entryUrl },
                        title = entryTitle,
                        uploader = uploader.ifBlank { "Artista" },
                        durationSeconds = duration,
                        thumbnailUrl = if (id.length == 11) "https://i.ytimg.com/vi/$id/hqdefault.jpg" else "",
                        url = entryUrl
                    )
                )
            }

            Result.success(
                FlatPlaylistDump(
                    title = playlistTitle,
                    uploader = playlistUploader,
                    thumbnailUrl = "",
                    entries = entries
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
