package com.example.core.data.importer

import android.content.Context
import android.net.Uri
import com.example.debug.AuraDebugManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Orquesta detección + mux físico + respaldo por clipping para cualquier códec.
 * Rol arquitectónico: submódulo de AudioSilenceTrimmer (fachada pública intacta).
 */
internal object SilenceTrimProcessor {
    suspend fun processFile(
        context: Context,
        audioFile: File,
        originalDurationMs: Long
    ): AudioSilenceTrimmer.SilenceTrimResult = withContext(Dispatchers.IO) {
        val fileUri = Uri.fromFile(audioFile)
        val totalDurationMs = if (originalDurationMs > 0L) {
            originalDurationMs
        } else {
            SilenceMediaUtils.extractDurationMs(context, fileUri)
        }

        if (totalDurationMs < 2500L) {
            return@withContext AudioSilenceTrimmer.SilenceTrimResult(
                wasTrimmed = false,
                startTrimMs = 0L,
                endTrimMs = totalDurationMs,
                newDurationMs = totalDurationMs,
                outputUriString = fileUri.toString()
            )
        }

        val detected = SilenceDetector.detectBoundsUs(context, fileUri, totalDurationMs * 1000L)
        val startMs = (detected.first / 1000L).coerceIn(0L, totalDurationMs / 3)
        val endMs = (detected.second / 1000L).coerceIn((totalDurationMs * 2) / 3, totalDurationMs)

        val hasLeadingSilence = startMs >= SilenceTrimConfig.MIN_TRIM_START_MS
        val hasTrailingSilence = (totalDurationMs - endMs) >= SilenceTrimConfig.MIN_TRIM_END_MS

        if (!hasLeadingSilence && !hasTrailingSilence) {
            return@withContext AudioSilenceTrimmer.SilenceTrimResult(
                wasTrimmed = false,
                startTrimMs = 0L,
                endTrimMs = totalDurationMs,
                newDurationMs = totalDurationMs,
                outputUriString = fileUri.toString()
            )
        }

        val effectiveStartMs = if (hasLeadingSilence) startMs else 0L
        val effectiveEndMs = if (hasTrailingSilence) endMs else totalDurationMs
        val trimmedDurationMs = (effectiveEndMs - effectiveStartMs).coerceAtLeast(1000L)

        // Intentar recorte físico sin pérdida por Direct Stream Copy si es contenedor MP4/M4A
        val tempTrimmedFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}_trimmed.m4a")
        val physicalSuccess = SilenceMuxer.muxTrimmed(
            context = context,
            sourceUri = fileUri,
            outputFile = tempTrimmedFile,
            startUs = effectiveStartMs * 1000L,
            endUs = effectiveEndMs * 1000L
        )

        if (physicalSuccess && tempTrimmedFile.exists() && tempTrimmedFile.length() > 1024L) {
            try {
                tempTrimmedFile.copyTo(audioFile, overwrite = true)
                tempTrimmedFile.delete()
                ClippingBoundsStore.remove(context, fileUri.toString())
                AuraDebugManager.logInfo(
                    "AudioSilenceTrimmer",
                    "Silencio recortado físicamente: inicio=${effectiveStartMs}ms, fin=${totalDurationMs - effectiveEndMs}ms"
                )
                return@withContext AudioSilenceTrimmer.SilenceTrimResult(
                    wasTrimmed = true,
                    startTrimMs = effectiveStartMs,
                    endTrimMs = effectiveEndMs,
                    newDurationMs = trimmedDurationMs,
                    outputUriString = fileUri.toString()
                )
            } catch (_: Throwable) {
                tempTrimmedFile.delete()
            }
        } else {
            tempTrimmedFile.delete()
        }

        // Respaldo universal sin pérdida mediante ClippingConfiguration persistido para cualquier códec
        ClippingBoundsStore.save(context, fileUri.toString(), effectiveStartMs, effectiveEndMs)
        AuraDebugManager.logInfo(
            "AudioSilenceTrimmer",
            "Límites de silencio registrados sin recodificación: inicio=${effectiveStartMs}ms, fin=${effectiveEndMs}ms"
        )

        AudioSilenceTrimmer.SilenceTrimResult(
            wasTrimmed = true,
            startTrimMs = effectiveStartMs,
            endTrimMs = effectiveEndMs,
            newDurationMs = trimmedDurationMs,
            outputUriString = fileUri.toString()
        )
    }

    suspend fun processUri(
        context: Context,
        uri: Uri,
        originalDurationMs: Long
    ): AudioSilenceTrimmer.SilenceTrimResult = withContext(Dispatchers.IO) {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            if (file.exists()) {
                return@withContext processFile(context, file, originalDurationMs)
            }
        }

        val totalDurationMs = if (originalDurationMs > 0L) {
            originalDurationMs
        } else {
            SilenceMediaUtils.extractDurationMs(context, uri)
        }

        if (totalDurationMs < 2500L) {
            return@withContext AudioSilenceTrimmer.SilenceTrimResult(
                wasTrimmed = false,
                startTrimMs = 0L,
                endTrimMs = totalDurationMs,
                newDurationMs = totalDurationMs,
                outputUriString = uri.toString()
            )
        }

        val detected = SilenceDetector.detectBoundsUs(context, uri, totalDurationMs * 1000L)
        val startMs = (detected.first / 1000L).coerceIn(0L, totalDurationMs / 3)
        val endMs = (detected.second / 1000L).coerceIn((totalDurationMs * 2) / 3, totalDurationMs)

        val hasLeadingSilence = startMs >= SilenceTrimConfig.MIN_TRIM_START_MS
        val hasTrailingSilence = (totalDurationMs - endMs) >= SilenceTrimConfig.MIN_TRIM_END_MS

        if (!hasLeadingSilence && !hasTrailingSilence) {
            return@withContext AudioSilenceTrimmer.SilenceTrimResult(
                wasTrimmed = false,
                startTrimMs = 0L,
                endTrimMs = totalDurationMs,
                newDurationMs = totalDurationMs,
                outputUriString = uri.toString()
            )
        }

        val effectiveStartMs = if (hasLeadingSilence) startMs else 0L
        val effectiveEndMs = if (hasTrailingSilence) endMs else totalDurationMs
        val trimmedDurationMs = (effectiveEndMs - effectiveStartMs).coerceAtLeast(1000L)

        ClippingBoundsStore.save(context, uri.toString(), effectiveStartMs, effectiveEndMs)

        AudioSilenceTrimmer.SilenceTrimResult(
            wasTrimmed = true,
            startTrimMs = effectiveStartMs,
            endTrimMs = effectiveEndMs,
            newDurationMs = trimmedDurationMs,
            outputUriString = uri.toString()
        )
    }
}
