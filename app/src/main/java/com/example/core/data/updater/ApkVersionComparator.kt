package com.example.core.data.updater

import java.util.Locale

/**
 * Comparador semántico de tags de versión de Aura Music.
 * Rol arquitectónico: ordena tags dinámicos (`-beta.1a`, `-beta-1a`, estables)
 * para decidir si un Release remoto es más reciente que el instalado.
 */
object ApkVersionComparator {

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
}
