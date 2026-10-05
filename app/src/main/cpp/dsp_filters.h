#ifndef AURAMUSIC_DSP_FILTERS_H
#define AURAMUSIC_DSP_FILTERS_H

#include <array>
#include <cmath>
#include <algorithm>
#include <numbers>

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

    // Pone a cero los acumuladores de muestras previas (Buffer Flush)
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

// Limitador suave tipo saturación de cinta para prevenir recortes digitales duros (Anti-clipping)
inline double softClip(double x) {
    if (std::isnan(x) || std::isinf(x)) return 0.0;
    if (x > 1.2) return 1.0;
    if (x < -1.2) return -1.0;
    return x - (x * x * x) / 6.0;
}

} // namespace aura::dsp

#endif // AURAMUSIC_DSP_FILTERS_H
