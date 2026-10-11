package com.example.data.importer

import android.content.Context
import android.util.Base64
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.security.KeyStore
import java.util.zip.ZipFile
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Motor nativo de extracción directa con yt-dlp y entorno Python optimizado para Aura Music.
 *
 * Arquitectura y Principio de Operación:
 * 1. Utiliza el runtime nativo de CPython ('libpython.so') empaquetado en el APK para cada arquitectura
 *    (arm64-v8a, armeabi-v7a, x86_64, x86) e instalado por Android en [nativeLibraryDir] con permisos de ejecución.
 * 2. Desempaqueta atómicamente en segundo plano el entorno estándar de Python con certificados SSL ([libpython.zip.so])
 *    hacia [context.filesDir/env/python].
 * 3. Ejecuta el script oficial de yt-dlp ([context.filesDir/bin/yt-dlp]), con una copia base empaquetada
 *    en assets para operatividad inmediata y sincronización en caliente (OTA) mediante [YtDlpAutoUpdater].
 * 4. Incorpora QuickJS nativo ('libqjs.so') para resolución instantánea de firmas JS (n-sig) y enlace directo
 *    con el binario puro de FFmpeg ('libffmpeg.so').
 * 5. Soporta Android 14+ (API 34+) consolidando automáticamente los certificados CA de Conscrypt APEX y KeyStore
 *    hacia 'usr/etc/tls/cert.pem' para que OpenSSL no falle por verificación de emisor local.
 */
object YtDlpNativeEngine {

    private const val TAG = "YtDlpNativeEngine"

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
                append("direct_symlink_v5_")
                if (pythonZip.exists()) append("${pythonZip.length()}_${pythonZip.lastModified()}_")
                append("mods:${pymodFiles.size}")
            }

            if (pythonZip.exists() || pymodFiles.isNotEmpty()) {
                val needSetup = !pythonEnvDir.exists() ||
                        !versionMarker.exists() ||
                        versionMarker.readText().trim() != currentSignature

                if (needSetup) {
                    AuraDebugManager.logInfo(TAG, "Vinculando módulos C nativos de Python desde nativeLibraryDir mediante symlinks de 0 bytes (cero triplicación en disco)...")
                    if (pythonEnvDir.exists()) {
                        pythonEnvDir.deleteRecursively()
                    }
                    pythonEnvDir.mkdirs()

                    // Enlazar módulos C nativos (libpymod_*.so) desde nativeLibraryDir hacia modules/*.so sin duplicar bytes
                    linkNativeModulesFromLibDir(nativeLibDir, pythonZip, pythonEnvDir)
                    versionMarker.writeText(currentSignature)
                    AuraDebugManager.logInfo(TAG, "Módulos C de Python vinculados desde nativeLibraryDir y stdlib leído directo vía zipimport.")
                } else {
                    // Limpiar cualquier stdlib.zip o carpeta stdlib residual de versiones anteriores
                    File(pythonEnvDir, "stdlib.zip").takeIf { it.exists() }?.delete()
                    File(pythonEnvDir, "stdlib").takeIf { it.exists() }?.deleteRecursively()
                }
            }

            // Evitar duplicar yt-dlp en filesDir/bin/yt-dlp si ya existe nativeLibraryDir/libytdlp.zip.so
            // y no se ha descargado una actualización OTA más reciente.
            val nativeYtdlpSo = File(nativeLibDir, "libytdlp.zip.so").takeIf { it.exists() }
                ?: File(nativeLibDir, "libytdlp.so")
            val otaYtdlpFile = File(context.filesDir, "bin/yt-dlp")
            val versionFile = File(context.filesDir, "bin/ytdlp_version.txt")

            if (nativeYtdlpSo.exists() && nativeYtdlpSo.length() > 50_000L) {
                // Si la copia en files/bin/yt-dlp era simplemente la copia base duplicada (no OTA), la eliminamos para ahorrar ~3MB
                val recordedVer = if (versionFile.exists()) runCatching { versionFile.readText().trim() }.getOrDefault("") else ""
                if (otaYtdlpFile.exists() && (recordedVer.isEmpty() || recordedVer.contains("base", ignoreCase = true))) {
                    runCatching { otaYtdlpFile.delete() }
                }
                if (!versionFile.exists()) {
                    versionFile.parentFile?.mkdirs()
                    versionFile.writeText("v.base (nativo)")
                }
            } else if (!otaYtdlpFile.exists() || otaYtdlpFile.length() < 1000L) {
                // Respaldo exclusivo si algún entorno no empaquetó libytdlp.so en nativeLibraryDir
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

            // Limpiar copias duplicadas antiguas de python/python3 en filesDir/bin (se usa directamente nativeLibraryDir/libpython.so)
            val binDir = File(context.filesDir, "bin")
            if (binDir.exists()) {
                listOf("python3", "python").forEach { aliasName ->
                    val aliasFile = File(binDir, aliasName)
                    if (aliasFile.exists()) {
                        try {
                            aliasFile.delete()
                        } catch (_: Exception) {}
                    }
                }
            }

            // Asegurar certificados TLS consolidados para OpenSSL en Android 14+
            ensureTlsCertificates(pythonEnvDir)

            isInitialized = true
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Fallo al inicializar entorno Python yt-dlp: ${e.message}")
        }
    }

    /**
     * Comprueba si el ejecutable de Python y el script de yt-dlp están disponibles.
     */
    fun isAvailable(context: Context): Boolean {
        val ytdlpFile = YtDlpAutoUpdater.getYtDlpFile(context)
        return ytdlpFile.exists() && ytdlpFile.length() > 50_000L && getPythonExecutable(context) != null
    }

    /**
     * Localiza el binario ejecutable de Python disponible para el proceso de la app.
     */
    fun getPythonExecutable(context: Context): File? {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val pythonInNative = File(nativeDir, "libpython.so")
        // En Android 10+, los ejecutables nativos empaquetados en APK residen en nativeLibraryDir
        // y deben ser binarios PIE con longitud mayor a un stub de texto (> 1KB)
        if (pythonInNative.exists() && pythonInNative.length() > 1000L && pythonInNative.canExecute()) {
            return pythonInNative
        }

        val python3InNative = File(nativeDir, "libpython3.so")
        if (python3InNative.exists() && python3InNative.length() > 1000L && python3InNative.canExecute()) {
            return python3InNative
        }

        val binDir = File(context.filesDir, "bin")
        val candidateInBin = File(binDir, "python3")
        if (candidateInBin.exists() && candidateInBin.canExecute()) return candidateInBin

        val candidateAlt = File(binDir, "python")
        if (candidateAlt.exists() && candidateAlt.canExecute()) return candidateAlt

        return null
    }

    /**
     * Localiza el binario ejecutable de QuickJS para firma de tokens JS si existe.
     */
    fun getQuickJsExecutable(context: Context): File? {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val qjsInNative = File(nativeDir, "libqjs.so")
        if (qjsInNative.exists() && qjsInNative.canExecute()) return qjsInNative
        return null
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
        // Normalizar enlaces de YouTube Music (music.youtube.com) para compatibilidad 100% con yt-dlp
        val cleanUrl = normalizeYouTubeMusicUrlForYtDlp(rawTrimmedUrl)

        val pythonEnvDir = File(context.filesDir, "env/python")
        ensureTlsCertificates(pythonEnvDir)
        val localCert = File(pythonEnvDir, "usr/etc/tls/cert.pem")

        // IMPORTANTE: yt-dlp se ejecuta 100% limpio sin cookies (--cookies), dejando que negocie sus
        // propios clientes internos (tv_embedded, android_vr, tv, mweb) y descifre n-sig con QuickJS.

        fun extractCleanErrorSummary(rawErr: CharSequence): String {
            val lines = rawErr.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
            val errorLine = lines.lastOrNull { it.contains("ERROR:", ignoreCase = true) }
                ?: lines.lastOrNull()
                ?: ""
            return errorLine.take(360)
        }

        fun buildYtDlpCommand(
            extractorClientProfile: String?,
            checkFormats: Boolean
        ): MutableList<String> {
            return mutableListOf<String>().apply {
                add(activePython.absolutePath)
                add(ytdlpFile.absolutePath)
                add("--dump-single-json")
                add("--no-warnings")
                add("--no-playlist")
                if (checkFormats) {
                    add("--check-formats")
                } else {
                    add("--no-check-formats")
                }
                if (!extractorClientProfile.isNullOrBlank()) {
                    add("--extractor-args")
                    add(extractorClientProfile)
                }
                add("--format")
                add("bestvideo[height<=480][ext=mp4][vcodec^=avc1][protocol^=http]+bestaudio[ext=m4a][protocol^=http]/bestvideo[height<=480][ext=mp4][protocol^=http]+bestaudio[protocol^=http]/best[height<=480][ext=mp4][protocol^=http]/best[height<=480]/best")
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
        }

        val nativeDir = context.applicationInfo.nativeLibraryDir
        val directPythonZipSo = File(nativeDir, "libpython.zip.so")
        val stdlibZip = File(pythonEnvDir, "stdlib.zip")
        val stdlibDir = File(pythonEnvDir, "stdlib")
        val modulesDir = File(pythonEnvDir, "modules")
        val ffmpegEnvDir = File(context.filesDir, "env/ffmpeg")

        fun applyPythonEnvironment(env: MutableMap<String, String>) {
            env["PYTHONHOME"] = pythonEnvDir.absolutePath
            env["PYTHONPATH"] = "${directPythonZipSo.absolutePath}:${stdlibZip.absolutePath}:${stdlibDir.absolutePath}:${modulesDir.absolutePath}:${pythonEnvDir.absolutePath}:${pythonEnvDir.absolutePath}/usr/lib/python3.11:${context.filesDir.absolutePath}/bin"
            env["PYTHONNOUSERSITE"] = "1"
            env["HOME"] = pythonEnvDir.absolutePath
            env["LD_LIBRARY_PATH"] = "$nativeDir:${modulesDir.absolutePath}:${pythonEnvDir.absolutePath}/usr/lib:${ffmpegEnvDir.absolutePath}/usr/lib"
            if (localCert.exists() && localCert.length() > 0) {
                env["SSL_CERT_FILE"] = localCert.absolutePath
                env["CURL_CA_BUNDLE"] = localCert.absolutePath
                env["REQUESTS_CA_BUNDLE"] = localCert.absolutePath
            }
            val apexCacerts = File("/apex/com.android.conscrypt/cacerts")
            val systemCacerts = File("/system/etc/security/cacerts")
            val certDir = when {
                apexCacerts.exists() && apexCacerts.isDirectory && (apexCacerts.list()?.isNotEmpty() == true) -> apexCacerts
                systemCacerts.exists() && systemCacerts.isDirectory && (systemCacerts.list()?.isNotEmpty() == true) -> systemCacerts
                else -> null
            }
            if (certDir != null) {
                env["SSL_CERT_DIR"] = certDir.absolutePath
            }
            env["PATH"] = "$nativeDir:${context.filesDir.absolutePath}/bin:${System.getenv("PATH") ?: ""}"
            env["TMPDIR"] = context.cacheDir.absolutePath
        }

        fun executeYtDlpJsonAttempt(cmdList: List<String>, outBuf: StringBuilder, errBuf: StringBuilder): Int {
            outBuf.clear()
            errBuf.clear()
            val pb = ProcessBuilder(cmdList)
                .directory(File(nativeDir))
                .redirectErrorStream(false)
            applyPythonEnvironment(pb.environment())

            val proc = pb.start()
            val stdoutThread = Thread {
                try {
                    BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            outBuf.append(line)
                        }
                    }
                } catch (_: Exception) {}
            }
            stdoutThread.start()

            val stderrThread = Thread {
                try {
                    BufferedReader(InputStreamReader(proc.errorStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            errBuf.append(line).append("\n")
                        }
                    }
                } catch (_: Exception) {}
            }
            stderrThread.start()

            val code = proc.waitFor()
            stdoutThread.join()
            stderrThread.join()
            return code
        }

        fun parseAndValidateJsonOutput(rawJson: String): OnlineVideoAudioImporter.ResolvedMediaInfo? {
            if (rawJson.isBlank()) return null
            val rootJson = JSONObject(rawJson)
            val title = rootJson.optString("title", "Audio de YouTube").trim()
            val uploader = rootJson.optString("uploader", "").ifBlank {
                rootJson.optString("channel", "YouTube")
            }.trim()
            val duration = rootJson.optLong("duration", 0L)
            val thumbnail = rootJson.optString("thumbnail", "")

            var directAudioUrl: String? = null
            var directVideoUrl: String? = null
            val audioHeaders = mutableMapOf<String, String>()
            val videoHeaders = mutableMapOf<String, String>()

            fun parseHeadersMap(fmtObj: JSONObject): Map<String, String> {
                val map = mutableMapOf<String, String>()
                val headersObj = fmtObj.optJSONObject("http_headers") ?: rootJson.optJSONObject("http_headers")
                if (headersObj != null) {
                    val keys = headersObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = headersObj.optString(k, "")
                        if (v.isNotBlank()) {
                            map[k] = v
                        }
                    }
                }
                return map
            }

            fun isDirectHttpStream(fmtUrl: String, protocol: String, ext: String): Boolean {
                if (fmtUrl.isBlank()) return false
                val lowerUrl = fmtUrl.lowercase()
                val lowerProto = protocol.lowercase()
                if (lowerProto.contains("m3u8") || lowerProto.contains("dash") || lowerProto.contains("f4m") || lowerProto.contains("ism")) {
                    return false
                }
                if (lowerUrl.contains(".m3u8") || lowerUrl.contains(".mpd") || lowerUrl.contains("manifest.googlevideo.com")) {
                    return false
                }
                if (ext.equals("mhtml", ignoreCase = true)) return false
                return true
            }

            // 1. Revisar formatos seleccionados explícitamente por yt-dlp (requested_formats)
            val requestedFormats = rootJson.optJSONArray("requested_formats")
            if (requestedFormats != null) {
                for (i in 0 until requestedFormats.length()) {
                    val fmt = requestedFormats.getJSONObject(i)
                    val vcodec = fmt.optString("vcodec", "none")
                    val acodec = fmt.optString("acodec", "none")
                    val protocol = fmt.optString("protocol", "https")
                    val ext = fmt.optString("ext", "")
                    val fmtUrl = fmt.optString("url", "")
                    if (isDirectHttpStream(fmtUrl, protocol, ext)) {
                        val fmtHeaders = parseHeadersMap(fmt)
                        if (vcodec != "none" && directVideoUrl == null) {
                            directVideoUrl = fmtUrl
                            videoHeaders.clear()
                            videoHeaders.putAll(fmtHeaders)
                        }
                        if (acodec != "none" && directAudioUrl == null) {
                            directAudioUrl = fmtUrl
                            audioHeaders.clear()
                            audioHeaders.putAll(fmtHeaders)
                        }
                    }
                }
            }

            // 2. Inspeccionar la lista completa de formatos disponibles para elegir el mejor audio m4a y video 480p avc1 alcanzables (HTTP 206)
            val formats = rootJson.optJSONArray("formats")
            if (formats != null) {
                data class AudioCandidate(val url: String, val headers: Map<String, String>, val score: Double)
                data class VideoCandidate(val url: String, val headers: Map<String, String>, val score: Int)

                val audioCandidates = mutableListOf<AudioCandidate>()
                val videoCandidates = mutableListOf<VideoCandidate>()

                for (i in 0 until formats.length()) {
                    val fmt = formats.getJSONObject(i)
                    val fmtUrl = fmt.optString("url", "")
                    val protocol = fmt.optString("protocol", "https")
                    val ext = fmt.optString("ext", "").lowercase()
                    if (!isDirectHttpStream(fmtUrl, protocol, ext)) continue

                    val vcodec = fmt.optString("vcodec", "none").lowercase()
                    val acodec = fmt.optString("acodec", "none").lowercase()
                    val formatNote = fmt.optString("format_note", "").lowercase()
                    val formatId = fmt.optString("format_id", "").lowercase()
                    val height = fmt.optInt("height", 0)
                    val abr = fmt.optDouble("abr", 0.0).takeIf { it > 0 } ?: fmt.optDouble("tbr", 0.0)

                    if (acodec != "none" && vcodec == "none") {
                        val codecBonus = if (ext == "m4a" || acodec.contains("mp4a")) 500.0 else 0.0
                        val drcPenalty = if (formatId.contains("drc") || formatNote.contains("drc")) -150.0 else 0.0
                        val audioScore = abr + codecBonus + drcPenalty
                        audioCandidates.add(AudioCandidate(fmtUrl, parseHeadersMap(fmt), audioScore))
                    }

                    if (vcodec != "none" && height > 0) {
                        val targetHeight = 480
                        val diff = kotlin.math.abs(height - targetHeight)
                        var score = 10000 - (diff * 15)
                        if (height == 480) score += 5000
                        if (ext == "mp4") score += 3000
                        if (vcodec.startsWith("avc1") || vcodec.contains("h264")) {
                            score += 4000
                        } else if (vcodec.startsWith("av01") || vcodec.contains("av1")) {
                            score -= 6000
                        } else if (vcodec.contains("vp9")) {
                            score -= 2000
                        }
                        videoCandidates.add(VideoCandidate(fmtUrl, parseHeadersMap(fmt), score))
                    }
                }

                // Probar en orden descendente de calidad hasta encontrar un stream de audio que responda HTTP 200/206 real (no 403)
                for (cand in audioCandidates.sortedByDescending { it.score }.take(4)) {
                    val ua = cand.headers["User-Agent"] ?: cand.headers["user-agent"]
                        ?: "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)"
                    if (InnerTubeClient.isStreamUrlReachable(cand.url, ua, cand.headers)) {
                        directAudioUrl = cand.url
                        audioHeaders.clear()
                        audioHeaders.putAll(cand.headers)
                        break
                    }
                }

                for (vcand in videoCandidates.sortedByDescending { it.score }.take(3)) {
                    val ua = vcand.headers["User-Agent"] ?: vcand.headers["user-agent"]
                        ?: audioHeaders["User-Agent"]
                        ?: "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)"
                    if (InnerTubeClient.isStreamUrlReachable(vcand.url, ua, vcand.headers)) {
                        directVideoUrl = vcand.url
                        videoHeaders.clear()
                        videoHeaders.putAll(vcand.headers)
                        break
                    }
                }
            }

            if (directAudioUrl == null) {
                val rootUrl = rootJson.optString("url", "")
                val rootProto = rootJson.optString("protocol", "https")
                val rootExt = rootJson.optString("ext", "")
                if (isDirectHttpStream(rootUrl, rootProto, rootExt)) {
                    val rHeaders = parseHeadersMap(rootJson)
                    val ua = rHeaders["User-Agent"] ?: rHeaders["user-agent"] ?: ""
                    if (InnerTubeClient.isStreamUrlReachable(rootUrl, ua, rHeaders)) {
                        directAudioUrl = rootUrl
                        audioHeaders.clear()
                        audioHeaders.putAll(rHeaders)
                    }
                }
            }

            // Si aún no hay directAudioUrl verificada con 206, revisar formatos combinados (ej. itag 18)
            if (directAudioUrl == null && formats != null) {
                for (i in 0 until formats.length()) {
                    val fmt = formats.getJSONObject(i)
                    val fmtUrl = fmt.optString("url", "")
                    val protocol = fmt.optString("protocol", "https")
                    val ext = fmt.optString("ext", "").lowercase()
                    val vcodec = fmt.optString("vcodec", "none").lowercase()
                    val acodec = fmt.optString("acodec", "none").lowercase()
                    if (isDirectHttpStream(fmtUrl, protocol, ext) && vcodec != "none" && acodec != "none") {
                        val fmtHeaders = parseHeadersMap(fmt)
                        val ua = fmtHeaders["User-Agent"] ?: fmtHeaders["user-agent"] ?: ""
                        if (InnerTubeClient.isStreamUrlReachable(fmtUrl, ua, fmtHeaders)) {
                            directAudioUrl = fmtUrl
                            audioHeaders.clear()
                            audioHeaders.putAll(fmtHeaders)
                            if (directVideoUrl == null) {
                                directVideoUrl = fmtUrl
                                videoHeaders.clear()
                                videoHeaders.putAll(fmtHeaders)
                            }
                            break
                        }
                    }
                }
            }

            if (directAudioUrl.isNullOrBlank()) {
                return null
            }

            val primaryHeaders = if (audioHeaders.isNotEmpty()) audioHeaders else videoHeaders
            val finalVideoUrl = directVideoUrl ?: directAudioUrl ?: url
            return OnlineVideoAudioImporter.ResolvedMediaInfo(
                originalUrl = url,
                suggestedTitle = title,
                suggestedArtist = uploader,
                videoUrl = finalVideoUrl,
                audioUrl = directAudioUrl,
                coverUrl = thumbnail.ifBlank { null },
                durationSeconds = duration,
                httpHeaders = primaryHeaders
            )
        }

        val jsonOutput = StringBuilder()
        val errorLog = StringBuilder()

        try {
            // Cascada de perfiles de extracción limpios sin cookies ni visitor_data externo:
            // 1. tv_embedded,android_vr,tv con player_skip=webpage (descarga configs/JS para descifrar n-sig con QuickJS)
            // 2. android_vr,tv puro con --check-formats
            // 3. mweb,tv_embedded completo con QuickJS
            val candidateProfiles = listOf(
                "youtube:player_client=tv_embedded,android_vr,tv;player_skip=webpage",
                "youtube:player_client=android_vr,tv;player_skip=webpage,configs",
                "youtube:player_client=mweb,tv_embedded"
            )

            for ((idx, profile) in candidateProfiles.withIndex()) {
                AuraDebugManager.logInfo(TAG, "Ejecutando yt-dlp intento #${idx + 1} (perfil: $profile) para resolver URL: $url")
                val cmdList = buildYtDlpCommand(
                    extractorClientProfile = profile,
                    checkFormats = (idx > 0)
                )
                var exitCode = executeYtDlpJsonAttempt(cmdList, jsonOutput, errorLog)
                val errString = errorLog.toString()

                // Auto-recuperación si hubo fallo de certificados TLS
                if ((exitCode != 0 || jsonOutput.isBlank()) &&
                    (errString.contains("CERTIFICATE_VERIFY_FAILED", ignoreCase = true) ||
                        errString.contains("certificate verify failed", ignoreCase = true))
                ) {
                    ensureTlsCertificates(pythonEnvDir, forceRebuild = true)
                    exitCode = executeYtDlpJsonAttempt(cmdList, jsonOutput, errorLog)
                }

                if (exitCode == 0 && jsonOutput.isNotBlank()) {
                    val validatedMedia = parseAndValidateJsonOutput(jsonOutput.toString())
                    if (validatedMedia != null) {
                        AuraDebugManager.logInfo(TAG, "¡yt-dlp resolvió y verificó HTTP 206 exitosamente: '${validatedMedia.suggestedTitle}' (${validatedMedia.durationSeconds} s)!")
                        return@withContext Result.success(validatedMedia)
                    } else {
                        AuraDebugManager.logWarning(TAG, "yt-dlp intento #${idx + 1} devolvió formatos que dieron HTTP 403 en sonda real; pasando al siguiente perfil...")
                    }
                } else {
                    AuraDebugManager.logWarning(TAG, "yt-dlp intento #${idx + 1} terminó con código $exitCode (${extractCleanErrorSummary(errorLog)})")
                }
            }

            // Respaldo inmediato con InnerTubeClient verificado con HTTP 206 si los perfiles de yt-dlp fueron bloqueados con 403
            val videoId = WebStreamExtractor.extractVideoId(cleanUrl)
            if (!videoId.isNullOrBlank()) {
                val innerTubeFallback = InnerTubeClient.resolve(videoId, url)
                if (innerTubeFallback != null) {
                    AuraDebugManager.logInfo(TAG, "Stream recuperado y verificado (HTTP 206) mediante InnerTubeClient tras bloqueo en yt-dlp.")
                    return@withContext Result.success(innerTubeFallback)
                }
            }

            val fullCleanError = extractCleanErrorSummary(errorLog)
            AuraDebugManager.logWarning(TAG, "yt-dlp no pudo obtener un stream libre de HTTP 403. Último detalle: $fullCleanError")
            Result.failure(
                Exception("yt-dlp no pudo obtener un stream válido. Detalle: $fullCleanError")
            )
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Fallo al ejecutar proceso yt-dlp: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Descarga directamente el archivo de audio (y opcionalmente el Video Canvas en 480p) a disco
     * ejecutando `yt-dlp` como descargador nativo con `--check-formats`, resolución de `n-sig` mediante QuickJS
     * y 100% libre de cookies para erradicar bloqueos de sesión y `HTTP Error 403: Forbidden`.
     */
    suspend fun downloadMediaDirectlyToFiles(
        context: Context,
        url: String,
        targetAudioFile: File,
        targetVideoFile: File?,
        onProgress: (com.example.model.DownloadProgress) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (YtDlpAutoUpdater.isYtDlpTemporarilyBlocked()) return@withContext false

        init(context)
        FFmpegNativeEngine.init(context)

        val ytdlpFile = YtDlpAutoUpdater.getYtDlpFile(context)
        val activePython = getPythonExecutable(context) ?: return@withContext false
        if (!ytdlpFile.exists() || ytdlpFile.length() < 10_000L) return@withContext false

        val cleanUrl = normalizeYouTubeMusicUrlForYtDlp(url.trim())
        if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext false
        }

        val pythonEnvDir = File(context.filesDir, "env/python")
        ensureTlsCertificates(pythonEnvDir)
        val localCert = File(pythonEnvDir, "usr/etc/tls/cert.pem")
        val ffmpegBin = FFmpegNativeEngine.getBinaryFile(context)
        val qjsBin = getQuickJsExecutable(context)

        fun configurePythonEnv(pb: ProcessBuilder) {
            val nativeDir = context.applicationInfo.nativeLibraryDir
            val directPythonZipSo = File(nativeDir, "libpython.zip.so")
            val stdlibZip = File(pythonEnvDir, "stdlib.zip")
            val stdlibDir = File(pythonEnvDir, "stdlib")
            val modulesDir = File(pythonEnvDir, "modules")
            val ffmpegEnvDir = File(context.filesDir, "env/ffmpeg")

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
                val apexCacerts = File("/apex/com.android.conscrypt/cacerts")
                val systemCacerts = File("/system/etc/security/cacerts")
                val certDir = when {
                    apexCacerts.exists() && apexCacerts.isDirectory && (apexCacerts.list()?.isNotEmpty() == true) -> apexCacerts
                    systemCacerts.exists() && systemCacerts.isDirectory && (systemCacerts.list()?.isNotEmpty() == true) -> systemCacerts
                    else -> null
                }
                if (certDir != null) {
                    this["SSL_CERT_DIR"] = certDir.absolutePath
                }
                this["PATH"] = "$nativeDir:${context.filesDir.absolutePath}/bin:${System.getenv("PATH") ?: ""}"
                this["TMPDIR"] = context.cacheDir.absolutePath
            }
        }

        val progressRegex = Regex("""\[download\]\s+(\d+(?:\.\d+)?)%(?:\s+of\s+~?\s*([\d.]+)([KMG]i?B))?(?:\s+at\s+([\d.]+)([KMG]i?B)/s)?""", RegexOption.IGNORE_CASE)

        fun parseUnitToBytes(amount: Double, unit: String): Long {
            val u = unit.uppercase()
            val mult = when {
                u.startsWith("G") -> 1024L * 1024L * 1024L
                u.startsWith("M") -> 1024L * 1024L
                u.startsWith("K") -> 1024L
                else -> 1L
            }
            return (amount * mult).toLong()
        }

        fun runDirectYtDlpDownload(
            formatSelector: String,
            outputFile: File,
            phaseLabel: String,
            extractorClientProfile: String?
        ): Boolean {
            return try {
                outputFile.parentFile?.mkdirs()
                if (outputFile.exists()) outputFile.delete()
                File("${outputFile.absolutePath}.part").takeIf { it.exists() }?.delete()

                val cmd = mutableListOf<String>().apply {
                    add(activePython.absolutePath)
                    add(ytdlpFile.absolutePath)
                    add("--no-playlist")
                    add("--no-warnings")
                    add("--no-part")
                    add("--newline")
                    // Usar --check-formats para que yt-dlp pruebe internamente que el formato no devuelva HTTP 403 antes de elegirlo
                    add("--check-formats")
                    add("--http-chunk-size")
                    add("10M")
                    add("--retries")
                    add("3")
                    add("--fragment-retries")
                    add("3")
                    if (!extractorClientProfile.isNullOrBlank()) {
                        add("--extractor-args")
                        add(extractorClientProfile)
                    }
                    add("-f")
                    add(formatSelector)
                    if (qjsBin != null && qjsBin.exists()) {
                        add("--js-runtimes")
                        add("quickjs:${qjsBin.absolutePath}")
                    }
                    if (ffmpegBin != null && ffmpegBin.exists()) {
                        add("--ffmpeg-location")
                        add(ffmpegBin.absolutePath)
                    }
                    add("-o")
                    add(outputFile.absolutePath)
                    add("--")
                    add(cleanUrl)
                }

                val pb = ProcessBuilder(cmd)
                    .directory(File(context.applicationInfo.nativeLibraryDir))
                    .redirectErrorStream(true)
                configurePythonEnv(pb)

                val proc = pb.start()
                val errTail = StringBuilder()

                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val ln = line ?: continue
                        val match = progressRegex.find(ln)
                        if (match != null) {
                            val pct = (match.groupValues[1].toFloatOrNull() ?: 0f) / 100f
                            val totalVal = match.groupValues[2].toDoubleOrNull() ?: 0.0
                            val totalUnit = match.groupValues[3]
                            val speedVal = match.groupValues[4].toDoubleOrNull() ?: 0.0
                            val speedUnit = match.groupValues[5]
                            val totalBytes = if (totalVal > 0.0) parseUnitToBytes(totalVal, totalUnit) else 0L
                            val speedBytes = if (speedVal > 0.0) parseUnitToBytes(speedVal, speedUnit) else 0L
                            val downloadedBytes = if (totalBytes > 0L) (totalBytes * pct).toLong() else outputFile.length()

                            onProgress(
                                com.example.model.DownloadProgress(
                                    isDownloading = true,
                                    phase = phaseLabel,
                                    bytesDownloaded = downloadedBytes,
                                    totalBytes = totalBytes,
                                    bytesPerSecond = speedBytes,
                                    progressFraction = pct.coerceIn(0f, 1f)
                                )
                            )
                        } else if (ln.contains("ERROR:", ignoreCase = true)) {
                            errTail.append(ln).append("\n")
                        }
                    }
                }

                val code = proc.waitFor()
                val ok = code == 0 && outputFile.exists() && outputFile.length() > 1024L
                if (!ok && errTail.isNotEmpty()) {
                    val cleanErr = errTail.toString().lines().lastOrNull { it.contains("ERROR:", ignoreCase = true) }
                        ?: errTail.takeLast(280).toString().trim()
                    AuraDebugManager.logWarning(TAG, "yt-dlp descarga directa ($phaseLabel | perfil=${extractorClientProfile ?: "default"}) código $code: $cleanErr")
                }
                ok
            } catch (e: Exception) {
                AuraDebugManager.logWarning(TAG, "Excepción en descarga directa yt-dlp ($phaseLabel): ${e.message}")
                false
            }
        }

        // Perfiles limpios sin cookies ni visitor_data ajeno para que yt-dlp negocie su propio token de sesión y descifre n-sig con QuickJS
        val audioSelector = "bestaudio[ext=m4a][protocol^=http]/bestaudio[protocol^=http]/best[height<=480][ext=mp4]/best"
        val downloadProfiles = listOf(
            "youtube:player_client=tv_embedded,android_vr,tv;player_skip=webpage",
            "youtube:player_client=mweb,tv_embedded",
            "youtube:player_client=android_vr,tv;player_skip=webpage,configs"
        )

        var audioDownloaded = false
        var successfulProfile: String? = null
        for (profile in downloadProfiles) {
            audioDownloaded = runDirectYtDlpDownload(
                formatSelector = audioSelector,
                outputFile = targetAudioFile,
                phaseLabel = "Descargando audio de alta fidelidad (yt-dlp)...",
                extractorClientProfile = profile
            )
            if (audioDownloaded && targetAudioFile.exists() && targetAudioFile.length() > 1024L) {
                successfulProfile = profile
                break
            }
        }

        if (!audioDownloaded || !targetAudioFile.exists() || targetAudioFile.length() <= 1024L) {
            return@withContext false
        }

        // 2. Si el usuario solicitó Video Canvas (targetVideoFile != null), descargar el video en 480p usando el perfil que tuvo éxito
        if (targetVideoFile != null) {
            val videoSelector = "bestvideo[height<=480][ext=mp4][vcodec^=avc1][protocol^=http]/bestvideo[height<=480][ext=mp4][protocol^=http]/best[height<=480][ext=mp4]/best[height<=480]"
            val activeVideoProfile = successfulProfile ?: downloadProfiles.first()
            var videoOk = runDirectYtDlpDownload(
                formatSelector = videoSelector,
                outputFile = targetVideoFile,
                phaseLabel = "Descargando Video Canvas 480p (yt-dlp)...",
                extractorClientProfile = activeVideoProfile
            )
            if (!videoOk) {
                for (altProfile in downloadProfiles.filter { it != activeVideoProfile }) {
                    videoOk = runDirectYtDlpDownload(
                        formatSelector = videoSelector,
                        outputFile = targetVideoFile,
                        phaseLabel = "Descargando Video Canvas 480p (yt-dlp)...",
                        extractorClientProfile = altProfile
                    )
                    if (videoOk) break
                }
            }
            if (!videoOk) {
                AuraDebugManager.logInfo(TAG, "Video Canvas 480p opcional no se descargó en pase directo; se continuará con audio + carátula.")
            }
        }

        AuraDebugManager.logInfo(TAG, "¡Descarga directa nativa por yt-dlp completada con éxito (${targetAudioFile.length() / 1024} KB)!")
        true
    }

    /**
     * Consolida los certificados TLS/CA de Android 14+ y KeyStore hacia 'usr/etc/tls/cert.pem'.
     *
     * En Android 14 (API 34+), el almacén de CA del sistema se trasladó a '/apex/com.android.conscrypt/cacerts/'.
     * Esta función consolida las CA del sistema en un bundle PEM compatible con OpenSSL/CPython para evitar
     * el error '[SSL: CERTIFICATE_VERIFY_FAILED] certificate verify failed: unable to get local issuer certificate'.
     */
    fun ensureTlsCertificates(pythonEnvDir: File, forceRebuild: Boolean = false) {
        val certFile = File(pythonEnvDir, "usr/etc/tls/cert.pem")
        if (!forceRebuild && certFile.exists() && certFile.length() > 5000L) {
            return
        }

        try {
            certFile.parentFile?.mkdirs()
            val pemBuilder = StringBuilder()

            // 1. Extraer certificados X.509 de TrustManagerFactory
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
                AuraDebugManager.logWarning(TAG, "Advertencia leyendo certificados de TrustManagerFactory: ${e.message}")
            }

            // 2. Extraer certificados en texto plano PEM desde /apex/com.android.conscrypt/cacerts y /system/etc/security/cacerts
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
                AuraDebugManager.logInfo(TAG, "Bundle TLS consolidado generado con éxito en ${certFile.absolutePath} (${certFile.length()} bytes)")
            }
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Fallo al generar bundle de certificados TLS: ${e.message}")
        }
    }

    /**
     * Vincula los módulos C nativos de Python desde [nativeLibraryDir] (`libpymod_<nombre>.so`)
     * hacia `files/env/python/modules/<nombre>.so` utilizando enlaces simbólicos (`Os.symlink`) de 0 bytes.
     * Evita que los módulos C estén triplicados en el teléfono (APK + nativeLibraryDir + filesDir).
     * Incluye respaldo para extraer de `libpython.zip.so` si algún entorno legado aún los empaquetara dentro del zip.
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
                    throw SecurityException("Violación de seguridad Path Traversal al vincular módulo Python: '$originalModuleName'")
                }
                if (targetLink.exists()) targetLink.delete()
                try {
                    android.system.Os.symlink(srcSo.absolutePath, targetLink.absolutePath)
                } catch (_: Throwable) {
                    // Respaldo seguro si el sistema de archivos bloqueara symlinks
                    srcSo.copyTo(targetLink, overwrite = true)
                    targetLink.setExecutable(true, false)
                }
            }
            return
        }

        // Respaldo de compatibilidad si algún zip legado aún trajera modules/*.so adentro
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
                    throw SecurityException("Violación de seguridad Zip Slip: '${entry.name}' intenta escapar de '${destDir.path}'")
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
     * Normaliza cualquier URL de YouTube Music (`music.youtube.com/watch?v=...` o `music.youtube.com/playlist?list=...`)
     * al formato canónico compatible para que `yt-dlp` pueda extraer tanto audio de alta calidad como Video Canvas
     * sin errores de extractor en pistas de YouTube Music.
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
}
