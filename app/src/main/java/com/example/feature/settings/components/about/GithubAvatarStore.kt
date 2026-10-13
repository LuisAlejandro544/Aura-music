package com.example.feature.settings.components.about

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.example.BuildConfig
import com.example.core.data.storage.AppStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties

/**
 * Gestor de caché persistente de avatares de GitHub en `images/github_avatar_<user>.webp`.
 * Rol arquitectónico: capa de datos del apartado Detrás del Proyecto; todo corre en
 * `Dispatchers.IO` para no bloquear el hilo principal y guarda WebP sin pérdida.
 *
 * Política de red (descarga única con revalidación condicional):
 * - Sin caché en disco -> descarga la imagen una vez y la guarda junto a su metadato
 *   lateral `github_avatar_<user>.properties` (avatarUrl, ETag, Last-Modified).
 * - Con caché en disco -> muestra el disco al instante y solo re-descarga los bytes si
 *   GitHub reporta un cambio: compara `avatar_url` de `api.github.com/users/<user>`
 *   (cambia su `?v=` cuando el usuario cambia de foto) y usa GET condicional con
 *   `If-None-Match` / `If-Modified-Since` (304 = sin cambios, 0 bytes descargados).
 * - Sin internet o ante cualquier error -> devuelve la caché existente sin fallar.
 */
internal object GithubAvatarPersistentStore {

    private const val MIN_VALID_BYTES = 128L
    private const val CONNECT_TIMEOUT_FRESH_MS = 8000
    private const val CONNECT_TIMEOUT_REVALIDATE_MS = 5000

    private data class AvatarMeta(
        val avatarUrl: String? = null,
        val eTag: String? = null,
        val lastModified: String? = null
    )

    private sealed interface ImageFetchResult {
        data object NotModified : ImageFetchResult
        data class Downloaded(
            val bytes: ByteArray,
            val eTag: String?,
            val lastModified: String?
        ) : ImageFetchResult
        data object Failed : ImageFetchResult
    }

    suspend fun loadOrDownloadOnce(context: Context, githubUsername: String): Bitmap? =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext ?: context
            val safeUser = githubUsername.replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase()
            val storageManager = AppStorageManager(appContext)
            val avatarFile = File(storageManager.imagesDir, "github_avatar_${safeUser}.webp")
            val metaFile = File(storageManager.imagesDir, "github_avatar_${safeUser}.properties")

            val cached = decodeCachedAvatar(avatarFile)
            val meta = readAvatarMeta(metaFile)

            // Sin caché: primera y única descarga completa.
            if (cached == null) {
                val fresh = downloadFreshAvatar(githubUsername)
                if (fresh != null) {
                    persistAvatar(avatarFile, metaFile, fresh.bytes, fresh.eTag, fresh.lastModified, fresh.avatarUrl)
                    return@withContext decodeCachedAvatar(avatarFile) ?: fresh.bitmap
                }
                return@withContext null
            }

            // Con caché: revalidación ligera; si GitHub no reporta cambios se conserva el disco.
            try {
                val apiAvatarUrl = fetchGithubApiAvatarUrl(githubUsername)
                if (apiAvatarUrl != null && meta.avatarUrl != null &&
                    normalizeAvatarUrl(apiAvatarUrl) == normalizeAvatarUrl(meta.avatarUrl)
                ) {
                    // La foto no cambió según GitHub: cero bytes descargados.
                    return@withContext cached
                }
                if (apiAvatarUrl != null && meta.avatarUrl != null &&
                    normalizeAvatarUrl(apiAvatarUrl) != normalizeAvatarUrl(meta.avatarUrl)
                ) {
                    // La foto sí cambió: descarga completa y actualización de caché.
                    val fresh = downloadFreshAvatar(githubUsername, forcedAvatarUrl = apiAvatarUrl)
                    if (fresh != null) {
                        persistAvatar(avatarFile, metaFile, fresh.bytes, fresh.eTag, fresh.lastModified, apiAvatarUrl)
                        return@withContext decodeCachedAvatar(avatarFile) ?: fresh.bitmap
                    }
                    return@withContext cached
                }

                // API no concluyente (sin URL previa o sin red para la API): GET condicional por ETag.
                val revalidated = revalidateImageCandidates(githubUsername, meta)
                when (revalidated) {
                    is ImageFetchResult.NotModified -> return@withContext cached
                    is ImageFetchResult.Downloaded -> {
                        val bitmap = BitmapFactory.decodeByteArray(
                            revalidated.bytes, 0, revalidated.bytes.size
                        )
                        if (bitmap != null && bitmap.width > 16 && bitmap.height > 16) {
                            persistAvatar(
                                avatarFile, metaFile, revalidated.bytes,
                                revalidated.eTag, revalidated.lastModified,
                                meta.avatarUrl ?: apiAvatarUrl
                            )
                            return@withContext decodeCachedAvatar(avatarFile) ?: bitmap
                        }
                        return@withContext cached
                    }
                    is ImageFetchResult.Failed -> {
                        // Sin red o error temporal: se conserva la caché sin tocarla.
                        if (apiAvatarUrl != null && meta.avatarUrl == null) {
                            writeAvatarMeta(metaFile, meta.copy(avatarUrl = apiAvatarUrl))
                        }
                        return@withContext cached
                    }
                }
            } catch (_: Exception) {
                return@withContext cached
            }
        }

    private data class FreshAvatar(
        val bytes: ByteArray,
        val bitmap: Bitmap,
        val eTag: String?,
        val lastModified: String?,
        val avatarUrl: String?
    )

    private fun decodeCachedAvatar(avatarFile: File): Bitmap? {
        return try {
            if (!avatarFile.exists() || avatarFile.length() <= MIN_VALID_BYTES) return null
            val bitmap = BitmapFactory.decodeFile(avatarFile.absolutePath) ?: return null
            if (bitmap.width <= 16 || bitmap.height <= 16) return null
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun avatarCandidates(githubUsername: String, forcedAvatarUrl: String? = null): List<String> {
        val forced = forcedAvatarUrl?.let { listOf(withSizeParam(it, 240)) } ?: emptyList()
        return forced + listOf(
            "https://github.com/${githubUsername}.png?size=240",
            "https://avatars.githubusercontent.com/${githubUsername}?s=240"
        )
    }

    private fun withSizeParam(avatarUrl: String, size: Int): String {
        if (avatarUrl.contains("s=") || avatarUrl.contains("size=")) return avatarUrl
        val separator = if (avatarUrl.contains("?")) "&" else "?"
        return "$avatarUrl${separator}s=$size"
    }

    private fun downloadFreshAvatar(
        githubUsername: String,
        forcedAvatarUrl: String? = null
    ): FreshAvatar? {
        for (candidateUrl in avatarCandidates(githubUsername, forcedAvatarUrl)) {
            val result = fetchImage(candidateUrl, eTag = null, lastModified = null, timeoutMs = CONNECT_TIMEOUT_FRESH_MS)
            if (result is ImageFetchResult.Downloaded) {
                val bitmap = try {
                    BitmapFactory.decodeByteArray(result.bytes, 0, result.bytes.size)
                } catch (_: Exception) {
                    null
                }
                if (bitmap != null && bitmap.width > 16 && bitmap.height > 16) {
                    return FreshAvatar(result.bytes, bitmap, result.eTag, result.lastModified, candidateUrl)
                }
            }
        }
        return null
    }

    private fun revalidateImageCandidates(githubUsername: String, meta: AvatarMeta): ImageFetchResult {
        for (candidateUrl in avatarCandidates(githubUsername)) {
            val result = fetchImage(candidateUrl, meta.eTag, meta.lastModified, CONNECT_TIMEOUT_REVALIDATE_MS)
            when (result) {
                is ImageFetchResult.NotModified -> return result
                is ImageFetchResult.Downloaded -> return result
                is ImageFetchResult.Failed -> { /* probar siguiente candidato */ }
            }
        }
        return ImageFetchResult.Failed
    }

    private fun fetchImage(
        urlString: String,
        eTag: String?,
        lastModified: String?,
        timeoutMs: Int
    ): ImageFetchResult {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AuraMusic/${BuildConfig.VERSION_NAME}"
                )
                setRequestProperty("Accept", "image/webp,image/png,image/*,*/*;q=0.8")
                if (!eTag.isNullOrBlank()) setRequestProperty("If-None-Match", eTag)
                if (!lastModified.isNullOrBlank()) setRequestProperty("If-Modified-Since", lastModified)
            }
            return when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> ImageFetchResult.NotModified
                in 200..299 -> {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    if (bytes.isEmpty()) return ImageFetchResult.Failed
                    ImageFetchResult.Downloaded(
                        bytes = bytes,
                        eTag = connection.getHeaderField("ETag"),
                        lastModified = connection.getHeaderField("Last-Modified")
                    )
                }
                else -> ImageFetchResult.Failed
            }
        } catch (_: Exception) {
            return ImageFetchResult.Failed
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Consulta ligera (~1 KB JSON) a la API pública de GitHub para obtener el `avatar_url`
     * canónico. GitHub cambia su parámetro `?v=` cuando el usuario actualiza su foto, por
     * lo que basta comparar URLs para saber si hay cambios sin descargar la imagen.
     * Devuelve null sin internet, ante rate-limit o cualquier error (no es fatal).
     */
    private fun fetchGithubApiAvatarUrl(githubUsername: String): String? {
        var connection: HttpURLConnection? = null
        try {
            val safeUser = githubUsername.replace(Regex("[^a-zA-Z0-9_-]"), "")
            if (safeUser.isBlank()) return null
            connection = (URL("https://api.github.com/users/$safeUser").openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_REVALIDATE_MS
                readTimeout = CONNECT_TIMEOUT_REVALIDATE_MS
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AuraMusic/${BuildConfig.VERSION_NAME}"
                )
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val keyIndex = body.indexOf("\"avatar_url\"")
            if (keyIndex == -1) return null
            val colonIndex = body.indexOf(':', keyIndex)
            if (colonIndex == -1) return null
            val firstQuote = body.indexOf('"', colonIndex + 1)
            if (firstQuote == -1) return null
            val endQuote = body.indexOf('"', firstQuote + 1)
            if (endQuote == -1) return null
            return body.substring(firstQuote + 1, endQuote).takeIf { it.startsWith("https://") }
        } catch (_: Exception) {
            return null
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /** Normaliza el `avatar_url` ignorando parámetros de tamaño (`s`, `size`) pero no `v`. */
    private fun normalizeAvatarUrl(avatarUrl: String): String {
        return try {
            var normalized = avatarUrl.trim()
            normalized = normalized.replace(Regex("[?&]s=\\d+"), "")
            normalized = normalized.replace(Regex("[?&]size=\\d+"), "")
            normalized.trimEnd('?', '&')
        } catch (_: Exception) {
            avatarUrl
        }
    }

    private fun readAvatarMeta(metaFile: File): AvatarMeta {
        return try {
            if (!metaFile.exists()) return AvatarMeta()
            val props = Properties()
            metaFile.inputStream().use { props.load(it) }
            AvatarMeta(
                avatarUrl = props.getProperty("avatarUrl")?.takeIf { it.isNotBlank() },
                eTag = props.getProperty("etag")?.takeIf { it.isNotBlank() },
                lastModified = props.getProperty("lastModified")?.takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) {
            AvatarMeta()
        }
    }

    private fun writeAvatarMeta(metaFile: File, meta: AvatarMeta) {
        try {
            val props = Properties()
            if (!meta.avatarUrl.isNullOrBlank()) props.setProperty("avatarUrl", meta.avatarUrl)
            if (!meta.eTag.isNullOrBlank()) props.setProperty("etag", meta.eTag)
            if (!meta.lastModified.isNullOrBlank()) props.setProperty("lastModified", meta.lastModified)
            metaFile.outputStream().use { props.store(it, "AuraMusic github avatar revalidation") }
        } catch (_: Exception) {
        }
    }

    private fun persistAvatar(
        avatarFile: File,
        metaFile: File,
        bytes: ByteArray,
        eTag: String?,
        lastModified: String?,
        avatarUrl: String?
    ) {
        try {
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            if (decoded.width <= 16 || decoded.height <= 16) return
            val parent = avatarFile.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            val tempFile = File(parent, "${avatarFile.name}.tmp")
            FileOutputStream(tempFile).use { out ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                decoded.compress(format, 100, out)
            }
            if (tempFile.exists() && tempFile.length() > MIN_VALID_BYTES) {
                if (avatarFile.exists()) avatarFile.delete()
                tempFile.renameTo(avatarFile)
                writeAvatarMeta(metaFile, AvatarMeta(avatarUrl, eTag, lastModified))
            } else {
                try {
                    tempFile.delete()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }
}
