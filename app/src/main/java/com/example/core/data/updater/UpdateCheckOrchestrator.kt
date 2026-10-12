package com.example.core.data.updater

import android.content.Context
import com.example.BuildConfig
import com.example.debug.AuraDebugManager
import com.example.core.model.AppUpdateReleaseInfo
import com.example.core.model.AppUpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Orquestador de búsqueda de actualizaciones en GitHub Releases.
 * Rol arquitectónico: consulta la API, filtra por canal Beta, compara tags y elige
 * el APK de la arquitectura del móvil; la fachada AppReleaseUpdater delega aquí.
 */
internal object UpdateCheckOrchestrator {

    private const val ALLOWED_API_HOST = "api.github.com"

    internal suspend fun checkForUpdates(
        context: Context,
        manualCheck: Boolean = false,
        setState: (AppUpdateState) -> Unit,
        getState: () -> AppUpdateState
    ): AppUpdateState = withContext(Dispatchers.IO) {
        val repoSlug = UpdatePreferences.getConfiguredRepositorySlug(context)
        if (repoSlug.isBlank() || !repoSlug.contains("/")) {
            val state = if (manualCheck) {
                AppUpdateState.Error("Configura primero el repositorio de GitHub (usuario/repositorio) para buscar actualizaciones.")
            } else {
                AppUpdateState.Idle
            }
            setState(state)
            return@withContext state
        }

        if (!manualCheck && !UpdatePreferences.isAutoCheckOnStartEnabled(context)) {
            return@withContext getState()
        }

        setState(AppUpdateState.Checking(
            repositorySlug = repoSlug,
            message = "Consultando Releases de $repoSlug..."
        ))

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
                setState(state)
                return@withContext state
            }
            if (code !in 200..299) {
                conn.disconnect()
                val msg = "GitHub API devolvió código HTTP $code al consultar '$repoSlug'."
                val state = if (manualCheck) AppUpdateState.Error(msg) else AppUpdateState.Idle
                setState(state)
                return@withContext state
            }

            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val releasesArray = JSONArray(responseBody)
            val currentTag = BuildConfig.VERSION_NAME
            val isBetaChannel = currentTag.contains("-beta", ignoreCase = true) ||
                context.packageName.contains("beta", ignoreCase = true)

            val deviceAbi = ApkDownloadManager.getDevicePrimaryMobileAbi()
            var bestCandidate: AppUpdateReleaseInfo? = null
            var bestParsedTag: ApkVersionComparator.ParsedAuraTag? = null

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

                if (!ApkVersionComparator.isRemoteTagNewer(currentTag, tagName)) {
                    continue
                }

                val assetsArray = relObj.optJSONArray("assets") ?: continue
                val matchedAsset = ApkDownloadManager.selectBestApkAsset(assetsArray, deviceAbi) ?: continue

                val candidateParsed = ApkVersionComparator.parseTag(tagName)
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
                val dismissedTag = UpdatePreferences.getDismissedTag(context)

                if (!manualCheck && dismissedTag != null && dismissedTag.equals(bestCandidate.tagName, ignoreCase = true)) {
                    setState(AppUpdateState.Idle)
                    return@withContext AppUpdateState.Idle
                }

                val availableState = AppUpdateState.UpdateAvailable(
                    releaseInfo = bestCandidate,
                    currentTag = currentTag
                )
                setState(availableState)
                return@withContext availableState
            } else {
                val upToDateState = AppUpdateState.UpToDate(
                    currentTag = currentTag,
                    codename = BuildConfig.APP_CODENAME
                )
                setState(if (manualCheck) upToDateState else AppUpdateState.Idle)
                return@withContext upToDateState
            }
        } catch (e: Exception) {
            AuraDebugManager.logWarning("AppUpdater", "Aviso al buscar actualizaciones de APK: ${e.message}")
            val errState = if (manualCheck) {
                AppUpdateState.Error("No se pudo conectar con GitHub Releases: ${e.localizedMessage ?: "Error de red"}")
            } else {
                AppUpdateState.Idle
            }
            setState(errState)
            return@withContext errState
        }
    }
}
