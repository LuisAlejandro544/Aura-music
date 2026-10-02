package com.example.data.importer

import android.content.Context
import android.net.Uri
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generador de pistas de audio demostrativas sintéticas (Synthwave / Lo-Fi).
 * Crea archivos WAV estéreo PCM válidos directamente en el almacenamiento interno
 * para que el usuario pueda reproducir música y probar el ecualizador de inmediato.
 */
object SampleMusicGenerator {

    suspend fun generateDemoTracks(context: Context): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val storageManager = com.example.data.storage.AppStorageManager(context)
        val musicDir = storageManager.songsDir

        val demoDefinitions = listOf(
            Triple("Aura Neon Horizon", "Synthwave Collective", "Cyberpunk Nights") to listOf(220.0, 277.18, 329.63, 440.0, 329.63),
            Triple("Midnight Starlight", "Aurora Beats", "Lofi Chillwave") to listOf(174.61, 220.0, 261.63, 349.23, 261.63),
            Triple("Velvet Pulse", "Deep Space Sound", "Electronic Dreams") to listOf(196.0, 246.94, 293.66, 392.0, 293.66),
            Triple("Emerald Sunset", "Solaris Echoes", "Ambient Chill") to listOf(261.63, 329.63, 392.0, 523.25, 392.0)
        )

        demoDefinitions.forEachIndexed { index, (meta, melody) ->
            val (title, artist, album) = meta
            val file = File(musicDir, "demo_track_${index + 1}.wav")
            if (!file.exists() || file.length() < 1000) {
                createSynthesizedWav(file, durationSeconds = 30, melody = melody)
            }

            val uri = Uri.fromFile(file)
            tracks.add(
                Track(
                    id = 0,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = 30_000L,
                    uriString = uri.toString(),
                    albumArtPath = null,
                    mimeType = "audio/wav",
                    dateAdded = System.currentTimeMillis() - (index * 60_000),
                    isFavorite = index == 0,
                    playCount = 0,
                    folderName = "Aura Demos",
                    fileSizeFormatted = "2.6 MB"
                )
            )
        }

        tracks
    }

    private fun createSynthesizedWav(outputFile: File, durationSeconds: Int, melody: List<Double>) {
        val sampleRate = 44100
        val numChannels = 2
        val bitsPerSample = 16
        val totalSamples = durationSeconds * sampleRate
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = (numChannels * bitsPerSample / 8).toShort()
        val dataSize = totalSamples * numChannels * (bitsPerSample / 8)

        FileOutputStream(outputFile).use { fos ->
            // Escribir cabecera WAV (RIFF)
            fos.write("RIFF".toByteArray())
            fos.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(36 + dataSize).array())
            fos.write("WAVE".toByteArray())
            fos.write("fmt ".toByteArray())
            fos.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(16).array()) // Subchunk1Size (16 for PCM)
            fos.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(1).array()) // AudioFormat (1 for PCM)
            fos.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(numChannels.toShort()).array())
            fos.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sampleRate).array())
            fos.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(byteRate).array())
            fos.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(blockAlign).array())
            fos.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(bitsPerSample.toShort()).array())
            fos.write("data".toByteArray())
            fos.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataSize).array())

            // Generar muestras sintetizadas con modulación agradable
            val buffer = ByteArray(2048)
            var byteIdx = 0
            val melodyCount = melody.size

            for (i in 0 until totalSamples) {
                val time = i.toDouble() / sampleRate
                // Cambiar tono melódico cada 0.6 segundos
                val noteIndex = ((time / 0.6).toInt()) % melodyCount
                val freq = melody[noteIndex]

                // Onda senoidal fundamental + armónicos suaves
                val fundamental = sin(2.0 * PI * freq * time)
                val subBass = sin(2.0 * PI * (freq / 2.0) * time) * 0.4
                val chordHarmonic = sin(2.0 * PI * (freq * 1.5) * time) * 0.25

                // Envolvente de decaimiento rítmico
                val noteTime = time % 0.6
                val envelope = kotlin.math.exp(-noteTime * 2.2)

                val sampleVal = ((fundamental + subBass + chordHarmonic) * envelope * 0.6 * Short.MAX_VALUE).toInt()
                val clamped = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

                // Canal izquierdo
                buffer[byteIdx++] = (clamped.toInt() and 0xFF).toByte()
                buffer[byteIdx++] = ((clamped.toInt() shr 8) and 0xFF).toByte()

                // Canal derecho con ligero retardo estéreo espacial
                val rightSample = (clamped * 0.9).toInt().toShort()
                buffer[byteIdx++] = (rightSample.toInt() and 0xFF).toByte()
                buffer[byteIdx++] = ((rightSample.toInt() shr 8) and 0xFF).toByte()

                if (byteIdx >= buffer.size) {
                    fos.write(buffer, 0, byteIdx)
                    byteIdx = 0
                }
            }

            if (byteIdx > 0) {
                fos.write(buffer, 0, byteIdx)
            }
        }
    }
}
