package com.example.core.data.importer

import android.content.Context
import com.example.core.data.streaming.StreamingHttpDataSourceConfig
import com.example.core.model.DownloadProgress
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
        return ytdlpFile.exists() && ytdlpFile.length() > YTDLP_MIN_PACKAGE_BYTES && getPythonExecutable(context) != null
    }

    fun getPythonExecutable(context: Context): File? = YtDlpEnvironmentSetup.getPythonExecutable(context)

    fun getQuickJsExecutable(context: Context): File? = YtDlpEnvironmentSetup.getQuickJsExecutable(context)

    /**
     * Estado honesto del motor JS (CLI vía `--js-runtimes`, nunca JNI).
     * Distingue binario ausente, presente sin bit de ejecución y archivo demasiado
     * pequeño (stub), en lugar del anterior "activo" basado solo en existencia.
     */
    fun getQuickJsStatusSummary(context: Context): String {
        return when (YtDlpEnvironmentSetup.quickJsState(context)) {
            YtDlpEnvironmentSetup.QuickJsState.OK ->
                "QuickJS C99 activo (JS ejecutable verificado)"
            YtDlpEnvironmentSetup.QuickJsState.PRESENT_NOT_EXECUTABLE ->
                "QuickJS presente pero no ejecutable (sin motor JS)"
            YtDlpEnvironmentSetup.QuickJsState.TOO_SMALL ->
                "QuickJS inválido (archivo demasiado pequeño, sin motor JS)"
            YtDlpEnvironmentSetup.QuickJsState.MISSING ->
                "QuickJS no disponible (respaldo sin motor JS)"
        }
    }

    /** Self-test real de ejecución JS (`qjs -e`), para diagnóstico y Ajustes. */
    fun runQuickJsSelfTest(context: Context) =
        YtDlpEnvironmentSetup.runQuickJsSelfTest(context)

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
        onProgressUpdate: (DownloadProgress) -> Unit = {},
        audioQuality: com.example.core.model.StreamingAudioQuality =
            com.example.core.model.StreamingAudioQuality.AUTO_HIFI
    ): Boolean {
        return YtDlpStreamDownloader.downloadToFile(
            context = context,
            url = url,
            targetFile = targetFile,
            isAudioOnly = isAudioOnly,
            onProgressUpdate = onProgressUpdate,
            audioQuality = audioQuality
        )
    }

    /**
     * Resuelve los flujos de audio y video de YouTube utilizando yt-dlp y Python localmente.
     * @param audioQuality preferencia Opus/M4A elegida en Ajustes; por defecto AUTO_HIFI.
     */
    suspend fun resolveStream(
        context: Context,
        url: String,
        audioQuality: com.example.core.model.StreamingAudioQuality =
            com.example.core.model.StreamingAudioQuality.AUTO_HIFI
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
        val jsRuntimeArgs = YtDlpEnvironmentSetup.buildJsRuntimeArgs(context)

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
            addAll(jsRuntimeArgs)
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

        AuraDebugManager.logInfo(TAG, "Ejecutando yt-dlp con Python nativo para resolver URL: $cleanUrl")

        val procResult = runYtDlpProcess(
            cmd = cmdList,
            workDir = File(nativeDir),
            env = ytdlpProcessEnv(context, pythonEnvDir, File(nativeDir)),
            timeoutSeconds = 20,
            tag = TAG
        )
        val exitCode = procResult.exitCode
        val jsonOutput = procResult.stdout
        val errorLog = procResult.stderr

        if (exitCode != 0 || jsonOutput.isBlank()) {
            if (YtDlpEnvironmentSetup.containsJsFailureHints(errorLog)) {
                AuraDebugManager.logWarning(TAG, "yt-dlp falló con pistas de motor JS ausente (n-sig/cipher/throttling). QuickJS: ${getQuickJsStatusSummary(context)}. Error: ${errorLog.takeLast(300)}")
            } else {
                AuraDebugManager.logWarning(TAG, "yt-dlp terminó con código $exitCode. Error: ${errorLog.takeLast(300)}")
            }
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

            // v0.2.1-beta.1f: si el teléfono no tiene decodificador Opus, priorizar M4A aunque se pida AUTO.
            val opusOk = AudioDownloadFormatResolver.supportsOpus(context)
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
                        val isOpus = ext == "webm" || acodec.contains("opus")
                        val isM4a = ext == "m4a" || acodec.contains("mp4a")
                        // Sin Opus en el SoC, los flujos Opus se penalizan para no descargar algo irreproducible.
                        val opusBonus = if (opusOk) 1.0 else -1000000.0
                        val score = when (audioQuality) {
                            com.example.core.model.StreamingAudioQuality.AHORRO_OPUS_48K ->
                                (if (isOpus) 100000.0 * opusBonus else 0.0) - abr
                            com.example.core.model.StreamingAudioQuality.ALTA_OPUS_160K ->
                                abr + (if (isOpus) 50000.0 * opusBonus else 0.0) + (if (!opusOk && isM4a) 60000.0 else 0.0)
                            com.example.core.model.StreamingAudioQuality.ESTANDAR_M4A_128K ->
                                abr + (if (isM4a) 50000.0 else 0.0)
                            com.example.core.model.StreamingAudioQuality.AUTO_HIFI ->
                                if (opusOk) abr + (if (isOpus) 20000.0 else 0.0)
                                else abr + (if (isM4a) 40000.0 else -500000.0)
                        }
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

            if (directAudioUrl == null && directVideoUrl == null) {
                AuraDebugManager.logWarning(TAG, "yt-dlp no devolvió ningún flujo directo reproducible para: $cleanUrl")
                return@withContext Result.failure(
                    IllegalStateException("yt-dlp no encontró flujos de audio/video reproducibles para este enlace.")
                )
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
            val pythonEnvDir = File(context.filesDir, "env/python")

            ensureTlsCertificates(pythonEnvDir, forceRebuild = false)
            val tmpDir = File(context.cacheDir, "ytdlp_tmp").apply { mkdirs() }
            // Vía única: solo se añade --js-runtimes si hay binario ejecutable real.
            // Se elimina el fallback literal "quickjs" (buscaba un binario inexistente en PATH).
            val jsRuntimeArgs = YtDlpEnvironmentSetup.buildJsRuntimeArgs(context)

            val normalizedInput = if (trimmedInput.startsWith("http", ignoreCase = true)) {
                normalizeYouTubeMusicUrlForYtDlp(trimmedInput)
            } else {
                trimmedInput
            }

            val extractorArgs = buildYouTubeExtractorArgs(context)
            val activeCookies = YtDlpCookieManager.getActiveCookiesFileIfEnabled(context)

            val command = mutableListOf(
                activePython.absolutePath,
                // Sin "-I": ver nota en YtDlpStreamDownloader (rompe PYTHONPATH/zipimport).
                ytdlpBin.absolutePath,
                "--no-config",
                "--dump-single-json",
                "--flat-playlist",
                "--no-warnings",
                "--no-progress",
                "--socket-timeout", "14",
                "--extractor-args", extractorArgs
            ).apply {
                addAll(jsRuntimeArgs)
                if (activeCookies != null && activeCookies.exists()) {
                    add("--cookies")
                    add(activeCookies.absolutePath)
                }
                add("--")
                add(normalizedInput)
            }

            val procResult = runYtDlpProcess(
                cmd = command,
                workDir = File(nativeDir),
                env = ytdlpProcessEnv(context, pythonEnvDir, File(nativeDir), tmpDir),
                timeoutSeconds = 60,
                tag = TAG
            )
            val stdout = procResult.stdout
            val stderr = procResult.stderr
            val code = procResult.exitCode

            if (code != 0 || stdout.isBlank()) {
                if (YtDlpEnvironmentSetup.containsJsFailureHints(stderr)) {
                    AuraDebugManager.logWarning(TAG, "flat-playlist falló con pistas de motor JS ausente. QuickJS: ${getQuickJsStatusSummary(context)}")
                }
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
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }
}
