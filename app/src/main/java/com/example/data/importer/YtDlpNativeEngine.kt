package com.example.data.importer

import android.content.Context
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipFile

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

            val currentSignature = if (pythonZip.exists()) "${pythonZip.length()}_${pythonZip.lastModified()}" else "none"

            if (pythonZip.exists()) {
                val needExtraction = !pythonEnvDir.exists() ||
                        !versionMarker.exists() ||
                        versionMarker.readText().trim() != currentSignature

                if (needExtraction) {
                    AuraDebugManager.logInfo(TAG, "Desempaquetando entorno Python optimizado en almacenamiento privado...")
                    if (pythonEnvDir.exists()) {
                        pythonEnvDir.deleteRecursively()
                    }
                    pythonEnvDir.mkdirs()

                    extractZip(pythonZip, pythonEnvDir)
                    versionMarker.writeText(currentSignature)
                    AuraDebugManager.logInfo(TAG, "Entorno Python optimizado inicializado correctamente.")
                }
            }

            // Inicializar script de yt-dlp base si no existe
            val ytdlpFile = YtDlpAutoUpdater.getYtDlpFile(context)
            if (!ytdlpFile.exists() || ytdlpFile.length() < 1000L) {
                try {
                    context.assets.open("bin/yt-dlp").use { input ->
                        ytdlpFile.parentFile?.mkdirs()
                        FileOutputStream(ytdlpFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    ytdlpFile.setExecutable(true, false)
                    File(context.filesDir, "bin/ytdlp_version.txt").writeText("v.base (empaquetado)")
                    AuraDebugManager.logInfo(TAG, "Script base yt-dlp copiado desde assets a almacenamiento ejecutable.")
                } catch (e: Exception) {
                    AuraDebugManager.logWarning(TAG, "No se pudo copiar el yt-dlp base desde assets: ${e.message}")
                }
            } else {
                ytdlpFile.setExecutable(true, false)
            }

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

        val cmdList = mutableListOf<String>().apply {
            add(activePython.absolutePath)
            add(ytdlpFile.absolutePath)
            add("--dump-single-json")
            add("--no-warnings")
            add("--no-check-certificates")
            add("--format")
            add("bestvideo[height<=480]+bestaudio/best[height<=480]/best")
            if (qjsBin != null && qjsBin.exists()) {
                add("--js-runtimes")
                add("quickjs:${qjsBin.absolutePath}")
            }
            if (ffmpegBin != null && ffmpegBin.exists()) {
                add("--ffmpeg-location")
                add(ffmpegBin.absolutePath)
            }
            add(url)
        }

        AuraDebugManager.logInfo(TAG, "Ejecutando yt-dlp con Python nativo para resolver URL: $url")
        val jsonOutput = StringBuilder()
        val errorLog = StringBuilder()

        try {
            val processBuilder = ProcessBuilder(cmdList)
                .redirectErrorStream(false)

            val pythonEnvDir = File(context.filesDir, "env/python")
            val stdlibDir = File(pythonEnvDir, "stdlib")
            val modulesDir = File(pythonEnvDir, "modules")
            val ffmpegEnvDir = File(context.filesDir, "env/ffmpeg")
            val nativeDir = context.applicationInfo.nativeLibraryDir

            processBuilder.environment().apply {
                this["PYTHONHOME"] = pythonEnvDir.absolutePath
                this["PYTHONPATH"] = "${stdlibDir.absolutePath}:${modulesDir.absolutePath}:${pythonEnvDir.absolutePath}:${pythonEnvDir.absolutePath}/usr/lib/python3.12:${context.filesDir.absolutePath}/bin"
                this["HOME"] = pythonEnvDir.absolutePath
                this["LD_LIBRARY_PATH"] = "$nativeDir:${modulesDir.absolutePath}:${pythonEnvDir.absolutePath}/usr/lib:${ffmpegEnvDir.absolutePath}/usr/lib"
                this["SSL_CERT_FILE"] = "${pythonEnvDir.absolutePath}/usr/etc/tls/cert.pem"
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

            val exitCode = process.waitFor()
            stdoutThread.join()
            stderrThread.join()

            if (exitCode != 0 || jsonOutput.isBlank()) {
                AuraDebugManager.logWarning(TAG, "yt-dlp terminó con código $exitCode. Error: ${errorLog.takeLast(300)}")
                // Intentar auto-actualización por si YouTube parchó sus firmas
                YtDlpAutoUpdater.checkAndUpdate(context, forceDownload = true)
                return@withContext Result.failure(
                    Exception("yt-dlp finalizó con código $exitCode. Se sincronizó actualización OTA.")
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

            // 1. Revisar formatos seleccionados explícitamente por yt-dlp (requested_formats)
            val requestedFormats = rootJson.optJSONArray("requested_formats")
            if (requestedFormats != null) {
                for (i in 0 until requestedFormats.length()) {
                    val fmt = requestedFormats.getJSONObject(i)
                    val vcodec = fmt.optString("vcodec", "none")
                    val acodec = fmt.optString("acodec", "none")
                    val fmtUrl = fmt.optString("url", "")
                    if (fmtUrl.isNotBlank()) {
                        if (vcodec != "none" && directVideoUrl == null) {
                            directVideoUrl = fmtUrl
                        }
                        if (acodec != "none" && directAudioUrl == null) {
                            directAudioUrl = fmtUrl
                        }
                    }
                }
            }

            // 2. Inspeccionar la lista completa de formatos disponibles para forzar 480p de forma estricta
            val formats = rootJson.optJSONArray("formats")
            if (formats != null) {
                var bestAudioBitrate = 0.0
                var selectedVideoUrl: String? = null
                var selectedVideoDiff = 9999

                for (i in 0 until formats.length()) {
                    val fmt = formats.getJSONObject(i)
                    val fmtUrl = fmt.optString("url", "")
                    if (fmtUrl.isBlank()) continue

                    val vcodec = fmt.optString("vcodec", "none")
                    val acodec = fmt.optString("acodec", "none")
                    val height = fmt.optInt("height", 0)
                    val abr = fmt.optDouble("abr", 0.0).takeIf { it > 0 } ?: fmt.optDouble("tbr", 0.0)

                    // Audio de alta fidelidad
                    if (acodec != "none" && vcodec == "none") {
                        if (abr > bestAudioBitrate || directAudioUrl == null) {
                            bestAudioBitrate = abr
                            directAudioUrl = fmtUrl
                        }
                    }

                    // Video con prioridad estricta a 480p
                    if (vcodec != "none") {
                        val targetHeight = 480
                        val diff = kotlin.math.abs(height - targetHeight)
                        if (height == 480) {
                            // Coincidencia exacta a 480p
                            selectedVideoUrl = fmtUrl
                            selectedVideoDiff = 0
                        } else if (selectedVideoDiff > 0 && diff < selectedVideoDiff) {
                            selectedVideoUrl = fmtUrl
                            selectedVideoDiff = diff
                        }
                    }
                }

                if (selectedVideoUrl != null) {
                    directVideoUrl = selectedVideoUrl
                }
            }

            if (directAudioUrl == null) {
                directAudioUrl = rootJson.optString("url", "").ifBlank { null }
            }
            if (directVideoUrl == null) {
                directVideoUrl = directAudioUrl
            }

            val finalVideoUrl = directVideoUrl ?: directAudioUrl ?: url
            val mediaInfo = OnlineVideoAudioImporter.ResolvedMediaInfo(
                originalUrl = url,
                suggestedTitle = title,
                suggestedArtist = uploader,
                videoUrl = finalVideoUrl,
                audioUrl = directAudioUrl,
                coverUrl = thumbnail.ifBlank { null },
                durationSeconds = duration
            )

            AuraDebugManager.logInfo(TAG, "¡yt-dlp resolvió exitosamente: '$title' de $uploader ($duration s) con video 480p!")
            Result.success(mediaInfo)
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Fallo al ejecutar proceso yt-dlp: ${e.message}")
            Result.failure(e)
        }
    }

    private fun extractZip(zipFile: File, destDir: File) {
        val zip = ZipFile(zipFile)
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val entryFile = File(destDir, entry.name)
            if (entry.isDirectory) {
                entryFile.mkdirs()
            } else {
                entryFile.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(entryFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (entryFile.name.endsWith(".so") || entryFile.name.contains("bin")) {
                    entryFile.setExecutable(true, false)
                }
            }
        }
        zip.close()
    }
}
