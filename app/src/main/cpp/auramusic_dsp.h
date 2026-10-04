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

// Procesador de Reverberación Acústica Híbrida en C++20 (Red de Peines con Amortiguación y Pasa-Todo tipo Freeverb)
class ReverbProcessor {
public:
    void init(int sampleRate) {
        mSampleRate = (sampleRate > 0) ? sampleRate : 44100;
        double scale = static_cast<double>(mSampleRate) / 44100.0;

        mCombL1.assign(std::max<size_t>(10, static_cast<size_t>(1116 * scale)), 0.0);
        mCombL2.assign(std::max<size_t>(10, static_cast<size_t>(1188 * scale)), 0.0);
        mCombL3.assign(std::max<size_t>(10, static_cast<size_t>(1277 * scale)), 0.0);
        mCombL4.assign(std::max<size_t>(10, static_cast<size_t>(1356 * scale)), 0.0);

        mCombR1.assign(std::max<size_t>(10, static_cast<size_t>(1139 * scale)), 0.0);
        mCombR2.assign(std::max<size_t>(10, static_cast<size_t>(1211 * scale)), 0.0);
        mCombR3.assign(std::max<size_t>(10, static_cast<size_t>(1298 * scale)), 0.0);
        mCombR4.assign(std::max<size_t>(10, static_cast<size_t>(1380 * scale)), 0.0);

        mAllpassL1.assign(std::max<size_t>(10, static_cast<size_t>(556 * scale)), 0.0);
        mAllpassL2.assign(std::max<size_t>(10, static_cast<size_t>(441 * scale)), 0.0);
        mAllpassR1.assign(std::max<size_t>(10, static_cast<size_t>(568 * scale)), 0.0);
        mAllpassR2.assign(std::max<size_t>(10, static_cast<size_t>(452 * scale)), 0.0);

        resetBuffers();
    }

    void resetBuffers() {
        std::fill(mCombL1.begin(), mCombL1.end(), 0.0);
        std::fill(mCombL2.begin(), mCombL2.end(), 0.0);
        std::fill(mCombL3.begin(), mCombL3.end(), 0.0);
        std::fill(mCombL4.begin(), mCombL4.end(), 0.0);

        std::fill(mCombR1.begin(), mCombR1.end(), 0.0);
        std::fill(mCombR2.begin(), mCombR2.end(), 0.0);
        std::fill(mCombR3.begin(), mCombR3.end(), 0.0);
        std::fill(mCombR4.begin(), mCombR4.end(), 0.0);

        std::fill(mAllpassL1.begin(), mAllpassL1.end(), 0.0);
        std::fill(mAllpassL2.begin(), mAllpassL2.end(), 0.0);
        std::fill(mAllpassR1.begin(), mAllpassR1.end(), 0.0);
        std::fill(mAllpassR2.begin(), mAllpassR2.end(), 0.0);

        mIdxL1 = mIdxL2 = mIdxL3 = mIdxL4 = 0;
        mIdxR1 = mIdxR2 = mIdxR3 = mIdxR4 = 0;
        mAllpassIdxL1 = mAllpassIdxL2 = 0;
        mAllpassIdxR1 = mAllpassIdxR2 = 0;

        mDampL1 = mDampL2 = mDampL3 = mDampL4 = 0.0;
        mDampR1 = mDampR2 = mDampR3 = mDampR4 = 0.0;
    }

    void setParameters(bool enabled, float roomSize, int decayMs, float levelDb) {
        if (!enabled && mEnabled) {
            mPendingReset = true;
        } else if (enabled && !mEnabled) {
            mPendingReset = true;
        }
        mEnabled = enabled;
        mRoomSize = std::clamp(static_cast<double>(roomSize), 0.1, 2.0);
        mDecayMs = std::clamp(decayMs, 100, 6000);
        mLevelDb = std::clamp(static_cast<double>(levelDb), -30.0, 6.0);

        mWet = std::pow(10.0, mLevelDb / 20.0);
        if (mWet > 1.2) mWet = 1.2;

        // Feedback controlado para evitar resonancias explosivas o divergencia numérica
        double decayFactor = static_cast<double>(mDecayMs) / 12000.0;
        mFeedback = std::clamp(0.45 + (mRoomSize * 0.18) + decayFactor, 0.35, 0.84);
        mDamping = std::clamp(0.20 + (mRoomSize * 0.10), 0.15, 0.45);
    }

    [[nodiscard]] bool isEnabled() const { return mEnabled; }

    inline void processSample(double& sampleL, double& sampleR) {
        if (!mEnabled) return;

        if (mPendingReset) {
            mPendingReset = false;
            resetBuffers();
        }

        // Protección anti-denormal y anti-NaN en entrada
        if (std::isnan(sampleL) || std::isinf(sampleL)) sampleL = 0.0;
        if (std::isnan(sampleR) || std::isinf(sampleR)) sampleR = 0.0;

        // Filtros Peine con amortiguación paso-bajos (LBCF)
        double combOutL1 = processComb(sampleL, mCombL1, mIdxL1, mDampL1);
        double combOutL2 = processComb(sampleL, mCombL2, mIdxL2, mDampL2);
        double combOutL3 = processComb(sampleL, mCombL3, mIdxL3, mDampL3);
        double combOutL4 = processComb(sampleL, mCombL4, mIdxL4, mDampL4);

        double sumCombL = (combOutL1 + combOutL2 + combOutL3 + combOutL4) * 0.25;

        double combOutR1 = processComb(sampleR, mCombR1, mIdxR1, mDampR1);
        double combOutR2 = processComb(sampleR, mCombR2, mIdxR2, mDampR2);
        double combOutR3 = processComb(sampleR, mCombR3, mIdxR3, mDampR3);
        double combOutR4 = processComb(sampleR, mCombR4, mIdxR4, mDampR4);

        double sumCombR = (combOutR1 + combOutR2 + combOutR3 + combOutR4) * 0.25;

        // Filtros Schroeder Pasa-Todo normalizados
        sumCombL = processAllpass(sumCombL, mAllpassL1, mAllpassIdxL1);
        sumCombL = processAllpass(sumCombL, mAllpassL2, mAllpassIdxL2);

        sumCombR = processAllpass(sumCombR, mAllpassR1, mAllpassIdxR1);
        sumCombR = processAllpass(sumCombR, mAllpassR2, mAllpassIdxR2);

        double dryGain = std::clamp(1.0 - (mWet * 0.18), 0.72, 1.0);
        sampleL = (sampleL * dryGain) + (sumCombL * mWet * 0.45);
        sampleR = (sampleR * dryGain) + (sumCombR * mWet * 0.45);
    }

private:
    int mSampleRate{44100};
    bool mEnabled{false};
    bool mPendingReset{false};
    double mRoomSize{0.5};
    int mDecayMs{1500};
    double mLevelDb{-4.0};
    double mWet{0.63};
    double mFeedback{0.70};
    double mDamping{0.25};

    std::vector<double> mCombL1, mCombL2, mCombL3, mCombL4;
    std::vector<double> mCombR1, mCombR2, mCombR3, mCombR4;
    size_t mIdxL1{0}, mIdxL2{0}, mIdxL3{0}, mIdxL4{0};
    size_t mIdxR1{0}, mIdxR2{0}, mIdxR3{0}, mIdxR4{0};
    double mDampL1{0.0}, mDampL2{0.0}, mDampL3{0.0}, mDampL4{0.0};
    double mDampR1{0.0}, mDampR2{0.0}, mDampR3{0.0}, mDampR4{0.0};

    std::vector<double> mAllpassL1, mAllpassL2;
    std::vector<double> mAllpassR1, mAllpassR2;
    size_t mAllpassIdxL1{0}, mAllpassIdxL2{0};
    size_t mAllpassIdxR1{0}, mAllpassIdxR2{0};

    inline double processComb(double input, std::vector<double>& buffer, size_t& idx, double& filterStore) {
        if (buffer.empty()) return input;
        double output = buffer[idx];
        if (std::isnan(output) || std::isinf(output)) output = 0.0;
        filterStore = (output * (1.0 - mDamping)) + (filterStore * mDamping);
        if (std::isnan(filterStore) || std::isinf(filterStore)) filterStore = 0.0;
        buffer[idx] = input + (filterStore * mFeedback);
        idx = (idx + 1) % buffer.size();
        return output;
    }

    inline double processAllpass(double input, std::vector<double>& buffer, size_t& idx) {
        if (buffer.empty()) return input;
        double bufOut = buffer[idx];
        if (std::isnan(bufOut) || std::isinf(bufOut)) bufOut = 0.0;
        double output = -input + bufOut;
        buffer[idx] = input + (bufOut * 0.5);
        idx = (idx + 1) % buffer.size();
        return output;
    }
};

// Motor Principal DSP de 10 Bandas en C++20 con soporte de Audio 8D, Crossfeed y Balance Estéreo
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
            mCrossfeedProcessor.init(mSampleRate);
            mReverbProcessor.init(mSampleRate);
            mInitialized = true;
        }
    }

    void setReverbParameters(bool enabled, float roomSize, int decayMs, float levelDb) {
        mReverbProcessor.setParameters(enabled, roomSize, decayMs, levelDb);
    }

    [[nodiscard]] bool isReverbEnabled() const {
        return mReverbProcessor.isEnabled();
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

        // Medición acústica en tiempo real (C++20) para el visualizador
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

            // Procesamiento de Reverb Acústico en C++20 (soporta mono y estéreo)
            if (mReverbProcessor.isEnabled()) {
                mReverbProcessor.processSample(sampleL, sampleR);
            }

            // Si hubo procesamiento acústico activo, aplicar limitador suave y reescribir muestras
            if (mEnabled || mEightDProcessor.isEnabled() || 
                (mCrossfeedProcessor.isEnabled() && mCrossfeedProcessor.isHeadphonesConnected()) || 
                mBalanceEnabled || mReverbProcessor.isEnabled()) {
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
                    mVisualizerBands[b] = (mVisualizerBands[b] * 0.35f) + (targetH * 0.65f); // Ataque dinámico rápido
                } else {
                    mVisualizerBands[b] = (mVisualizerBands[b] * 0.82f) + (targetH * 0.18f); // Decaimiento suave
                }
            }
        }
    }

    [[nodiscard]] std::string getEngineInfo() const {
        return "Aura Music C++20 10-Band Biquad, 8D Spatial & Crossfeed DSP Core [Active]";
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

    EightDProcessor mEightDProcessor{};
    CrossfeedProcessor mCrossfeedProcessor{};
    ReverbProcessor mReverbProcessor{};

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

    static inline double softClip(double x) {
        if (std::isnan(x) || std::isinf(x)) return 0.0;
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

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetReverbParameters(JNIEnv* env, jobject thiz, jboolean enabled, jfloat roomSize, jint decayMs, jfloat levelDb);

JNIEXPORT jfloat JNICALL
Java_com_example_playback_NativeAudioEngine_nativeGetAudioIntensity(JNIEnv* env, jobject thiz);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeGetVisualizerBands(JNIEnv* env, jobject thiz, jfloatArray outBands);

#ifdef __cplusplus
}
#endif

#endif // AURAMUSIC_DSP_H
