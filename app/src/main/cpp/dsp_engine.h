// Motor principal DSP de 10 bandas en C++20 para Aura Music.
// Rol arquitectónico: submódulo de `auramusic_dsp.h`.
// Orquesta EQ paramétrico, Bass Boost, 8D/16D, Crossfeed, Reverb,
// Balance, Normalización de volumen, DJ Automix y Bit-Perfect.
// El espectro del visualizador vive en `dsp_visualizer.h` (SpectrumVisualizer).
#ifndef AURAMUSIC_DSP_ENGINE_H
#define AURAMUSIC_DSP_ENGINE_H

#include <string>
#include <span>
#include <vector>
#include <array>
#include <cmath>
#include <algorithm>
#include <numbers>
#include <cstddef>

#include "dsp_filters.h"
#include "dsp_spatial.h"
#include "dsp_crossfeed.h"
#include "dsp_reverb.h"
#include "dsp_bitperfect.h"
#include "dsp_visualizer.h"

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
        mBitPerfectController.updateActiveStreamFormat(mSampleRate, mChannels, 16);

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
        mVisualizer.reset();
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

    // --- Configuración de Modo Bit-Perfect y Salida AAudio Ultra-Baja Latencia ---
    void setBitPerfectMode(bool enabled) {
        mBitPerfectController.setBitPerfectEnabled(enabled);
    }

    [[nodiscard]] bool isBitPerfectMode() const {
        return mBitPerfectController.isBitPerfectEnabled();
    }

    void setLowLatencyMode(bool enabled) {
        mBitPerfectController.setLowLatencyEnabled(enabled);
    }

    [[nodiscard]] bool isLowLatencyMode() const {
        return mBitPerfectController.isLowLatencyEnabled();
    }

    void setUsbDacExclusiveMode(bool enabled) {
        mBitPerfectController.setUsbDacExclusiveMode(enabled);
    }

    [[nodiscard]] bool isUsbDacExclusiveMode() const {
        return mBitPerfectController.isUsbDacExclusiveMode();
    }

    void setHiResTargetMode(int mode) {
        mBitPerfectController.setHiResTargetMode(mode);
    }

    [[nodiscard]] int getHiResTargetMode() const {
        return mBitPerfectController.getHiResTargetMode();
    }

    [[nodiscard]] float getEstimatedLatencyMs() const {
        return mBitPerfectController.getEstimatedLatencyMs();
    }

    [[nodiscard]] int getActiveSampleRate() const {
        return mBitPerfectController.getActiveSampleRate();
    }

    [[nodiscard]] int getActiveBitDepth() const {
        return mBitPerfectController.getActiveBitDepth();
    }

    [[nodiscard]] int getFramesPerBurst() const {
        return mBitPerfectController.getFramesPerBurst();
    }

    [[nodiscard]] int getHardwareSampleRate() const {
        return mBitPerfectController.getHardwareSampleRate();
    }

    [[nodiscard]] std::string getBitPerfectStatusSummary() const {
        return mBitPerfectController.getStatusSummary();
    }

    void refreshHardwareCapabilities() {
        mBitPerfectController.probeHardwareCapabilities();
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
        return mVisualizer.intensity();
    }

    void getVisualizerBands(std::span<float> outBands) const {
        mVisualizer.getBands(outBands);
    }

    // Procesa un buffer de audio PCM de 16 bits estéreo entrelazado (L, R, L, R)
    void processPcm16(std::span<int16_t> samples) {
        const size_t totalSamples = samples.size();
        if (totalSamples == 0) return;

        constexpr size_t NUM_BANDS = SpectrumVisualizer::kNumBands;
        std::array<double, NUM_BANDS> bandEnergy{};
        const size_t frames = totalSamples / mChannels;
        const size_t samplesPerBand = std::max<size_t>(1, frames / NUM_BANDS);
        double sumSquares = 0.0;

        const bool bitPerfect = mBitPerfectController.isBitPerfectEnabled();
        const bool crossfeedActive = mCrossfeedProcessor.isEnabled() && mCrossfeedProcessor.isHeadphonesConnected() && (mChannels > 1);
        const bool anyDspActive = !bitPerfect && (
            mEnabled ||
            mVocalClarityProcessor.isEnabled() ||
            (mEightDProcessor.isEnabled() && mChannels > 1) ||
            crossfeedActive ||
            (mBalanceEnabled && mChannels > 1) ||
            mReverbProcessor.isEnabled() ||
            mVolumeNormalizer.isEnabled() ||
            mDjAutomixFilter.isEnabled()
        );

        constexpr double kInv32768 = 1.0 / 32768.0;

        if (!anyDspActive) {
            // Fast-Path de cero alteración de muestras (ahorra ramificaciones y escrituras en memoria por muestra)
            for (size_t i = 0, frameIdx = 0; i < totalSamples; i += mChannels, ++frameIdx) {
                const double sampleL = samples[i] * kInv32768;
                const double sampleR = (mChannels > 1 && (i + 1) < totalSamples) ? (samples[i + 1] * kInv32768) : sampleL;
                const double monoSample = (sampleL + sampleR) * 0.5;
                sumSquares += (monoSample * monoSample);

                const size_t bandIdx = std::min(NUM_BANDS - 1, frameIdx / samplesPerBand);
                bandEnergy[bandIdx] += std::abs(monoSample);
            }
        } else {
            for (size_t i = 0, frameIdx = 0; i < totalSamples; i += mChannels, ++frameIdx) {
                double sampleL = samples[i] * kInv32768;
                double sampleR = (mChannels > 1 && (i + 1) < totalSamples) ? (samples[i + 1] * kInv32768) : sampleL;

                const double monoSample = (sampleL + sampleR) * 0.5;
                sumSquares += (monoSample * monoSample);

                const size_t bandIdx = std::min(NUM_BANDS - 1, frameIdx / samplesPerBand);
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
                if (crossfeedActive) {
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

                // Normalización Inteligente de Volumen (Loudness Normalizer EBU R128)
                if (mVolumeNormalizer.isEnabled()) {
                    mVolumeNormalizer.processSample(sampleL, sampleR);
                }

                // Curva de Ecualización DJ Automix para transiciones suaves de pista
                if (mDjAutomixFilter.isEnabled()) {
                    mDjAutomixFilter.processSample(sampleL, sampleR);
                }

                sampleL = softClip(sampleL);
                samples[i] = static_cast<int16_t>(std::clamp(sampleL * 32767.0, -32768.0, 32767.0));

                if (mChannels > 1 && (i + 1) < totalSamples) {
                    sampleR = softClip(sampleR);
                    samples[i + 1] = static_cast<int16_t>(std::clamp(sampleR * 32767.0, -32768.0, 32767.0));
                }
            }
        }

        // Actualizar intensidad global RMS y envolvente espectral de las 28 bandas
        mVisualizer.update(bandEnergy, samplesPerBand, sumSquares, frames);
    }

    /**
     * Analiza las muestras PCM de 16-bit en modo de SOLO LECTURA (sin alterar ni un solo bit del buffer de salida).
     * Diseñado para el Modo Streaming Híbrido: mantiene el bypass C++20 sobre los efectos de audio mientras
     * alimenta en tiempo real las 28 bandas espectrales del visualizador (AudioVisualizer) y la intensidad acústica.
     */
    void analyzeReadOnlyPcm16(std::span<const int16_t> samples) {
        if (samples.empty() || mChannels <= 0) return;

        const size_t totalSamples = samples.size();
        const size_t frames = totalSamples / static_cast<size_t>(mChannels);
        if (frames == 0) return;

        constexpr size_t NUM_BANDS = SpectrumVisualizer::kNumBands;
        std::array<double, NUM_BANDS> bandEnergy{};
        const size_t samplesPerBand = std::max<size_t>(1, frames / NUM_BANDS);
        double sumSquares = 0.0;
        size_t frameIdx = 0;

        for (size_t i = 0; i < totalSamples; i += static_cast<size_t>(mChannels)) {
            double sampleL = static_cast<double>(samples[i]) / 32768.0;
            double sampleR = (mChannels > 1 && (i + 1) < totalSamples)
                             ? static_cast<double>(samples[i + 1]) / 32768.0
                             : sampleL;

            double monoAbs = (std::abs(sampleL) + std::abs(sampleR)) * 0.5;
            sumSquares += monoAbs * monoAbs;

            size_t bandIdx = std::min<size_t>(NUM_BANDS - 1, frameIdx / samplesPerBand);
            bandEnergy[bandIdx] += monoAbs;
            frameIdx++;
        }

        mVisualizer.update(bandEnergy, samplesPerBand, sumSquares, frames);
    }

    [[nodiscard]] std::string getEngineInfo() const {
        return "Aura Music C++20 10-Band Biquad, Vocal Clarity, 8D/16D Spatial, Reverb & Crossfeed DSP Core [Modular Active]";
    }

private:
    int mSampleRate{44100};
    int mChannels{2};
    bool mInitialized{false};
    bool mEnabled{false};
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
    BitPerfectController mBitPerfectController{};

    bool mBalanceEnabled{false};
    double mStereoBalance{0.0};
    double mGainL{1.0};
    double mGainR{1.0};

    SpectrumVisualizer mVisualizer{};
};

} // namespace aura::dsp

#endif // AURAMUSIC_DSP_ENGINE_H
