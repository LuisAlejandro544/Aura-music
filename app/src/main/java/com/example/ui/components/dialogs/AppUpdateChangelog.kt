package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary


/**
 * Notas del Pre-Release (chanelog-beta.md).
 * Rol arquitectónico: sección de AppUpdateDialog (fachada pública intacta).
 *
 * v0.2.1-beta.1f:
 * - Convierte el Markdown crudo de GitHub (`#`, `>`, `**`, enlaces `[t](u)`,
 *   tablas `|`) en texto plano legible, porque la mayoría de usuarios no abre
 *   el changelog antes de descargar y veía símbolos crudos (ver captura).
 * - Sin scroll anidado fantasma: el scroll vive solo en esta caja con altura
 *   acotada; el diálogo padre ya no hace scroll completo.
 */
@Composable
fun AppUpdateChangelog(
    releaseInfo: com.example.core.model.AppUpdateReleaseInfo,
    modifier: Modifier = Modifier
) {
    val cleanNotes = remember(releaseInfo.tagName, releaseInfo.changelogNotes) {
        formatChangelogForDisplay(releaseInfo.changelogNotes, releaseInfo.tagName)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.NewReleases,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Novedades de ${releaseInfo.releaseTitle.ifBlank { releaseInfo.tagName }}",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                ),
                maxLines = 2
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Resumen fijo: esta actualización incluye la base + el fix (para quienes no leen el changelog).
        Text(
            text = "Incluye todo lo de la v0.2.0-beta-1a más las correcciones v0.2.1-beta.1f (formato auto, anti-cierres y Explorar).",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 16.sp
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp, max = 260.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = cleanNotes,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                )
            }
        }
    }
}

/**
 * Limpia Markdown a texto legible: quita `#`, `>`, `**`, backticks, convierte
 * enlaces `[texto](url)` a "texto (url)" y tablas `|` a líneas con "•".
 * Función pura y testeable, sin dependencias de Android.
 */
fun formatChangelogForDisplay(raw: String, tagName: String): String {
    if (raw.isBlank()) {
        return "Nueva versión $tagName disponible para Aura Beta. Incluye todo lo de la v0.2.0-beta-1a más las correcciones v0.2.1-beta.1f."
    }
    val linkRegex = Regex("""\[([^\]]+)]\(([^)]+)\)""")
    val lines = raw.lines()
    val out = StringBuilder()
    var inTable = false
    for (rawLine in lines) {
        var line = rawLine.trimEnd()
        // Tablas Markdown -> lista legible
        if (line.trim().startsWith("|")) {
            val cells = line.trim().trim('|').split("|").map {
                it.trim().replace("**", "").replace("`", "")
            }.filter { it.isNotBlank() && !it.all { c -> c == ':' || c == '-' || c == ' ' } }
            if (cells.isNotEmpty()) {
                inTable = true
                out.appendLine("• " + cells.joinToString(" — "))
            }
            continue
        } else {
            if (inTable) {
                inTable = false
                out.appendLine()
            }
        }
        line = linkRegex.replace(line) { mr ->
            val text = mr.groupValues[1].trim()
            val url = mr.groupValues[2].trim()
            if (text == url) url else "$text ($url)"
        }
        line = line.replace(Regex("""^#{1,6}\s*"""), "")
        line = line.replace(Regex("""^>\s?"""), "")
        line = line.replace("**", "")
        line = line.replace("__", "")
        line = line.replace("`", "")
        line = line.replace(Regex("""^---+\s*$"""), "——————————")
        line = line.replace(Regex("""^\*\s+"""), "• ")
        line = line.replace(Regex("""^-\s+"""), "• ")
        out.appendLine(line.trimEnd())
    }
    // Colapsar más de 2 saltos seguidos y recortar a ~6000 caracteres para no saturar el diálogo.
    var result = out.toString().replace(Regex("""\n{3,}"""), "\n\n").trim()
    if (result.length > 6000) {
        result = result.take(6000).trimEnd() + "\n\n…(ver changelog completo en GitHub Releases)"
    }
    return result
}
