package com.example.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Procesador de audio Media3 que canaliza las muestras de audio PCM
 * directamente hacia el motor DSP C++20 (o su analizador espectral de solo lectura en modo Streaming).
 *
 * Soporta flujos PCM de 16-bit, Float de 32-bit (habitual en decodificadores Opus/Vorbis/AAC de Streaming)
 * y 24/32-bit para que las 28 bandas del visualizador espectral reaccionen siempre en tiempo real.
 */
@OptIn(UnstableApi::class)
class NativeAudioProcessor : BaseAudioProcessor() {

    private var sourceEncoding: Int = C.ENCODING_INVALID

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encoding = inputAudioFormat.encoding
        val isSupported = encoding == C.ENCODING_PCM_16BIT ||
            encoding == C.ENCODING_PCM_FLOAT ||
            encoding == C.ENCODING_PCM_24BIT ||
            encoding == C.ENCODING_PCM_24BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_32BIT

        if (!isSupported) {
            sourceEncoding = C.ENCODING_INVALID
            return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.channelCount != 1 && inputAudioFormat.channelCount != 2) {
            sourceEncoding = C.ENCODING_INVALID
            return AudioProcessor.AudioFormat.NOT_SET
        }

        sourceEncoding = encoding
        NativeAudioEngine.initDsp(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            inputAudioFormat.channelCount,
            C.ENCODING_PCM_16BIT
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val remaining = limit - position
        if (remaining <= 0) return

        val outSize: Int
        val outputBuffer: ByteBuffer

        when (sourceEncoding) {
            C.ENCODING_PCM_FLOAT -> {
                val sampleCount = remaining / 4
                outSize = sampleCount * 2
                outputBuffer = replaceOutputBuffer(outSize)
                outputBuffer.order(ByteOrder.nativeOrder())
                inputBuffer.order(ByteOrder.nativeOrder())
                for (i in 0 until sampleCount) {
                    val f = inputBuffer.getFloat(position + i * 4)
                    val clamped = if (f.isNaN()) 0f else f.coerceIn(-1.0f, 1.0f)
                    val s16 = (clamped * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                    outputBuffer.putShort(s16)
                }
                inputBuffer.position(limit)
                outputBuffer.flip()
            }

            C.ENCODING_PCM_24BIT -> {
                val sampleCount = remaining / 3
                outSize = sampleCount * 2
                outputBuffer = replaceOutputBuffer(outSize)
                outputBuffer.order(ByteOrder.nativeOrder())
                for (i in 0 until sampleCount) {
                    val baseIdx = position + i * 3
                    val b1 = inputBuffer.get(baseIdx + 1).toInt() and 0xFF
                    val b2 = inputBuffer.get(baseIdx + 2).toInt()
                    outputBuffer.putShort(((b2 shl 8) or b1).toShort())
                }
                inputBuffer.position(limit)
                outputBuffer.flip()
            }

            C.ENCODING_PCM_24BIT_BIG_ENDIAN -> {
                val sampleCount = remaining / 3
                outSize = sampleCount * 2
                outputBuffer = replaceOutputBuffer(outSize)
                outputBuffer.order(ByteOrder.nativeOrder())
                for (i in 0 until sampleCount) {
                    val baseIdx = position + i * 3
                    val b0 = inputBuffer.get(baseIdx).toInt()
                    val b1 = inputBuffer.get(baseIdx + 1).toInt() and 0xFF
                    outputBuffer.putShort(((b0 shl 8) or b1).toShort())
                }
                inputBuffer.position(limit)
                outputBuffer.flip()
            }

            C.ENCODING_PCM_32BIT -> {
                val sampleCount = remaining / 4
                outSize = sampleCount * 2
                outputBuffer = replaceOutputBuffer(outSize)
                outputBuffer.order(ByteOrder.nativeOrder())
                inputBuffer.order(ByteOrder.nativeOrder())
                for (i in 0 until sampleCount) {
                    val s32 = inputBuffer.getInt(position + i * 4)
                    outputBuffer.putShort((s32 shr 16).toShort())
                }
                inputBuffer.position(limit)
                outputBuffer.flip()
            }

            else -> {
                outSize = remaining
                outputBuffer = replaceOutputBuffer(outSize)
                outputBuffer.order(ByteOrder.nativeOrder())
                outputBuffer.put(inputBuffer)
                outputBuffer.flip()
            }
        }

        if (!outputBuffer.isDirect || outSize <= 0) return

        // Modo Streaming Fases 1-3: DSP activo para EQ, Normalización y 8D/16D.
        // La Fase 4 (Reverb, VocalClarity, Crossfeed/Balance, Bit-Perfect, DJ) se
        // fuerza a bypass a nivel nativo en applyStreamingPolicy() para que suene
        // limpio aunque el usuario los tuviera activos en local. El visualizador
        // de 28 bandas sigue reaccionando vía processPcmBuffer (incluye espectro).
        if (isStreamingBypassActive) {
            NativeAudioEngine.analyzePcmBufferReadOnly(outputBuffer, 0, outSize)
            return
        }

        // Procesar efectos de audio + espectro en el buffer de salida mediante el motor DSP nativo C++20
        NativeAudioEngine.processPcmBuffer(outputBuffer, 0, outSize)
    }

    override fun onFlush() {
        super.onFlush()
        NativeAudioEngine.flushBuffers()
    }

    companion object {
        @Volatile
        var isStreamingBypassActive: Boolean = false
    }
}

