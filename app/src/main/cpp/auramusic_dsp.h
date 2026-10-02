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

// Motor Principal DSP de 10 Bandas en C++20
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
        // strength: 0.0 a 1.0 (equivale a 0 dB a +12 dB en frecuencias graves de 60 Hz)
        mBassBoostStrength = std::clamp(strength, 0.0, 1.0);
        double boostDb = mBassBoostStrength * 12.0;
        mBassBoostFilterL.configurePeaking(mSampleRate, 60.0, boostDb, 1.2);
        mBassBoostFilterR.configurePeaking(mSampleRate, 60.0, boostDb, 1.2);
    }

    // Procesa un buffer de audio PCM de 16 bits estéreo entrelazado (L, R, L, R)
    void processPcm16(std::span<int16_t> samples) {
        if (!mEnabled) return;

        const size_t totalSamples = samples.size();
        for (size_t i = 0; i < totalSamples; i += mChannels) {
            // Canal Izquierdo
            double sampleL = samples[i] / 32768.0;
            // Refuerzo de bajos
            if (mBassBoostStrength > 0.001) {
                sampleL = mBassBoostFilterL.process(sampleL);
            }
            // 10 bandas en serie
            for (int b = 0; b < 10; ++b) {
                if (std::abs(mBandGainsDb[b]) > 0.01) {
                    sampleL = mFiltersL[b].process(sampleL);
                }
            }
            // Limitador suave anti-clipping
            sampleL = softClip(sampleL);
            samples[i] = static_cast<int16_t>(std::clamp(sampleL * 32767.0, -32768.0, 32767.0));

            // Canal Derecho (si es estéreo)
            if (mChannels > 1 && (i + 1) < totalSamples) {
                double sampleR = samples[i + 1] / 32768.0;
                if (mBassBoostStrength > 0.001) {
                    sampleR = mBassBoostFilterR.process(sampleR);
                }
                for (int b = 0; b < 10; ++b) {
                    if (std::abs(mBandGainsDb[b]) > 0.01) {
                        sampleR = mFiltersR[b].process(sampleR);
                    }
                }
                sampleR = softClip(sampleR);
                samples[i + 1] = static_cast<int16_t>(std::clamp(sampleR * 32767.0, -32768.0, 32767.0));
            }
        }
    }

    [[nodiscard]] std::string getEngineInfo() const {
        return "Aura Music C++20 10-Band Biquad DSP Core [Active]";
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

    // Limitador suave analógico (soft clipping cúbico)
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
Java_com_example_playback_NativeAudioEngine_initDsp(JNIEnv* env, jobject thiz, jint sampleRate, jint channels);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_setBandGain(JNIEnv* env, jobject thiz, jint bandIndex, jfloat gainDb);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_setBassBoost(JNIEnv* env, jobject thiz, jfloat strength);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_setDspEnabled(JNIEnv* env, jobject thiz, jboolean enabled);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_processPcmBuffer(JNIEnv* env, jobject thiz, jobject byteBuffer, jint offset, jint length);

#ifdef __cplusplus
}
#endif

#endif // AURAMUSIC_DSP_H
