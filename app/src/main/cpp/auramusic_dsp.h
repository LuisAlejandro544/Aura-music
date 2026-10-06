#ifndef AURAMUSIC_DSP_H
#define AURAMUSIC_DSP_H

#include <jni.h>
#include <string>
#include <span>
#include <vector>
#include <array>
#include <cmath>
#include <algorithm>
#include <numbers>
#include <memory>

#include "dsp_filters.h"
#include "dsp_spatial.h"
#include "dsp_crossfeed.h"
#include "dsp_reverb.h"

/**
 * Aura Music - Motor Nativo de Procesamiento Digital de Señales (DSP)
 * Estándar: ISO C++20
 *
 * Orquestador principal que integra:
 * - Ecualizador Paramétrico de 10 Bandas con Filtros IIR Bi-cuadráticos (64-bit).
 * - Refuerzo de Graves Dinámico (Bass Boost a 60 Hz).
 * - Motor de Audio 8D Espacial Binaural para Auriculares.
 * - Filtro Crossfeed Acústico (Algoritmo Bauer / Chu Moy).
 * - Suite Reverb Acústica Híbrida en tiempo real (LBCF / Schroeder).
 * - Balance Estéreo Fino L/R.
 * - Limpieza Atómica de Buffers (Buffer Flushing) para cambios de pista y seekTo limpios.
 * - Limitador Suave Anti-Clipping.
 */

namespace aura::dsp {

// Motor Principal DSP de 10 Bandas en C++20 con soporte de Audio 8D, Crossfeed, Reverb y Balance
class NativeDspEngine {
public:
    NativeDspEngine() {
        init(44100, 2);
    }

    void init(int sampleRate, int channels) {
        int newSampleRate = (sampleRate > 0) ? sampleRate : 44100;
        int newChannels = (channels > 0) ? channels : 2;
        bool rateChanged = (newSampleRate != mSampleRate) || !mInitialized;

        mSampleRate = newSampleRate;
        mChannels = newChannels;

        for (int i = 0; i < 10; ++i) {
            mFiltersL[i].configurePeaking(mSampleRate, EQ_FREQUENCIES_HZ[i], mBandGainsDb[i]);
            mFiltersR[i].configurePeaking(mSampleRate, EQ_FREQUENCIES_HZ[i], mBandGainsDb[i]);
            mFiltersL[i].reset();
            mFiltersR[i].reset();
        }
        double boostDb = mBassBoostStrength * 12.0;
        mBassBoostFilterL.configurePeaking(mSampleRate, 60.0, boostDb, 1.2);
        mBassBoostFilterR.configurePeaking(mSampleRate, 60.0, boostDb, 1.2);
        mBassBoostFilterL.reset();
        mBassBoostFilterR.reset();

        if (rateChanged) {
            mEightDProcessor.init(mSampleRate);
            mVocalClarityProcessor.init(mSampleRate);
            mCrossfeedProcessor.init(mSampleRate);
            mReverbProcessor.init(mSampleRate);
            mVolumeNormalizer.init(mSampleRate);
            mDjAutomixFilter.init(mSampleRate);
            mInitialized = true;
        }
    }

    /**
     * Limpieza Atómica de Buffer (Buffer Flushing):
     * Pone a cero los acumuladores de muestras previas (x1, x2, y1, y2) en las 10 bandas del ecualizador,
     * en el filtro de graves, en el clarificador vocal, en los procesadores espaciales 8D/16D,
     * en las líneas de retardo del filtro Crossfeed y en las colas de Reverb.
     * Erradica pops, clics y ecos residuales en cambios de pista, seekTo y pausas.
     */
    void flushDspBuffers() {
        for (int i = 0; i < 10; ++i) {
            mFiltersL[i].reset();
            mFiltersR[i].reset();
        }
        mBassBoostFilterL.reset();
        mBassBoostFilterR.reset();
        mVocalClarityProcessor.reset();
        mEightDProcessor.reset();
        mCrossfeedProcessor.reset();
        mReverbProcessor.resetBuffers();
        mVolumeNormalizer.reset();
        mDjAutomixFilter.reset();
        mCurrentIntensity = 0.08f;
    }

    void setVolumeNormalization(bool enabled, float targetLufs, int mode) {
        mVolumeNormalizer.setEnabled(enabled);
        mVolumeNormalizer.setMode(mode);
        if (targetLufs < -5.0f) {
            mVolumeNormalizer.setTargetLufs(targetLufs);
        }
    }

    [[nodiscard]] bool isVolumeNormalizationEnabled() const {
        return mVolumeNormalizer.isEnabled();
    }

    void setDjAutomixTransition(bool enabled, float progress) {
        mDjAutomixFilter.setEnabled(enabled);
        mDjAutomixFilter.setTransitionProgress(progress);
    }

    [[nodiscard]] bool isDjAutomixEnabled() const {
        return mDjAutomixFilter.isEnabled();
    }

    void setReverbParameters(bool enabled, float roomSize, int decayMs, float levelDb) {
        mReverbProcessor.setParameters(enabled, roomSize, decayMs, levelDb);
    }

    [[nodiscard]] bool isReverbEnabled() const {
        return mReverbProcessor.isEnabled();
    }

    void setVocalClarityParameters(bool enabled, double strength) {
        mVocalClarityProcessor.setEnabled(enabled);
        mVocalClarityProcessor.setStrength(strength);
    }

    [[nodiscard]] bool isVocalClarityEnabled() const {
        return mVocalClarityProcessor.isEnabled();
    }

    void setEnabled(bool enabled) {
        mEnabled = enabled;
    }

    [[nodiscard]] bool isEnabled() const {
        return mEnabled;
    }

    void setBandGain(int bandIndex, double gainDb) {
        if (bandIndex < 0 || bandIndex >= 10) return;
        double clampedGain = std::clamp(gainDb, -15.0, 15.0);
        mBandGainsDb[bandIndex] = clampedGain;

        mFiltersL[bandIndex].configurePeaking(mSampleRate, EQ_FREQUENCIES_HZ[bandIndex], clampedGain);
        mFiltersR[bandIndex].configurePeaking(mSampleRate, EQ_FREQUENCIES_HZ[bandIndex], clampedGain);
    }

    void setBassBoost(double strength) {
        mBassBoostStrength = std::clamp(strength, 0.0, 1.0);
        double boostDb = mBassBoostStrength * 12.0;
        mBassBoostFilterL.configurePeaking(mSampleRate, 60.0, boostDb, 1.2);
        mBassBoostFilterR.configurePeaking(mSampleRate, 60.0, boostDb, 1.2);
    }

    void setEightDEnabled(bool enabled) {
        mEightDProcessor.setEnabled(enabled);
    }

    [[nodiscard]] bool isEightDEnabled() const {
        return mEightDProcessor.isEnabled();
    }

    void setEightD16DMode(bool is16DMode) {
        mEightDProcessor.setSixteenDMode(is16DMode);
    }

    [[nodiscard]] bool isEightD16DMode() const {
        return mEightDProcessor.isSixteenDMode();
    }

    void setEightDOrbitSpeed(double speedSeconds) {
        mEightDProcessor.setOrbitSpeed(speedSeconds);
    }

    void setEightDSpatialIntensity(double intensity) {
        mEightDProcessor.setSpatialIntensity(intensity);
    }

    void setEightDRoomDepth(double depth) {
        mEightDProcessor.setRoomDepth(depth);
    }

    // Configuración de Crossfeed Bauer / Chu Moy
    void setCrossfeedEnabled(bool enabled) {
        mCrossfeedProcessor.setEnabled(enabled);
    }

    [[nodiscard]] bool isCrossfeedEnabled() const {
        return mCrossfeedProcessor.isEnabled();
    }

    void setCrossfeedHeadphonesConnected(bool connected) {
        mCrossfeedProcessor.setHeadphonesConnected(connected);
    }

    void setCrossfeedStrength(int strengthMode) {
        mCrossfeedProcessor.setStrength(strengthMode);
    }

    // Balance Estéreo Fino (-1.0 = 100% Izquierda, 0.0 = Centro, +1.0 = 100% Derecha)
    void setBalanceEnabled(bool enabled) {
        mBalanceEnabled = enabled;
    }

    [[nodiscard]] bool isBalanceEnabled() const {
        return mBalanceEnabled;
    }

    void setStereoBalance(double balance) {
        mStereoBalance = std::clamp(balance, -1.0, 1.0);
        if (std::abs(mStereoBalance) < 0.001) {
            mGainL = 1.0;
            mGainR = 1.0;
        } else if (mStereoBalance < 0.0) {
            mGainL = 1.0;
            mGainR = 1.0 + mStereoBalance;
        } else {
            mGainL = 1.0 - mStereoBalance;
            mGainR = 1.0;
        }
    }

    [[nodiscard]] double getStereoBalance() const {
        return mStereoBalance;
    }

    [[nodiscard]] float getAudioIntensity() const {
        return mCurrentIntensity;
    }

    void getVisualizerBands(std::span<float> outBands) const {
        const size_t count = std::min(outBands.size(), mVisualizerBands.size());
        for (size_t i = 0; i < count; ++i) {
            outBands[i] = mVisualizerBands[i];
        }
    }

    // Procesa un buffer de audio PCM de 16 bits estéreo entrelazado (L, R, L, R)
    void processPcm16(std::span<int16_t> samples) {
        const size_t totalSamples = samples.size();
        if (totalSamples == 0) return;

        constexpr size_t NUM_BANDS = 28;
        std::array<double, NUM_BANDS> bandEnergy{};
        const size_t frames = totalSamples / mChannels;
        const size_t samplesPerBand = std::max<size_t>(1, frames / NUM_BANDS);
        double sumSquares = 0.0;

        for (size_t i = 0; i < totalSamples; i += mChannels) {
            double sampleL = samples[i] / 32768.0;
            double sampleR = (mChannels > 1 && (i + 1) < totalSamples) ? (samples[i + 1] / 32768.0) : sampleL;

            double monoSample = (sampleL + sampleR) * 0.5;
            sumSquares += (monoSample * monoSample);

            size_t frameIdx = i / mChannels;
            size_t bandIdx = std::min(NUM_BANDS - 1, frameIdx / samplesPerBand);
            bandEnergy[bandIdx] += std::abs(monoSample);

            // Modificaciones DSP si están activas
            if (mEnabled) {
                if (mBassBoostStrength > 0.001) {
                    sampleL = mBassBoostFilterL.process(sampleL);
                    sampleR = mBassBoostFilterR.process(sampleR);
                }
                for (int b = 0; b < 10; ++b) {
                    if (std::abs(mBandGainsDb[b]) > 0.01) {
                        sampleL = mFiltersL[b].process(sampleL);
                        sampleR = mFiltersR[b].process(sampleR);
                    }
                }
            }

            // Clarificador Vocal HD en C++20 (Aislamiento Mid-Side y realce de inteligibilidad)
            if (mVocalClarityProcessor.isEnabled()) {
                mVocalClarityProcessor.processSample(sampleL, sampleR);
            }

            // Procesamiento de Audio 8D / 16D Espacial
            if (mEightDProcessor.isEnabled() && mChannels > 1) {
                mEightDProcessor.processSample(sampleL, sampleR);
            }

            // Procesamiento de Filtro Crossfeed Acústico (exclusivo para auriculares conectados)
            if (mCrossfeedProcessor.isEnabled() && mCrossfeedProcessor.isHeadphonesConnected() && mChannels > 1) {
                mCrossfeedProcessor.processSample(sampleL, sampleR);
            }

            // Balance Estéreo Fino L/R
            if (mBalanceEnabled && mChannels > 1) {
                sampleL *= mGainL;
                sampleR *= mGainR;
            }

            // Procesamiento de Reverb Acústico en C++20 (soporta mono y estéreo)
            if (mReverbProcessor.isEnabled()) {
                mReverbProcessor.processSample(sampleL, sampleR);
            }

            // Normalización Inteligente de Volumen (Loudness Normalizer estilo Spotify / EBU R128)
            if (mVolumeNormalizer.isEnabled()) {
                mVolumeNormalizer.processSample(sampleL, sampleR);
            }

            // Curva de Ecualización DJ Automix para transiciones suaves de pista
            if (mDjAutomixFilter.isEnabled()) {
                mDjAutomixFilter.processSample(sampleL, sampleR);
            }

            // Si hubo procesamiento acústico activo, aplicar limitador suave y reescribir muestras
            if (mEnabled || mVocalClarityProcessor.isEnabled() || mEightDProcessor.isEnabled() || 
                (mCrossfeedProcessor.isEnabled() && mCrossfeedProcessor.isHeadphonesConnected()) || 
                mBalanceEnabled || mReverbProcessor.isEnabled() ||
                mVolumeNormalizer.isEnabled() || mDjAutomixFilter.isEnabled()) {
                sampleL = softClip(sampleL);
                samples[i] = static_cast<int16_t>(std::clamp(sampleL * 32767.0, -32768.0, 32767.0));

                if (mChannels > 1 && (i + 1) < totalSamples) {
                    sampleR = softClip(sampleR);
                    samples[i + 1] = static_cast<int16_t>(std::clamp(sampleR * 32767.0, -32768.0, 32767.0));
                }
            }
        }

        // Actualizar intensidad global RMS y envolvente espectral de las 28 bandas
        if (frames > 0) {
            double rms = std::sqrt(sumSquares / static_cast<double>(frames));
            float targetIntensity = std::clamp(static_cast<float>(rms * 3.4), 0.06f, 1.0f);
            mCurrentIntensity = (mCurrentIntensity * 0.60f) + (targetIntensity * 0.40f);

            for (size_t b = 0; b < NUM_BANDS; ++b) {
                float avgBand = static_cast<float>(bandEnergy[b] / static_cast<double>(samplesPerBand));
                float weight = 1.0f + 0.35f * std::sin((static_cast<float>(b) / NUM_BANDS) * std::numbers::pi_v<float>);
                float targetH = std::clamp(avgBand * 3.8f * weight, 0.08f, 1.0f);
                if (targetH > mVisualizerBands[b]) {
                    mVisualizerBands[b] = (mVisualizerBands[b] * 0.35f) + (targetH * 0.65f);
                } else {
                    mVisualizerBands[b] = (mVisualizerBands[b] * 0.82f) + (targetH * 0.18f);
                }
            }
        }
    }

    [[nodiscard]] std::string getEngineInfo() const {
        return "Aura Music C++20 10-Band Biquad, Vocal Clarity, 8D/16D Spatial, Reverb & Crossfeed DSP Core [Modular Active]";
    }

private:
    int mSampleRate{44100};
    int mChannels{2};
    bool mInitialized{false};
    bool mEnabled{true};
    double mBassBoostStrength{0.0};
    std::array<double, 10> mBandGainsDb{};

    std::array<BiquadPeakingFilter, 10> mFiltersL{};
    std::array<BiquadPeakingFilter, 10> mFiltersR{};
    BiquadPeakingFilter mBassBoostFilterL{};
    BiquadPeakingFilter mBassBoostFilterR{};

    VocalClarityProcessor mVocalClarityProcessor{};
    EightDProcessor mEightDProcessor{};
    CrossfeedProcessor mCrossfeedProcessor{};
    ReverbProcessor mReverbProcessor{};
    VolumeNormalizerProcessor mVolumeNormalizer{};
    DjAutomixFilter mDjAutomixFilter{};

    bool mBalanceEnabled{false};
    double mStereoBalance{0.0};
    double mGainL{1.0};
    double mGainR{1.0};

    float mCurrentIntensity{0.15f};
    std::array<float, 28> mVisualizerBands{
        0.12f, 0.15f, 0.18f, 0.22f, 0.28f, 0.35f, 0.42f, 0.48f,
        0.52f, 0.55f, 0.58f, 0.60f, 0.58f, 0.55f, 0.52f, 0.48f,
        0.44f, 0.40f, 0.36f, 0.32f, 0.28f, 0.24f, 0.20f, 0.18f,
        0.15f, 0.13f, 0.11f, 0.09f
    };
};

} // namespace aura::dsp

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jstring JNICALL
Java_com_example_playback_NativeAudioEngine_getNativeEngineInfo(JNIEnv* env, jobject thiz);

JNIEXPORT jboolean JNICALL
Java_com_example_playback_NativeAudioEngine_isDspActive(JNIEnv* env, jobject thiz);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeInitDsp(JNIEnv* env, jobject thiz, jint sampleRate, jint channels);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeFlushDspBuffers(JNIEnv* env, jobject thiz);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetBandGain(JNIEnv* env, jobject thiz, jint bandIndex, jfloat gainDb);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetBassBoost(JNIEnv* env, jobject thiz, jfloat strength);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetDspEnabled(JNIEnv* env, jobject thiz, jboolean enabled);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeProcessPcmBuffer(JNIEnv* env, jobject thiz, jobject byteBuffer, jint offset, jint length);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightDEnabled(JNIEnv* env, jobject thiz, jboolean enabled);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightD16DMode(JNIEnv* env, jobject thiz, jboolean is16DMode);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightDOrbitSpeed(JNIEnv* env, jobject thiz, jfloat speedSeconds);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightDSpatialIntensity(JNIEnv* env, jobject thiz, jfloat intensity);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightDRoomDepth(JNIEnv* env, jobject thiz, jfloat depth);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetVocalClarityParameters(JNIEnv* env, jobject thiz, jboolean enabled, jfloat strength);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetCrossfeedEnabled(JNIEnv* env, jobject thiz, jboolean enabled);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetCrossfeedHeadphonesConnected(JNIEnv* env, jobject thiz, jboolean connected);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetCrossfeedStrength(JNIEnv* env, jobject thiz, jint strengthMode);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetBalanceEnabled(JNIEnv* env, jobject thiz, jboolean enabled);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetStereoBalance(JNIEnv* env, jobject thiz, jfloat balance);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetReverbParameters(JNIEnv* env, jobject thiz, jboolean enabled, jfloat roomSize, jint decayMs, jfloat levelDb);

JNIEXPORT jfloat JNICALL
Java_com_example_playback_NativeAudioEngine_nativeGetAudioIntensity(JNIEnv* env, jobject thiz);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeGetVisualizerBands(JNIEnv* env, jobject thiz, jfloatArray outBands);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetVolumeNormalization(JNIEnv* env, jobject thiz, jboolean enabled, jfloat targetLufs, jint mode);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetDjAutomixTransition(JNIEnv* env, jobject thiz, jboolean enabled, jfloat progress);

#ifdef __cplusplus
}
#endif

#endif // AURAMUSIC_DSP_H
