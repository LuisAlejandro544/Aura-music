package com.example.core.data.updater

import android.content.Context
import com.example.BuildConfig

/**
 * Preferencias del actualizador de APK (repositorio, auto-chequeo y diálogos omitidos).
 * Rol arquitectónico: persiste la configuración en SharedPreferences.
 */
internal object UpdatePreferences {

    internal const val PREFS_NAME = "aura_app_updater_prefs"
    internal const val KEY_GITHUB_REPO_SLUG = "github_repo_slug"
    internal const val KEY_DISMISSED_TAG = "dismissed_release_tag"
    internal const val KEY_AUTO_CHECK_ON_START = "auto_check_on_start"
    const val DEFAULT_GITHUB_REPO_SLUG = "LuisAlejandro544/Aura-music"
    const val DEFAULT_GITHUB_RELEASES_URL = "https://github.com/LuisAlejandro544/Aura-music/releases"

    private val SAFE_REPO_SEGMENT_REGEX = Regex("^[a-zA-Z0-9._-]{1,100}$")

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

    fun getDismissedTag(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DISMISSED_TAG, null)
    }

    fun rememberDismissedTag(context: Context, tagToRemember: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISMISSED_TAG, tagToRemember)
            .apply()
    }
}
