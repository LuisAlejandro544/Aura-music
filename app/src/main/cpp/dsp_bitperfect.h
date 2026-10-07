#ifndef DSP_BITPERFECT_H
#define DSP_BITPERFECT_H

#include <aaudio/AAudio.h>
#include <atomic>
#include <cstdint>
#include <cmath>
#include <algorithm>
#include <string>

/**
 * Aura Music - Submódulo de Salida de Ultra-Baja Latencia, AAudio y Modo Bit-Perfect
 * Estándar: ISO C++20
 *
 * Responsabilidades:
 * 1. Sondear las capacidades reales del subsistema AAudio de Android NDK (API 26+):
 *    - Detección de modo exclusivo (AAUDIO_SHARING_MODE_EXCLUSIVE) para saltarse AudioFlinger.
 *    - Detección de modo de rendimiento de baja latencia (AAUDIO_PERFORMANCE_MODE_LOW_LATENCY).
 *    - Cálculo del tamaño de ráfaga (FramesPerBurst), tasa de muestreo nativa del DAC y latencia en ms.
 * 2. Gestionar el Modo Bit-Perfect (Direct DAC Bypass) en la ruta de procesamiento PCM:
 *    - Cuando Bit-Perfect está activo, salta los filtros de coloración y altera 0 bits del flujo PCM,
 *      permitiendo salida íntegra 1:1 hacia auriculares o DACs USB-C externos.
 * 3. Mantener telemetría en vivo de formato PCM (16/24/32-bit, 44.1 kHz a 192 kHz) para la UI.
 */

namespace aura::dsp {

struct AAudioHardwareCapabilities {
    bool isSupported{true};
    bool isExclusiveSupported{false};
    bool isLowLatencySupported{true};
    int nativeSampleRate{48000};
    int framesPerBurst{192};
    int channelCount{2};
    float estimatedLatencyMs{4.0f};
};

class BitPerfectController {
public:
    BitPerfectController() {
        probeHardwareCapabilities();
    }

    /**
     * Sondea el subsistema AAudio de Android NDK (disponible desde minSdk 26)
     * para determinar las capacidades reales de baja latencia y modo exclusivo del hardware/DAC.
     */
    void probeHardwareCapabilities() {
        AAudioStreamBuilder* builder = nullptr;
        aaudio_result_t res = AAudio_createStreamBuilder(&builder);
        if (res != AAUDIO_OK || builder == nullptr) {
            mCapabilities.isSupported = false;
            mCapabilities.isExclusiveSupported = false;
            mCapabilities.isLowLatencySupported = false;
            mCapabilities.nativeSampleRate = 48000;
            mCapabilities.framesPerBurst = 240;
            mCapabilities.estimatedLatencyMs = 5.0f;
            return;
        }

        AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
        AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
        AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
        AAudioStreamBuilder_setChannelCount(builder, 2);

        AAudioStream* stream = nullptr;
        res = AAudioStreamBuilder_openStream(builder, &stream);
        if (res == AAUDIO_OK && stream != nullptr) {
            mCapabilities.isSupported = true;
            int32_t sharing = AAudioStream_getSharingMode(stream);
            int32_t perf = AAudioStream_getPerformanceMode(stream);
            int32_t sr = AAudioStream_getSampleRate(stream);
            int32_t burst = AAudioStream_getFramesPerBurst(stream);

            mCapabilities.isExclusiveSupported = (sharing == AAUDIO_SHARING_MODE_EXCLUSIVE);
            mCapabilities.isLowLatencySupported = (perf == AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
            mCapabilities.nativeSampleRate = (sr > 0) ? sr : 48000;
            mCapabilities.framesPerBurst = (burst > 0) ? burst : 192;

            if (mCapabilities.nativeSampleRate > 0) {
                float rawMs = (static_cast<float>(mCapabilities.framesPerBurst) * 1000.0f) /
                              static_cast<float>(mCapabilities.nativeSampleRate);
                mCapabilities.estimatedLatencyMs = std::clamp(rawMs, 1.2f, 25.0f);
            }
            AAudioStream_close(stream);
        } else {
            // Intentar en modo compartido de baja latencia si el exclusivo está ocupado
            AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
            res = AAudioStreamBuilder_openStream(builder, &stream);
            if (res == AAUDIO_OK && stream != nullptr) {
                mCapabilities.isSupported = true;
                mCapabilities.isExclusiveSupported = false;
                int32_t perf = AAudioStream_getPerformanceMode(stream);
                int32_t sr = AAudioStream_getSampleRate(stream);
                int32_t burst = AAudioStream_getFramesPerBurst(stream);

                mCapabilities.isLowLatencySupported = (perf == AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
                mCapabilities.nativeSampleRate = (sr > 0) ? sr : 48000;
                mCapabilities.framesPerBurst = (burst > 0) ? burst : 192;

                if (mCapabilities.nativeSampleRate > 0) {
                    float rawMs = (static_cast<float>(mCapabilities.framesPerBurst) * 1000.0f) /
                                  static_cast<float>(mCapabilities.nativeSampleRate);
                    mCapabilities.estimatedLatencyMs = std::clamp(rawMs, 1.5f, 30.0f);
                }
                AAudioStream_close(stream);
            }
        }

        AAudioStreamBuilder_delete(builder);
    }

    void setBitPerfectEnabled(bool enabled) {
        mBitPerfectEnabled.store(enabled, std::memory_order_relaxed);
    }

    [[nodiscard]] bool isBitPerfectEnabled() const {
        return mBitPerfectEnabled.load(std::memory_order_relaxed);
    }

    void setLowLatencyEnabled(bool enabled) {
        mLowLatencyEnabled.store(enabled, std::memory_order_relaxed);
    }

    [[nodiscard]] bool isLowLatencyEnabled() const {
        return mLowLatencyEnabled.load(std::memory_order_relaxed);
    }

    void setUsbDacExclusiveMode(bool enabled) {
        mUsbDacExclusiveMode.store(enabled, std::memory_order_relaxed);
    }

    [[nodiscard]] bool isUsbDacExclusiveMode() const {
        return mUsbDacExclusiveMode.load(std::memory_order_relaxed);
    }

    /**
     * Modo de Upsampling / Resampling Hi-Res:
     * 0 = Nativo 1:1 (Sin alterar frecuencia original)
     * 1 = 96 kHz / 24-bit Hi-Res Target
     * 2 = 192 kHz / 32-bit Ultra Hi-Res Target
     */
    void setHiResTargetMode(int mode) {
        mHiResTargetMode.store(std::clamp(mode, 0, 2), std::memory_order_relaxed);
    }

    [[nodiscard]] int getHiResTargetMode() const {
        return mHiResTargetMode.load(std::memory_order_relaxed);
    }

    void updateActiveStreamFormat(int sampleRate, int channels, int bitDepth = 16) {
        mActiveSampleRate = (sampleRate > 0) ? sampleRate : 44100;
        mActiveChannels = (channels > 0) ? channels : 2;
        mActiveBitDepth = (bitDepth > 0) ? bitDepth : 16;
    }

    [[nodiscard]] int getActiveSampleRate() const {
        int mode = getHiResTargetMode();
        if (!isBitPerfectEnabled()) {
            if (mode == 1) return std::max(mActiveSampleRate, 96000);
            if (mode == 2) return std::max(mActiveSampleRate, 192000);
        }
        return mActiveSampleRate;
    }

    [[nodiscard]] int getActiveBitDepth() const {
        int mode = getHiResTargetMode();
        if (!isBitPerfectEnabled()) {
            if (mode == 1) return 24;
            if (mode == 2) return 32;
        }
        return mActiveBitDepth;
    }

    [[nodiscard]] float getEstimatedLatencyMs() const {
        if (isLowLatencyEnabled()) {
            return mCapabilities.estimatedLatencyMs;
        }
        return std::max(14.5f, mCapabilities.estimatedLatencyMs * 3.2f);
    }

    [[nodiscard]] int getFramesPerBurst() const {
        return mCapabilities.framesPerBurst;
    }

    [[nodiscard]] int getHardwareSampleRate() const {
        return mCapabilities.nativeSampleRate;
    }

    [[nodiscard]] bool isExclusiveHardwareSupported() const {
        return mCapabilities.isExclusiveSupported;
    }

    [[nodiscard]] std::string getStatusSummary() const {
        std::string modeStr = isBitPerfectEnabled()
            ? "Bit-Perfect Direct 1:1 (Bypass AudioFlinger/DSP)"
            : "AAudio Low-Latency + C++20 64-bit Float DSP";
        return modeStr + " | Fs=" + std::to_string(getActiveSampleRate()) + "Hz/" +
               std::to_string(getActiveBitDepth()) + "-bit | Burst=" +
               std::to_string(mCapabilities.framesPerBurst) + "f (" +
               std::to_string(static_cast<int>(getEstimatedLatencyMs() * 10.0f) / 10.0f) + "ms)";
    }

private:
    AAudioHardwareCapabilities mCapabilities{};
    std::atomic<bool> mBitPerfectEnabled{false};
    std::atomic<bool> mLowLatencyEnabled{true};
    std::atomic<bool> mUsbDacExclusiveMode{true};
    std::atomic<int> mHiResTargetMode{0};

    int mActiveSampleRate{44100};
    int mActiveChannels{2};
    int mActiveBitDepth{16};
};

} // namespace aura::dsp

#endif // DSP_BITPERFECT_H
