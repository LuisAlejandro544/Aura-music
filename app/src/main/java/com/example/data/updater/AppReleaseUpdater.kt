package com.example.data.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.debug.AuraDebugManager
import com.example.model.AppUpdateReleaseInfo
import com.example.model.AppUpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Motor de Actualización Automática de APK en Caliente (OTA App Updater) para Aura Music / Aura Beta.
 *
 * Rol arquitectónico:
 * 1. Consulta la API pública de GitHub Releases (`https://api.github.com/repos/{owner}/{repo}/releases`)
 *    sin requerir tokens privados.
 * 2. Filtra por canal (`-beta` y `prerelease == true` cuando el usuario corre el canal Beta).
 * 3. Compara semánticamente cualquier formato de Tag dinámico (ej: `v0.1.0-beta.1a` vs `v0.1.0-beta.2d`
 *    vs `v0.2.0-beta.1m`), desglosando `major.minor.patch`, número de revisión beta (`beta.N`) y
 *    letra estratégica (`a`, `d`, `m`, `s`, `u`), sin importar que los tags cambien en cada versión.
 * 4. Detecta automáticamente la arquitectura real del teléfono (`Build.SUPPORTED_ABIS`) y elige el APK
 *    óptimo entre los 3 activos del Release (`arm64-v8a`, `armeabi-v7a` o `universal`).
 * 5. Descarga el APK en `Android/data/<paquete>/files/updates/`, verifica su integridad SHA-256 contra
 *    `SHA256SUMS.txt` (si está adjunto en el Release) y lanza el instalador nativo de Android mediante
 *    `FileProvider` conservando intactas todas las canciones, playlists, carátulas y videos del usuario.
 */
object AppReleaseUpdater {

    private const val PREFS_NAME = "aura_app_updater_prefs"
    private const val KEY_GITHUB_REPO_SLUG = "github_repo_slug"
    private const val KEY_DISMISSED_TAG = "dismissed_release_tag"
    private const val KEY_AUTO_CHECK_ON_START = "auto_check_on_start"
    const val DEFAULT_GITHUB_REPO_SLUG = "LuisAlejandro544/Aura-music"
    const val DEFAULT_GITHUB_RELEASES_URL = "https://github.com/LuisAlejandro544/Aura-music/releases"

    private const val ALLOWED_API_HOST = "api.github.com"
    private val ALLOWED_DOWNLOAD_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com"
    )

    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    /**
     * Representación estructurada de un Tag de versión de Aura Music (ej: `v0.1.0-beta.1a`).
     * Permite comparar con precisión cualquier tag pasado o futuro aunque cambien los números o sufijos.
     */
    data class ParsedAuraTag(
        val rawTag: String,
        val major: Int,
        val minor: Int,
        val patch: Int,
        val channelRank: Int, // 0 = pre-alpha/alpha, 1 = beta, 2 = rc, 3 = stable release
        val channelRevision: Int, // ej: el '2' en -beta.2d
        val strategicSuffix: String // ej: la 'd' en -beta.2d
    ) : Comparable<ParsedAuraTag> {

        override fun compareTo(other: ParsedAuraTag): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            if (patch != other.patch) return patch.compareTo(other.patch)
            if (channelRank != other.channelRank) return channelRank.compareTo(other.channelRank)
            if (channelRevision != other.channelRevision) return channelRevision.compareTo(other.channelRevision)
            return strategicSuffix.compareTo(other.strategicSuffix)
        }
    }

    /**
     * Parsea un tag semántico con sufijo estratégico (ej: `v0.1.0-beta.1a`, `v0.1.0-beta.2d`, `v1.0.0`).
     */
    fun parseTag(tag: String): ParsedAuraTag {
        val cleaned = tag.trim().removePrefix("v").removePrefix("V")
        val mainAndPre = cleaned.split("-", limit = 2)
        val versionParts = mainAndPre[0].split(".")
        val major = versionParts.getOrNull(0)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        val minor = versionParts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        val patch = versionParts.getOrNull(2)?.filter { it.isDigit() }?.toIntOrNull() ?: 0

        if (mainAndPre.size < 2) {
            return ParsedAuraTag(
                rawTag = tag.trim(),
                major = major,
                minor = minor,
                patch = patch,
                channelRank = 3,
                channelRevision = Int.MAX_VALUE,
                strategicSuffix = "z"
            )
        }

        val prePart = mainAndPre[1].lowercase(Locale.US)
        val channelRank = when {
            prePart.contains("alpha") -> 0
            prePart.contains("beta") -> 1
            prePart.contains("rc") -> 2
            else -> 1
        }

        // Extraer revisión y letra estratégica después de "beta." o "beta" (ej: "beta.1a" -> rev=1, suffix="a")
        val afterChannel = prePart
            .substringAfter("beta.", prePart.substringAfter("beta", prePart))
            .removePrefix(".")
            .removePrefix("-")

        val digits = afterChannel.takeWhile { it.isDigit() }
        val revision = digits.toIntOrNull() ?: 0
        val letters = afterChannel.drop(digits.length).takeWhile { it.isLetterOrDigit() }

        return ParsedAuraTag(
            rawTag = tag.trim(),
            major = major,
            minor = minor,
            patch = patch,
            channelRank = channelRank,
            channelRevision = revision,
            strategicSuffix = letters
        )
    }

    /**
     * Determina si `remoteTag` es estrictamente más reciente que `installedTag`.
     */
    fun isRemoteTagNewer(installedTag: String, remoteTag: String): Boolean {
        val cleanInstalled = installedTag.trim()
        val cleanRemote = remoteTag.trim()
        if (cleanRemote.isEmpty() || cleanInstalled.equals(cleanRemote, ignoreCase = true)) {
            return false
        }
        val parsedInstalled = parseTag(cleanInstalled)
        val parsedRemote = parseTag(cleanRemote)
        val cmp = parsedRemote.compareTo(parsedInstalled)
        return cmp > 0
    }

    /**
     * Obtiene el repositorio GitHub configurado (`usuario/repositorio`).
     * Si el usuario lo personalizó en Ajustes se usa ese valor; de lo contrario se usa `BuildConfig.GITHUB_REPO_SLUG`.
     */
    fun getConfiguredRepositorySlug(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val custom = prefs.getString(KEY_GITHUB_REPO_SLUG, null)?.trim().orEmpty()
        if (custom.isNotEmpty() && custom.contains("/")) {
            return normalizeRepoSlug(custom)
        }
        val buildDefault = BuildConfig.GITHUB_REPO_SLUG.trim()
        return if (buildDefault.isNotEmpty() && buildDefault.contains("/")) {
            normalizeRepoSlug(buildDefault)
        } else {
            DEFAULT_GITHUB_REPO_SLUG
        }
    }

    fun setConfiguredRepositorySlug(context: Context, rawSlugOrUrl: String) {
        val normalized = normalizeRepoSlug(rawSlugOrUrl)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GITHUB_REPO_SLUG, normalized)
            .apply()
    }

    fun isAutoCheckOnStartEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_CHECK_ON_START, true)
    }

    fun setAutoCheckOnStartEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_CHECK_ON_START, enabled)
            .apply()
    }

    fun dismissCurrentUpdateDialog(context: Context, tagToRemember: String? = null) {
        if (!tagToRemember.isNullOrBlank()) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_DISMISSED_TAG, tagToRemember)
                .apply()
        }
        _updateState.value = AppUpdateState.Idle
    }

    private val SAFE_REPO_SEGMENT_REGEX = Regex("^[a-zA-Z0-9._-]{1,100}$")

    /**
     * Normaliza tanto una URL completa (`https://github.com/usuario/repo`) como un slug (`usuario/repo`)
     * y verifica que no contenga secuencias de Path Traversal (`..`) ni parámetros inyectados (`?`, `#`, `%`).
     */
    fun normalizeRepoSlug(input: String): String {
        val trimmed = input.trim()
            .substringBefore("?")
            .substringBefore("#")
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        val parts = trimmed.split("/").filter { it.isNotBlank() }
        if (parts.size >= 2) {
            val owner = parts[0].trim()
            val repo = parts[1].trim()
            if (owner != "." && owner != ".." &&
                repo != "." && repo != ".." &&
                SAFE_REPO_SEGMENT_REGEX.matches(owner) &&
                SAFE_REPO_SEGMENT_REGEX.matches(repo)
            ) {
                return "$owner/$repo"
            }
        }
        return ""
    }

    /**
     * Detecta la arquitectura primaria del móvil para elegir el APK exacto.
     */
    fun getDevicePrimaryMobileAbi(): String {
        val supported = Build.SUPPORTED_ABIS?.toList().orEmpty()
        return when {
            supported.contains("arm64-v8a") -> "arm64-v8a"
            supported.contains("armeabi-v7a") -> "armeabi-v7a"
            else -> "universal"
        }
    }

    /**
     * Consulta en GitHub Releases si existe una versión Beta (o estable según el canal) más nueva que
     * `BuildConfig.VERSION_NAME` y selecciona automáticamente el APK correspondiente a la arquitectura del móvil.
     */
    suspend fun checkForUpdates(
        context: Context,
        manualCheck: Boolean = false
    ): AppUpdateState = withContext(Dispatchers.IO) {
        val repoSlug = getConfiguredRepositorySlug(context)
        if (repoSlug.isBlank() || !repoSlug.contains("/")) {
            val state = if (manualCheck) {
                AppUpdateState.Error("Configura primero el repositorio de GitHub (usuario/repositorio) para buscar actualizaciones.")
            } else {
                AppUpdateState.Idle
            }
            _updateState.value = state
            return@withContext state
        }

        if (!manualCheck && !isAutoCheckOnStartEnabled(context)) {
            return@withContext _updateState.value
        }

        _updateState.value = AppUpdateState.Checking(
            repositorySlug = repoSlug,
            message = "Consultando Releases de $repoSlug..."
        )

        try {
            val apiUrl = URL("https://$ALLOWED_API_HOST/repos/$repoSlug/releases?per_page=15")
            if (!apiUrl.host.equals(ALLOWED_API_HOST, ignoreCase = true)) {
                throw SecurityException("Host de API no permitido: ${apiUrl.host}")
            }

            val conn = (apiUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "AuraBeta-Android-Updater/${BuildConfig.VERSION_NAME}")
            }

            val code = conn.responseCode
            if (code == 404) {
                conn.disconnect()
                val msg = "No se encontraron Releases publicados en '$repoSlug' o el repositorio aún no tiene Pre-Releases."
                val state = if (manualCheck) AppUpdateState.Error(msg) else AppUpdateState.Idle
                _updateState.value = state
                return@withContext state
            }
            if (code !in 200..299) {
                conn.disconnect()
                val msg = "GitHub API devolvió código HTTP $code al consultar '$repoSlug'."
                val state = if (manualCheck) AppUpdateState.Error(msg) else AppUpdateState.Idle
                _updateState.value = state
                return@withContext state
            }

            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val releasesArray = JSONArray(responseBody)
            val currentTag = BuildConfig.VERSION_NAME
            val isBetaChannel = currentTag.contains("-beta", ignoreCase = true) ||
                context.packageName.contains("beta", ignoreCase = true)

            val deviceAbi = getDevicePrimaryMobileAbi()
            var bestCandidate: AppUpdateReleaseInfo? = null
            var bestParsedTag: ParsedAuraTag? = null

            for (i in 0 until releasesArray.length()) {
                val relObj = releasesArray.optJSONObject(i) ?: continue
                if (relObj.optBoolean("draft", false)) continue

                val tagName = relObj.optString("tag_name", "").trim()
                if (tagName.isEmpty()) continue

                val isPrerelease = relObj.optBoolean("prerelease", false)
                // Si estamos en el canal Beta, filtramos Pre-Releases o tags con '-beta'
                if (isBetaChannel && !tagName.contains("-beta", ignoreCase = true) && !isPrerelease) {
                    continue
                }

                if (!isRemoteTagNewer(currentTag, tagName)) {
                    continue
                }

                val assetsArray = relObj.optJSONArray("assets") ?: continue
                val matchedAsset = selectBestApkAsset(assetsArray, deviceAbi) ?: continue

                val candidateParsed = parseTag(tagName)
                if (bestParsedTag == null || candidateParsed > bestParsedTag) {
                    bestParsedTag = candidateParsed
                    bestCandidate = AppUpdateReleaseInfo(
                        tagName = tagName,
                        releaseTitle = relObj.optString("name", tagName).ifBlank { tagName },
                        isPrerelease = isPrerelease,
                        publishedAt = relObj.optString("published_at", "").take(10),
                        changelogNotes = relObj.optString("body", "").ifBlank {
                            "Nueva versión $tagName disponible para Aura Beta (Codename ${BuildConfig.APP_CODENAME})."
                        },
                        matchedAbi = matchedAsset.matchedAbiLabel,
                        apkAssetName = matchedAsset.assetName,
                        apkDownloadUrl = matchedAsset.downloadUrl,
                        apkSizeBytes = matchedAsset.sizeBytes,
                        sha256SumsUrl = matchedAsset.sha256SumsUrl,
                        htmlReleaseUrl = relObj.optString("html_url", "https://github.com/$repoSlug/releases")
                    )
                }
            }

            if (bestCandidate != null) {
                val dismissedTag = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_DISMISSED_TAG, null)

                if (!manualCheck && dismissedTag != null && dismissedTag.equals(bestCandidate.tagName, ignoreCase = true)) {
                    _updateState.value = AppUpdateState.Idle
                    return@withContext AppUpdateState.Idle
                }

                val availableState = AppUpdateState.UpdateAvailable(
                    releaseInfo = bestCandidate,
                    currentTag = currentTag
                )
                _updateState.value = availableState
                return@withContext availableState
            } else {
                val upToDateState = AppUpdateState.UpToDate(
                    currentTag = currentTag,
                    codename = BuildConfig.APP_CODENAME
                )
                _updateState.value = if (manualCheck) upToDateState else AppUpdateState.Idle
                return@withContext upToDateState
            }
        } catch (e: Exception) {
            AuraDebugManager.logWarning("AppUpdater", "Aviso al buscar actualizaciones de APK: ${e.message}")
            val errState = if (manualCheck) {
                AppUpdateState.Error("No se pudo conectar con GitHub Releases: ${e.localizedMessage ?: "Error de red"}")
            } else {
                AppUpdateState.Idle
            }
            _updateState.value = errState
            return@withContext errState
        }
    }

    private data class MatchedApkAsset(
        val assetName: String,
        val downloadUrl: String,
        val sizeBytes: Long,
        val matchedAbiLabel: String,
        val sha256SumsUrl: String?
    )

    /**
     * Selecciona entre los 3 APKs del Pre-Release (`arm64-v8a`, `armeabi-v7a`, `universal`)
     * el que corresponde exactamente al procesador del teléfono, usando `universal` como respaldo.
     */
    private fun selectBestApkAsset(assets: JSONArray, deviceAbi: String): MatchedApkAsset? {
        var exactAbiObj: JSONObject? = null
        var universalObj: JSONObject? = null
        var fallbackAnyApkObj: JSONObject? = null
        var sha256Url: String? = null

        for (j in 0 until assets.length()) {
            val asset = assets.optJSONObject(j) ?: continue
            val name = asset.optString("name", "")
            val url = asset.optString("browser_download_url", "")
            if (name.equals("SHA256SUMS.txt", ignoreCase = true) || name.endsWith("SHA256SUMS", ignoreCase = true)) {
                sha256Url = url
                continue
            }
            if (!name.endsWith(".apk", ignoreCase = true) || url.isBlank()) continue

            when {
                name.contains(deviceAbi, ignoreCase = true) -> exactAbiObj = asset
                name.contains("universal", ignoreCase = true) -> universalObj = asset
                fallbackAnyApkObj == null -> fallbackAnyApkObj = asset
            }
        }

        val chosen = exactAbiObj ?: universalObj ?: fallbackAnyApkObj ?: return null
        val chosenName = chosen.optString("name", "AuraBeta-update.apk")
        val chosenUrl = chosen.optString("browser_download_url", "")
        val chosenSize = chosen.optLong("size", 0L)
        val matchedLabel = when {
            exactAbiObj != null -> deviceAbi
            universalObj != null -> "universal"
            else -> "estándar"
        }

        return MatchedApkAsset(
            assetName = chosenName,
            downloadUrl = chosenUrl,
            sizeBytes = chosenSize,
            matchedAbiLabel = matchedLabel,
            sha256SumsUrl = sha256Url
        )
    }

    /**
     * Descarga el APK seleccionado en `Android/data/<paquete>/files/updates/`, verifica su hash SHA-256
     * si el Release incluye `SHA256SUMS.txt` y lanza automáticamente el instalador de paquetes de Android.
     */
    suspend fun downloadAndInstallUpdate(
        context: Context,
        releaseInfo: AppUpdateReleaseInfo
    ): AppUpdateState = withContext(Dispatchers.IO) {
        try {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val updatesDir = File(baseDir, "updates").apply { if (!exists()) mkdirs() }

            // Limpiar APKs antiguos en updates/ para no desperdiciar almacenamiento
            updatesDir.listFiles()?.forEach { old ->
                if (old.name.endsWith(".apk") || old.name.endsWith(".tmp")) {
                    old.delete()
                }
            }

            val safeFileName = releaseInfo.apkAssetName
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .ifBlank { "AuraBeta-${releaseInfo.tagName}.apk" }

            val tempApkFile = File(updatesDir, "$safeFileName.tmp")
            val finalApkFile = File(updatesDir, safeFileName)

            _updateState.value = AppUpdateState.Downloading(
                releaseInfo = releaseInfo,
                progressPercent = 0,
                downloadedBytes = 0L,
                totalBytes = releaseInfo.apkSizeBytes,
                statusMessage = "Conectando con GitHub Releases (${releaseInfo.matchedAbi})..."
            )

            // 1. Si existe SHA256SUMS.txt, obtener el hash esperado para este archivo APK
            val expectedSha256 = fetchExpectedSha256(releaseInfo.sha256SumsUrl, releaseInfo.apkAssetName)

            // 2. Descargar el archivo APK siguiendo redirecciones seguras de GitHub
            val connection = openValidatedDownloadConnection(releaseInfo.apkDownloadUrl)
            val totalLength = connection.contentLengthLong.takeIf { it > 0L } ?: releaseInfo.apkSizeBytes
            var downloaded = 0L

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(tempApkFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    var lastReportedPercent = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        val percent = if (totalLength > 0L) {
                            ((downloaded * 100L) / totalLength).toInt().coerceIn(0, 99)
                        } else {
                            50
                        }
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            _updateState.value = AppUpdateState.Downloading(
                                releaseInfo = releaseInfo,
                                progressPercent = percent,
                                downloadedBytes = downloaded,
                                totalBytes = totalLength,
                                statusMessage = "Descargando ${releaseInfo.apkAssetName} ($percent%)..."
                            )
                        }
                    }
                    output.flush()
                }
            }
            connection.disconnect()

            if (!tempApkFile.exists() || tempApkFile.length() < 100_000L) {
                tempApkFile.delete()
                val err = AppUpdateState.Error("El archivo APK descargado está incompleto o corrupto.")
                _updateState.value = err
                return@withContext err
            }

            // 3. Verificación criptográfica SHA-256
            var shaVerified = false
            if (!expectedSha256.isNullOrBlank()) {
                _updateState.value = AppUpdateState.Downloading(
                    releaseInfo = releaseInfo,
                    progressPercent = 100,
                    downloadedBytes = downloaded,
                    totalBytes = totalLength,
                    statusMessage = "Verificando firma de integridad SHA-256..."
                )
                val actualSha256 = computeFileSha256(tempApkFile)
                if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    tempApkFile.delete()
                    val err = AppUpdateState.Error("Alerta de seguridad: La suma SHA-256 del APK no coincide con SHA256SUMS.txt.")
                    _updateState.value = err
                    return@withContext err
                }
                shaVerified = true
            }

            if (finalApkFile.exists()) finalApkFile.delete()
            if (!tempApkFile.renameTo(finalApkFile)) {
                tempApkFile.copyTo(finalApkFile, overwrite = true)
                tempApkFile.delete()
            }

            val readyState = AppUpdateState.ReadyToInstall(
                releaseInfo = releaseInfo,
                apkFilePath = finalApkFile.absolutePath,
                sha256Verified = shaVerified
            )
            _updateState.value = readyState

            // 4. Disparar el instalador nativo del sistema Android en el hilo principal
            withContext(Dispatchers.Main) {
                promptAndroidPackageInstaller(context, finalApkFile)
            }

            return@withContext readyState
        } catch (e: Exception) {
            AuraDebugManager.logWarning("AppUpdater", "Error descargando actualización de APK: ${e.message}")
            val err = AppUpdateState.Error("Error al descargar el APK: ${e.localizedMessage ?: "Fallo de conexión"}")
            _updateState.value = err
            return@withContext err
        }
    }

    /**
     * Lanza el instalador oficial de paquetes de Android (`ACTION_VIEW` con `FileProvider`).
     * En Android 8.0+ (API 26+), verifica si el usuario otorgó permiso para instalar APKs desde esta fuente;
     * si aún no lo ha concedido, abre directamente la pantalla de permiso y luego permite instalar con 1 toque.
     */
    fun promptAndroidPackageInstaller(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = context.packageManager.canRequestPackageInstalls()
            if (!canInstall) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        val authority = "${context.packageName}.fileprovider"
        val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    private fun openValidatedDownloadConnection(urlStr: String): HttpURLConnection {
        var currentUrl = URL(urlStr)
        var redirects = 0
        while (redirects < 6) {
            val host = currentUrl.host.lowercase(Locale.US)
            if (currentUrl.protocol != "https" || ALLOWED_DOWNLOAD_HOSTS.none { host == it || host.endsWith(".$it") }) {
                throw SecurityException("Dominio de descarga no autorizado: $host")
            }
            val conn = (currentUrl.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "AuraBeta-Android-Updater/${BuildConfig.VERSION_NAME}")
            }
            val status = conn.responseCode
            if (status in 300..399) {
                val location = conn.getHeaderField("Location") ?: throw IllegalStateException("Redirección sin cabecera Location")
                conn.disconnect()
                currentUrl = URL(currentUrl, location)
                redirects++
            } else if (status in 200..299) {
                return conn
            } else {
                conn.disconnect()
                throw IllegalStateException("Servidor de descarga devolvió HTTP $status")
            }
        }
        throw IllegalStateException("Demasiadas redirecciones al descargar el APK")
    }

    private fun fetchExpectedSha256(sha256SumsUrl: String?, targetApkName: String): String? {
        if (sha256SumsUrl.isNullOrBlank()) return null
        return try {
            val conn = openValidatedDownloadConnection(sha256SumsUrl)
            val content = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            content.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.contains(targetApkName, ignoreCase = true) }
                .map { it.split(Regex("\\s+")).firstOrNull().orEmpty() }
                .firstOrNull { it.length == 64 }
        } catch (_: Exception) {
            null
        }
    }

    private fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(32 * 1024)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
