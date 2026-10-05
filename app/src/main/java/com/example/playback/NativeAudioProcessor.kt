package com.example.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Procesador de audio Media3 que canaliza las muestras de audio PCM
 * directamente hacia el motor DSP C++20 (o su respaldo de alta precisión).
 */
@OptIn(UnstableApi::class)
class NativeAudioProcessor : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        NativeAudioEngine.initDsp(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        val pos = inputBuffer.position()

        // Copiar los datos del buffer de entrada al buffer de salida
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()

        // Procesar en el buffer de salida mediante el motor DSP nativo
        NativeAudioEngine.processPcmBuffer(outputBuffer, 0, remaining)
    }

    override fun onFlush() {
        super.onFlush()
        NativeAudioEngine.flushBuffers()
    }
}

