package com.example.data.importer

import android.content.Context
import android.os.Build
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Motor nativo de extracción directa con yt-dlp para Aura Music.
 *
 * Arquitectura y Principio de Operación:
 * 1. Ejecuta el paquete de extracción local yt-dlp desde el almacenamiento privado de la app
 *    (context.filesDir/bin/yt-dlp) junto a FFmpeg puro para eludir las restricciones de
 *    cifrado n-sig, Botguard y caídas de servidores proxy públicos.
 * 2. Utiliza el ejecutable de Python o el intérprete de sistema disponible para correr el script.
 * 3. Si yt-dlp no está presente en el teléfono o si YouTube actualiza sus firmas, se enlaza
 *    con [YtDlpAutoUpdater] para descargar y actualizar el extractor en caliente (OTA) sin requerir
 *    un nuevo APK.
 * 4. Entrega metadatos completos y URLs de audio/video de alta fidelidad listas para ser
 *    procesadas por [FFmpegNativeEngine] y [OnlineVideoAudioImporter].
 */
object YtDlpNativeEngine {

    private const val TAG = "YtDlpNativeEngine"

    /**
     * Comprueba si el ejecutable de yt-dlp y un intérprete de ejecución están listos en el dispositivo.
     */
    fun isAvailable(context: Context): Boolean {
        val ytdlpFile = YtDlpAutoUpdater.getYtDlpFile(context)
        return ytdlpFile.exists() && ytdlpFile.length() > 50_000L && getPythonExecutable(context) != null
    }

    /**
     * Localiza el binario ejecutable de Python disponible para el proceso de la app.
     */
    fun getPythonExecutable(context: Context): File? {
        // 1. Directorio de librerías nativas del APK (libpython3.so o libpython.so marcado ejecutable)
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val pythonInNative = File(nativeDir, "libpython.so")
        if (pythonInNative.exists() && pythonInNative.canExecute()) return pythonInNative

        val python3InNative = File(nativeDir, "libpython3.so")
        if (python3InNative.exists() && python3InNative.canExecute()) return python3InNative

        // 2. Directorio binario interno de la app (files/bin/python3 o files/bin/python)
        val binDir = File(context.filesDir, "bin")
        val candidateInBin = File(binDir, "python3")
        if (candidateInBin.exists() && candidateInBin.canExecute()) return candidateInBin

        val candidateAlt = File(binDir, "python")
        if (candidateAlt.exists() && candidateAlt.canExecute()) return candidateAlt

        // 3. Comprobar si el sistema operativo tiene python3 accesible en PATH
        try {
            val systemPython = File("/system/bin/python3")
            if (systemPython.exists() && systemPython.canExecute()) return systemPython
        } catch (_: Throwable) {}

        return null
    }

    /**
     * Resuelve los flujos de audio y video de YouTube utilizando yt-dlp localmente.
     */
    suspend fun resolveStream(
        context: Context,
        url: String
    ): Result<OnlineVideoAudioImporter.ResolvedMediaInfo> = withContext(Dispatchers.IO) {
        val ytdlpFile = YtDlpAutoUpdater.getYtDlpFile(context)
        val pythonBin = getPythonExecutable(context)

        if (!ytdlpFile.exists() || pythonBin == null) {
            // Intentar provisión automática si falta el script
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
        val cmdList = mutableListOf<String>().apply {
            add(activePython.absolutePath)
            add(ytdlpFile.absolutePath)
            add("--dump-single-json")
            add("--no-warnings")
            add("--no-check-certificates")
            add("--format")
            add("bestaudio/best")
            if (ffmpegBin != null) {
                add("--ffmpeg-location")
                add(ffmpegBin.absolutePath)
            }
            add(url)
        }

        AuraDebugManager.logInfo(TAG, "Ejecutando yt-dlp para resolver URL: $url")
        val jsonOutput = StringBuilder()

        try {
            val process = ProcessBuilder(cmdList)
                .redirectErrorStream(false)
                .start()

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    jsonOutput.append(line)
                }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0 || jsonOutput.isBlank()) {
                AuraDebugManager.logWarning(TAG, "yt-dlp terminó con código $exitCode. Intentando auto-actualización por si YouTube parchó...")
                YtDlpAutoUpdater.checkAndUpdate(context, forceDownload = true)
                return@withContext Result.failure(
                    Exception("yt-dlp no pudo extraer el video (código de salida $exitCode). Se actualizó el motor.")
                )
            }

            val rootJson = JSONObject(jsonOutput.toString())
            val title = rootJson.optString("title", "Audio de YouTube").trim()
            val uploader = rootJson.optString("uploader", "").ifBlank {
                rootJson.optString("channel", "YouTube")
            }.trim()
            val duration = rootJson.optLong("duration", 0L)
            val thumbnail = rootJson.optString("thumbnail", "")

            // Extraer mejor enlace de audio directo o url principal
            val directAudioUrl = rootJson.optString("url", "")
            var videoPlayUrl = directAudioUrl

            // Si hay formatos específicos, buscar video separado para el Video Canvas
            val formats = rootJson.optJSONArray("formats")
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val fmt = formats.getJSONObject(i)
                    val vcodec = fmt.optString("vcodec", "none")
                    val fmtUrl = fmt.optString("url", "")
                    if (vcodec != "none" && fmtUrl.isNotBlank()) {
                        videoPlayUrl = fmtUrl
                        break
                    }
                }
            }

            val mediaInfo = OnlineVideoAudioImporter.ResolvedMediaInfo(
                originalUrl = url,
                suggestedTitle = title,
                suggestedArtist = uploader,
                videoUrl = videoPlayUrl,
                audioUrl = directAudioUrl.ifBlank { null },
                coverUrl = thumbnail.ifBlank { null },
                durationSeconds = duration
            )

            AuraDebugManager.logInfo(TAG, "¡yt-dlp resolvió exitosamente: '$title' de $uploader ($duration s)!")
            Result.success(mediaInfo)
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Fallo al ejecutar proceso yt-dlp: ${e.message}")
            Result.failure(e)
        }
    }
}
