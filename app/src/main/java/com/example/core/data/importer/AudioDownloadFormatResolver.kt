package com.example.core.data.importer

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import com.example.core.model.StreamingAudioQuality
import com.example.debug.AuraDebugManager

/**
 * Aura Music - Selector Automático de Formato de Descarga según el Teléfono (v0.2.1-beta.1f).
 *
 * Rol arquitectónico:
 * - Decide automáticamente entre Opus (.opus) y M4A/AAC (.m4a) según lo que el
 *   procesador / decodificador del teléfono realmente soporta, evitando crashes
 *   en gamas bajas o SoCs antiguos sin decodificador Opus.
 * - Respeta la preferencia del usuario (Ajustes > Calidad de Audio) pero la
 *   corrige si el dispositivo no puede reproducirla: nunca se devuelve Opus en
 *   un teléfono sin `audio/opus` en MediaCodecList.
 * - Toda la lógica es síncrona, cacheada y sin I/O pesado; segura para llamar
 *   desde Dispatchers.IO o Main.
 */
object AudioDownloadFormatResolver {

    private const val TAG = "AudioFormatResolver"

    @Volatile
    private var cachedOpusSupport: Boolean? = null

    /**
     * True si el teléfono puede decodificar Opus de forma nativa
     * (ExoPlayer + MediaCodec). Se cachea por proceso.
     */
    fun supportsOpus(context: Context? = null): Boolean {
        cachedOpusSupport?.let { return it }
        val supported = try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val infos = codecList.codecInfos
            var foundDecoder = false
            for (info in infos) {
                if (info.isEncoder) continue
                val types = try { info.supportedTypes } catch (_: Throwable) { emptyArray() }
                for (t in types) {
                    if (t.equals("audio/opus", ignoreCase = true) ||
                        t.equals("audio/vorbis", ignoreCase = true)
                    ) {
                        foundDecoder = true
                        break
                    }
                }
                if (foundDecoder) break
            }
            // Fallback: Android 5.0+ (API 21) declara Opus en Matroska/WebM vía
            // extractor nativo aunque algún OEM oculte el codec en la lista.
            if (!foundDecoder && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Solo aceptamos el fallback si no es un dispositivo de RAM muy baja
                // con decodificadores recortados (Android Go).
                !isLowRamDevice(context)
            } else foundDecoder
        } catch (_: Throwable) {
            // Ante cualquier duda, M4A/AAC es universal desde API 16.
            false
        }
        cachedOpusSupport = supported
        return supported
    }

    fun isLowRamDevice(context: Context?): Boolean {
        return try {
            if (context == null) return false
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.isLowRamDevice == true
        } catch (_: Throwable) {
            false
        }
    }

    /** RAM total aproximada; true si el equipo es de gama baja (<= 2 GB). */
    fun isLowMemoryDevice(context: Context?): Boolean {
        return try {
            if (context == null) return false
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            memInfo.totalMem in 1L..(2L * 1024L * 1024L * 1024L)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Resuelve la calidad efectiva a usar: respeta la pedida salvo que el
     * teléfono no soporte Opus (fuerza M4A) o que sea AUTO_HIFI (elige sola).
     */
    fun resolveEffectiveQuality(
        requested: StreamingAudioQuality,
        context: Context? = null
    ): StreamingAudioQuality {
        val opusOk = supportsOpus(context)
        return when (requested) {
            StreamingAudioQuality.AUTO_HIFI -> if (opusOk) {
                StreamingAudioQuality.ALTA_OPUS_160K
            } else {
                StreamingAudioQuality.ESTANDAR_M4A_128K
            }
            StreamingAudioQuality.AHORRO_OPUS_48K,
            StreamingAudioQuality.ALTA_OPUS_160K -> if (opusOk) {
                requested
            } else {
                AuraDebugManager.logWarning(
                    TAG,
                    "Teléfono sin decodificador Opus: se fuerza M4A 128k en vez de ${requested.title}."
                )
                StreamingAudioQuality.ESTANDAR_M4A_128K
            }
            StreamingAudioQuality.ESTANDAR_M4A_128K -> requested
        }
    }

    /** Extensión local segura (.opus solo si el teléfono lo reproduce). */
    fun resolveLocalExtension(
        requested: StreamingAudioQuality,
        context: Context? = null
    ): String {
        val effective = resolveEffectiveQuality(requested, context)
        return if (effective.prefersOpus && effective != StreamingAudioQuality.AUTO_HIFI) {
            // Doble verificación: aunque la calidad pida Opus, sin codec => m4a.
            if (supportsOpus(context)) "opus" else "m4a"
        } else {
            "m4a"
        }
    }

    /** MIME seguro acorde a la extensión resuelta. */
    fun resolveLocalMimeType(
        requested: StreamingAudioQuality,
        context: Context? = null
    ): String {
        return if (resolveLocalExtension(requested, context) == "opus") "audio/opus" else "audio/mp4"
    }

    /** Espacio libre en el volumen de destino; false si hay menos de [minFreeBytes]. */
    fun hasEnoughFreeSpace(targetDir: java.io.File?, minFreeBytes: Long = 25L * 1024L * 1024L): Boolean {
        return try {
            if (targetDir == null) return false
            targetDir.mkdirs()
            val free = try {
                android.os.StatFs(targetDir.absolutePath).let {
                    it.availableBytes
                }
            } catch (_: Throwable) {
                targetDir.usableSpace
            }
            free >= minFreeBytes
        } catch (_: Throwable) {
            false
        }
    }
}
