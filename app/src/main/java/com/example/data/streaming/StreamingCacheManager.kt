package com.example.data.streaming

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.importer.download.ChunkedStreamDownloader
import com.example.debug.AuraDebugManager
import com.example.model.StreamingCacheConfig
import com.example.model.StreamingVideoNetworkPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestor de Caché Temporal Inteligente para el Modo Streaming (Audio + Video Canvas).
 *
 * Rol Arquitectónico:
 * - Almacena bloques pre-descargados por adelantado (Read-Ahead Buffer no lineal) tanto de música
 *   como de Video Canvas en `cacheDir/streaming_media/`.
 * - Permite reproducción instantánea a 0ms cuando el usuario retrocede a la canción anterior o repite en bucle.
 * - Aplica expiración automática tras 30 minutos sin usarse (TTL = 30 min) y desalojo LRU (los más viejos
 *   se eliminan primero) respetando el límite configurable por el usuario (50 MB por defecto, máximo 500 MB).
 * - Evalúa en tiempo real la política de red para Video Canvas en streaming (Solo Wi-Fi, Siempre o Desactivado).
 */
class StreamingCacheManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheMutex = Mutex()

    val cacheRootDir: File = File(context.cacheDir, "streaming_media").apply {
        if (!exists()) mkdirs()
    }
    val audioCacheDir: File = File(cacheRootDir, "audio").apply {
        if (!exists()) mkdirs()
    }
    val videoCacheDir: File = File(cacheRootDir, "video").apply {
        if (!exists()) mkdirs()
    }
    val artworkCacheDir: File = File(cacheRootDir, "artwork").apply {
        if (!exists()) mkdirs()
    }

    private val activePrefetchJobs = ConcurrentHashMap<String, Job>()

    private val _cacheConfig = MutableStateFlow(loadInitialConfig())
    val cacheConfig: StateFlow<StreamingCacheConfig> = _cacheConfig.asStateFlow()

    init {
        cacheScope.launch {
            evictExpiredAndOverLimit()
        }
    }

    private fun loadInitialConfig(): StreamingCacheConfig {
        val maxMb = prefs.getInt(
            KEY_MAX_CACHE_MB,
            StreamingCacheConfig.DEFAULT_CACHE_SIZE_MB
        ).coerceIn(StreamingCacheConfig.MIN_CACHE_SIZE_MB, StreamingCacheConfig.MAX_CACHE_SIZE_MB)

        val policyId = prefs.getInt(KEY_VIDEO_NETWORK_POLICY, StreamingVideoNetworkPolicy.WIFI_ONLY.id)
        val policy = StreamingVideoNetworkPolicy.fromId(policyId)

        return StreamingCacheConfig(
            maxCacheSizeMb = maxMb,
            videoNetworkPolicy = policy
        )
    }

    /**
     * Actualiza el límite máximo de la caché temporal (entre 50 MB y 500 MB) y ejecuta limpieza LRU si es necesario.
     */
    fun setMaxCacheSizeMb(sizeMb: Int) {
        val clamped = sizeMb.coerceIn(
            StreamingCacheConfig.MIN_CACHE_SIZE_MB,
            StreamingCacheConfig.MAX_CACHE_SIZE_MB
        )
        prefs.edit().putInt(KEY_MAX_CACHE_MB, clamped).apply()
        _cacheConfig.value = _cacheConfig.value.copy(maxCacheSizeMb = clamped)
        cacheScope.launch {
            evictExpiredAndOverLimit()
        }
    }

    /**
     * Actualiza la política de red para los Videos Canvas en modo Streaming (Solo Wi-Fi, Siempre o Desactivado).
     */
    fun setVideoNetworkPolicy(policy: StreamingVideoNetworkPolicy) {
        prefs.edit().putInt(KEY_VIDEO_NETWORK_POLICY, policy.id).apply()
        _cacheConfig.value = _cacheConfig.value.copy(videoNetworkPolicy = policy)
    }

    /**
     * Verifica si la red actual permite descargar y mostrar el Video Canvas en modo Streaming,
     * sin afectar en absoluto a los videos locales ya guardados en el dispositivo.
     */
    fun isStreamingVideoAllowedByNetwork(): Boolean {
        return when (_cacheConfig.value.videoNetworkPolicy) {
            StreamingVideoNetworkPolicy.DISABLED -> false
            StreamingVideoNetworkPolicy.ALWAYS -> isNetworkAvailable()
            StreamingVideoNetworkPolicy.WIFI_ONLY -> isWifiConnected()
        }
    }

    /**
     * Comprueba si el dispositivo cuenta con conexión activa por Wi-Fi o Ethernet.
     */
    fun isWifiConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (_: Exception) {
            false
        }
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Retorna el archivo de audio en caché si ya existe y es válido, actualizando su marca de tiempo
     * de último acceso (para reiniciar su contador de 30 minutos de vida útil).
     */
    fun getCachedAudioFile(videoId: String): File? {
        val safeId = sanitizeId(videoId)
        val file = File(audioCacheDir, "stream_audio_$safeId.m4a")
        if (file.exists() && file.length() > MIN_VALID_AUDIO_BYTES) {
            val ageMs = System.currentTimeMillis() - file.lastModified()
            if (ageMs <= StreamingCacheConfig.CACHE_TTL_MS) {
                touchFile(file)
                return file
            } else {
                try { file.delete() } catch (_: Exception) {}
            }
        }
        return null
    }

    /**
     * Retorna el archivo de Video Canvas en caché si ya existe y es válido, actualizando su último acceso.
     */
    fun getCachedVideoFile(videoId: String): File? {
        val safeId = sanitizeId(videoId)
        val file = File(videoCacheDir, "stream_video_$safeId.mp4")
        if (file.exists() && file.length() > MIN_VALID_VIDEO_BYTES) {
            val ageMs = System.currentTimeMillis() - file.lastModified()
            if (ageMs <= StreamingCacheConfig.CACHE_TTL_MS) {
                touchFile(file)
                return file
            } else {
                try { file.delete() } catch (_: Exception) {}
            }
        }
        return null
    }

    /**
     * Retorna la miniatura en caché temporal si existe.
     */
    fun getCachedArtworkFile(videoId: String): File? {
        val safeId = sanitizeId(videoId)
        val file = File(artworkCacheDir, "stream_art_$safeId.webp")
        if (file.exists() && file.length() > 512L) {
            touchFile(file)
            return file
        }
        return null
    }

    /**
     * Pre-descarga de forma no lineal (por bloques HTTP Range adelantados) los minutos de la canción
     * en segundo plano mientras el usuario escucha, guardándolos en la caché temporal para que
     * búsquedas, repeticiones en bucle o volver a la canción anterior sean instantáneos.
     */
    fun prefetchAudioAheadAsync(
        videoId: String,
        streamUrl: String,
        onReadyForLocalPlayback: ((File) -> Unit)? = null
    ) {
        val safeId = sanitizeId(videoId)
        if (safeId.isBlank() || streamUrl.isBlank()) return

        val existing = getCachedAudioFile(safeId)
        if (existing != null) {
            onReadyForLocalPlayback?.invoke(existing)
            return
        }

        val jobKey = "audio_$safeId"
        if (activePrefetchJobs[jobKey]?.isActive == true) return

        val job = cacheScope.launch {
            try {
                evictExpiredAndOverLimit()
                val targetFile = File(audioCacheDir, "stream_audio_$safeId.m4a")
                val tempFile = File(audioCacheDir, "stream_audio_${safeId}.part")

                // Descarga acelerada por bloques HTTP Range (estilo YouTube Read-Ahead)
                val success = downloadNonLinearRangeWindow(
                    streamUrl = streamUrl,
                    outputFile = tempFile,
                    maxPreloadBytes = MAX_SINGLE_AUDIO_CACHE_BYTES
                )

                if (success && tempFile.exists() && tempFile.length() > MIN_VALID_AUDIO_BYTES) {
                    cacheMutex.withLock {
                        if (targetFile.exists()) targetFile.delete()
                        tempFile.renameTo(targetFile)
                        touchFile(targetFile)
                    }
                    evictExpiredAndOverLimit()
                    onReadyForLocalPlayback?.invoke(targetFile)
                } else {
                    try { tempFile.delete() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                AuraDebugManager.logWarning(
                    tag = "StreamingCache",
                    message = "Aviso en pre-carga de audio ($safeId): ${e.localizedMessage ?: "Error en pre-carga por bloques"}",
                    throwable = e
                )
            } finally {
                activePrefetchJobs.remove(jobKey)
            }
        }
        activePrefetchJobs[jobKey] = job
    }

    /**
     * Pre-descarga en caché temporal el Video Canvas (480p sin audio) si la política de red
     * actual del usuario lo permite (Solo Wi-Fi vs Siempre vs Desactivado).
     */
    fun prefetchVideoCanvasAsync(
        videoId: String,
        videoStreamUrl: String,
        onVideoCached: ((File) -> Unit)? = null
    ) {
        if (!isStreamingVideoAllowedByNetwork()) return
        val safeId = sanitizeId(videoId)
        if (safeId.isBlank() || videoStreamUrl.isBlank()) return

        val existing = getCachedVideoFile(safeId)
        if (existing != null) {
            onVideoCached?.invoke(existing)
            return
        }

        val jobKey = "video_$safeId"
        if (activePrefetchJobs[jobKey]?.isActive == true) return

        val job = cacheScope.launch {
            try {
                evictExpiredAndOverLimit()
                val targetFile = File(videoCacheDir, "stream_video_$safeId.mp4")
                val tempFile = File(videoCacheDir, "stream_video_${safeId}.part")

                val success = downloadNonLinearRangeWindow(
                    streamUrl = videoStreamUrl,
                    outputFile = tempFile,
                    maxPreloadBytes = MAX_SINGLE_VIDEO_CACHE_BYTES
                )

                if (success && tempFile.exists() && tempFile.length() > MIN_VALID_VIDEO_BYTES) {
                    cacheMutex.withLock {
                        if (targetFile.exists()) targetFile.delete()
                        tempFile.renameTo(targetFile)
                        touchFile(targetFile)
                    }
                    evictExpiredAndOverLimit()
                    onVideoCached?.invoke(targetFile)
                } else {
                    try { tempFile.delete() } catch (_: Exception) {}
                }
            } catch (_: Exception) {
            } finally {
                activePrefetchJobs.remove(jobKey)
            }
        }
        activePrefetchJobs[jobKey] = job
    }

    /**
     * Descarga no lineal por ventanas de bloques HTTP Range (`bytes=start-end`) para adelantarse
     * varios minutos en la reproducción sin sufrir el estrangulamiento lineal de conexiones simples.
     */
    private suspend fun downloadNonLinearRangeWindow(
        streamUrl: String,
        outputFile: File,
        maxPreloadBytes: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Intentar primero con el acelerador multihilo por bloques HTTP Range
            val chunkedOk = ChunkedStreamDownloader.downloadUrlToFile(
                url = streamUrl,
                targetFile = outputFile,
                phase = "Pre-cargando caché en segundo plano...",
                customHeaders = mapOf("User-Agent" to USER_AGENT),
                onProgress = {}
            )
            if (chunkedOk && outputFile.exists() && outputFile.length() > MIN_VALID_AUDIO_BYTES) {
                return@withContext true
            }

            // Respaldo por ventanas de rango secuenciales rápidas de 512 KB
            var downloaded = 0L
            val chunkSize = 512 * 1024L
            RandomAccessFile(outputFile, "rw").use { raf ->
                while (downloaded < maxPreloadBytes) {
                    val endByte = downloaded + chunkSize - 1
                    val conn = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 10000
                        setRequestProperty("User-Agent", USER_AGENT)
                        setRequestProperty("Range", "bytes=$downloaded-$endByte")
                    }
                    val code = conn.responseCode
                    if (code != HttpURLConnection.HTTP_PARTIAL && code != HttpURLConnection.HTTP_OK) {
                        conn.disconnect()
                        break
                    }
                    val stream = conn.inputStream
                    val buffer = ByteArray(32 * 1024)
                    var readInChunk = 0L
                    var n: Int
                    while (stream.read(buffer).also { n = it } != -1) {
                        raf.write(buffer, 0, n)
                        downloaded += n
                        readInChunk += n
                        if (downloaded >= maxPreloadBytes) break
                    }
                    stream.close()
                    conn.disconnect()

                    // Si el servidor no soportó Range (devolvió 200 completo) o llegó al EOF
                    if (code == HttpURLConnection.HTTP_OK || readInChunk < chunkSize / 2) {
                        break
                    }
                }
            }
            outputFile.exists() && outputFile.length() > MIN_VALID_AUDIO_BYTES
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Guarda una miniatura en la caché temporal para acelerar su visualización y reutilizarla si el usuario descarga la pista.
     */
    suspend fun cacheArtworkBytes(videoId: String, bytes: ByteArray): File? = withContext(Dispatchers.IO) {
        try {
            val safeId = sanitizeId(videoId)
            val file = File(artworkCacheDir, "stream_art_$safeId.webp")
            FileOutputStream(file).use { it.write(bytes) }
            touchFile(file)
            evictExpiredAndOverLimit()
            file
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Ejecuta la política de limpieza en dos fases:
     * 1. Elimina todos los archivos que lleven más de 30 minutos sin usarse (`CACHE_TTL_MS`).
     * 2. Si el tamaño total aún supera el límite configurado (ej. 50 MB - 500 MB), elimina primero
     *    los archivos más antiguos (LRU por `lastModified`).
     */
    suspend fun evictExpiredAndOverLimit() = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            try {
                val now = System.currentTimeMillis()
                val ttlMs = StreamingCacheConfig.CACHE_TTL_MS
                val maxBytes = _cacheConfig.value.maxCacheSizeMb.toLong() * 1024L * 1024L

                val allFiles = collectAllCacheFiles()

                // Fase 1: Eliminar archivos expirados (> 30 minutos sin usarse) o .part huérfanos antiguos
                for (file in allFiles) {
                    val age = now - file.lastModified()
                    if (age > ttlMs) {
                        try { file.delete() } catch (_: Exception) {}
                    }
                }

                // Fase 2: Desalojo LRU (los más viejos primero) si supera el límite configurado en MB
                val remainingFiles = collectAllCacheFiles()
                    .filter { !it.name.endsWith(".part") }
                    .sortedBy { it.lastModified() } // Más antiguos primero

                var totalBytes = remainingFiles.sumOf { it.length() }
                for (file in remainingFiles) {
                    if (totalBytes <= maxBytes) break
                    val len = file.length()
                    if (try { file.delete() } catch (_: Exception) { false }) {
                        totalBytes = (totalBytes - len).coerceAtLeast(0L)
                    }
                }

                refreshCacheStatsLocked()
            } catch (_: Exception) {}
        }
    }

    /**
     * Limpia manualmente toda la caché temporal de streaming a petición del usuario desde Ajustes.
     */
    suspend fun clearAllStreamingCache() = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            activePrefetchJobs.values.forEach { it.cancel() }
            activePrefetchJobs.clear()
            collectAllCacheFiles().forEach { file ->
                try { file.delete() } catch (_: Exception) {}
            }
            refreshCacheStatsLocked()
        }
    }

    /**
     * Refresca las métricas de uso de caché para mostrarlas reactivamente en Ajustes.
     */
    suspend fun refreshStats() = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            refreshCacheStatsLocked()
        }
    }

    private fun refreshCacheStatsLocked() {
        val files = collectAllCacheFiles().filter { !it.name.endsWith(".part") }
        val totalBytes = files.sumOf { it.length() }
        val audioCount = audioCacheDir.listFiles()?.count { it.isFile && !it.name.endsWith(".part") } ?: 0
        val videoCount = videoCacheDir.listFiles()?.count { it.isFile && !it.name.endsWith(".part") } ?: 0

        _cacheConfig.value = _cacheConfig.value.copy(
            currentUsedBytes = totalBytes,
            cachedAudioCount = audioCount,
            cachedVideoCount = videoCount
        )
    }

    private fun collectAllCacheFiles(): List<File> {
        val list = mutableListOf<File>()
        listOf(audioCacheDir, videoCacheDir, artworkCacheDir).forEach { dir ->
            dir.listFiles()?.forEach { f ->
                if (f.isFile) list.add(f)
            }
        }
        return list
    }

    private fun touchFile(file: File) {
        try {
            file.setLastModified(System.currentTimeMillis())
        } catch (_: Exception) {}
    }

    private fun sanitizeId(rawId: String): String {
        return rawId.replace(Regex("[^a-zA-Z0-9_-]"), "").take(40)
    }

    companion object {
        private const val PREFS_NAME = "aura_streaming_cache_prefs"
        private const val KEY_MAX_CACHE_MB = "max_cache_size_mb"
        private const val KEY_VIDEO_NETWORK_POLICY = "streaming_video_network_policy"

        private const val MIN_VALID_AUDIO_BYTES = 16 * 1024L
        private const val MIN_VALID_VIDEO_BYTES = 32 * 1024L
        private const val MAX_SINGLE_AUDIO_CACHE_BYTES = 20L * 1024L * 1024L // 20 MB máx por pista en caché
        private const val MAX_SINGLE_VIDEO_CACHE_BYTES = 25L * 1024L * 1024L // 25 MB máx por video canvas en caché
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
