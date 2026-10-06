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

/**
 * Procesador C++20 de Clarificación de Voces (Vocal Clarity HD).
 * Extrae la componente central (Mid) donde reside la voz principal, atenúa el enmascaramiento
 * de graves fangosos (~220 Hz) y realza las bandas formantes de presencia (1.85 kHz) y
 * articulación/dicción (3.8 kHz) en 64-bit, con 3 modos estratégicos:
 * - Modo 0 (Natural): Realce vocal equilibrado conservando todo el cuerpo instrumental.
 * - Modo 1 (Nítido HD): Mayor articulación de consonantes y enfoque central definido.
 * - Modo 2 (Enfoque Vocal): Prioridad máxima a la voz atenuando el acompañamiento lateral (Side).
 */
class VocalClarityProcessor {
public:
    void init(int sampleRate) {
        mSampleRate = (sampleRate > 0) ? sampleRate : 44100;
        reconfigureFilters();
        reset();
    }

    void reset() {
        mMudCutFilter.reset();
        mPresenceFilter.reset();
        mArticulationFilter.reset();
    }

    void setEnabled(bool enabled) {
        if (mEnabled != enabled) {
            mEnabled = enabled;
            if (!enabled) reset();
        }
    }

    [[nodiscard]] bool isEnabled() const { return mEnabled; }

    void setStrength(double strength) {
        double clamped = std::clamp(strength, 0.0, 1.0);
        if (std::abs(mStrength - clamped) > 0.001) {
            mStrength = clamped;
            reconfigureFilters();
        }
    }

    [[nodiscard]] double getStrength() const { return mStrength; }

    void setMode(int mode) {
        int clamped = std::clamp(mode, 0, 2);
        if (mMode != clamped) {
            mMode = clamped;
            reconfigureFilters();
        }
    }

    [[nodiscard]] int getMode() const { return mMode; }

    inline void processSample(double& sampleL, double& sampleR) {
        if (!mEnabled || mStrength <= 0.001) return;

        // Descomposición Mid-Side (Mid = centro vocal, Side = apertura instrumental)
        double mid = (sampleL + sampleR) * 0.5;
        double side = (sampleL - sampleR) * 0.5;

        // Procesar el canal central con limpieza de frecuencias fangosas y realce de formantes vocales
        double cleanMid = mMudCutFilter.process(mid);
        cleanMid = mPresenceFilter.process(cleanMid);
        cleanMid = mArticulationFilter.process(cleanMid);

        // Atenuación selectiva del canal lateral según el modo elegido para despejar la voz
        double sideScale = 1.0;
        if (mMode == 0) {
            sideScale = 1.0 - (0.10 * mStrength);
        } else if (mMode == 1) {
            sideScale = 1.0 - (0.24 * mStrength);
        } else {
            sideScale = 1.0 - (0.52 * mStrength);
        }

        double outSide = side * sideScale;
        sampleL = cleanMid + outSide;
        sampleR = cleanMid - outSide;
    }

private:
    void reconfigureFilters() {
        double modeMultiplier = (mMode == 0) ? 0.85 : ((mMode == 1) ? 1.15 : 1.35);
        double mudCutDb = -3.8 * mStrength * modeMultiplier;
        double presenceBoostDb = 5.6 * mStrength * modeMultiplier;
        double articulationBoostDb = 4.2 * mStrength * modeMultiplier;

        mMudCutFilter.configurePeaking(mSampleRate, 220.0, mudCutDb, 1.15);
        mPresenceFilter.configurePeaking(mSampleRate, 1850.0, presenceBoostDb, 1.25);
        mArticulationFilter.configurePeaking(mSampleRate, 3800.0, articulationBoostDb, 1.40);
    }

    int mSampleRate{44100};
    bool mEnabled{false};
    double mStrength{0.65};
    int mMode{1}; // 0 = Natural, 1 = Nítido HD, 2 = Enfoque Vocal

    BiquadPeakingFilter mMudCutFilter{};
    BiquadPeakingFilter mPresenceFilter{};
    BiquadPeakingFilter mArticulationFilter{};
};

} // namespace aura::dsp

#endif // AURAMUSIC_DSP_FILTERS_H
