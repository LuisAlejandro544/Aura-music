package com.example.core.data.importer

import com.example.core.model.LyricLine
import java.util.regex.Pattern

/**
 * Parser .LRC ([mm:ss.xx]) y limpieza segura de títulos para LRCLIB.
 * Rol arquitectónico: submódulo de LyricsManager (fachada pública intacta).
 */
internal object LyricsTextParsing {
    private val LRC_LINE_PATTERN: Pattern = Pattern.compile(
        """\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\](.*)"""
    )

    /**
     * Parsea un texto con formato .LRC y retorna las líneas ordenadas cronológicamente.
     */
    fun parse(content: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val rawLines = content.lines()

        for (raw in rawLines) {
            val trimmed = raw.trim()
            if (trimmed.isBlank() || trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") ||
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:") || trimmed.startsWith("[length:")
            ) {
                continue
            }

            val matcher = LRC_LINE_PATTERN.matcher(trimmed)
            if (matcher.find()) {
                val minStr = matcher.group(1) ?: "0"
                val secStr = matcher.group(2) ?: "0"
                val msStr = matcher.group(3) ?: "0"
                val text = matcher.group(4)?.trim() ?: ""

                val minutes = minStr.toLongOrNull() ?: 0L
                val seconds = secStr.toLongOrNull() ?: 0L
                val millis = when (msStr.length) {
                    1 -> (msStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (msStr.toLongOrNull() ?: 0L) * 10L
                    else -> msStr.take(3).toLongOrNull() ?: 0L
                }

                val totalMs = (minutes * 60L * 1000L) + (seconds * 1000L) + millis
                lines.add(LyricLine(timeMs = totalMs, text = text))
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    fun cleanSearchTerm(raw: String): String {
        return raw
            .replace(Regex("""\.(mp3|m4a|wav|flac|ogg)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\((?:official|video|audio|lyrics|remastered|hd|4k)[^)]*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[(?:official|video|audio|lyrics|remastered|hd|4k)[^\]]*\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+\((?:feat\.|ft\.)[^)]*\)""", RegexOption.IGNORE_CASE), "") // Borra "feat" SOLO si está entre paréntesis
            .replace("_", " ")
            .trim()
    }
}
