// Visualizador espectral de 28 bandas para Aura Music.
// Rol arquitectónico: submódulo de `auramusic_dsp.h`.
// Concentra la envolvente espectral (ataque rápido / decaimiento lento),
// la intensidad global RMS y los pesos senoidales precalculados,
// sin asignaciones dinámicas en la ruta de audio (trata buffers con std::span).
#ifndef AURAMUSIC_DSP_VISUALIZER_H
#define AURAMUSIC_DSP_VISUALIZER_H

#include <array>
#include <span>
#include <cmath>
#include <algorithm>
#include <cstddef>

namespace aura::dsp {

// Tabla precalculada de pesos senoidales para las 28 bandas espectrales
// (evita 28 llamadas a std::sin por buffer PCM).
inline constexpr std::array<float, 28> kVisualizerBandWeights = {
    1.000000f, 1.039191f, 1.077886f, 1.115600f, 1.151860f, 1.186214f, 1.218231f,
    1.247487f, 1.273605f, 1.296238f, 1.315075f, 1.329846f, 1.340325f, 1.346329f,
    1.347724f, 1.344423f, 1.336389f, 1.323632f, 1.306210f, 1.284228f, 1.257836f,
    1.227230f, 1.192647f, 1.154365f, 1.112699f, 1.068000f, 1.020648f, 0.971048f
};

// Acumulador espectral reutilizable por NativeDspEngine y por el análisis
// de solo lectura del Modo Streaming Híbrido.
class SpectrumVisualizer {
public:
    static constexpr size_t kNumBands = 28;

    void reset(float idleLevel = 0.08f) {
        mIntensity = idleLevel;
    }

    [[nodiscard]] float intensity() const { return mIntensity; }

    void getBands(std::span<float> outBands) const {
        const size_t count = std::min(outBands.size(), mBands.size());
        for (size_t i = 0; i < count; ++i) {
            outBands[i] = mBands[i];
        }
    }

    // Integra la energía mono por banda y el RMS global con la curva
    // ataque rápido (0.65) / decaimiento lento (0.18) de Aura Music.
    void update(const std::array<double, kNumBands>& bandEnergy,
                size_t samplesPerBand,
                double sumSquares,
                size_t frames) {
        if (frames == 0 || samplesPerBand == 0) return;
        const double rms = std::sqrt(sumSquares / static_cast<double>(frames));
        const float targetIntensity = std::clamp(static_cast<float>(rms * 3.4), 0.06f, 1.0f);
        mIntensity = (mIntensity * 0.60f) + (targetIntensity * 0.40f);

        const double invSamplesPerBand = 1.0 / static_cast<double>(samplesPerBand);
        for (size_t b = 0; b < kNumBands; ++b) {
            const float avgBand = static_cast<float>(bandEnergy[b] * invSamplesPerBand);
            const float targetH =
                std::clamp(avgBand * 3.8f * kVisualizerBandWeights[b], 0.08f, 1.0f);
            if (targetH > mBands[b]) {
                mBands[b] = (mBands[b] * 0.35f) + (targetH * 0.65f);
            } else {
                mBands[b] = (mBands[b] * 0.82f) + (targetH * 0.18f);
            }
        }
    }

private:
    float mIntensity{0.15f};
    std::array<float, kNumBands> mBands{
        0.12f, 0.15f, 0.18f, 0.22f, 0.28f, 0.35f, 0.42f, 0.48f,
        0.52f, 0.55f, 0.58f, 0.60f, 0.58f, 0.55f, 0.52f, 0.48f,
        0.44f, 0.40f, 0.36f, 0.32f, 0.28f, 0.24f, 0.20f, 0.18f,
        0.15f, 0.13f, 0.11f, 0.09f
    };
};

} // namespace aura::dsp

#endif // AURAMUSIC_DSP_VISUALIZER_H
