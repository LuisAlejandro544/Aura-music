#ifndef AURAMUSIC_DSP_SPATIAL_H
#define AURAMUSIC_DSP_SPATIAL_H

#include "dsp_filters.h"
#include <vector>
#include <cmath>
#include <numbers>
#include <algorithm>

namespace aura::dsp {

// Procesador de Audio Espacial 8D (Órbita 360°) y 16D (Doble Órbita Multi-Capa) Binaural para Auriculares
class EightDProcessor {
public:
    void init(int sampleRate) {
        mSampleRate = (sampleRate > 0) ? sampleRate : 44100;
        mAngle = 0.0;
        mSecondaryAngle = std::numbers::pi * 0.25;
        const size_t maxDelaySamples = static_cast<size_t>(mSampleRate * 0.1);
        mDelayBufferL.assign(maxDelaySamples, 0.0);
        mDelayBufferR.assign(maxDelaySamples, 0.0);
        mDelayIndex = 0;
        mBackFilterL.reset();
        mBackFilterR.reset();
        mBackFilterL.configurePeaking(mSampleRate, 4000.0, -4.5, 0.7);
        mBackFilterR.configurePeaking(mSampleRate, 4000.0, -4.5, 0.7);

        // Filtros divisores de frecuencia (Crossover a 320 Hz) para el modo Audio 16D Multi-Órbita
        mCrossoverLowL.reset();
        mCrossoverLowR.reset();
        mCrossoverLowL.configureLowPass(mSampleRate, 320.0, 0.707);
        mCrossoverLowR.configureLowPass(mSampleRate, 320.0, 0.707);
    }

    // Pone a cero los acumuladores de eco, crossover y retardo (Buffer Flush)
    void reset() {
        std::fill(mDelayBufferL.begin(), mDelayBufferL.end(), 0.0);
        std::fill(mDelayBufferR.begin(), mDelayBufferR.end(), 0.0);
        mDelayIndex = 0;
        mAngle = 0.0;
        mSecondaryAngle = std::numbers::pi * 0.25;
        mBackFilterL.reset();
        mBackFilterR.reset();
        mCrossoverLowL.reset();
        mCrossoverLowR.reset();
    }

    void setEnabled(bool enabled) { mEnabled = enabled; }
    [[nodiscard]] bool isEnabled() const { return mEnabled; }

    void setSixteenDMode(bool is16D) { mIs16DMode = is16D; }
    [[nodiscard]] bool isSixteenDMode() const { return mIs16DMode; }

    void setOrbitSpeed(double secondsPerRevolution) {
        mOrbitSpeedSeconds = std::clamp(secondsPerRevolution, 3.0, 45.0);
    }

    void setSpatialIntensity(double intensity) {
        mIntensity = std::clamp(intensity, 0.0, 1.0);
    }

    void setRoomDepth(double depth) {
        mRoomDepth = std::clamp(depth, 0.0, 1.0);
    }

    // Procesa un par de muestras estéreo en coma flotante aplicando paneo binaural 8D o 16D Multi-Órbita
    inline void processSample(double& sampleL, double& sampleR) {
        if (!mEnabled) return;

        double deltaTheta = (2.0 * std::numbers::pi) / (static_cast<double>(mSampleRate) * mOrbitSpeedSeconds);
        mAngle += deltaTheta;
        if (mAngle >= 2.0 * std::numbers::pi) {
            mAngle -= 2.0 * std::numbers::pi;
        }

        double outL = sampleL;
        double outR = sampleR;

        if (!mIs16DMode) {
            // --- MODO AUDIO 8D CLÁSICO (Órbita única 360°) ---
            double sinAngle = std::sin(mAngle);
            double cosAngle = std::cos(mAngle);

            double pan = sinAngle * mIntensity;
            double gainL = std::sqrt(std::clamp(0.5 * (1.0 - pan), 0.0, 1.0));
            double gainR = std::sqrt(std::clamp(0.5 * (1.0 + pan), 0.0, 1.0));

            outL = (sampleL * 0.75 + sampleR * 0.25) * (gainL * 1.414);
            outR = (sampleR * 0.75 + sampleL * 0.25) * (gainR * 1.414);

            if (cosAngle < -0.1) {
                double backFactor = std::abs(cosAngle) * mIntensity;
                double filteredL = mBackFilterL.process(outL);
                double filteredR = mBackFilterR.process(outR);
                outL = (outL * (1.0 - backFactor)) + (filteredL * backFactor);
                outR = (outR * (1.0 - backFactor)) + (filteredR * backFactor);
            }
        } else {
            // --- MODO AUDIO 16D MULTI-ÓRBITA (Doble plano en contrarrotación + trayectoria lemniscata) ---
            mSecondaryAngle += deltaTheta * 1.5;
            if (mSecondaryAngle >= 2.0 * std::numbers::pi) {
                mSecondaryAngle -= 2.0 * std::numbers::pi;
            }

            // 1. Separar el espectro en Plano Grave/Rítmico (<320 Hz) y Plano Melódico/Vocal (>320 Hz)
            double lowL = mCrossoverLowL.process(sampleL);
            double lowR = mCrossoverLowR.process(sampleR);
            double highL = sampleL - lowL;
            double highR = sampleR - lowR;

            // 2. Órbita 1 (Graves/Base): Rotación suave anclada con estabilidad central
            double lowPan = std::sin(mAngle) * (mIntensity * 0.55);
            double lowGainL = std::sqrt(std::clamp(0.5 * (1.0 - lowPan), 0.0, 1.0)) * 1.414;
            double lowGainR = std::sqrt(std::clamp(0.5 * (1.0 + lowPan), 0.0, 1.0)) * 1.414;
            double orbLowL = (lowL * 0.82 + lowR * 0.18) * lowGainL;
            double orbLowR = (lowR * 0.82 + lowL * 0.18) * lowGainR;

            // 3. Órbita 2 (Melodía/Voces/Agudos): Trayectoria en figura de infinito (Lemniscata 16 puntos) en contrarrotación
            double highPan = (0.72 * std::sin(-mSecondaryAngle) + 0.28 * std::sin(2.0 * mAngle)) * mIntensity;
            highPan = std::clamp(highPan, -1.0, 1.0);
            double highGainL = std::sqrt(std::clamp(0.5 * (1.0 - highPan), 0.0, 1.0)) * 1.414;
            double highGainR = std::sqrt(std::clamp(0.5 * (1.0 + highPan), 0.0, 1.0)) * 1.414;

            double orbHighL = (highL * 0.70 + highR * 0.30) * highGainL;
            double orbHighR = (highR * 0.70 + highL * 0.30) * highGainR;

            // Sombra acústica de cabeza (Head Shadow) independiente en el plano superior
            double cosHigh = std::cos(mSecondaryAngle);
            if (cosHigh < -0.1) {
                double backFactor = std::abs(cosHigh) * (mIntensity * 0.85);
                double filtL = mBackFilterL.process(orbHighL);
                double filtR = mBackFilterR.process(orbHighR);
                orbHighL = (orbHighL * (1.0 - backFactor)) + (filtL * backFactor);
                orbHighR = (orbHighR * (1.0 - backFactor)) + (filtR * backFactor);
            }

            outL = orbLowL + orbHighL;
            outR = orbLowR + orbHighR;
        }

        if (mRoomDepth > 0.02 && !mDelayBufferL.empty()) {
            double depthFactor = mIs16DMode ? std::min(1.0, mRoomDepth * 1.15) : mRoomDepth;
            size_t delaySamples = static_cast<size_t>(mSampleRate * (0.025 + 0.020 * depthFactor));
            if (delaySamples >= mDelayBufferL.size()) delaySamples = mDelayBufferL.size() - 1;

            size_t readIndex = (mDelayIndex + mDelayBufferL.size() - delaySamples) % mDelayBufferL.size();
            double delayedL = mDelayBufferL[readIndex];
            double delayedR = mDelayBufferR[readIndex];

            double feedback = 0.26 * depthFactor;
            mDelayBufferL[mDelayIndex] = outL + delayedR * feedback;
            mDelayBufferR[mDelayIndex] = outR + delayedL * feedback;
            mDelayIndex = (mDelayIndex + 1) % mDelayBufferL.size();

            outL = outL * (1.0 - depthFactor * 0.22) + delayedL * (depthFactor * 0.32);
            outR = outR * (1.0 - depthFactor * 0.22) + delayedR * (depthFactor * 0.32);
        }

        sampleL = outL;
        sampleR = outR;
    }

private:
    int mSampleRate{44100};
    bool mEnabled{false};
    bool mIs16DMode{false};
    double mOrbitSpeedSeconds{10.0};
    double mIntensity{0.85};
    double mRoomDepth{0.35};
    double mAngle{0.0};
    double mSecondaryAngle{0.785398};

    std::vector<double> mDelayBufferL{};
    std::vector<double> mDelayBufferR{};
    size_t mDelayIndex{0};

    BiquadPeakingFilter mBackFilterL{};
    BiquadPeakingFilter mBackFilterR{};
    BiquadPeakingFilter mCrossoverLowL{};
    BiquadPeakingFilter mCrossoverLowR{};
};

} // namespace aura::dsp

#endif // AURAMUSIC_DSP_SPATIAL_H
