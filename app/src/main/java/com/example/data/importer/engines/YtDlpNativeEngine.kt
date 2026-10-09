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
                append("direct_symlink_v4_")
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

        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(
                IllegalArgumentException("URL inválida o insegura para resolver con yt-dlp: $url")
            )
        }

        val pythonEnvDir = File(context.filesDir, "env/python")
        ensureTlsCertificates(pythonEnvDir)
        val localCert = File(pythonEnvDir, "usr/etc/tls/cert.pem")

        val cmdList = mutableListOf<String>().apply {
            add(activePython.absolutePath)
            add(ytdlpFile.absolutePath)
            add("--dump-single-json")
            add("--no-warnings")
            add("--format")
            add("bestvideo[height<=480][ext=mp4][vcodec^=avc1][protocol^=http]+bestaudio[ext=m4a][protocol^=http]/bestvideo[height<=480][ext=mp4][protocol^=http]+bestaudio[protocol^=http]/best[height<=480][ext=mp4][protocol^=http]/best[height<=480]")
            if (qjsBin != null && qjsBin.exists()) {
                add("--js-runtimes")
                add("quickjs:${qjsBin.absolutePath}")
            }
            if (ffmpegBin != null && ffmpegBin.exists()) {
                add("--ffmpeg-location")
                add(ffmpegBin.absolutePath)
            }
            // Asegurar siempre el bundle de certificados CA consolidados en lugar de desactivar TLS
            if (!localCert.exists() || localCert.length() < 1000L) {
                ensureTlsCertificates(pythonEnvDir, forceRebuild = true)
            }
            // Delimitador para garantizar que el argumento no sea interpretado como flag CLI
            add("--")
            add(cleanUrl)
        }

        AuraDebugManager.logInfo(TAG, "Ejecutando yt-dlp con Python nativo para resolver URL: $url")
        val jsonOutput = StringBuilder()
        val errorLog = StringBuilder()

        try {
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

            val process = processBuilder.start()

            // Hilo lector de stdout
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

            // Hilo lector de stderr
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

            var exitCode = process.waitFor()
            stdoutThread.join()
            stderrThread.join()

            val errString = errorLog.toString()
            // Auto-recuperación ante error de validación SSL de OpenSSL en Android 14+ regenerando el almacén CA oficial (sin desactivar jamás TLS)
            if ((exitCode != 0 || jsonOutput.isBlank()) &&
                (errString.contains("CERTIFICATE_VERIFY_FAILED", ignoreCase = true) ||
                 errString.contains("certificate verify failed", ignoreCase = true))
            ) {
                AuraDebugManager.logWarning(TAG, "Error SSL en yt-dlp detectado. Regenerando almacén de certificados CA del sistema y reintentando con verificación TLS activa...")
                jsonOutput.clear()
                errorLog.clear()

                ensureTlsCertificates(pythonEnvDir, forceRebuild = true)

                val retryPb = ProcessBuilder(cmdList)
                    .directory(File(nativeDir))
                    .redirectErrorStream(false)
                retryPb.environment().putAll(processBuilder.environment())
                if (localCert.exists() && localCert.length() > 0) {
                    retryPb.environment()["SSL_CERT_FILE"] = localCert.absolutePath
                    retryPb.environment()["CURL_CA_BUNDLE"] = localCert.absolutePath
                    retryPb.environment()["REQUESTS_CA_BUNDLE"] = localCert.absolutePath
                }

                val retryProcess = retryPb.start()
                val retryOutThread = Thread {
                    try {
                        BufferedReader(InputStreamReader(retryProcess.inputStream)).use { r ->
                            var l: String?
                            while (r.readLine().also { l = it } != null) {
                                jsonOutput.append(l)
                            }
                        }
                    } catch (_: Exception) {}
                }
                retryOutThread.start()

                val retryErrThread = Thread {
                    try {
                        BufferedReader(InputStreamReader(retryProcess.errorStream)).use { r ->
                            var l: String?
                            while (r.readLine().also { l = it } != null) {
                                errorLog.append(l).append("\n")
                            }
                        }
                    } catch (_: Exception) {}
                }
                retryErrThread.start()

                exitCode = retryProcess.waitFor()
                retryOutThread.join()
                retryErrThread.join()
            }

            // Reintento automático inmediato ante fallos transitorios (ej. conexión efímera o firma temporal)
            if (exitCode != 0 || jsonOutput.isBlank()) {
                AuraDebugManager.logWarning(TAG, "Primer intento de yt-dlp finalizó con código $exitCode. Ejecutando reintento automático inmediato...")
                jsonOutput.clear()
                errorLog.clear()

                val secondPb = ProcessBuilder(cmdList)
                    .directory(File(nativeDir))
                    .redirectErrorStream(false)
                secondPb.environment().putAll(processBuilder.environment())

                val secondProcess = secondPb.start()
                val secondOutThread = Thread {
                    try {
                        BufferedReader(InputStreamReader(secondProcess.inputStream)).use { r ->
                            var l: String?
                            while (r.readLine().also { l = it } != null) {
                                jsonOutput.append(l)
                            }
                        }
                    } catch (_: Exception) {}
                }
                secondOutThread.start()

                val secondErrThread = Thread {
                    try {
                        BufferedReader(InputStreamReader(secondProcess.errorStream)).use { r ->
                            var l: String?
                            while (r.readLine().also { l = it } != null) {
                                errorLog.append(l).append("\n")
                            }
                        }
                    } catch (_: Exception) {}
                }
                secondErrThread.start()

                exitCode = secondProcess.waitFor()
                secondOutThread.join()
                secondErrThread.join()
            }

            if (exitCode != 0 || jsonOutput.isBlank()) {
                AuraDebugManager.logWarning(TAG, "yt-dlp terminó con código $exitCode. Error: ${errorLog.takeLast(300)}")
                return@withContext Result.failure(
                    Exception("yt-dlp finalizó con código $exitCode. Error: ${errorLog.takeLast(120).trim()}")
                )
            }

            val rootJson = JSONObject(jsonOutput.toString())
            val title = rootJson.optString("title", "Audio de YouTube").trim()
            val uploader = rootJson.optString("uploader", "").ifBlank {
                rootJson.optString("channel", "YouTube")
            }.trim()
            val duration = rootJson.optLong("duration", 0L)
            val thumbnail = rootJson.optString("thumbnail", "")

            var directAudioUrl: String? = null
            var directVideoUrl: String? = null
            val resolvedHeaders = mutableMapOf<String, String>()

            fun extractHeadersFromJson(fmtObj: JSONObject) {
                val headersObj = fmtObj.optJSONObject("http_headers") ?: rootJson.optJSONObject("http_headers")
                if (headersObj != null) {
                    val keys = headersObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = headersObj.optString(k, "")
                        if (v.isNotBlank()) {
                            resolvedHeaders[k] = v
                        }
                    }
                }
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
                        extractHeadersFromJson(fmt)
                        if (vcodec != "none" && directVideoUrl == null) {
                            directVideoUrl = fmtUrl
                        }
                        if (acodec != "none" && directAudioUrl == null) {
                            directAudioUrl = fmtUrl
                        }
                    }
                }
            }

            // 2. Inspeccionar la lista completa de formatos disponibles para forzar 480p en MP4 (H.264/avc1) libre de HLS/m3u8
            val formats = rootJson.optJSONArray("formats")
            if (formats != null) {
                var bestAudioScore = -1.0
                var selectedVideoUrl: String? = null
                var bestVideoScore = -999999

                for (i in 0 until formats.length()) {
                    val fmt = formats.getJSONObject(i)
                    val fmtUrl = fmt.optString("url", "")
                    val protocol = fmt.optString("protocol", "https")
                    val ext = fmt.optString("ext", "").lowercase()
                    if (!isDirectHttpStream(fmtUrl, protocol, ext)) continue

                    val vcodec = fmt.optString("vcodec", "none").lowercase()
                    val acodec = fmt.optString("acodec", "none").lowercase()
                    val height = fmt.optInt("height", 0)
                    val abr = fmt.optDouble("abr", 0.0).takeIf { it > 0 } ?: fmt.optDouble("tbr", 0.0)

                    // Audio directo de alta fidelidad (priorizar m4a / mp4a para compatibilidad nativa en Android)
                    if (acodec != "none" && vcodec == "none") {
                        val codecBonus = if (ext == "m4a" || acodec.contains("mp4a")) 500.0 else 0.0
                        val audioScore = abr + codecBonus
                        if (audioScore > bestAudioScore || directAudioUrl == null) {
                            bestAudioScore = audioScore
                            directAudioUrl = fmtUrl
                            extractHeadersFromJson(fmt)
                        }
                    }

                    // Video directo con prioridad estricta a 480p en contenedor MP4 (H.264 / avc1) evitando fallos de AV1/VP9/m3u8
                    if (vcodec != "none" && height > 0) {
                        val targetHeight = 480
                        val diff = kotlin.math.abs(height - targetHeight)
                        var score = 10000 - (diff * 15)

                        if (height == 480) score += 5000
                        if (ext == "mp4") score += 3000
                        if (vcodec.startsWith("avc1") || vcodec.contains("h264")) {
                            score += 4000
                        } else if (vcodec.startsWith("av01") || vcodec.contains("av1")) {
                            // Penalizar AV1 ya que muchos decodificadores móviles y extractores fallan con él
                            score -= 6000
                        } else if (vcodec.contains("vp9")) {
                            score -= 2000
                        }

                        if (score > bestVideoScore) {
                            bestVideoScore = score
                            selectedVideoUrl = fmtUrl
                            extractHeadersFromJson(fmt)
                        }
                    }
                }

                if (selectedVideoUrl != null) {
                    directVideoUrl = selectedVideoUrl
                }
            }

            if (directAudioUrl == null) {
                val rootUrl = rootJson.optString("url", "")
                val rootProto = rootJson.optString("protocol", "https")
                val rootExt = rootJson.optString("ext", "")
                if (isDirectHttpStream(rootUrl, rootProto, rootExt)) {
                    directAudioUrl = rootUrl
                    extractHeadersFromJson(rootJson)
                }
            }

            // Si aún no hay directVideoUrl separado, buscar un stream combinado MP4 (con video + audio, ej. itag 18)
            if (directVideoUrl == null && formats != null) {
                for (i in 0 until formats.length()) {
                    val fmt = formats.getJSONObject(i)
                    val fmtUrl = fmt.optString("url", "")
                    val protocol = fmt.optString("protocol", "https")
                    val ext = fmt.optString("ext", "").lowercase()
                    val vcodec = fmt.optString("vcodec", "none").lowercase()
                    val acodec = fmt.optString("acodec", "none").lowercase()
                    if (isDirectHttpStream(fmtUrl, protocol, ext) && vcodec != "none" && acodec != "none") {
                        directVideoUrl = fmtUrl
                        extractHeadersFromJson(fmt)
                        if (directAudioUrl == null) directAudioUrl = fmtUrl
                        break
                    }
                }
            }

            val finalVideoUrl = directVideoUrl ?: directAudioUrl ?: url
            val mediaInfo = OnlineVideoAudioImporter.ResolvedMediaInfo(
                originalUrl = url,
                suggestedTitle = title,
                suggestedArtist = uploader,
                videoUrl = finalVideoUrl,
                audioUrl = directAudioUrl,
                coverUrl = thumbnail.ifBlank { null },
                durationSeconds = duration,
                httpHeaders = resolvedHeaders
            )

            AuraDebugManager.logInfo(TAG, "¡yt-dlp resolvió exitosamente: '$title' de $uploader ($duration s) con video 480p!")
            Result.success(mediaInfo)
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Fallo al ejecutar proceso yt-dlp: ${e.message}")
            Result.failure(e)
        }
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
}
