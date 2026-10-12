package com.example.core.data.updater

import android.content.Context
import com.example.core.model.AppUpdateReleaseInfo
import com.example.core.model.AppUpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Motor de Actualización Automática de APK en Caliente (OTA App Updater).
 *
 * Rol arquitectónico: fachada pública estable; delega la comparación de tags en
 * ApkVersionComparator, las preferencias en UpdatePreferences, la descarga en
 * ApkDownloadManager y la consulta de Releases en UpdateCheckOrchestrator.
 */
object AppReleaseUpdater {

    const val DEFAULT_GITHUB_REPO_SLUG = UpdatePreferences.DEFAULT_GITHUB_REPO_SLUG
    const val DEFAULT_GITHUB_RELEASES_URL = UpdatePreferences.DEFAULT_GITHUB_RELEASES_URL

    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    fun parseTag(tag: String): ApkVersionComparator.ParsedAuraTag = ApkVersionComparator.parseTag(tag)

    fun isRemoteTagNewer(installedTag: String, remoteTag: String): Boolean =
        ApkVersionComparator.isRemoteTagNewer(installedTag, remoteTag)

    fun normalizeRepoSlug(input: String): String = UpdatePreferences.normalizeRepoSlug(input)

    fun getConfiguredRepositorySlug(context: Context): String =
        UpdatePreferences.getConfiguredRepositorySlug(context)

    fun setConfiguredRepositorySlug(context: Context, rawSlugOrUrl: String) =
        UpdatePreferences.setConfiguredRepositorySlug(context, rawSlugOrUrl)

    fun isAutoCheckOnStartEnabled(context: Context): Boolean =
        UpdatePreferences.isAutoCheckOnStartEnabled(context)

    fun setAutoCheckOnStartEnabled(context: Context, enabled: Boolean) =
        UpdatePreferences.setAutoCheckOnStartEnabled(context, enabled)

    fun dismissCurrentUpdateDialog(context: Context, tagToRemember: String? = null) {
        if (!tagToRemember.isNullOrBlank()) {
            UpdatePreferences.rememberDismissedTag(context, tagToRemember)
        }
        _updateState.value = AppUpdateState.Idle
    }

    fun getDevicePrimaryMobileAbi(): String = ApkDownloadManager.getDevicePrimaryMobileAbi()

    suspend fun checkForUpdates(
        context: Context,
        manualCheck: Boolean = false
    ): AppUpdateState = UpdateCheckOrchestrator.checkForUpdates(
        context = context,
        manualCheck = manualCheck,
        setState = { _updateState.value = it },
        getState = { _updateState.value }
    )

    suspend fun downloadAndInstallUpdate(
        context: Context,
        releaseInfo: AppUpdateReleaseInfo
    ): AppUpdateState = ApkDownloadManager.downloadAndInstallUpdate(
        context = context,
        releaseInfo = releaseInfo,
        setState = { _updateState.value = it }
    )

    fun promptAndroidPackageInstaller(context: Context, apkFile: File) =
        ApkDownloadManager.promptAndroidPackageInstaller(context, apkFile)
}
