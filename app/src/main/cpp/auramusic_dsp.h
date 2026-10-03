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

/**
 * Aura Music - Motor Nativo de Procesamiento Digital de Señales (DSP)
 * Estándar: ISO C++20
 *
 * Implementa un Ecualizador Paramétrico de 10 Bandas con Filtros IIR Bi-cuadráticos
 * de coma flotante de doble precisión (64-bit), refuerzo de graves dinámico (Bass Boost)
 * y limitador analógico suave sin distorsión por recorte digital (anti-clipping).
 */

namespace aura::dsp {

// Frecuencias ISO centrales para el ecualizador de 10 bandas
inline constexpr std::array<double, 10> EQ_FREQUENCIES_HZ = {
    31.25, 62.5, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0
};

// Filtro Bi-cuadrático (Biquad IIR) con cálculo paramétrico Peaking EQ
class BiquadPeakingFilter {
public:
    double b0{1.0}, b1{0.0}, b2{0.0};
    double a1{0.0}, a2{0.0};
    double x1{0.0}, x2{0.0};
    double y1{0.0}, y2{0.0};

    void reset() {
        x1 = x2 = y1 = y2 = 0.0;
    }

    // Calcula los coeficientes de filtro peaking según Robert Bristow-Johnson Audio EQ Cookbook
    void configurePeaking(double sampleRate, double centerFreqHz, double gainDb, double q = 1.414) {
        if (sampleRate <= 0.0 || centerFreqHz <= 0.0) return;
        
        // Limitar la frecuencia central por debajo de Nyquist (Fs / 2)
        double maxFreq = (sampleRate * 0.49);
        double f0 = std::clamp(centerFreqHz, 20.0, maxFreq);

        double A = std::pow(10.0, gainDb / 40.0);
        double omega = 2.0 * std::numbers::pi * f0 / sampleRate;
        double sinOmega = std::sin(omega);
        double cosOmega = std::cos(omega);
        double alpha = sinOmega / (2.0 * q);

        double a0 = 1.0 + (alpha / A);
        b0 = (1.0 + alpha * A) / a0;
        b1 = (-2.0 * cosOmega) / a0;
        b2 = (1.0 - alpha * A) / a0;
        a1 = (-2.0 * cosOmega) / a0;
        a2 = (1.0 - alpha / A) / a0;
    }

    // Filtro Paso-Bajos Bi-cuadrático (Biquad Low-Pass)
    void configureLowPass(double sampleRate, double cutoffHz, double q = 0.707) {
        if (sampleRate <= 0.0 || cutoffHz <= 0.0) return;
        double maxFreq = sampleRate * 0.49;
        double f0 = std::clamp(cutoffHz, 20.0, maxFreq);
        double omega = 2.0 * std::numbers::pi * f0 / sampleRate;
        double sinOmega = std::sin(omega);
        double cosOmega = std::cos(omega);
        double alpha = sinOmega / (2.0 * q);

        double a0 = 1.0 + alpha;
        b0 = ((1.0 - cosOmega) / 2.0) / a0;
        b1 = (1.0 - cosOmega) / a0;
        b2 = ((1.0 - cosOmega) / 2.0) / a0;
        a1 = (-2.0 * cosOmega) / a0;
        a2 = (1.0 - alpha) / a0;
    }

    // Procesa una muestra de audio flotante
    inline double process(double in) {
        double out = (b0 * in) + (b1 * x1) + (b2 * x2) - (a1 * y1) - (a2 * y2);
        x2 = x1;
        x1 = in;
        y2 = y1;
        y1 = out;
        return out;
    }
};

// Procesador de Audio Espacial 8D Binaural para Auriculares
class EightDProcessor {
public:
    void init(int sampleRate) {
        mSampleRate = (sampleRate > 0) ? sampleRate : 44100;
        mAngle = 0.0;
        const size_t maxDelaySamples = static_cast<size_t>(mSampleRate * 0.1);
        mDelayBufferL.assign(maxDelaySamples, 0.0);
        mDelayBufferR.assign(maxDelaySamples, 0.0);
        mDelayIndex = 0;
        mBackFilterL.reset();
        mBackFilterR.reset();
        mBackFilterL.configurePeaking(mSampleRate, 4000.0, -4.5, 0.7);
        mBackFilterR.configurePeaking(mSampleRate, 4000.0, -4.5, 0.7);
    }

    void setEnabled(bool enabled) { mEnabled = enabled; }
    [[nodiscard]] bool isEnabled() const { return mEnabled; }

    void setOrbitSpeed(double secondsPerRevolution) {
        mOrbitSpeedSeconds = std::clamp(secondsPerRevolution, 3.0, 45.0);
    }

    void setSpatialIntensity(double intensity) {
        mIntensity = std::clamp(intensity, 0.0, 1.0);
    }

    void setRoomDepth(double depth) {
        mRoomDepth = std::clamp(depth, 0.0, 1.0);
    }

    // Procesa un par de muestras estéreo en coma flotante aplicando paneo binaural orbital
    inline void processSample(double& sampleL, double& sampleR) {
        if (!mEnabled) return;

        double deltaTheta = (2.0 * std::numbers::pi) / (static_cast<double>(mSampleRate) * mOrbitSpeedSeconds);
        mAngle += deltaTheta;
        if (mAngle >= 2.0 * std::numbers::pi) {
            mAngle -= 2.0 * std::numbers::pi;
        }

        double sinAngle = std::sin(mAngle);
        double cosAngle = std::cos(mAngle);

        double pan = sinAngle * mIntensity;
        double gainL = std::sqrt(std::clamp(0.5 * (1.0 - pan), 0.0, 1.0));
        double gainR = std::sqrt(std::clamp(0.5 * (1.0 + pan), 0.0, 1.0));

        double outL = (sampleL * 0.75 + sampleR * 0.25) * (gainL * 1.414);
        double outR = (sampleR * 0.75 + sampleL * 0.25) * (gainR * 1.414);

        if (cosAngle < -0.1) {
            double backFactor = std::abs(cosAngle) * mIntensity;
            double filteredL = mBackFilterL.process(outL);
            double filteredR = mBackFilterR.process(outR);
            outL = (outL * (1.0 - backFactor)) + (filteredL * backFactor);
            outR = (outR * (1.0 - backFactor)) + (filteredR * backFactor);
        }

        if (mRoomDepth > 0.02 && !mDelayBufferL.empty()) {
            size_t delaySamples = static_cast<size_t>(mSampleRate * (0.025 + 0.020 * mRoomDepth));
            if (delaySamples >= mDelayBufferL.size()) delaySamples = mDelayBufferL.size() - 1;

            size_t readIndex = (mDelayIndex + mDelayBufferL.size() - delaySamples) % mDelayBufferL.size();
            double delayedL = mDelayBufferL[readIndex];
            double delayedR = mDelayBufferR[readIndex];

            double feedback = 0.26 * mRoomDepth;
            mDelayBufferL[mDelayIndex] = outL + delayedR * feedback;
            mDelayBufferR[mDelayIndex] = outR + delayedL * feedback;
            mDelayIndex = (mDelayIndex + 1) % mDelayBufferL.size();

            outL = outL * (1.0 - mRoomDepth * 0.22) + delayedL * (mRoomDepth * 0.32);
            outR = outR * (1.0 - mRoomDepth * 0.22) + delayedR * (mRoomDepth * 0.32);
        }

        sampleL = outL;
        sampleR = outR;
    }

private:
    int mSampleRate{44100};
    bool mEnabled{false};
    double mOrbitSpeedSeconds{10.0};
    double mIntensity{0.85};
    double mRoomDepth{0.35};
    double mAngle{0.0};

    std::vector<double> mDelayBufferL{};
    std::vector<double> mDelayBufferR{};
    size_t mDelayIndex{0};

    BiquadPeakingFilter mBackFilterL{};
    BiquadPeakingFilter mBackFilterR{};
};

// Procesador de Filtro Crossfeed Acústico (Algoritmo Bauer / Chu Moy para Auriculares)
// Proyecta el sonido frontalmente y elimina la fatiga auditiva mezclando sutilmente
// una señal paso-bajos con retardo temporal interaural (ITD) al canal opuesto.
// EXCLUSIVAMENTE ACTIVO cuando hay auriculares conectados.
class CrossfeedProcessor {
public:
    void init(int sampleRate) {
        mSampleRate = (sampleRate > 0) ? sampleRate : 44100;
        configureParameters();
        reset();
    }

    void reset() {
        size_t bufferSize = static_cast<size_t>(mSampleRate * 0.002) + 64; // ~2ms de buffer
        mDelayBufferL.assign(bufferSize, 0.0);
        mDelayBufferR.assign(bufferSize, 0.0);
        mDelayIdx = 0;
        mLowPassFilterL.reset();
        mLowPassFilterR.reset();
    }

    void setEnabled(bool enabled) {
        mEnabled = enabled;
    }

    [[nodiscard]] bool isEnabled() const {
        return mEnabled;
    }

    void setHeadphonesConnected(bool connected) {
        mHeadphonesConnected = connected;
    }

    [[nodiscard]] bool isHeadphonesConnected() const {
        return mHeadphonesConnected;
    }

    // 0 = Sutil (Bauer 4.5dB / 700Hz), 1 = Moderado (Chu Moy Classic), 2 = Intenso (Monitores de Estudio)
    void setStrength(int strengthMode) {
        mStrengthMode = std::clamp(strengthMode, 0, 2);
        configureParameters();
    }

    [[nodiscard]] int getStrength() const {
        return mStrengthMode;
    }

    // Procesa un par de muestras estéreo. Se ejecuta únicamente si el usuario lo activó Y hay audífonos conectados.
    inline void processSample(double& sampleL, double& sampleR) {
        if (!mEnabled || !mHeadphonesConnected) return;

        // Filtrado paso-bajos de la señal cruzada (alrededor de 700 Hz)
        double feedL = mLowPassFilterL.process(sampleR) * mCrossFeedGain;
        double feedR = mLowPassFilterR.process(sampleL) * mCrossFeedGain;

        // Retardo interaural (ITD: ~250 a 330 microsegundos)
        if (mDelaySamples > 0 && !mDelayBufferL.empty()) {
            size_t readIdx = (mDelayIdx + mDelayBufferL.size() - mDelaySamples) % mDelayBufferL.size();
            double delayedFeedL = mDelayBufferL[readIdx];
            double delayedFeedR = mDelayBufferR[readIdx];

            mDelayBufferL[mDelayIdx] = feedL;
            mDelayBufferR[mDelayIdx] = feedR;
            mDelayIdx = (mDelayIdx + 1) % mDelayBufferL.size();

            feedL = delayedFeedL;
            feedR = delayedFeedR;
        }

        // Fusión de señal directa con señal cruzada compensada
        sampleL = (sampleL * mDirectGain) + feedL;
        sampleR = (sampleR * mDirectGain) + feedR;
    }

private:
    int mSampleRate{44100};
    bool mEnabled{false};
    bool mHeadphonesConnected{false};
    int mStrengthMode{1}; // Moderado por defecto

    double mCrossFeedGain{0.36};
    double mDirectGain{0.82};
    size_t mDelaySamples{13};

    std::vector<double> mDelayBufferL{};
    std::vector<double> mDelayBufferR{};
    size_t mDelayIdx{0};

    BiquadPeakingFilter mLowPassFilterL{};
    BiquadPeakingFilter mLowPassFilterR{};

    void configureParameters() {
        // Frecuencia de corte natural de sombra de cabeza (~700 Hz)
        const double cutoffHz = 700.0;
        mLowPassFilterL.configureLowPass(mSampleRate, cutoffHz, 0.707);
        mLowPassFilterR.configureLowPass(mSampleRate, cutoffHz, 0.707);

        switch (mStrengthMode) {
            case 0: // Sutil (Bauer 4.5 dB)
                mCrossFeedGain = 0.26;
                mDirectGain = 0.88;
                mDelaySamples = static_cast<size_t>(mSampleRate * 0.00025); // 250 us
                break;
            case 1: // Moderado (Chu Moy Estándar)
            default:
                mCrossFeedGain = 0.36;
                mDirectGain = 0.82;
                mDelaySamples = static_cast<size_t>(mSampleRate * 0.00029); // 290 us
                break;
            case 2: // Intenso (Monitores de Estudio)
                mCrossFeedGain = 0.46;
                mDirectGain = 0.76;
                mDelaySamples = static_cast<size_t>(mSampleRate * 0.00034); // 340 us
                break;
        }
        if (mDelaySamples < 1) mDelaySamples = 1;
    }
};

// Motor Principal DSP de 10 Bandas en C++20 con soporte de Audio 8D, Crossfeed y Balance Estéreo
class NativeDspEngine {
public:
    NativeDspEngine() {
        init(44100, 2);
    }

    void init(int sampleRate, int channels) {
        mSampleRate = (sampleRate > 0) ? sampleRate : 44100;
        mChannels = (channels > 0) ? channels : 2;

        for (int i = 0; i < 10; ++i) {
            mBandGainsDb[i] = 0.0;
            mFiltersL[i].configurePeaking(mSampleRate, EQ_FREQUENCIES_HZ[i], 0.0);
            mFiltersR[i].configurePeaking(mSampleRate, EQ_FREQUENCIES_HZ[i], 0.0);
            mFiltersL[i].reset();
            mFiltersR[i].reset();
        }
        mBassBoostStrength = 0.0;
        mBassBoostFilterL.configurePeaking(mSampleRate, 60.0, 0.0, 1.2);
        mBassBoostFilterR.configurePeaking(mSampleRate, 60.0, 0.0, 1.2);

        mEightDProcessor.init(mSampleRate);
        mCrossfeedProcessor.init(mSampleRate);
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
            // Hacia la izquierda: canal izquierdo al 100%, derecho atenuado
            mGainL = 1.0;
            mGainR = 1.0 + mStereoBalance; // Si balance es -0.4 -> 0.6
        } else {
            // Hacia la derecha: canal derecho al 100%, izquierdo atenuado
            mGainL = 1.0 - mStereoBalance; // Si balance es 0.4 -> 0.6
            mGainR = 1.0;
        }
    }

    [[nodiscard]] double getStereoBalance() const {
        return mStereoBalance;
    }

    // Procesa un buffer de audio PCM de 16 bits estéreo entrelazado (L, R, L, R)
    void processPcm16(std::span<int16_t> samples) {
        if (!mEnabled && !mEightDProcessor.isEnabled() && !mCrossfeedProcessor.isEnabled() && !mBalanceEnabled) return;

        const size_t totalSamples = samples.size();
        for (size_t i = 0; i < totalSamples; i += mChannels) {
            double sampleL = samples[i] / 32768.0;
            double sampleR = (mChannels > 1 && (i + 1) < totalSamples) ? (samples[i + 1] / 32768.0) : sampleL;

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

            // Procesamiento de Audio 8D Espacial
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

            // Limitador suave anti-clipping
            sampleL = softClip(sampleL);
            samples[i] = static_cast<int16_t>(std::clamp(sampleL * 32767.0, -32768.0, 32767.0));

            if (mChannels > 1 && (i + 1) < totalSamples) {
                sampleR = softClip(sampleR);
                samples[i + 1] = static_cast<int16_t>(std::clamp(sampleR * 32767.0, -32768.0, 32767.0));
            }
        }
    }

    [[nodiscard]] std::string getEngineInfo() const {
        return "Aura Music C++20 10-Band Biquad, 8D Spatial & Crossfeed DSP Core [Active]";
    }

private:
    int mSampleRate{44100};
    int mChannels{2};
    bool mEnabled{true};
    double mBassBoostStrength{0.0};
    std::array<double, 10> mBandGainsDb{};

    std::array<BiquadPeakingFilter, 10> mFiltersL{};
    std::array<BiquadPeakingFilter, 10> mFiltersR{};
    BiquadPeakingFilter mBassBoostFilterL{};
    BiquadPeakingFilter mBassBoostFilterR{};

    EightDProcessor mEightDProcessor{};
    CrossfeedProcessor mCrossfeedProcessor{};

    bool mBalanceEnabled{false};
    double mStereoBalance{0.0};
    double mGainL{1.0};
    double mGainR{1.0};

    static inline double softClip(double x) {
        if (x > 1.2) return 1.0;
        if (x < -1.2) return -1.0;
        return x - (x * x * x) / 6.0;
    }
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
Java_com_example_playback_NativeAudioEngine_nativeSetEightDOrbitSpeed(JNIEnv* env, jobject thiz, jfloat speedSeconds);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightDSpatialIntensity(JNIEnv* env, jobject thiz, jfloat intensity);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightDRoomDepth(JNIEnv* env, jobject thiz, jfloat depth);

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

#ifdef __cplusplus
}
#endif

#endif // AURAMUSIC_DSP_H
