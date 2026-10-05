#ifndef AURAMUSIC_DSP_CROSSFEED_H
#define AURAMUSIC_DSP_CROSSFEED_H

#include "dsp_filters.h"
#include <vector>
#include <algorithm>

namespace aura::dsp {

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

    // Pone a cero los acumuladores y líneas de retardo (Buffer Flush)
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

} // namespace aura::dsp

#endif // AURAMUSIC_DSP_CROSSFEED_H
