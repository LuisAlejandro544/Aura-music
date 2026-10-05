#ifndef AURAMUSIC_DSP_SPATIAL_H
#define AURAMUSIC_DSP_SPATIAL_H

#include "dsp_filters.h"
#include <vector>
#include <cmath>
#include <numbers>
#include <algorithm>

namespace aura::dsp {

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

    // Pone a cero los acumuladores de eco y retardo (Buffer Flush)
    void reset() {
        std::fill(mDelayBufferL.begin(), mDelayBufferL.end(), 0.0);
        std::fill(mDelayBufferR.begin(), mDelayBufferR.end(), 0.0);
        mDelayIndex = 0;
        mAngle = 0.0;
        mBackFilterL.reset();
        mBackFilterR.reset();
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

} // namespace aura::dsp

#endif // AURAMUSIC_DSP_SPATIAL_H
