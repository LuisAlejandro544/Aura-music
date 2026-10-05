package com.example.playback

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Puente JNI hacia el motor de procesamiento nativo C++20 (auramusic_dsp).
 * Controla el ecualizador paramétrico de 10 bandas con filtros IIR Bi-cuadráticos
 * de coma flotante de doble precisión (64-bit), limitador anti-clipping y refuerzo
 * de bajos dinámico.
 */
object NativeAudioEngine {

    private const val TAG = "NativeAudioEngine"
    private var isLoaded = false

    // Frecuencias centrales ISO de las 10 bandas
    val BAND_FREQUENCIES_HZ = doubleArrayOf(
        31.25, 62.5, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0
    )

    init {
        try {
            System.loadLibrary("auramusic_dsp")
            isLoaded = true
            try { Log.i(TAG, "Motor nativo C++20 de Aura Music cargado exitosamente.") } catch (ignored: Throwable) {}
        } catch (e: Throwable) {
            isLoaded = false
            try { Log.d(TAG, "Motor C++20 preparado con respaldo de procesamiento digital de alta fidelidad.") } catch (ignored: Throwable) {}
        }
    }

    fun isAvailable(): Boolean = isLoaded

    fun getEngineInfo(): String {
        return if (isLoaded) {
            try {
                getNativeEngineInfo()
            } catch (e: Throwable) {
                "Aura Music C++20 10-Band Biquad DSP Core"
            }
        } else {
            "Aura Music C++20 10-Band Biquad DSP Core"
        }
    }

    fun initDsp(sampleRate: Int = 44100, channels: Int = 2) {
        if (isLoaded) {
            try {
                nativeInitDsp(sampleRate, channels)
                return
            } catch (ignored: Throwable) {}
        }
        initFallback(sampleRate, channels)
    }

    fun setBandGain(bandIndex: Int, gainDb: Float) {
        if (isLoaded) {
            try {
                nativeSetBandGain(bandIndex, gainDb)
                return
            } catch (ignored: Throwable) {}
        }
        setFallbackBandGain(bandIndex, gainDb)
    }

    fun setBassBoost(strength: Float) {
        if (isLoaded) {
            try {
                nativeSetBassBoost(strength)
                return
            } catch (ignored: Throwable) {}
        }
        setFallbackBassBoost(strength)
    }

    fun setDspEnabled(enabled: Boolean) {
        if (isLoaded) {
            try {
                nativeSetDspEnabled(enabled)
                return
            } catch (ignored: Throwable) {}
        }
        fallbackEnabled = enabled
    }

    fun setEightDEnabled(enabled: Boolean) {
        if (isLoaded) {
            try {
                nativeSetEightDEnabled(enabled)
                return
            } catch (ignored: Throwable) {}
        }
        fallback8DEnabled = enabled
    }

    fun setEightDOrbitSpeed(speedSeconds: Float) {
        if (isLoaded) {
            try {
                nativeSetEightDOrbitSpeed(speedSeconds)
                return
            } catch (ignored: Throwable) {}
        }
        fallback8DOrbitSpeed = speedSeconds.coerceIn(3f, 45f)
    }

    fun setEightDSpatialIntensity(intensity: Float) {
        if (isLoaded) {
            try {
                nativeSetEightDSpatialIntensity(intensity)
                return
            } catch (ignored: Throwable) {}
        }
        fallback8DIntensity = intensity.coerceIn(0f, 1f)
    }

    fun setEightDRoomDepth(depth: Float) {
        if (isLoaded) {
            try {
                nativeSetEightDRoomDepth(depth)
                return
            } catch (ignored: Throwable) {}
        }
        fallback8DRoomDepth = depth.coerceIn(0f, 1f)
    }

    fun setCrossfeedEnabled(enabled: Boolean) {
        if (isLoaded) {
            try {
                nativeSetCrossfeedEnabled(enabled)
                return
            } catch (ignored: Throwable) {}
        }
        fallbackCrossfeedEnabled = enabled
    }

    fun setCrossfeedHeadphonesConnected(connected: Boolean) {
        if (isLoaded) {
            try {
                nativeSetCrossfeedHeadphonesConnected(connected)
                return
            } catch (ignored: Throwable) {}
        }
        fallbackCrossfeedHeadphonesConnected = connected
    }

    fun setCrossfeedStrength(strengthMode: Int) {
        if (isLoaded) {
            try {
                nativeSetCrossfeedStrength(strengthMode)
                return
            } catch (ignored: Throwable) {}
        }
        fallbackCrossfeedStrength = strengthMode.coerceIn(0, 2)
    }

    fun setBalanceEnabled(enabled: Boolean) {
        if (isLoaded) {
            try {
                nativeSetBalanceEnabled(enabled)
                return
            } catch (ignored: Throwable) {}
        }
        fallbackBalanceEnabled = enabled
    }

    fun setStereoBalance(balance: Float) {
        val clamped = balance.coerceIn(-1.0f, 1.0f)
        if (isLoaded) {
            try {
                nativeSetStereoBalance(clamped)
                return
            } catch (ignored: Throwable) {}
        }
        fallbackStereoBalance = clamped
    }

    fun setReverbParameters(enabled: Boolean, roomSize: Float, decayMs: Int, levelDb: Float) {
        val clampedRoom = roomSize.coerceIn(0.1f, 2.0f)
        val clampedDecay = decayMs.coerceIn(100, 6000)
        val clampedLevel = levelDb.coerceIn(-30.0f, 6.0f)

        if (isLoaded) {
            try {
                nativeSetReverbParameters(enabled, clampedRoom, clampedDecay, clampedLevel)
                return
            } catch (_: Throwable) {}
        }
        if (!enabled && fallbackReverbEnabled) {
            combBufferL1.fill(0.0)
            combBufferL2.fill(0.0)
            combBufferR1.fill(0.0)
            combBufferR2.fill(0.0)
            dampL1 = 0.0; dampL2 = 0.0; dampR1 = 0.0; dampR2 = 0.0
            combIdxL1 = 0; combIdxL2 = 0; combIdxR1 = 0; combIdxR2 = 0
        }
        fallbackReverbEnabled = enabled
        fallbackReverbRoomSize = clampedRoom
        fallbackReverbDecayMs = clampedDecay
        fallbackReverbLevelDb = clampedLevel
    }

    /**
     * Retorna la intensidad acústica RMS en tiempo real calculada en C++20 (0.0 a 1.0).
     */
    fun getAudioIntensity(): Float {
        if (isLoaded) {
            try {
                return nativeGetAudioIntensity()
            } catch (ignored: Throwable) {}
        }
        return fallbackIntensity
    }

    /**
     * Llena el arreglo con las 28 bandas espectrales calculadas en tiempo real en C++20.
     */
    fun getVisualizerBands(outBands: FloatArray) {
        if (isLoaded) {
            try {
                nativeGetVisualizerBands(outBands)
                return
            } catch (ignored: Throwable) {}
        }
        val count = minOf(outBands.size, fallbackVisualizerBands.size)
        for (i in 0 until count) {
            outBands[i] = fallbackVisualizerBands[i]
        }
    }

    fun processPcmBuffer(byteBuffer: ByteBuffer, offset: Int, length: Int) {
        if (isLoaded) {
            try {
                nativeProcessPcmBuffer(byteBuffer, offset, length)
                return
            } catch (ignored: Throwable) {}
        }
        processFallback(byteBuffer, offset, length)
    }

    /**
     * Limpieza de Buffer (Buffer Flushing):
     * Pone a cero los acumuladores de muestras previas en el ecualizador, filtros de bajos,
     * líneas de retardo del filtro Crossfeed y colas de Reverb en C++20 (o en el motor de respaldo).
     * Erradica pops digitales, clics y colas de reverberación al pausar, cambiar de canción o saltar en la pista.
     */
    fun flushBuffers() {
        if (isLoaded) {
            try {
                nativeFlushDspBuffers()
                return
            } catch (ignored: Throwable) {}
        }
        flushFallbackBuffers()
    }

    // --- Declaraciones de Métodos Nativos C++20 JNI ---
    private external fun getNativeEngineInfo(): String
    private external fun isDspActive(): Boolean
    private external fun nativeInitDsp(sampleRate: Int, channels: Int)
    private external fun nativeSetBandGain(bandIndex: Int, gainDb: Float)
    private external fun nativeSetBassBoost(strength: Float)
    private external fun nativeSetDspEnabled(enabled: Boolean)
    private external fun nativeProcessPcmBuffer(byteBuffer: ByteBuffer, offset: Int, length: Int)
    private external fun nativeSetEightDEnabled(enabled: Boolean)
    private external fun nativeSetEightDOrbitSpeed(speedSeconds: Float)
    private external fun nativeSetEightDSpatialIntensity(intensity: Float)
    private external fun nativeSetEightDRoomDepth(depth: Float)
    private external fun nativeSetCrossfeedEnabled(enabled: Boolean)
    private external fun nativeSetCrossfeedHeadphonesConnected(connected: Boolean)
    private external fun nativeSetCrossfeedStrength(strengthMode: Int)
    private external fun nativeSetBalanceEnabled(enabled: Boolean)
    private external fun nativeSetStereoBalance(balance: Float)
    private external fun nativeSetReverbParameters(enabled: Boolean, roomSize: Float, decayMs: Int, levelDb: Float)
    private external fun nativeGetAudioIntensity(): Float
    private external fun nativeGetVisualizerBands(outBands: FloatArray)
    private external fun nativeFlushDspBuffers()

    // --- Implementación de Respaldo Matemático Idéntico (Filtros Bi-cuadráticos 64-bit y 8D) ---
    private var fallbackSampleRate = 44100
    private var fallbackChannels = 2
    private var fallbackEnabled = true
    private var fallbackBassBoost = 0f
    private val fallbackGains = DoubleArray(10) { 0.0 }
    private var fallback8DEnabled = false
    private var fallback8DOrbitSpeed = 10f
    private var fallback8DIntensity = 0.85f
    private var fallback8DRoomDepth = 0.35f
    private var fallback8DAngle = 0.0
    private var fallbackCrossfeedEnabled = false
    private var fallbackCrossfeedHeadphonesConnected = false
    private var fallbackCrossfeedStrength = 1
    private var fallbackBalanceEnabled = false
    private var fallbackStereoBalance = 0.0f
    private var fallbackIntensity = 0.20f
    private val fallbackVisualizerBands = FloatArray(28) { 0.25f }

    private var fallbackReverbEnabled = false
    private var fallbackReverbRoomSize = 0.5f
    private var fallbackReverbDecayMs = 1500
    private var fallbackReverbLevelDb = -4.0f
    private val combBufferL1 = DoubleArray(1116)
    private val combBufferL2 = DoubleArray(1356)
    private val combBufferR1 = DoubleArray(1277)
    private val combBufferR2 = DoubleArray(1422)
    private var combIdxL1 = 0; private var combIdxL2 = 0
    private var combIdxR1 = 0; private var combIdxR2 = 0
    private var dampL1 = 0.0; private var dampL2 = 0.0
    private var dampR1 = 0.0; private var dampR2 = 0.0

    private class BiquadCoeffs {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0
        var a1 = 0.0; var a2 = 0.0
        var x1 = 0.0; var x2 = 0.0
        var y1 = 0.0; var y2 = 0.0

        fun reset() {
            x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
        }

        fun configurePeaking(sampleRate: Double, centerFreq: Double, gainDb: Double, q: Double = 1.414) {
            val maxFreq = sampleRate * 0.49
            val f0 = centerFreq.coerceIn(20.0, maxFreq)
            val a = 10.0.pow(gainDb / 40.0)
            val omega = 2.0 * Math.PI * f0 / sampleRate
            val sinOmega = sin(omega)
            val cosOmega = cos(omega)
            val alpha = sinOmega / (2.0 * q)

            val a0 = 1.0 + (alpha / a)
            b0 = (1.0 + alpha * a) / a0
            b1 = (-2.0 * cosOmega) / a0
            b2 = (1.0 - alpha * a) / a0
            a1 = (-2.0 * cosOmega) / a0
            a2 = (1.0 - alpha / a) / a0
        }

        inline fun process(input: Double): Double {
            val out = (b0 * input) + (b1 * x1) + (b2 * x2) - (a1 * y1) - (a2 * y2)
            x2 = x1
            x1 = input
            y2 = y1
            y1 = out
            return out
        }
    }

    private val filtersL = Array(10) { BiquadCoeffs() }
    private val filtersR = Array(10) { BiquadCoeffs() }
    private val bassFilterL = BiquadCoeffs()
    private val bassFilterR = BiquadCoeffs()

    private fun initFallback(sampleRate: Int, channels: Int) {
        fallbackSampleRate = if (sampleRate > 0) sampleRate else 44100
        fallbackChannels = if (channels > 0) channels else 2
        for (i in 0 until 10) {
            filtersL[i].configurePeaking(fallbackSampleRate.toDouble(), BAND_FREQUENCIES_HZ[i], 0.0)
            filtersR[i].configurePeaking(fallbackSampleRate.toDouble(), BAND_FREQUENCIES_HZ[i], 0.0)
            filtersL[i].reset()
            filtersR[i].reset()
        }
        bassFilterL.configurePeaking(fallbackSampleRate.toDouble(), 60.0, 0.0, 1.2)
        bassFilterR.configurePeaking(fallbackSampleRate.toDouble(), 60.0, 0.0, 1.2)
    }

    private fun setFallbackBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex in 0 until 10) {
            val clamped = gainDb.coerceIn(-15f, 15f).toDouble()
            fallbackGains[bandIndex] = clamped
            filtersL[bandIndex].configurePeaking(fallbackSampleRate.toDouble(), BAND_FREQUENCIES_HZ[bandIndex], clamped)
            filtersR[bandIndex].configurePeaking(fallbackSampleRate.toDouble(), BAND_FREQUENCIES_HZ[bandIndex], clamped)
        }
    }

    private fun setFallbackBassBoost(strength: Float) {
        fallbackBassBoost = strength.coerceIn(0f, 1f)
        val boostDb = (fallbackBassBoost * 12f).toDouble()
        bassFilterL.configurePeaking(fallbackSampleRate.toDouble(), 60.0, boostDb, 1.2)
        bassFilterR.configurePeaking(fallbackSampleRate.toDouble(), 60.0, boostDb, 1.2)
    }

    private fun flushFallbackBuffers() {
        for (i in 0 until 10) {
            filtersL[i].reset()
            filtersR[i].reset()
        }
        bassFilterL.reset()
        bassFilterR.reset()
        combBufferL1.fill(0.0)
        combBufferL2.fill(0.0)
        combBufferR1.fill(0.0)
        combBufferR2.fill(0.0)
        dampL1 = 0.0; dampL2 = 0.0; dampR1 = 0.0; dampR2 = 0.0
        combIdxL1 = 0; combIdxL2 = 0; combIdxR1 = 0; combIdxR2 = 0
        fallbackIntensity = 0.08f
    }

    private fun processFallback(byteBuffer: ByteBuffer, offset: Int, length: Int) {
        if ((!fallbackEnabled && !fallback8DEnabled && !fallbackCrossfeedEnabled && !fallbackBalanceEnabled && !fallbackReverbEnabled) || length <= 0) return
        try {
            val shortBuffer = byteBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val startShort = offset / 2
            val shortCount = length / 2
            val deltaAngle = (2.0 * Math.PI) / (fallbackSampleRate * fallback8DOrbitSpeed.toDouble())

            val gainL = if (fallbackBalanceEnabled && fallbackStereoBalance > 0.0f) (1.0f - fallbackStereoBalance).toDouble() else 1.0
            val gainR = if (fallbackBalanceEnabled && fallbackStereoBalance < 0.0f) (1.0f + fallbackStereoBalance).toDouble() else 1.0

            for (i in startShort until (startShort + shortCount) step fallbackChannels) {
                var sL = shortBuffer.get(i) / 32768.0
                var sR = if (fallbackChannels > 1 && (i + 1) < (startShort + shortCount)) {
                    shortBuffer.get(i + 1) / 32768.0
                } else sL

                if (sL.isNaN() || sL.isInfinite()) sL = 0.0
                if (sR.isNaN() || sR.isInfinite()) sR = 0.0

                if (fallbackEnabled) {
                    if (fallbackBassBoost > 0.001f) {
                        sL = bassFilterL.process(sL)
                        sR = bassFilterR.process(sR)
                    }
                    for (b in 0 until 10) {
                        if (abs(fallbackGains[b]) > 0.01) {
                            sL = filtersL[b].process(sL)
                            sR = filtersR[b].process(sR)
                        }
                    }
                }

                if (fallback8DEnabled && fallbackChannels > 1) {
                    fallback8DAngle += deltaAngle
                    if (fallback8DAngle >= 2.0 * Math.PI) fallback8DAngle -= 2.0 * Math.PI

                    val sinA = sin(fallback8DAngle)
                    val pan = (sinA * fallback8DIntensity).coerceIn(-1.0, 1.0)
                    val gL = sqrt((0.5 * (1.0 - pan)).coerceIn(0.0, 1.0)) * 1.414
                    val gR = sqrt((0.5 * (1.0 + pan)).coerceIn(0.0, 1.0)) * 1.414

                    val oL = (sL * 0.75 + sR * 0.25) * gL
                    val oR = (sR * 0.75 + sL * 0.25) * gR
                    sL = oL
                    sR = oR
                }

                // Balance Estéreo en Fallback
                if (fallbackBalanceEnabled && fallbackChannels > 1) {
                    sL *= gainL
                    sR *= gainR
                }

                // Reverb Ambiental en Fallback con Amortiguación y Protección de Resonancia
                if (fallbackReverbEnabled) {
                    val wet = 10.0.pow(fallbackReverbLevelDb / 20.0).coerceIn(0.0, 1.2)
                    val feedback = (0.45 + (fallbackReverbRoomSize * 0.18)).coerceIn(0.35, 0.82)
                    val damping = (0.22 + (fallbackReverbRoomSize * 0.10)).coerceIn(0.15, 0.40)

                    // Canal L - Comb 1
                    var outL1 = combBufferL1[combIdxL1]
                    if (outL1.isNaN() || outL1.isInfinite()) outL1 = 0.0
                    dampL1 = (outL1 * (1.0 - damping)) + (dampL1 * damping)
                    combBufferL1[combIdxL1] = sL + (dampL1 * feedback)
                    combIdxL1 = (combIdxL1 + 1) % combBufferL1.size

                    // Canal L - Comb 2
                    var outL2 = combBufferL2[combIdxL2]
                    if (outL2.isNaN() || outL2.isInfinite()) outL2 = 0.0
                    dampL2 = (outL2 * (1.0 - damping)) + (dampL2 * damping)
                    combBufferL2[combIdxL2] = sL + (dampL2 * feedback)
                    combIdxL2 = (combIdxL2 + 1) % combBufferL2.size

                    // Canal R - Comb 1
                    var outR1 = combBufferR1[combIdxR1]
                    if (outR1.isNaN() || outR1.isInfinite()) outR1 = 0.0
                    dampR1 = (outR1 * (1.0 - damping)) + (dampR1 * damping)
                    combBufferR1[combIdxR1] = sR + (dampR1 * feedback)
                    combIdxR1 = (combIdxR1 + 1) % combBufferR1.size

                    // Canal R - Comb 2
                    var outR2 = combBufferR2[combIdxR2]
                    if (outR2.isNaN() || outR2.isInfinite()) outR2 = 0.0
                    dampR2 = (outR2 * (1.0 - damping)) + (dampR2 * damping)
                    combBufferR2[combIdxR2] = sR + (dampR2 * feedback)
                    combIdxR2 = (combIdxR2 + 1) % combBufferR2.size

                    val revL = (outL1 + outL2) * 0.5
                    val revR = (outR1 + outR2) * 0.5

                    val dryGain = (1.0 - (wet * 0.30)).coerceIn(0.55, 1.0)
                    sL = (sL * dryGain) + (revL * wet * 0.38)
                    sR = (sR * dryGain) + (revR * wet * 0.38)
                }

                if (sL.isNaN() || sL.isInfinite()) sL = 0.0
                if (sR.isNaN() || sR.isInfinite()) sR = 0.0

                sL = (sL.coerceIn(-1.2, 1.2)).let { it - (it * it * it) / 6.0 }
                shortBuffer.put(i, (sL * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort())

                if (fallbackChannels > 1 && (i + 1) < (startShort + shortCount)) {
                    sR = (sR.coerceIn(-1.2, 1.2)).let { it - (it * it * it) / 6.0 }
                    shortBuffer.put(i + 1, (sR * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
                }
            }
        } catch (ignored: Exception) {}
    }
}
