package com.example.data.importer.mixtape

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.data.importer.IncomingMediaHandler
import com.example.data.storage.AppStorageManager
import com.example.debug.AuraDebugManager
import com.example.model.Track
import com.example.model.TrackAcousticProfile
import java.io.File
import java.io.FileInputStream
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aura Music - Analizador Acústico Real de BPM y Energía para Auto-Remix (`AutoRemixAcousticAnalyzer`).
 *
 * Responsabilidades:
 * - Decodificar en segundo plano (`Dispatchers.IO`) una ventana central representativa (~8.5 segundos)
 *   de una canción local mediante `MediaExtractor` y `MediaCodec`, sin bloquear jamás la interfaz.
 * - Calcular la envolvente de ataques de percusión y subgraves (Onset Detection Envelope a 100 Hz)
 *   y estimar el BPM exacto en el rango musical de 68 a 176 BPM mediante autocorrelación de intervalos.
 * - Calcular el nivel de energía acústica (RMS normalizado + densidad rítmica de transitorios) de 0 a 100%.
 * - Mantener una caché concurrente en memoria indexada por `track.id` para que consultas sucesivas
 *   en el diálogo de Auto-Remix sean instantáneas (< 1 ms).
 * - Proveer un estimador determinista instantáneo mientras la decodificación en segundo plano se completa
 *   o como respaldo si un contenedor exótico no expone pista PCM estándar en `MediaExtractor`.
 */
object AutoRemixAcousticAnalyzer {

    private const val TAG = "AutoRemixAcousticAnalyzer"
    private const val ENVELOPE_RATE_HZ = 100
    private const val MIN_BPM = 68
    private const val MAX_BPM = 176

    private val profileCache = ConcurrentHashMap<Long, TrackAcousticProfile>()

    /**
     * Obtiene el perfil en caché si ya fue analizado previamente, o una estimación determinista rápida
     * basada en las propiedades de la canción para uso inmediato sin bloqueo en la UI.
     */
    fun getCachedOrFastProfile(track: Track): TrackAcousticProfile {
        profileCache[track.id]?.let { return it }
        return buildDeterministicFallbackProfile(track)
    }

    /**
     * Analiza de forma asíncrona en `Dispatchers.IO` una canción local para detectar su BPM real
     * y su porcentaje de energía acústica, guardando el resultado en caché.
     */
    suspend fun analyzeTrack(context: Context, track: Track): TrackAcousticProfile = withContext(Dispatchers.IO) {
        profileCache[track.id]?.let { if (it.isAnalyzedFromAudio) return@withContext it }

        if (track.isStreamingTrack || track.uriString.isBlank()) {
            val fallback = buildDeterministicFallbackProfile(track)
            profileCache[track.id] = fallback
            return@withContext fallback
        }

        val analyzed = try {
            decodeAndAnalyzeAudioWindow(context, track)
        } catch (e: Exception) {
            AuraDebugManager.logWarning(TAG, "Fallo analizando PCM de '${track.title}', usando perfil determinista: ${e.message}")
            null
        } ?: buildDeterministicFallbackProfile(track)

        profileCache[track.id] = analyzed
        analyzed
    }

    /**
     * Analiza un conjunto de canciones locales en lote (hasta [maxTracks]) para poblar la caché
     * antes de ejecutar el emparejador inteligente Auto-Match.
     */
    suspend fun warmupProfiles(
        context: Context,
        tracks: List<Track>,
        maxTracks: Int = 24
    ): Map<Long, TrackAcousticProfile> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<Long, TrackAcousticProfile>()
        val subset = tracks.filter { !it.isStreamingTrack && it.uriString.isNotBlank() }.take(maxTracks)
        for (track in subset) {
            result[track.id] = analyzeTrack(context, track)
        }
        result
    }

    /**
     * Calcula la relación óptima de tempo (`atempo`) para que la Canción B empatice rítmicamente
     * con el BPM de la Canción A, resolviendo automáticamente relaciones de doble/mitad de tiempo
     * (por ejemplo, 85 BPM con 170 BPM) y limitando el estiramiento a un rango musical natural.
     */
    fun computeOptimalAtempoForTrackB(
        bpmA: Int,
        bpmB: Int,
        minTempo: Double = 0.88,
        maxTempo: Double = 1.14
    ): Double {
        if (bpmA <= 0 || bpmB <= 0) return 1.0
        val targetA = bpmA.toDouble()
        val candidatesB = listOf(
            bpmB.toDouble(),
            bpmB.toDouble() * 2.0,
            bpmB.toDouble() * 0.5
        )
        val bestEffectiveB = candidatesB.minByOrNull { candidate ->
            abs(targetA - candidate)
        } ?: bpmB.toDouble()

        val rawRatio = targetA / bestEffectiveB.coerceAtLeast(40.0)
        return rawRatio.coerceIn(minTempo, maxTempo)
    }

    /**
     * Evalúa la compatibilidad rítmica y energética entre dos perfiles acústicos (0 a 100%).
     * Devuelve el puntaje y la diferencia porcentual de BPM tras normalización armónica.
     */
    fun evaluateRhythmicCompatibility(
        profileA: TrackAcousticProfile,
        profileB: TrackAcousticProfile
    ): Pair<Int, Double> {
        val a = profileA.bpm.toDouble()
        val candidatesB = listOf(
            profileB.bpm.toDouble(),
            profileB.bpm.toDouble() * 2.0,
            profileB.bpm.toDouble() * 0.5
        )
        val closestB = candidatesB.minByOrNull { abs(a - it) } ?: profileB.bpm.toDouble()
        val bpmDiff = abs(a - closestB)
        val bpmDeltaPercent = (bpmDiff / max(a, 1.0)) * 100.0

        // Penalización suave según distancia de BPM (0 BPM diff = 100 pts, 6 BPM diff = 92 pts, 15 BPM diff = 78 pts)
        val bpmScore = (100.0 - (bpmDiff * 1.45)).coerceIn(68.0, 100.0)

        // Afinidad de energía (ideal cuando ambas tienen buena presencia o se complementan dentro de ±28%)
        val energyDiff = abs(profileA.energyPercent - profileB.energyPercent).toDouble()
        val energyScore = (100.0 - (energyDiff * 0.55)).coerceIn(72.0, 100.0)

        val combined = ((bpmScore * 0.72) + (energyScore * 0.28)).roundToInt().coerceIn(74, 99)
        return combined to bpmDeltaPercent
    }

    /**
     * Decodifica ~8.5 segundos desde el centro de la pista (donde suele residir el pulso principal/coro)
     * y calcula la envolvente de energía a 100 Hz para extraer BPM por autocorrelación y RMS.
     */
    private fun decodeAndAnalyzeAudioWindow(context: Context, track: Track): TrackAcousticProfile? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var fis: FileInputStream? = null

        try {
            val uriStr = track.uriString.trim()
            if (uriStr.startsWith("/") || uriStr.startsWith("file://", ignoreCase = true)) {
                val path = if (uriStr.startsWith("file://", ignoreCase = true)) {
                    Uri.parse(uriStr).path ?: uriStr.removePrefix("file://")
                } else {
                    uriStr
                }
                val file = File(path)
                if (!file.exists() || !file.canRead()) return null
                // Verificar seguridad canónica
                val storageManager = AppStorageManager(context)
                val canonical = file.canonicalFile.toPath()
                val songsDir = storageManager.songsDir.canonicalFile.toPath()
                val internalSongsDir = File(context.filesDir, "songs").canonicalFile.toPath()
                val dataDir = context.applicationInfo.dataDir?.let { File(it).canonicalFile.toPath() }
                if (!canonical.startsWith(songsDir) && !canonical.startsWith(internalSongsDir)) {
                    if (dataDir != null && canonical.startsWith(dataDir)) return null
                }
                fis = FileInputStream(file)
                extractor.setDataSource(fis.fd)
            } else {
                val uri = Uri.parse(uriStr)
                if (!IncomingMediaHandler.isSafeExternalUri(context, uri)) return null
                extractor.setDataSource(context, uri, null)
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) return null
            extractor.selectTrack(audioTrackIndex)

            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceIn(8000, 192000)
            } else 44100
            val channels = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
            } else 2

            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                track.durationMs * 1000L
            }

            // Posicionarse hacia el 32% de la canción para capturar el ritmo principal evitando intros silenciosas
            val startSeekUs = if (durationUs > 24_000_000L) {
                (durationUs * 32L) / 100L
            } else {
                0L
            }
            if (startSeekUs > 0L) {
                extractor.seekTo(startSeekUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            // Queremos ~8.5 segundos de envolvente a 100 Hz -> 850 frames de energía
            val targetEnvelopeFrames = 850
            val samplesPerEnvFrame = (sampleRate / ENVELOPE_RATE_HZ).coerceAtLeast(64)
            val envelopeEnergies = FloatArray(targetEnvelopeFrames)
            var envIndex = 0
            var currentWindowSumSq = 0.0
            var currentWindowSamples = 0
            var lowPassState = 0.0f

            var totalSumSq = 0.0
            var totalSampleCount = 0L

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var safetyLoops = 0

            while (!outputDone && envIndex < targetEnvelopeFrames && safetyLoops < 2200) {
                safetyLoops++
                if (!inputDone) {
                    val inBufIndex = codec.dequeueInputBuffer(2500L)
                    if (inBufIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inBufIndex)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(inBufIndex, 0, sampleSize, presentationTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outBufIndex = codec.dequeueOutputBuffer(bufferInfo, 2500L)
                if (outBufIndex >= 0) {
                    val outBuf = codec.getOutputBuffer(outBufIndex)
                    if (outBuf != null && bufferInfo.size >= 2) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuf = outBuf.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val numShorts = shortBuf.remaining()
                        var sIdx = 0
                        while (sIdx < numShorts && envIndex < targetEnvelopeFrames) {
                            // Promediar canales en mono normalizado [-1.0, 1.0]
                            var mono = 0.0f
                            val step = min(channels, numShorts - sIdx).coerceAtLeast(1)
                            for (c in 0 until step) {
                                mono += shortBuf.get(sIdx + c) / 32768.0f
                            }
                            mono /= step.toFloat()
                            sIdx += step

                            // Filtro paso-bajos ligero (~180 Hz) para enfatizar bombo y bajo rítmico
                            lowPassState += 0.18f * (mono - lowPassState)
                            val weightedSample = (lowPassState * 0.72f) + (mono * 0.28f)

                            val sq = (weightedSample * weightedSample).toDouble()
                            currentWindowSumSq += sq
                            currentWindowSamples++

                            totalSumSq += (mono * mono).toDouble()
                            totalSampleCount++

                            if (currentWindowSamples >= samplesPerEnvFrame) {
                                val rmsFrame = sqrt(currentWindowSumSq / currentWindowSamples.toDouble()).toFloat()
                                envelopeEnergies[envIndex++] = rmsFrame
                                currentWindowSumSq = 0.0
                                currentWindowSamples = 0
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outBufIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }

            if (envIndex < 180) return null

            val detectedBpm = estimateBpmFromEnvelope(envelopeEnergies, envIndex)
            val globalRms = if (totalSampleCount > 0L) {
                sqrt(totalSumSq / totalSampleCount.toDouble()).toFloat()
            } else 0.14f

            // Mapear RMS típico de música masterizada (0.05 a 0.32) a porcentaje de energía 42%..98%
            val energyPct = ((globalRms / 0.28f) * 88f + 18f).roundToInt().coerceIn(42, 98)

            return TrackAcousticProfile(
                trackId = track.id,
                bpm = detectedBpm,
                energyPercent = energyPct,
                isAnalyzedFromAudio = true
            )
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
            try { fis?.close() } catch (_: Throwable) {}
        }
    }

    /**
     * Calcula la función de novedad de ataques (primera derivada positiva de la envolvente de energía)
     * y evalúa la autocorrelación en los retardos correspondientes a 68..176 BPM.
     */
    private fun estimateBpmFromEnvelope(envelope: FloatArray, length: Int): Int {
        val onset = FloatArray(length)
        var maxOnset = 0.0001f
        for (i in 1 until length) {
            val diff = (envelope[i] - envelope[i - 1]).coerceAtLeast(0f)
            onset[i] = diff
            if (diff > maxOnset) maxOnset = diff
        }
        // Normalizar y aplicar umbral contra ruido de fondo
        for (i in 0 until length) {
            val norm = onset[i] / maxOnset
            onset[i] = if (norm > 0.12f) norm else 0f
        }

        var bestBpm = 120
        var bestScore = -1.0

        for (bpm in MIN_BPM..MAX_BPM) {
            val lag = (60.0 * ENVELOPE_RATE_HZ / bpm.toDouble()).roundToInt()
            if (lag <= 1 || lag >= length / 2) continue

            var corr = 0.0
            var count = 0
            for (i in 0 until (length - lag)) {
                // Incluir armónico de 2 pulsos si cabe en la ventana para reforzar el compás real
                val secondBeat = if (i + lag * 2 < length) onset[i + lag * 2] * 0.45f else 0f
                corr += onset[i] * (onset[i + lag] + secondBeat)
                count++
            }
            if (count > 0) {
                var normalizedCorr = corr / count.toDouble()
                // Ventana de preferencia perceptual centrada en el rango bailable/urbano/pop común (92..138 BPM)
                if (bpm in 90..140) {
                    normalizedCorr *= 1.08
                }
                if (normalizedCorr > bestScore) {
                    bestScore = normalizedCorr
                    bestBpm = bpm
                }
            }
        }

        return bestBpm.coerceIn(MIN_BPM, MAX_BPM)
    }

    private const val KEY_channel_COUNT_COMPAT = "channel-count"

    /**
     * Genera un perfil determinista consistente basado en la firma de la pista cuando aún no se ha
     * decodificado el PCM o en entornos de prueba sin codec hardware.
     */
    private fun buildDeterministicFallbackProfile(track: Track): TrackAcousticProfile {
        val hash = abs((track.title.lowercase().trim() + "|" + track.artist.lowercase().trim()).hashCode())
        val baseBpm = 88 + (hash % 54) // 88..141 BPM
        val energy = 62 + ((hash / 7) % 34) // 62..95%
        return TrackAcousticProfile(
            trackId = track.id,
            bpm = baseBpm,
            energyPercent = energy,
            isAnalyzedFromAudio = false
        )
    }
}
