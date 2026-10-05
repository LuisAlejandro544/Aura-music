#ifndef AURAMUSIC_DSP_REVERB_H
#define AURAMUSIC_DSP_REVERB_H

#include <vector>
#include <cmath>
#include <algorithm>

namespace aura::dsp {

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

    // Pone a cero los acumuladores de eco y colas de reverberación (Buffer Flush)
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

} // namespace aura::dsp

#endif // AURAMUSIC_DSP_REVERB_H
